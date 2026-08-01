package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryResult {
    private List<ColumnMeta> columns;
    private List<Map<String, Object>> rows;
    private Long affectedRows;
    private Long total;
    private Long elapsedMs;
    private String sql;
    private boolean query;
}
