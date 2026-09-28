package com.tenpo.challenge.controller;

import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.service.HistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    private static final String HISTORY_URL = "/api/v1/history";
    private static final Locale SPANISH_ARGENTINA = Locale.forLanguageTag("es-AR");

    private static final CallHistory NEWEST = new CallHistory(2L, Instant.parse("2026-01-01T12:05:00Z"), "GET",
            "/api/v1/calculate", "num1=5&num2=5", 200, "{\"result\":11.00}");
    private static final CallHistory OLDEST = new CallHistory(1L, Instant.parse("2026-01-01T12:00:00Z"), "GET",
            "/api/v1/calculate", "num1=abc", 400, "{\"title\":\"Invalid request parameters\"}");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HistoryService historyService;

    @Test
    void returnsTheFirstPageWithDefaultValues() throws Exception {
        given(historyService.findPage(0, 20)).willReturn(page(List.of(NEWEST, OLDEST), 0, 20, 2));

        mockMvc.perform(get(HISTORY_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].timestamp").value("2026-01-01T12:05:00Z"))
                .andExpect(jsonPath("$.items[0].method").value("GET"))
                .andExpect(jsonPath("$.items[0].path").value("/api/v1/calculate"))
                .andExpect(jsonPath("$.items[0].queryString").value("num1=5&num2=5"))
                .andExpect(jsonPath("$.items[0].status").value(200))
                .andExpect(jsonPath("$.items[0].responseBody").value("{\"result\":11.00}"))
                .andExpect(jsonPath("$.items[1].id").value(1))
                .andExpect(jsonPath("$.items[1].status").value(400));
    }

    @Test
    void returnsTheRequestedPage() throws Exception {
        given(historyService.findPage(2, 5)).willReturn(page(List.of(OLDEST), 2, 5, 11));

        mockMvc.perform(get(HISTORY_URL).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void acceptsTheMaximumPageSize() throws Exception {
        given(historyService.findPage(0, 100)).willReturn(page(List.of(), 0, 100, 0));

        mockMvc.perform(get(HISTORY_URL).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page | -1  | page must be greater than or equal to 0",
            "size | 0   | size must be greater than or equal to 1",
            "size | 101 | size must be less than or equal to 100",
            "page | abc | page must be a valid number",
            "size | 1.5 | size must be a valid number"
    })
    void rejectsInvalidPagingParameters(String parameter, String value, String detail) throws Exception {
        mockMvc.perform(get(HISTORY_URL).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request parameters"))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.errors." + parameter).exists());

        verify(historyService, never()).findPage(anyInt(), anyInt());
    }

    @Test
    void reportsEveryInvalidParameter() throws Exception {
        mockMvc.perform(get(HISTORY_URL).param("page", "-1").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "page must be greater than or equal to 0; size must be less than or equal to 100"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "size=0   | size must be greater than or equal to 1",
            "size=101 | size must be less than or equal to 100",
            "page=-1  | page must be greater than or equal to 0"
    })
    void answersInEnglishRegardlessOfTheServerAndClientLocale(String query, String detail) throws Exception {
        Locale originalDefault = Locale.getDefault();
        Locale.setDefault(SPANISH_ARGENTINA);
        try {
            mockMvc.perform(get(HISTORY_URL + "?" + query)
                            .header(HttpHeaders.ACCEPT_LANGUAGE, SPANISH_ARGENTINA.toLanguageTag()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(detail));
        } finally {
            Locale.setDefault(originalDefault);
        }
    }

    private static Page<CallHistory> page(List<CallHistory> content, int page, int size, long total) {
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }
}
