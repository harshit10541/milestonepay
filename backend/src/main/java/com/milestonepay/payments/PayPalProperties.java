package com.milestonepay.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayPal settings, bound from the {@code paypal.*} keys in application.yml (which read env vars).
 *
 * @param baseUrl      sandbox: https://api-m.sandbox.paypal.com
 * @param clientId     REST app client id
 * @param clientSecret REST app secret
 * @param returnUrl    dev flow only: where PayPal sends the buyer's browser after they approve the hold
 * @param cancelUrl    dev flow only: where PayPal sends the buyer's browser if they cancel
 * @param milestoneReturnUrl milestone funding: where the buyer lands after approving (later the frontend,
 *                           which then calls {@code POST /payments/paypal/return})
 * @param milestoneCancelUrl milestone funding: where the buyer lands after cancelling
 */
@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        String returnUrl,
        String cancelUrl,
        String milestoneReturnUrl,
        String milestoneCancelUrl
) {
}
