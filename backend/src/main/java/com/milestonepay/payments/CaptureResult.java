package com.milestonepay.payments;

import com.milestonepay.common.Money;

/**
 * Money taken from a hold (our "release").
 *
 * @param captureId PayPal capture id, needed later for refunds
 * @param status    COMPLETED, or PENDING while PayPal reviews it
 * @param amount    captured amount, in minor units; null if PayPal omitted it
 */
public record CaptureResult(String captureId, String status, Money amount) {
}
