package com.tenpo.challenge.controller;

import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.PercentageService;
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalculationController.class)
@Import(CalculationService.class)
class CalculationControllerTest {

    private static final String CALCULATE_URL = "/api/v1/calculate";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PercentageService percentageService;

    @Test
    void appliesThePercentageToTheSum() throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("10")));

        mockMvc.perform(get(CALCULATE_URL).param("num1", "5").param("num2", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"result\":11.00}"));
    }

    @Test
    void acceptsDecimalAndNegativeNumbers() throws Exception {
        given(percentageService.getPercentage()).willReturn(Try.success(new BigDecimal("12.5")));

        mockMvc.perform(get(CALCULATE_URL).param("num1", "-1.5").param("num2", "2.5"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"result\":1.13}"));
    }

    @Test
    void returnsServiceUnavailableWhenThePercentageCannotBeObtained() throws Exception {
        given(percentageService.getPercentage())
                .willReturn(Try.failure(new ResourceAccessException("Connection refused")));

        mockMvc.perform(get(CALCULATE_URL).param("num1", "5").param("num2", "5"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Percentage service unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        "The percentage could not be obtained from the external service. Please try again later."));
    }

    @Test
    void rejectsMissingParameter() throws Exception {
        mockMvc.perform(get(CALCULATE_URL).param("num1", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("num2 is required"))
                .andExpect(jsonPath("$.errors.num2").value("is required"));

        verify(percentageService, never()).getPercentage();
    }

    @Test
    void rejectsBothParametersMissing() throws Exception {
        mockMvc.perform(get(CALCULATE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("num1 is required; num2 is required"));

        verify(percentageService, never()).getPercentage();
    }

    @Test
    void rejectsEmptyParameter() throws Exception {
        mockMvc.perform(get(CALCULATE_URL).param("num1", "").param("num2", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.num1").value("is required"));

        verify(percentageService, never()).getPercentage();
    }

    @Test
    void rejectsNonNumericParameter() throws Exception {
        mockMvc.perform(get(CALCULATE_URL).param("num1", "abc").param("num2", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value("num1 must be a valid number"))
                .andExpect(jsonPath("$.errors.num1").value("must be a valid number"));

        verify(percentageService, never()).getPercentage();
    }
}
