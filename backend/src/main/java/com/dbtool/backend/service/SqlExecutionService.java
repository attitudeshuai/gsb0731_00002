package com.dbtool.backend.service;

import com.dbtool.backend.dto.QueryResult;
import com.dbtool.backend.entity.QueryHistory;
import com.dbtool.backend.repository.QueryHistoryRepository;
import com.dbtool.backend.target.TargetDataSourceManager;
import com.dbtool.backend.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes arbitrary SQL against a target database via {@link JdbcTemplate} /
 * streaming JDBC. Records every execution in query_history (metadata db).
 * <p>
 * Result sets are read in streaming mode (MySQL row-by-row cursor) so peak memory
 * is bounded by the row cap / batch size, NOT by the size of the underlying table.
 */
@Service
public class SqlExecutionService {

    /** Row callback used by the streaming export path. */
    public interface RowConsumer {
        void columns(List<QueryResult.ColumnMeta> columns) throws Exception;
        void row(List<Object> values) throws Exception;
    }

    private final TargetDataSourceManager dataSourceManager;
    private final QueryHistoryRepository historyRepository;
    private final RunningQueryRegistry runningQueryRegistry;

    @Value("${app.target-db.query-timeout-seconds:30}")
    private int queryTimeoutSeconds;

    @Value("${app.target-db.max-result-rows:10000}")
    private int maxResultRows;

    public SqlExecutionService(TargetDataSourceManager dataSourceManager,
                               QueryHistoryRepository historyRepository,
                               RunningQueryRegistry runningQueryRegistry) {
        this.dataSourceManager = dataSourceManager;
        this.historyRepository = historyRepository;
        this.runningQueryRegistry = runningQueryRegistry;
    }

    /** Executes a single SQL statement, recording history. */
    public QueryResult execute(Long connectionId, String sql) {
        return execute(connectionId, sql, null);
    }

    /**
     * Executes a single SQL statement. If {@code queryToken} is provided the
     * running statement is registered so it can be cancelled server-side.
     */
    public QueryResult execute(Long connectionId, String sql, String queryToken) {
        String trimmed = sql == null ? "" : sql.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SQL must not be empty");
        }
        long start = System.currentTimeMillis();
        try {
            QueryResult result = doExecute(connectionId, trimmed, queryToken);
            result.durationMs = System.currentTimeMillis() - start;
            recordHistory(connectionId, trimmed, true, null,
                    result.affectedRows != null ? result.affectedRows : result.rowCount, result.durationMs);
            return result;
        } catch (ApiException e) {
            recordHistory(connectionId, trimmed, false, e.getMessage(), null,
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            String msg = cancellationMessage(e);
            recordHistory(connectionId, trimmed, false, msg, null,
                    System.currentTimeMillis() - start);
            throw new ApiException(HttpStatus.BAD_REQUEST, msg);
        }
    }

