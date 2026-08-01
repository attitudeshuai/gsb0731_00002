package com.dbtool.backend.controller;

import com.dbtool.backend.entity.QueryHistory;
import com.dbtool.backend.repository.QueryHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query-history")
public class QueryHistoryController {

    private final QueryHistoryRepository repository;

    public QueryHistoryController(QueryHistoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<QueryHistory> list(@RequestParam(required = false) Long connectionId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
        if (connectionId != null) {
            return repository.findByConnectionIdOrderByExecutedAtDesc(connectionId, pageable);
        }
        return repository.findAllByOrderByExecutedAtDesc(pageable);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
