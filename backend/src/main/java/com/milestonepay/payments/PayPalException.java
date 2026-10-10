package com.milestonepay.payments;

/**
 * A PayPal call failed. Carries what PayPal told us so callers and logs can act on it.
 *
 * @see <a href="https://developer.paypal.com/api/rest/responses/">PayPal error responses</a>
 */
public class PayPalException extends RuntimeException {

    private final int httpStatus;
    private final String issue;
    private final String debugId;

    public PayPalException(int httpStatus, String issue, String debugId, String message) {
        super("PayPal " + httpStatus + (issue == null ? "" : " " + issue) + ": " + message
                + (debugId == null ? "" : " (debug_id " + debugId + ")"));
        this.httpStatus = httpStatus;
        this.issue = issue;
        this.debugId = debugId;
    }

    /** HTTP status PayPal returned, or 0 when PayPal could not be reached. */
    public int httpStatus() {
        return httpStatus;
    }

    /** PayPal's issue code, e.g. ORDER_NOT_APPROVED or AUTHORIZATION_ALREADY_CAPTURED. May be null. */
    public String issue() {
        return issue;
    }

    /** Quote this to PayPal support. May be null. */
    public String debugId() {
        return debugId;
    }
}
