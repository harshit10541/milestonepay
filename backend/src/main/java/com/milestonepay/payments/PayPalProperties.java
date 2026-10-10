package com.milestonepay.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayPal settings, bound from the {@code paypal.*} keys in application.yml (which read env vars).
 *
 * @param baseUrl      sandbox: https://api-m.sandbox.paypal.com
 * @param clientId     REST app client id
 * @param clientSecret REST app secret
 * @param returnUrl    where PayPal sends the buyer's browser after they approve the hold
 * @param cancelUrl    where PayPal sends the buyer's browser if they cancel
 */
@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        String returnUrl,
        String cancelUrl
) {
}
