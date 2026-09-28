package com.tenpo.challenge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculationServiceTest {

    private final CalculationService service = new CalculationService();

    @ParameterizedTest
    @CsvSource({
            "5, 5, 10",
            "0, 0, 0",
            "-3, 7, 4",
            "0.1, 0.2, 0.3",
            "123456789012345678901234567890, 1, 123456789012345678901234567891"
    })
    void calculateReturnsTheSumOfBothNumbers(String num1, String num2, String expected) {
        BigDecimal result = service.calculate(new BigDecimal(num1), new BigDecimal(num2));

        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void calculateIsExactForDecimals() {
        BigDecimal result = service.calculate(new BigDecimal("0.1"), new BigDecimal("0.2"));

        assertThat(result).isEqualTo(new BigDecimal("0.3"));
    }
}
