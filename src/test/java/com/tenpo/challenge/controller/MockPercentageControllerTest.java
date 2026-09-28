package com.tenpo.challenge.controller;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tenpo.challenge.controller.MockPercentageController.MAX_BASIS_POINTS;
import static com.tenpo.challenge.controller.MockPercentageController.MIN_BASIS_POINTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MockPercentageController.class)
class MockPercentageControllerTest {

    private static final String PERCENTAGE_URL = "/mock/percentage";
    private static final Pattern PERCENTAGE_JSON = Pattern.compile("\\{\"percentage\":(\\d+\\.\\d{2})}");

    private final Random seededRandom = new Random(42);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RandomGenerator randomGenerator;

    @ParameterizedTest
    @CsvSource({
            "500, 5.00",
            "1234, 12.34",
            "2000, 20.00"
    })
    void returnsTheRandomValueAsAPercentageWithTwoDecimals(int basisPoints, String expected) throws Exception {
        given(randomGenerator.nextInt(MIN_BASIS_POINTS, MAX_BASIS_POINTS + 1)).willReturn(basisPoints);

        mockMvc.perform(get(PERCENTAGE_URL))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"percentage\":" + expected + "}"));
    }

    @RepeatedTest(20)
    void returnsAPercentageWithinRange() throws Exception {
        willAnswer(invocation -> seededRandom.nextInt(invocation.<Integer>getArgument(0), invocation.getArgument(1)))
                .given(randomGenerator).nextInt(MIN_BASIS_POINTS, MAX_BASIS_POINTS + 1);

        String body = mockMvc.perform(get(PERCENTAGE_URL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = PERCENTAGE_JSON.matcher(body);
        assertThat(matcher.matches()).as("body %s has a 2-decimal percentage", body).isTrue();
        assertThat(new BigDecimal(matcher.group(1))).isBetween(new BigDecimal("5.00"), new BigDecimal("20.00"));
    }
}
