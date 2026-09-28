package com.tenpo.challenge.service;

import com.tenpo.challenge.exception.PercentageUnavailableException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Orchestrates the calculation: resolves the percentage (effect) and applies it (pure).
 * Free of any web or DTO dependency.
 */
@Service
public class CalculationService {

    private final PercentageService percentageService;

    public CalculationService(PercentageService percentageService) {
        this.percentageService = percentageService;
    }

    /**
     * Adds both numbers and applies the current percentage.
     *
     * @throws PercentageUnavailableException if the percentage cannot be obtained
     */
    public BigDecimal calculate(BigDecimal num1, BigDecimal num2) {
        BigDecimal percentage = percentageService.getPercentage()
                .getOrElseThrow(PercentageUnavailableException::new);
        return PercentageCalculator.applyPercentage(num1, num2, percentage);
    }
}
