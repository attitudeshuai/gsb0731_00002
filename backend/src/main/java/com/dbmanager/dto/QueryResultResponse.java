package com.dbmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueryResultResponse {

    private List<String> columns;
    private List<List<Object>> rows;
    private Long updateCount;
    private long executionTimeMs;
    private String sql;
    private boolean truncated;
    private Integer maxRows;
    private boolean cancelled;
    private boolean timeout;
}
