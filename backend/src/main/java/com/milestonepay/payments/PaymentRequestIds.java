package com.milestonepay.payments;

/**
 * Stable {@code PayPal-Request-Id} values, one per milestone and action.
 * The same milestone and action always give the same id, so a retry after a timeout or a
 * crash gets PayPal's first answer back instead of creating a second order or a second capture.
 */
final class PaymentRequestIds {

    private PaymentRequestIds() {
    }

    static String order(String milestoneId) {
        return "order-" + milestoneId;
    }

    static String authorize(String milestoneId) {
        return "authorize-" + milestoneId;
    }

    static String capture(String milestoneId) {
        return "capture-" + milestoneId;
    }

    static String voidHold(String milestoneId) {
        return "void-" + milestoneId;
    }
}
