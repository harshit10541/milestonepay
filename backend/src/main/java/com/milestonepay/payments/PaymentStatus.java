package com.milestonepay.payments;

/** One funding attempt's state, stored as the name (matches the CHECK on payments.status in V2). */
public enum PaymentStatus {
    /** PayPal order exists; the buyer still has to approve it. */
    ORDER_CREATED,
    /** Money is held on the buyer's account. */
    AUTHORIZED,
    CAPTURED,
    VOIDED,
    EXPIRED,
    /** Abandoned or refused before any hold, e.g. the order expired unapproved. A new attempt can follow. */
    FAILED
}
