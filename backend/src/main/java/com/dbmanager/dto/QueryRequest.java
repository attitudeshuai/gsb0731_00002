package com.dbmanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QueryRequest {

    @NotNull(message = "Connection id is required")
    private Long connectionId;

    private String databaseName;

    private String sql;

    private Integer page;

    private Integer pageSize;

    private String sortColumn;

    private String sortDirection;

    private List<FilterCondition> filters;

    @Data
    public static class FilterCondition {
        private String column;
        private String operator;
        private Object value;
    }
}