    /** Runs a query without recording history (used internally, e.g. for table browsing). Capped. */
    public QueryResult query(Long connectionId, String sql, Object... params) {
        long start = System.currentTimeMillis();
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        QueryResult result = jt.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement(sql,
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                enableStreaming(ps);
                capRows(ps, maxResultRows);
                bind(ps, params);
                ps.setQueryTimeout(queryTimeoutSeconds);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapResultSet(rs, maxResultRows);
                }
            }
        });
        result.durationMs = System.currentTimeMillis() - start;
        return result;
    }

    /**
     * Streams a query result to the given consumer with NO row cap. Rows are read
     * one at a time via a server-side cursor so memory stays constant regardless of
     * how many rows the query returns. Used by full-table export.
     *
     * @return total number of rows streamed
     */
    public long streamQuery(Long connectionId, String sql, RowConsumer consumer) {
        return streamQuery(connectionId, sql, consumer, null);
    }

    /**
     * Streams a query result to the given consumer with NO row cap, using the
     * ISOLATED export connection pool so it never starves interactive queries.
     * Rows are read one at a time via a server-side cursor so memory stays constant.
     * If {@code queryToken} is provided the running statement is registered so a
     * cancel request can abort it in the DB and free the connection back to the pool.
     *
     * @return total number of rows streamed
     */
    public long streamQuery(Long connectionId, String sql, RowConsumer consumer, String queryToken) {
        try (Connection conn = dataSourceManager.borrowExportConnection(connectionId);
             PreparedStatement ps = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            enableStreaming(ps);
            // no query timeout on export: a full-table dump can legitimately take a while
            ps.setQueryTimeout(0);
            runningQueryRegistry.register(queryToken, ps);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                List<QueryResult.ColumnMeta> columns = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    columns.add(new QueryResult.ColumnMeta(
                            md.getColumnLabel(i), md.getColumnTypeName(i),
                            md.getColumnType(i),
                            md.isNullable(i) != ResultSetMetaData.columnNoNulls));
                }
                consumer.columns(columns);
                long count = 0;
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        row.add(normalize(rs.getObject(i)));
                    }
                    consumer.row(row);
                    count++;
                }
                return count;
            } finally {
                runningQueryRegistry.unregister(queryToken);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Export query failed: " + e.getMessage());
        }
    }

    /** Executes a DML update statement (INSERT/UPDATE/DELETE), returns affected rows. */
    public int update(Long connectionId, String sql, Object... params) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        jt.setQueryTimeout(queryTimeoutSeconds);
        return jt.update(sql, params);
    }

    private QueryResult doExecute(Long connectionId, String sql, String queryToken) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        return jt.execute((Connection conn) -> {
            try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setQueryTimeout(queryTimeoutSeconds);
                enableStreaming(stmt);
                capRows(stmt, maxResultRows);
                // register so a concurrent cancel request can abort this statement in the DB
                runningQueryRegistry.register(queryToken, stmt);
                try {
                    boolean hasResultSet = stmt.execute(sql);
                    if (hasResultSet) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            return mapResultSet(rs, maxResultRows);
                        }
                    } else {
                        QueryResult r = new QueryResult();
                        r.columns = new ArrayList<>();
                        r.rows = new ArrayList<>();
                        r.affectedRows = stmt.getUpdateCount();
                        r.rowCount = 0;
                        return r;
                    }
                } finally {
                    runningQueryRegistry.unregister(queryToken);
                }
            }
        });
    }

    /**
     * Enables MySQL result streaming (row-by-row cursor). Combined with the row cap
     * this keeps heap usage independent of the total result-set size.
     */
    private void enableStreaming(Statement stmt) {
        try {
            stmt.setFetchSize(Integer.MIN_VALUE);
        } catch (SQLException ignored) {
            // driver may not support the streaming hint; safe to continue
        }
    }

    /**
     * Caps how many rows the driver will surface. We ask for cap+1 so the caller can
     * still detect truncation (rowCount > cap), while the DB stops producing rows
     * beyond that — keeping memory bounded.
     */
    private void capRows(Statement stmt, int cap) {
        try {
            long want = (long) cap + 1;
            stmt.setMaxRows(want > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) want);
        } catch (SQLException ignored) {
            // fall back to the in-loop cap in mapResultSet
        }
    }

    private QueryResult mapResultSet(ResultSet rs, int cap) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        QueryResult result = new QueryResult();
        result.columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            result.columns.add(new QueryResult.ColumnMeta(
                    md.getColumnLabel(i),
                    md.getColumnTypeName(i),
                    md.getColumnType(i),
                    md.isNullable(i) != ResultSetMetaData.columnNoNulls));
        }
        List<List<Object>> rows = new ArrayList<>();
        int count = 0;
        boolean truncated = false;
        while (rs.next()) {
            if (count >= cap) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.add(normalize(rs.getObject(i)));
            }
            rows.add(row);
            count++;
        }
        result.rows = rows;
        result.rowCount = count;
        result.truncated = truncated;
        return result;
    }

    /** Converts JDBC types to JSON-friendly representations. */
    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return "0x" + toHex(bytes);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toString();
        }
        if (value instanceof java.sql.Time t) {
            return t.toString();
        }
        return value;
    }

    /** Produces a friendly message, recognising cancelled statements. */
    private String cancellationMessage(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof SQLException sqlEx) {
            // MySQL reports cancelled statements as "Query execution was interrupted"
            if (m.toLowerCase().contains("interrupted") || m.toLowerCase().contains("cancel")
                    || "70100".equals(sqlEx.getSQLState())) {
                return "Query was cancelled";
            }
        }
        return "SQL error: " + m;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private void recordHistory(Long connectionId, String sql, boolean success,
                               String error, Integer rowsAffected, long durationMs) {
        try {
            QueryHistory h = new QueryHistory();
            h.setConnectionId(connectionId);
            h.setSqlText(sql);
            h.setSuccess(success);
            h.setErrorMessage(error);
            h.setRowsAffected(rowsAffected);
            h.setDurationMs(durationMs);
            historyRepository.save(h);
        } catch (Exception ignored) {
            // history recording must never break the main flow
        }
    }
}
