package com.milestonepay.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void convertsMinorUnitsToPayPalDecimal() {
        assertThat(Money.of(30000, "USD").toDecimalString()).isEqualTo("300.00");
        assertThat(Money.of(5, "usd").toDecimalString()).isEqualTo("0.05");
        assertThat(Money.of(500, "JPY").toDecimalString()).isEqualTo("500");
    }

    @Test
    void parsesPayPalDecimalBackToMinorUnits() {
        assertThat(Money.fromDecimalString("300.00", "USD")).isEqualTo(Money.of(30000, "USD"));
        assertThat(Money.fromDecimalString("12.5", "USD")).isEqualTo(Money.of(1250, "USD"));
    }

    @Test
    void rejectsBadValues() {
        assertThatThrownBy(() -> Money.of(0, "USD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(100, "XYZ1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(100, "QQQ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.fromDecimalString("1.001", "USD")).isInstanceOf(ArithmeticException.class);
    }
}
