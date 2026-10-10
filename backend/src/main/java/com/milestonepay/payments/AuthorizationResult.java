package com.milestonepay.payments;

import com.milestonepay.common.Money;

import java.time.Instant;

/**
 * A hold on the buyer's funds.
 *
 * @param authorizationId use this to capture or void
 * @param status          CREATED (held), CAPTURED, VOIDED, EXPIRED, ...
 * @param amount          held amount, in minor units
 * @param expiresAt       when PayPal drops the hold if we neither capture nor void; may be null
 */
public record AuthorizationResult(String authorizationId, String status, Money amount, Instant expiresAt) {
}
