package com.tenpo.challenge.controller;

import com.tenpo.challenge.dto.HistoryPageResponse;
import com.tenpo.challenge.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "History")
@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    static final int MAX_PAGE_SIZE = 100;

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Operation(
            summary = "List the history of calls",
            description = "Returns the recorded calls to the API, newest first, one page at a time. Calls to this "
                    + "endpoint are not recorded.")
    @ApiResponse(responseCode = "200", description = "A page of the call history.")
    @ApiResponse(responseCode = "400", description = "page or size is not a valid number or is out of range.")
    @GetMapping("/history")
    public HistoryPageResponse history(
            @Parameter(description = "Zero-based page number.")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Number of calls per page.")
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return HistoryPageResponse.from(historyService.findPage(page, size));
    }
}
