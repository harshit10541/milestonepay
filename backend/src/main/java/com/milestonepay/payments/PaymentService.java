package com.milestonepay.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.milestonepay.common.Money;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The payments module's public API: the only way the rest of the backend moves money.
 * <p>
 * Flow: {@link #createAuthorizeOrder} → buyer approves on PayPal → {@link #authorizeOrder} (money held)
 * → {@link #capture} (released to the payee) or {@link #voidAuthorization} (hold cancelled).
 * <p>
 * Stateless for now: callers keep the returned ids. Persisting them in a payments table comes
 * with the Week 1 schema task.
 */
@Service
public class PaymentService {

    /** PayPal ids are upper-case letters and digits; milestone ids are ours (UUIDs or slugs). */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final PayPalClient paypal;
    private final PayPalProperties props;

    PaymentService(PayPalClient paypal, PayPalProperties props) {
        this.paypal = paypal;
        this.props = props;
    }

    /**
     * Creates a PayPal order with intent AUTHORIZE for one milestone.
     * Nothing is held yet: the buyer must open {@link CreatedOrder#approveUrl()} and approve.
     */
    public CreatedOrder createAuthorizeOrder(String milestoneId, Money amount, String description) {
        requireSafeId(milestoneId, "milestoneId");
        Map<String, Object> body = Map.of(
                "intent", "AUTHORIZE",
                "purchase_units", List.of(Map.of(
                        "reference_id", milestoneId,
                        "custom_id", milestoneId,
                        "description", truncate(description == null ? "MilestonePay milestone" : description, 127),
                        "amount", Map.of(
                                "currency_code", amount.currency(),
                                "value", amount.toDecimalString())
                )),
                "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                        "return_url", withMilestone(props.returnUrl(), milestoneId),
                        "cancel_url", withMilestone(props.cancelUrl(), milestoneId),
                        "user_action", "CONTINUE",
                        "shipping_preference", "NO_SHIPPING")))
        );
        JsonNode order = paypal.post("/v2/checkout/orders", body, PaymentRequestIds.order(milestoneId));
        String approveUrl = link(order, "payer-action");
        if (approveUrl == null) {
            approveUrl = link(order, "approve");
        }
        if (approveUrl == null) {
            throw new PayPalException(200, null, null, "order " + order.path("id").asText() + " has no approve link");
        }
        return new CreatedOrder(order.path("id").asText(), order.path("status").asText(), approveUrl);
    }

    /**
     * After the buyer approves: places the hold. This is the moment the milestone becomes funded.
     * Fails with issue ORDER_NOT_APPROVED if the buyer has not approved yet.
     */
    public AuthorizationResult authorizeOrder(String milestoneId, String orderId) {
        requireSafeId(milestoneId, "milestoneId");
        requireSafeId(orderId, "orderId");
        JsonNode order = paypal.post("/v2/checkout/orders/" + orderId + "/authorize", Map.of(),
                PaymentRequestIds.authorize(milestoneId));
        JsonNode auth = order.path("purchase_units").path(0).path("payments").path("authorizations").path(0);
        if (auth.isMissingNode()) {
            throw new PayPalException(200, null, null, "order " + orderId + " returned no authorization");
        }
        return toAuthorization(auth);
    }

    /** Releases the held money to the payee (full amount, final capture). */
    public CaptureResult capture(String milestoneId, String authorizationId) {
        requireSafeId(milestoneId, "milestoneId");
        requireSafeId(authorizationId, "authorizationId");
        JsonNode capture = paypal.post("/v2/payments/authorizations/" + authorizationId + "/capture",
                Map.of("final_capture", true), PaymentRequestIds.capture(milestoneId));
        return new CaptureResult(capture.path("id").asText(), capture.path("status").asText(), money(capture.path("amount")));
    }

    /** Cancels the hold; the buyer keeps their money. */
    public AuthorizationResult voidAuthorization(String milestoneId, String authorizationId) {
        requireSafeId(milestoneId, "milestoneId");
        requireSafeId(authorizationId, "authorizationId");
        JsonNode voided = paypal.post("/v2/payments/authorizations/" + authorizationId + "/void",
                Map.of(), PaymentRequestIds.voidHold(milestoneId));
        // PayPal may answer 204 with no body; then read the authorization to report its status.
        return voided.has("id") ? toAuthorization(voided) : getAuthorization(authorizationId);
    }

    /** Current state of a hold, straight from PayPal. Use when in doubt (e.g. after a timeout). */
    public AuthorizationResult getAuthorization(String authorizationId) {
        requireSafeId(authorizationId, "authorizationId");
        return toAuthorization(paypal.get("/v2/payments/authorizations/" + authorizationId));
    }

    /** Current order status, e.g. PAYER_ACTION_REQUIRED, APPROVED, COMPLETED. */
    public String getOrderStatus(String orderId) {
        requireSafeId(orderId, "orderId");
        return paypal.get("/v2/checkout/orders/" + orderId).path("status").asText();
    }

    private static AuthorizationResult toAuthorization(JsonNode auth) {
        String expiration = auth.path("expiration_time").asText(null);
        return new AuthorizationResult(
                auth.path("id").asText(),
                auth.path("status").asText(),
                money(auth.path("amount")),
                expiration == null ? null : Instant.parse(expiration));
    }

    private static Money money(JsonNode amount) {
        if (amount.isMissingNode() || !amount.has("value")) {
            return null;
        }
        return Money.fromDecimalString(amount.path("value").asText(), amount.path("currency_code").asText());
    }

    private static String link(JsonNode resource, String rel) {
        for (JsonNode l : resource.path("links")) {
            if (rel.equals(l.path("rel").asText())) {
                return l.path("href").asText();
            }
        }
        return null;
    }

    private static String withMilestone(String url, String milestoneId) {
        return url + (url.contains("?") ? "&" : "?") + "milestoneId=" + milestoneId;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void requireSafeId(String value, String name) {
        if (value == null || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be 1-64 letters, digits or dashes");
        }
    }
}
