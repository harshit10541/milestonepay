package com.milestonepay.common;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * An amount of money in integer minor units (e.g. $300.00 = 30000 USD).
 * The only place we convert to and from decimal strings is the PayPal boundary.
 */
public record Money(long minor, String currency) {

    public Money {
        if (minor <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got " + minor);
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO code, got " + currency);
        }
        currency = currency.toUpperCase();
        Currency.getInstance(currency); // throws IllegalArgumentException for unknown codes
    }

    public static Money of(long minor, String currency) {
        return new Money(minor, currency);
    }

    /** PayPal's decimal string, e.g. 30000 USD becomes "300.00" and 500 JPY stays "500". */
    public String toDecimalString() {
        return BigDecimal.valueOf(minor, fractionDigits(currency)).toPlainString();
    }

    /** Parses PayPal's decimal string back into minor units. Rejects more decimals than the currency has. */
    public static Money fromDecimalString(String value, String currency) {
        int digits = fractionDigits(currency);
        long minor = new BigDecimal(value).movePointRight(digits).longValueExact();
        return new Money(minor, currency);
    }

    private static int fractionDigits(String currency) {
        int digits = Currency.getInstance(currency.toUpperCase()).getDefaultFractionDigits();
        return Math.max(digits, 0);
    }
}
