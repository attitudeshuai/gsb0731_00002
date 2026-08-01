package com.example.dbmanager.service;

import com.example.dbmanager.dto.CancelResponse;
import com.example.dbmanager.dto.ColumnMeta;
import com.example.dbmanager.dto.QueryRequest;
import com.example.dbmanager.dto.QueryResponse;
import com.example.dbmanager.dto.StatementResult;
import com.example.dbmanager.entity.QueryHistory;
import com.example.dbmanager.entity.QueryStatus;
import com.example.dbmanager.exception.BadRequestException;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.QueryHistoryRepository;
import com.example.dbmanager.util.SqlUtils;
import com.example.dbmanager.util.ValueMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
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

/**
 * SQL 查询执行：支持多条语句（分号分隔）逐条执行，每条记录 query_history。
 * 结果集最多返回 1000 行（超出时 truncated=true）。
 * 每次执行登记 executionId，可通过 KILL QUERY 取消数据库侧查询。
 */
@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);
    private static final int MAX_ROWS = 1000;
    /** MySQL ER_QUERY_INTERRUPTED：语句被 KILL（本驱动的 queryTimeout 或手动取消都会触发） */
    private static final int MYSQL_ERROR_QUERY_INTERRUPTED = 1317;

    private final TargetJdbcService targetJdbcService;
    private final QueryHistoryRepository historyRepository;
    private final QueryExecutionRegistry executionRegistry;

    public QueryService(TargetJdbcService targetJdbcService,
                        QueryHistoryRepository historyRepository,
                        QueryExecutionRegistry executionRegistry) {
        this.targetJdbcService = targetJdbcService;
        this.historyRepository = historyRepository;
        this.executionRegistry = executionRegistry;
    }

    public QueryResponse execute(Long connectionId, QueryRequest request) {
        List<String> statements = SqlUtils.splitStatements(request.sql());
        if (statements.isEmpty()) {
            throw new BadRequestException("没有可执行的 SQL 语句");
        }
        String database = request.database().trim();
        String executionId = (request.executionId() == null || request.executionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.executionId().trim();
        int timeoutSeconds = targetJdbcService.getQueryTimeoutSeconds(connectionId);

        HikariDataSource dataSource = targetJdbcService.getDataSource(connectionId);
        List<StatementResult> results = new ArrayList<>();
        long totalStart = System.currentTimeMillis();

        // 多条语句在同一物理连接上执行，保证 USE / SET 等会话状态一致
        java.sql.Connection connection = DataSourceUtils.getConnection(dataSource);
        executionRegistry.register(executionId, connectionId);
        try {
            connection.setCatalog(database);
            registerServerConnectionId(executionId, connection);
            JdbcTemplate jt = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jt.setQueryTimeout(timeoutSeconds);
            for (String sql : statements) {
                if (executionRegistry.isCancelled(executionId)) {
                    break;
                }
                results.add(executeOne(connectionId, database, sql, jt, executionId, timeoutSeconds));
                if (executionRegistry.isCancelled(executionId)) {
                    break;
                }
            }
        } catch (SQLException e) {
            throw new BadRequestException("无法切换到数据库 " + database + ": " + e.getMessage());
        } finally {
            executionRegistry.unregister(executionId);
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
        return new QueryResponse(executionId, results, System.currentTimeMillis() - totalStart);
    }

    /**
     * 取消指定执行：新建独立的裸 JDBC 连接（不走可能已满的连接池）执行 KILL QUERY，
     * 真正中断数据库侧查询；登记状态置 CANCELLED。
     */
    public CancelResponse cancel(String executionId) {
        QueryExecutionRegistry.QueryExecution execution = executionRegistry.find(executionId)
                .filter(e -> e.getStatus() == QueryExecutionRegistry.Status.RUNNING)
                .orElseThrow(() -> new NotFoundException("查询执行不存在或已结束: " + executionId));
        executionRegistry.markCancelled(executionId);

        Long serverConnectionId = execution.getServerConnectionId();
        if (serverConnectionId == null) {
            return new CancelResponse(true, "已标记取消（查询尚未开始在数据库侧执行）");
        }
        try (java.sql.Connection killer = targetJdbcService.createBareConnection(execution.getConnectionId());
             Statement statement = killer.createStatement()) {
            statement.execute("KILL QUERY " + serverConnectionId);
            log.info("已 KILL 查询 executionId={} serverConnectionId={}", executionId, serverConnectionId);
            return new CancelResponse(true, "已取消执行 " + executionId);
        } catch (SQLException e) {
            throw new BadRequestException("取消执行失败: " + e.getMessage());
        }
    }

    /**
     * 在同一物理连接上查询 MySQL 服务端连接 id 并登记，供取消时 KILL 使用。
     */
    private void registerServerConnectionId(String executionId, java.sql.Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT CONNECTION_ID()")) {
            if (rs.next()) {
                executionRegistry.updateServerConnectionId(executionId, rs.getLong(1));
            }
        }
    }

    private StatementResult executeOne(Long connectionId, String database, String sql,
                                       JdbcTemplate jt, String executionId, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        try {
            if (SqlUtils.isReadQuery(sql)) {
                ResultSetData data = jt.query(sql, rs -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<ColumnMeta> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(new ColumnMeta(meta.getColumnLabel(i), meta.getColumnTypeName(i)));
                    }
                    // 最多读取 MAX_ROWS + 1 行用于判断是否截断，只保留前 MAX_ROWS 行
                    List<List<Object>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rows.size() >= MAX_ROWS) {
                            truncated = true;
                            break;
                        }
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(ValueMapper.normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    return new ResultSetData(columns, rows, truncated);
                });
                long durationMs = System.currentTimeMillis() - start;
                saveHistory(connectionId, database, sql, QueryStatus.SUCCESS,
                        data == null ? 0 : data.rows().size(), durationMs, null);
                return StatementResult.resultSet(
                        data == null ? List.of() : data.columns(),
                        data == null ? List.of() : data.rows(),
                        data != null && data.truncated(),
                        durationMs);
            }
            int affected = jt.update(sql);
            long durationMs = System.currentTimeMillis() - start;
            saveHistory(connectionId, database, sql, QueryStatus.SUCCESS, affected, durationMs, null);
            return StatementResult.updateCount(affected, durationMs);
        } catch (DataAccessException e) {
            long durationMs = System.currentTimeMillis() - start;
            if (executionRegistry.isCancelled(executionId)) {
                String message = "Query cancelled by user";
                saveHistory(connectionId, database, sql, QueryStatus.CANCELLED, null, durationMs, message);
                return StatementResult.error(message, "cancelled", durationMs);
            }
            if (isTimeout(e)) {
                String message = "Query timeout after " + timeoutSeconds + "s";
                saveHistory(connectionId, database, sql, QueryStatus.TIMEOUT, null, durationMs, message);
                return StatementResult.error(message, "timeout", durationMs);
            }
            String message = rootMessage(e);
            saveHistory(connectionId, database, sql, QueryStatus.ERROR, null, durationMs, message);
            return StatementResult.error(message, "error", durationMs);
        }
    }

    /**
     * 判断异常是否由语句超时触发（驱动 queryTimeout 会 KILL 查询，MySQL 报 1317）。
     */
    static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLTimeoutException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_ERROR_QUERY_INTERRUPTED) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record ResultSetData(List<ColumnMeta> columns, List<List<Object>> rows, boolean truncated) {
    }

    private void saveHistory(Long connectionId, String database, String sql, QueryStatus status,
                             Integer rowCount, Long durationMs, String errorMessage) {
        try {
            QueryHistory history = new QueryHistory();
            history.setConnectionId(connectionId);
            history.setDatabaseName(database);
            history.setSqlText(sql);
            history.setStatus(status);
            history.setRowCount(rowCount);
            history.setDurationMs(durationMs);
            history.setErrorMessage(errorMessage);
            history.setExecutedAt(Instant.now());
            historyRepository.save(history);
        } catch (Exception e) {
            log.warn("写入查询历史失败: {}", e.getMessage());
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null ? message : throwable.toString();
    }
}
