package com.milestonepay.payments;

/**
 * Stable {@code PayPal-Request-Id} values, one per milestone, funding attempt and action, e.g.
 * {@code capture-<milestoneId>-1}.
 * The same inputs always give the same id, so a retry after a timeout or a crash gets PayPal's first
 * answer back instead of creating a second order or a second capture. The attempt number changes only
 * when a milestone is funded again after an earlier order was abandoned, so PayPal treats the new
 * order as new instead of replaying the old one.
 */
final class PaymentRequestIds {

    private PaymentRequestIds() {
    }

    static String order(String milestoneId, int attempt) {
        return "order-" + milestoneId + "-" + attempt;
    }

    static String authorize(String milestoneId, int attempt) {
        return "authorize-" + milestoneId + "-" + attempt;
    }

    static String capture(String milestoneId, int attempt) {
        return "capture-" + milestoneId + "-" + attempt;
    }

    static String voidHold(String milestoneId, int attempt) {
        return "void-" + milestoneId + "-" + attempt;
    }
}
