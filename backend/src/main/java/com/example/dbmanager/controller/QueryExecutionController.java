package com.example.dbmanager.controller;

import com.example.dbmanager.dto.CancelResponse;
import com.example.dbmanager.service.QueryService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询执行管理：取消正在运行的查询（KILL 数据库侧查询）。
 */
@RestController
@RequestMapping("/api/query-executions")
public class QueryExecutionController {

    private final QueryService queryService;

    public QueryExecutionController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/{executionId}/cancel")
    public CancelResponse cancel(@PathVariable String executionId) {
        return queryService.cancel(executionId);
    }
}
