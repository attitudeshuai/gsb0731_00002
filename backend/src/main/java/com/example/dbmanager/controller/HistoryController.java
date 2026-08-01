package com.example.dbmanager.controller;

import com.example.dbmanager.entity.QueryHistory;
import com.example.dbmanager.service.HistoryService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询历史。
 */
@RestController
@RequestMapping("/api/query-history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public Page<QueryHistory> page(@RequestParam(required = false) Long connectionId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return historyService.page(connectionId, page, size);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        historyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 清空历史（可带 connectionId 只清空指定连接的）。
     */
    @DeleteMapping
    public ResponseEntity<Void> clear(@RequestParam(required = false) Long connectionId) {
        historyService.clear(connectionId);
        return ResponseEntity.noContent().build();
    }
}
