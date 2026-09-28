package com.tenpo.challenge.service;

import com.tenpo.challenge.model.CallHistory;
import com.tenpo.challenge.repository.CallHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Reads the call history.
 */
@Service
public class HistoryService {

    /**
     * Newest first; the id breaks ties between calls recorded at the same instant.
     */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("calledAt"), Sort.Order.desc("id"));

    private final CallHistoryRepository repository;

    public HistoryService(CallHistoryRepository repository) {
        this.repository = repository;
    }

    public Page<CallHistory> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, NEWEST_FIRST));
    }
}
