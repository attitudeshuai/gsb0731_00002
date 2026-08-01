package com.example.dbmanager.controller;

import com.example.dbmanager.dto.QueryRequest;
import com.example.dbmanager.dto.QueryResponse;
import com.example.dbmanager.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQL 查询执行。
 */
@RestController
@RequestMapping("/api/connections/{connId}")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/query")
    public QueryResponse execute(@PathVariable Long connId, @Valid @RequestBody QueryRequest request) {
        return queryService.execute(connId, request);
    }
}
