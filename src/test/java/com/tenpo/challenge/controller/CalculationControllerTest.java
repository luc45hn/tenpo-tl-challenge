package com.tenpo.challenge.controller;

import com.tenpo.challenge.service.CalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalculationController.class)
class CalculationControllerTest {

    private static final String CALCULATE_URL = "/api/v1/calculate";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalculationService calculationService;

    @Test
    void returnsTheCalculationResult() throws Exception {
        given(calculationService.calculate(new BigDecimal("5"), new BigDecimal("5")))
                .willReturn(new BigDecimal("10"));

        mockMvc.perform(get(CALCULATE_URL).param("num1", "5").param("num2", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(10));
    }

    @Test
    void acceptsDecimalAndNegativeNumbers() throws Exception {
        given(calculationService.calculate(new BigDecimal("-1.5"), new BigDecimal("2.25")))
                .willReturn(new BigDecimal("0.75"));

        mockMvc.perform(get(CALCULATE_URL).param("num1", "-1.5").param("num2", "2.25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(0.75));
    }

    @Test
    void rejectsMissingParameter() throws Exception {
        mockMvc.perform(get(CALCULATE_URL).param("num1", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("num2 is required"))
                .andExpect(jsonPath("$.errors.num2").value("is required"));

        verify(calculationService, never()).calculate(any(), any());
    }

    @Test
    void rejectsBothParametersMissing() throws Exception {
        mockMvc.perform(get(CALCULATE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("num1 is required; num2 is required"));

        verify(calculationService, never()).calculate(any(), any());
    }

    @Test
    void rejectsEmptyParameter() throws Exception {
        mockMvc.perform(get(CALCULATE_URL).param("num1", "").param("num2", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.num1").value("is required"));

        verify(calculationService, never()).calculate(any(), any());
    }

    @Test
    void rejectsNonNumericParameter() throws Exception {
        mockMvc.perform(get(CALCULATE_URL).param("num1", "abc").param("num2", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("num1 must be a valid number"))
                .andExpect(jsonPath("$.errors.num1").value("must be a valid number"));

        verify(calculationService, never()).calculate(any(), any());
    }
}
