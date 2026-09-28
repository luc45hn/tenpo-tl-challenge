package com.tenpo.challenge.dto;

import com.tenpo.challenge.model.CallHistory;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of the call history, newest first, with its paging metadata.
 */
public record HistoryPageResponse(
        List<CallHistoryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static HistoryPageResponse from(Page<CallHistory> page) {
        return new HistoryPageResponse(
                page.getContent().stream().map(CallHistoryResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
