package com.milestonepay.payments;

import com.milestonepay.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class FundingController {

    private final FundingService funding;

    FundingController(FundingService funding) {
        this.funding = funding;
    }

    record ReturnRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String orderId) {
    }

    /** Client only, milestone in criteria_approved. Returns the PayPal approve link (nothing is held yet). */
    @PostMapping("/milestones/{milestoneId}/fund")
    PaymentView fund(@PathVariable UUID milestoneId, CurrentUser caller) {
        return funding.fund(milestoneId, caller.id());
    }

    /**
     * After the client approved on PayPal. {@code orderId} is the {@code token} PayPal puts on the return URL.
     * Places the hold and moves the milestone to funded.
     */
    @PostMapping("/payments/paypal/return")
    FundingService.FundingResult paypalReturn(@Valid @RequestBody ReturnRequest req, CurrentUser caller) {
        return funding.completeFunding(req.orderId(), caller.id());
    }
}
