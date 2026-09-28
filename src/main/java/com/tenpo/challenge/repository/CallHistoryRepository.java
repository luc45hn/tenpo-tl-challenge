package com.tenpo.challenge.repository;

import com.tenpo.challenge.model.CallHistory;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

public interface CallHistoryRepository
        extends ListCrudRepository<CallHistory, Long>, ListPagingAndSortingRepository<CallHistory, Long> {
}
