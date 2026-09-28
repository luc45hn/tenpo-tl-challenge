package com.tenpo.challenge.controller;

import com.tenpo.challenge.dto.CalculationRequest;
import com.tenpo.challenge.dto.CalculationResponse;
import com.tenpo.challenge.service.CalculationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CalculationController {

    private final CalculationService calculationService;

    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @GetMapping("/calculate")
    public CalculationResponse calculate(@Valid @ModelAttribute CalculationRequest request) {
        return new CalculationResponse(calculationService.calculate(request.num1(), request.num2()));
    }
}
