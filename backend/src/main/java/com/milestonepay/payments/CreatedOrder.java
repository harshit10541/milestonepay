package com.milestonepay.payments;

/**
 * A PayPal order with intent AUTHORIZE, waiting for the buyer to approve it.
 *
 * @param orderId    PayPal order id; PayPal also sends it back as {@code token} on the return URL
 * @param status     PayPal order status, normally PAYER_ACTION_REQUIRED or CREATED
 * @param approveUrl send the buyer's browser here to approve the hold
 */
public record CreatedOrder(String orderId, String status, String approveUrl) {
}
