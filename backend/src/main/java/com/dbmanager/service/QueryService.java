package com.dbmanager.service;

import com.dbmanager.dto.QueryExecuteRequest;
import com.dbmanager.dto.QueryResultResponse;
import com.dbmanager.entity.QueryHistory;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.repository.QueryHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class QueryService {

    private final DynamicDataSourceService dynamicDataSourceService;
    private final QueryHistoryRepository queryHistoryRepository;
    private final ActiveStatementRegistry statementRegistry;

    @Value("${app.query.default-timeout-seconds:30}")
    private int defaultTimeoutSeconds;

    @Value("${app.query.default-max-rows:1000}")
    private int defaultMaxRows;

    public QueryService(DynamicDataSourceService dynamicDataSourceService,
                        QueryHistoryRepository queryHistoryRepository,
                        ActiveStatementRegistry statementRegistry) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.queryHistoryRepository = queryHistoryRepository;
        this.statementRegistry = statementRegistry;
    }

    public QueryResultResponse execute(Long connectionId, QueryExecuteRequest request, String executionId) {
        String execId = (executionId == null || executionId.isBlank())
                ? UUID.randomUUID().toString() : executionId;

        long startTime = System.currentTimeMillis();
        String sql = request.getSql().trim();
        int timeout = resolveTimeout(request.getTimeoutSeconds());
        Integer maxRows = resolveMaxRows(request.getMaxRows());

        JdbcTemplate jdbcTemplate;
        if (request.getDatabase() != null && !request.getDatabase().isBlank()) {
            jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, request.getDatabase());
        } else {
            jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId);
        }

        String[] statements = splitSqlStatements(sql);
        QueryResultResponse result = null;
        Exception error = null;

        try {
            for (int i = 0; i < statements.length; i++) {
                String stmt = statements[i].trim();
                if (stmt.isEmpty()) {
                    continue;
                }
                result = executeSingleStatement(jdbcTemplate, stmt, timeout, maxRows, execId);
            }
            if (result == null) {
                throw new BusinessException("No valid SQL statement provided");
            }
        } catch (Exception e) {
            error = e;
        }

        long executionTime = System.currentTimeMillis() - startTime;

        boolean cancelled = statementRegistry.consumeCancelled(execId);
        boolean timedOut = statementRegistry.consumeTimedOut(execId);

        if (cancelled) {
            saveHistory(connectionId, sql, executionTime, 0, false, "Query was cancelled by user");
            QueryResultResponse resp = new QueryResultResponse();
            resp.setCancelled(true);
            resp.setExecutionTimeMs(executionTime);
            resp.setSql(sql);
            resp.setColumns(List.of());
            resp.setRows(List.of());
            return resp;
        }

        if (timedOut || isTimeoutException(error)) {
            String msg = "查询超时（" + timeout + " 秒），已自动中断。请添加 LIMIT 或 WHERE 条件缩小结果范围。";
            saveHistory(connectionId, sql, executionTime, 0, false, msg);
            throw new BusinessException("QUERY_TIMEOUT", msg, error);
        }

        if (error != null) {
            saveHistory(connectionId, sql, executionTime, 0, false, error.getMessage());
            throw new BusinessException("QUERY_ERROR", "Query execution failed: " + error.getMessage(), error);
        }

        int rowCount = result.getRows() != null ? result.getRows().size() : 0;
        Long updateCount = result.getUpdateCount();
        int count = updateCount != null ? updateCount.intValue() : rowCount;
        saveHistory(connectionId, sql, executionTime, count, true, null);

        result.setExecutionTimeMs(executionTime);
        return result;
    }

    public void cancel(String executionId) {
        statementRegistry.cancel(executionId);
    }

    private QueryResultResponse executeSingleStatement(JdbcTemplate jdbcTemplate, String sql,
                                                        int timeoutSeconds, Integer maxRows,
                                                        String executionId) {
        boolean isQuery = isQueryStatement(sql);

        return jdbcTemplate.execute((ConnectionCallback<QueryResultResponse>) con -> {
            QueryResultResponse response = new QueryResultResponse();
            response.setSql(sql);

            try (Statement stmt = con.createStatement()) {
                stmt.setQueryTimeout(timeoutSeconds);
                if (isQuery && maxRows != null && maxRows > 0) {
                    stmt.setMaxRows(maxRows + 1);
                }

                statementRegistry.register(executionId, stmt);
                try {
                    if (isQuery) {
                        executeQuery(stmt, sql, response, maxRows);
                    } else {
                        int affected = stmt.executeUpdate(sql);
                        response.setColumns(List.of());
                        response.setRows(List.of());
                        response.setUpdateCount((long) affected);
                    }
                } finally {
                    statementRegistry.unregister(executionId, stmt);
                }
            } catch (SQLException e) {
                if (e instanceof SQLTimeoutException || isTimeoutMessage(e.getMessage())) {
                    statementRegistry.markTimedOut(executionId);
                }
                throw e;
            }
            return response;
        });
    }

    private void executeQuery(Statement stmt, String sql, QueryResultResponse response,
                               Integer maxRows) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;

        try (ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int colCount = metaData.getColumnCount();

            for (int i = 1; i <= colCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            while (rs.next()) {
                if (maxRows != null && maxRows > 0 && rows.size() >= maxRows) {
                    truncated = true;
                    break;
                }
                List<Object> row = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }
        }

        response.setColumns(columns);
        response.setRows(rows);
        response.setUpdateCount(null);
        response.setTruncated(truncated);
        response.setMaxRows(maxRows);
    }

    private int resolveTimeout(Integer requested) {
        if (requested != null && requested > 0) {
            return Math.min(requested, 600);
        }
        return defaultTimeoutSeconds;
    }

    private Integer resolveMaxRows(Integer requested) {
        if (requested != null) {
            if (requested <= 0) {
                return null;
            }
            return Math.min(requested, 100000);
        }
        return defaultMaxRows;
    }

    private boolean isQueryStatement(String sql) {
        String upper = sql.toUpperCase().trim();
        return upper.startsWith("SELECT") || upper.startsWith("SHOW")
                || upper.startsWith("DESC") || upper.startsWith("EXPLAIN")
                || upper.startsWith("WITH") || upper.startsWith("PRAGMA")
                || upper.startsWith("TABLE");
    }

    private boolean isTimeoutException(Exception e) {
        if (e == null) {
            return false;
        }
        if (e instanceof SQLTimeoutException) {
            return true;
        }
        Throwable cause = e.getCause();
        if (cause instanceof SQLTimeoutException) {
            return true;
        }
        return isTimeoutMessage(e.getMessage())
                || (cause != null && isTimeoutMessage(cause.getMessage()));
    }

    private boolean isTimeoutMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("query interrupted") || lower.contains("statement cancelled")
                || lower.contains("killed");
    }

    private String[] splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                if (i > 0 && sql.charAt(i - 1) == '\\') {
                    current.append(c);
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote && !inBacktick) {
                if (i > 0 && sql.charAt(i - 1) == '\\') {
                    current.append(c);
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            }

            if (c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }

        return statements.toArray(new String[0]);
    }

    private void saveHistory(Long connectionId, String sql, long executionTime,
                              int rowCount, boolean success, String errorMessage) {
        try {
            QueryHistory history = new QueryHistory();
            history.setConnectionId(connectionId);
            history.setSqlText(sql);
            history.setExecutionTimeMs(executionTime);
            history.setRowCount(success ? rowCount : 0);
            history.setSuccess(success);
            history.setErrorMessage(errorMessage);
            history.setExecutedAt(Instant.now());
            queryHistoryRepository.save(history);
        } catch (Exception e) {
            // silently ignore history save errors
        }
    }
}
