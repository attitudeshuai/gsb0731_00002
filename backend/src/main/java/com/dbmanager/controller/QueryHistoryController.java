package com.dbmanager.controller;

import com.dbmanager.dto.ApiResponse;
import com.dbmanager.entity.QueryHistory;
import com.dbmanager.service.QueryHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/query-history")
@RequiredArgsConstructor
public class QueryHistoryController {

    private final QueryHistoryService historyService;

    @GetMapping
    public ApiResponse<Page<QueryHistory>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long connectionId) {
        return ApiResponse.ok(historyService.list(page, size, connectionId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        historyService.delete(id);
        return ApiResponse.ok("History deleted", null);
    }

    @DeleteMapping
    public ApiResponse<Map<String, Boolean>> clear() {
        historyService.clear();
        return ApiResponse.ok("History cleared", Map.of("cleared", true));
    }
}
