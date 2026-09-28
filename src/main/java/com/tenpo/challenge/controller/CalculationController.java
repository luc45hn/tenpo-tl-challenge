package com.tenpo.challenge.controller;

import com.tenpo.challenge.dto.CalculationRequest;
import com.tenpo.challenge.dto.CalculationResponse;
import com.tenpo.challenge.service.CalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Calculation")
@RestController
@RequestMapping("/api/v1")
public class CalculationController {

    private final CalculationService calculationService;

    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @Operation(
            summary = "Add two numbers and apply the current percentage",
            description = "Returns (num1 + num2) * (1 + percentage / 100), rounded to 2 decimals. The percentage "
                    + "comes from an external service, is cached and falls back to the last known value when "
                    + "the service is unavailable.")
    @ApiResponse(responseCode = "200", description = "The result of the calculation.")
    @ApiResponse(responseCode = "400", description = "num1 or num2 is missing or not a valid number.")
    @ApiResponse(responseCode = "503",
            description = "The percentage could not be obtained and no previous value is available.")
    @GetMapping("/calculate")
    public CalculationResponse calculate(@Valid @ParameterObject @ModelAttribute CalculationRequest request) {
        return new CalculationResponse(calculationService.calculate(request.num1(), request.num2()));
    }
}
