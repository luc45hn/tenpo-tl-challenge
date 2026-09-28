package com.tenpo.challenge.service;

import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.repository.CallHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HistoryServiceTest {

    private final CallHistoryRepository repository = mock(CallHistoryRepository.class);
    private final HistoryService service = new HistoryService(repository);

    @Test
    void requestsThePageNewestFirst() {
        PageRequest expectedRequest = PageRequest.of(1, 10,
                Sort.by(Sort.Order.desc("calledAt"), Sort.Order.desc("id")));
        CallHistory call = new CallHistory(7L, Instant.parse("2026-01-01T12:00:00Z"), "GET", "/api/v1/calculate",
                null, 200, "{}");
        Page<CallHistory> page = new PageImpl<>(List.of(call), expectedRequest, 11);
        given(repository.findAll(expectedRequest)).willReturn(page);

        assertThat(service.findPage(1, 10)).isSameAs(page);
    }
}
