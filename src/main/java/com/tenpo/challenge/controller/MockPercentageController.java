package com.tenpo.challenge.controller;

import com.tenpo.challenge.dto.PercentageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

/**
 * Mock of the external percentage service. Not part of the public API.
 */
@RestController
@RequestMapping("/mock")
public class MockPercentageController {

    static final int MIN_BASIS_POINTS = 500;
    static final int MAX_BASIS_POINTS = 2000;
    private static final int SCALE = 2;

    private final RandomGenerator randomGenerator;

    public MockPercentageController(RandomGenerator randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    /**
     * Returns a random percentage between 5.00 and 20.00 (both inclusive), with 2 decimals.
     */
    @GetMapping("/percentage")
    public PercentageResponse percentage() {
        int basisPoints = randomGenerator.nextInt(MIN_BASIS_POINTS, MAX_BASIS_POINTS + 1);
        return new PercentageResponse(BigDecimal.valueOf(basisPoints, SCALE));
    }
}
