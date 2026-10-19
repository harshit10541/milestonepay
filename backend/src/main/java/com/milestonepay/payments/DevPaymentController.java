package com.milestonepay.payments;

import com.milestonepay.common.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only endpoints to run the Week 1 gate by hand: hold money, then capture or void it.
 * Only exists with the {@code dev} profile, so it can never ship to production.
 * No auth or ownership checks here on purpose; real endpoints go through the milestone API.
 */
@Profile("dev")
@RestController
@RequestMapping("/dev/payments")
class DevPaymentController {

    /** The dev flow makes up a fresh milestone id per run, so it never needs a second attempt. */
    private static final int DEV_ATTEMPT = 1;

    private final PaymentService payments;
    private final PayPalProperties props;

    DevPaymentController(PaymentService payments, PayPalProperties props) {
        this.payments = payments;
        this.props = props;
    }

    record CreateOrderRequest(
            @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String milestoneId,
            @Positive long amountMinor,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            String description) {
    }

    /** Step 1: create the order. A fresh milestone id is made up when none is given. */
    @PostMapping("/orders")
    Map<String, Object> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        String milestoneId = req.milestoneId() != null ? req.milestoneId() : "dev-" + UUID.randomUUID();
        CreatedOrder order = payments.createAuthorizeOrder(milestoneId, DEV_ATTEMPT,
                Money.of(req.amountMinor(), req.currency()), req.description(), props.returnUrl(), props.cancelUrl());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("milestoneId", milestoneId);
        out.put("orderId", order.orderId());
        out.put("status", order.status());
        out.put("approveUrl", order.approveUrl());
        out.put("next", "Open approveUrl in a private window and approve as the sandbox Personal account.");
        return out;
    }

    /** Step 2: PayPal redirects the buyer's browser here after approval; we place the hold right away. */
    @GetMapping("/return")
    Map<String, Object> paypalReturn(@RequestParam String milestoneId, @RequestParam("token") String orderId) {
        AuthorizationResult auth = payments.authorizeOrder(milestoneId, DEV_ATTEMPT, orderId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("milestoneId", milestoneId);
        out.put("orderId", orderId);
        out.put("authorization", auth);
        out.put("next", "POST /dev/payments/authorizations/" + auth.authorizationId()
                + "/capture?milestoneId=" + milestoneId + "  (or /void)");
        return out;
    }

    @GetMapping("/cancel")
    Map<String, Object> paypalCancel(@RequestParam String milestoneId, @RequestParam("token") String orderId) {
        return Map.of("milestoneId", milestoneId, "orderId", orderId, "result", "Buyer cancelled; nothing is held.");
    }

    /**
     * Milestone funding lands here after PayPal approval until the frontend exists. Moves no money: it only
     * shows the order id to send to {@code POST /payments/paypal/return}, which checks who is calling.
     */
    @GetMapping("/approved")
    Map<String, Object> milestoneApproved(@RequestParam String milestoneId, @RequestParam("token") String orderId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("milestoneId", milestoneId);
        out.put("orderId", orderId);
        out.put("next", "POST /payments/paypal/return {\"orderId\":\"" + orderId + "\"} with the client's X-User-Id");
        return out;
    }

    @GetMapping("/cancelled")
    Map<String, Object> milestoneCancelled(@RequestParam String milestoneId, @RequestParam("token") String orderId) {
        return Map.of("milestoneId", milestoneId, "orderId", orderId,
                "result", "Buyer cancelled on PayPal; nothing is held. POST /milestones/" + milestoneId
                        + "/fund again to get the approve link.");
    }

    /** Step 3a: release the money. */
    @PostMapping("/authorizations/{authorizationId}/capture")
    CaptureResult capture(@PathVariable String authorizationId, @RequestParam String milestoneId) {
        return payments.capture(milestoneId, DEV_ATTEMPT, authorizationId);
    }

    /** Step 3b: cancel the hold. */
    @PostMapping("/authorizations/{authorizationId}/void")
    AuthorizationResult voidHold(@PathVariable String authorizationId, @RequestParam String milestoneId) {
        return payments.voidAuthorization(milestoneId, DEV_ATTEMPT, authorizationId);
    }

    @GetMapping("/authorizations/{authorizationId}")
    AuthorizationResult authorization(@PathVariable String authorizationId) {
        return payments.getAuthorization(authorizationId);
    }

    @GetMapping("/orders/{orderId}")
    Map<String, String> order(@PathVariable String orderId) {
        return Map.of("orderId", orderId, "status", payments.getOrderStatus(orderId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(PayPalException.class)
    ResponseEntity<Map<String, Object>> paypalFailed(PayPalException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        body.put("paypalStatus", e.httpStatus());
        body.put("issue", e.issue());
        body.put("debugId", e.debugId());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}
