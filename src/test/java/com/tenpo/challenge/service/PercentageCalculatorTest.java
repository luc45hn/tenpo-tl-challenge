package com.tenpo.challenge.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageCalculatorTest {

    @ParameterizedTest(name = "({0} + {1}) with {2}% = {3}")
    @CsvSource({
            // challenge example
            "5, 5, 10, 11.00",
            "5, 5, 0, 10.00",
            "5, 5, 5.00, 10.50",
            "5, 5, 20.00, 12.00",
            "0, 0, 12.34, 0.00",
            "-3, 7, 10, 4.40",
            "-10, 0, 10, -11.00",
            // exact decimal arithmetic, no floating point drift
            "0.1, 0.2, 0, 0.30",
            "123456789012345678901234567890, 1, 10, 135802467913580246791358024680.10"
    })
    void appliesThePercentageToTheSum(String num1, String num2, String percentage, String expected) {
        assertThat(calculate(num1, num2, percentage)).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "({0} + {1}) with {2}% = {3}")
    @CsvSource({
            // 11.2345 rounds down
            "10, 0, 12.345, 11.23",
            // exact tie 1.125 rounds up with HALF_UP (HALF_EVEN would give 1.12)
            "1, 0, 12.5, 1.13",
            // negative tie rounds away from zero
            "-1, 0, 12.5, -1.13",
            // inputs are not rounded before summing: 0.004 + 0.004 = 0.008 -> 0.01, not 0.00
            "0.004, 0.004, 0, 0.01"
    })
    void roundsHalfUpToTwoDecimalsOnlyAtTheEnd(String num1, String num2, String percentage, String expected) {
        assertThat(calculate(num1, num2, percentage)).isEqualTo(new BigDecimal(expected));
    }

    private static BigDecimal calculate(String num1, String num2, String percentage) {
        return PercentageCalculator.applyPercentage(new BigDecimal(num1), new BigDecimal(num2), new BigDecimal(percentage));
    }
}
