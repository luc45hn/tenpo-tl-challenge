package com.tenpo.challenge.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Business logic of the calculation endpoint. Free of any web or DTO dependency.
 */
@Service
public class CalculationService {

    /**
     * Pure function: returns the sum of both numbers, with no side effects.
     */
    public BigDecimal calculate(BigDecimal num1, BigDecimal num2) {
        return num1.add(num2);
    }
}
