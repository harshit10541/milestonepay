package com.milestonepay.payments;

import java.time.Instant;

/**
 * A funding attempt as other modules and the API see it. {@code approveUrl} is where the client approves
 * the hold on PayPal; it only matters while {@code status} is ORDER_CREATED.
 */
public record PaymentView(int attempt, PaymentStatus status, long amountMinor, String currency,
                          String paypalOrderId, String approveUrl, String authorizationId,
                          Instant authorizationExpiresAt, Instant createdAt, Instant updatedAt) {
}
