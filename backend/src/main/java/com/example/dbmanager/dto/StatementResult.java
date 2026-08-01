package com.example.dbmanager.dto;

import java.util.List;

/**
 * 单条 SQL 的执行结果。type: resultSet / updateCount / error。
 * 错误结果带 errorKind（timeout / cancelled / error）；结果集带 truncated 截断标记。
 */
public record StatementResult(
        String type,
        List<ColumnMeta> columns,
        List<List<Object>> rows,
        Integer rowCount,
        Long durationMs,
        String message,
        String errorKind,
        Boolean truncated) {

    public static StatementResult resultSet(List<ColumnMeta> columns, List<List<Object>> rows,
                                            boolean truncated, long durationMs) {
        return new StatementResult("resultSet", columns, rows, rows.size(), durationMs, null, null, truncated);
    }

    public static StatementResult updateCount(int count, long durationMs) {
        return new StatementResult("updateCount", List.of(), List.of(), count, durationMs, null, null, null);
    }

    public static StatementResult error(String message, String errorKind, long durationMs) {
        return new StatementResult("error", List.of(), List.of(), null, durationMs, message, errorKind, null);
    }
}
