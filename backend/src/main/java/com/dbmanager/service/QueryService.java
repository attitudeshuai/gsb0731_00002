package com.dbmanager.service;

import com.dbmanager.config.AppProperties;
import com.dbmanager.dto.*;
import com.dbmanager.entity.ConnectionConfig;
import com.dbmanager.entity.QueryHistory;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.repository.ConnectionRepository;
import com.dbmanager.repository.QueryHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final DynamicDataSourceManager dataSourceManager;
    private final QueryHistoryRepository historyRepository;
    private final AppProperties appProperties;
    private final QueryCancelRegistry cancelRegistry;
    private final ConnectionRepository connectionRepository;

    public QueryResult executeQuery(QueryRequest request) {
        return executeQuery(request, null);
    }

    public QueryResult executeQuery(QueryRequest request, String requestId) {
        if (request.getSql() == null || request.getSql().isBlank()) {
            throw new BusinessException("SQL text is required");
        }
        String sql = request.getSql().trim();
        long start = System.currentTimeMillis();
        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 100;
        if (pageSize > appProperties.getQuery().getMaxRows()) {
            pageSize = appProperties.getQuery().getMaxRows();
        }

        ConnectionConfig config = request.getConnectionId() != null
                ? connectionRepository.findById(request.getConnectionId()).orElse(null)
                : null;
        try (Connection conn = dataSourceManager.getConnection(request.getConnectionId(), request.getDatabaseName())) {
            boolean isSelect = isSelectStatement(sql);

            if (isSelect) {
                String finalSql = buildPagedSql(sql, page, pageSize, request);
                List<ColumnMeta> columns = new ArrayList<>();
                List<Map<String, Object>> rows = new ArrayList<>();

                PreparedStatement ps = conn.prepareStatement(finalSql);
                if (requestId != null) {
                    cancelRegistry.register(requestId, ps, conn, "query", config, request.getDatabaseName());
                }
                try (ps; ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(ColumnMeta.builder()
                                .name(md.getColumnLabel(i))
                                .type(md.getColumnTypeName(i))
                                .typeName(md.getColumnTypeName(i))
                                .nullable(md.isNullable(i) != ResultSetMetaData.columnNoNulls)
                                .build());
                    }
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(md.getColumnLabel(i), normalizeValue(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                } finally {
                    if (requestId != null) cancelRegistry.unregister(requestId, ps);
                }

                if (requestId != null && cancelRegistry.isCancelled(requestId)) {
                    throw new SQLException("Query was cancelled", "57014");
                }

                Long total = null;
                if (requestId == null || !cancelRegistry.isCancelled(requestId)) {
                    PreparedStatement cntPs = conn.prepareStatement(buildCountSql(sql));
                    if (requestId != null) {
                        cancelRegistry.register(requestId, cntPs, conn, "count", config, request.getDatabaseName());
                    }
                    try (cntPs; ResultSet cntRs = cntPs.executeQuery()) {
                        if (cntRs.next()) {
                            total = cntRs.getLong(1);
                        }
                    } catch (SQLException e) {
                        if (isCancellation(e) || (requestId != null && cancelRegistry.isCancelled(requestId))) {
                            throw e;
                        }
                        log.debug("Count query failed: {}", e.getMessage());
                    } finally {
                        if (requestId != null) cancelRegistry.unregister(requestId, cntPs);
                    }
                }

                long elapsed = System.currentTimeMillis() - start;
                saveHistory(request, sql, "success", (long) rows.size(), elapsed, null);
                return QueryResult.builder()
                        .columns(columns)
                        .rows(rows)
                        .affectedRows((long) rows.size())
                        .total(total)
                        .elapsedMs(elapsed)
                        .sql(sql)
                        .query(true)
                        .build();
            } else {
                Statement stmt = conn.createStatement();
                if (requestId != null) {
                    cancelRegistry.register(requestId, stmt, conn, "update", config, request.getDatabaseName());
                }
                try (stmt) {
                    int affected = stmt.executeUpdate(sql);
                    long elapsed = System.currentTimeMillis() - start;
                    saveHistory(request, sql, "success", (long) affected, elapsed, null);
                    return QueryResult.builder()
                            .columns(Collections.emptyList())
                            .rows(Collections.emptyList())
                            .affectedRows((long) affected)
                            .total((long) affected)
                            .elapsedMs(elapsed)
                            .sql(sql)
                            .query(false)
                            .build();
                } finally {
                    if (requestId != null) cancelRegistry.unregister(requestId);
                }
            }
        } catch (SQLException e) {
            if (requestId != null) cancelRegistry.unregister(requestId);
            long elapsed = System.currentTimeMillis() - start;
            boolean cancelled = isCancellation(e);
            saveHistory(request, sql, cancelled ? "cancelled" : "error", 0L, elapsed, e.getMessage());
            if (cancelled) {
                throw new BusinessException("Query was cancelled");
            }
            throw new BusinessException("SQL execution failed: " + e.getMessage());
        }
    }

    private boolean isCancellation(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String upper = msg.toUpperCase();
        return upper.contains("QUERY INTERRUPTED")
                || upper.contains("CANCELLED")
                || upper.contains("CANCEL")
                || e.getSQLState() != null && e.getSQLState().equals("57014");
    }

    public QueryResult executeSelected(Long connectionId, String database, String selectedSql, String requestId) {
        QueryRequest req = new QueryRequest();
        req.setConnectionId(connectionId);
        req.setDatabaseName(database);
        req.setSql(selectedSql);
        req.setPage(1);
        req.setPageSize(100);
        return executeQuery(req, requestId);
    }

    public TableDataResponse getTableData(Long connectionId, String database, String table,
                                          int page, int pageSize, String sortColumn,
                                          String sortDirection, List<QueryRequest.FilterCondition> filters) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 100;
        if (pageSize > appProperties.getQuery().getMaxRows()) {
            pageSize = appProperties.getQuery().getMaxRows();
        }

        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            DatabaseMetaData md = conn.getMetaData();

            List<ColumnMeta> columns = new ArrayList<>();
            try (ResultSet rs = md.getColumns(database, null, table, null)) {
                while (rs.next()) {
                    columns.add(ColumnMeta.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .type(rs.getString("TYPE_NAME"))
                            .typeName(rs.getString("TYPE_NAME"))
                            .precision(getInt(rs, "COLUMN_SIZE"))
                            .scale(getInt(rs, "DECIMAL_DIGITS"))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .defaultValue(rs.getString("COLUMN_DEF"))
                            .comment(rs.getString("REMARKS"))
                            .autoIncrement("YES".equals(rs.getString("IS_AUTOINCREMENT")))
                            .primaryKey(false)
                            .build());
                }
            }

            List<PrimaryKeyColumn> primaryKeys = new ArrayList<>();
            try (ResultSet rs = md.getPrimaryKeys(database, null, table)) {
                while (rs.next()) {
                    PrimaryKeyColumn pk = PrimaryKeyColumn.builder()
                            .columnName(rs.getString("COLUMN_NAME"))
                            .keySeq(rs.getInt("KEY_SEQ"))
                            .build();
                    primaryKeys.add(pk);
                    columns.stream()
                            .filter(c -> c.getName().equals(pk.getColumnName()))
                            .findFirst()
                            .ifPresent(c -> c.setPrimaryKey(true));
                }
            }

            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (filters != null) {
                for (QueryRequest.FilterCondition f : filters) {
                    if (f.getColumn() == null || f.getValue() == null) continue;
                    where.append(" AND `").append(f.getColumn().replace("`", "``")).append("` ");
                    String op = f.getOperator() != null ? f.getOperator().toLowerCase() : "contains";
                    switch (op) {
                        case "eq" -> { where.append("= ?"); params.add(f.getValue()); }
                        case "neq" -> { where.append("<> ?"); params.add(f.getValue()); }
                        case "gt" -> { where.append("> ?"); params.add(f.getValue()); }
                        case "gte" -> { where.append(">= ?"); params.add(f.getValue()); }
                        case "lt" -> { where.append("< ?"); params.add(f.getValue()); }
                        case "lte" -> { where.append("<= ?"); params.add(f.getValue()); }
                        case "startswith" -> { where.append("LIKE ?"); params.add(f.getValue() + "%"); }
                        case "endswith" -> { where.append("LIKE ?"); params.add("%" + f.getValue()); }
                        default -> { where.append("LIKE ?"); params.add("%" + f.getValue() + "%"); }
                    }
                }
            }

            String orderBy = "";
            if (sortColumn != null && !sortColumn.isBlank()) {
                String dir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
                orderBy = " ORDER BY `" + sortColumn.replace("`", "``") + "` " + dir;
            } else if (!primaryKeys.isEmpty()) {
                orderBy = " ORDER BY `" + primaryKeys.get(0).getColumnName().replace("`", "``") + "` ASC";
            }

            String safeTable = "`" + table.replace("`", "``") + "`";
            String countSql = "SELECT COUNT(*) FROM " + safeTable + where;
            String dataSql = "SELECT * FROM " + safeTable + where + orderBy
                    + " LIMIT " + pageSize + " OFFSET " + ((long) (page - 1) * pageSize);

            Long total = 0L;
            try (PreparedStatement cntPs = conn.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    cntPs.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = cntPs.executeQuery()) {
                    if (rs.next()) total = rs.getLong(1);
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData rmd = rs.getMetaData();
                    int colCount = rmd.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(rmd.getColumnLabel(i), normalizeValue(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                }
            }

            return TableDataResponse.builder()
                    .columns(columns)
                    .rows(rows)
                    .total(total)
                    .page(page)
                    .pageSize(pageSize)
                    .primaryKeys(primaryKeys)
                    .build();
        } catch (SQLException e) {
            throw new BusinessException("Failed to query table data: " + e.getMessage());
        }
    }

    public long updateRow(Long connectionId, String database, String table,
                          Map<String, Object> primaryKey, Map<String, Object> data) {
        if (primaryKey == null || primaryKey.isEmpty()) {
            throw new BusinessException("Primary key is required for update");
        }
        if (data == null || data.isEmpty()) {
            return 0;
        }
        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            StringBuilder set = new StringBuilder();
            List<Object> params = new ArrayList<>();
            int idx = 0;
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (idx++ > 0) set.append(", ");
                set.append("`").append(e.getKey().replace("`", "``")).append("` = ?");
                params.add(e.getValue());
            }
            StringBuilder where = new StringBuilder(" WHERE 1=1");
            for (Map.Entry<String, Object> e : primaryKey.entrySet()) {
                where.append(" AND `").append(e.getKey().replace("`", "``")).append("` = ?");
                params.add(e.getValue());
            }
            String sql = "UPDATE `" + table.replace("`", "``") + "` SET " + set + where;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new BusinessException("Update failed: " + e.getMessage());
        }
    }

    public long insertRow(Long connectionId, String database, String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            throw new BusinessException("Row data is required for insert");
        }
        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();
            List<Object> params = new ArrayList<>();
            int idx = 0;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getValue() == null) continue;
                if (idx > 0) { cols.append(", "); vals.append(", "); }
                cols.append("`").append(e.getKey().replace("`", "``")).append("`");
                vals.append("?");
                params.add(e.getValue());
                idx++;
            }
            if (idx == 0) {
                throw new BusinessException("No non-null values to insert");
            }
            String sql = "INSERT INTO `" + table.replace("`", "``") + "` (" + cols + ") VALUES (" + vals + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new BusinessException("Insert failed: " + e.getMessage());
        }
    }

    public long deleteRow(Long connectionId, String database, String table, Map<String, Object> primaryKey) {
        if (primaryKey == null || primaryKey.isEmpty()) {
            throw new BusinessException("Primary key is required for delete");
        }
        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> e : primaryKey.entrySet()) {
                where.append(" AND `").append(e.getKey().replace("`", "``")).append("` = ?");
                params.add(e.getValue());
            }
            String sql = "DELETE FROM `" + table.replace("`", "``") + "`" + where;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new BusinessException("Delete failed: " + e.getMessage());
        }
    }

    private String buildCountSql(String sql) {
        String trimmed = removeTrailingSemicolon(sql);
        return "SELECT COUNT(*) FROM (" + trimmed + ") AS _cnt_tbl";
    }

    private String buildPagedSql(String sql, int page, int pageSize, QueryRequest req) {
        String trimmed = removeTrailingSemicolon(sql);
        StringBuilder sb = new StringBuilder(trimmed);
        if (req.getSortColumn() != null && !req.getSortColumn().isBlank()) {
            String dir = "desc".equalsIgnoreCase(req.getSortDirection()) ? "DESC" : "ASC";
            if (!trimmed.toUpperCase().contains("ORDER BY")) {
                sb.append(" ORDER BY `").append(req.getSortColumn().replace("`", "``")).append("` ").append(dir);
            }
        }
        sb.append(" LIMIT ").append(pageSize).append(" OFFSET ").append((long) (page - 1) * pageSize);
        return sb.toString();
    }

    private String removeTrailingSemicolon(String sql) {
        String s = sql.trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    private boolean isSelectStatement(String sql) {
        String upper = sql.trim().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("SHOW") || upper.startsWith("DESC")
                || upper.startsWith("EXPLAIN") || upper.startsWith("WITH") || upper.startsWith("DESCRIBE");
    }

    private Object normalizeValue(Object val) {
        if (val == null) return null;
        if (val instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (val instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (val instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (val instanceof java.util.Date d) {
            return d.toInstant().toString();
        }
        if (val instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return val;
    }

    private Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private void saveHistory(QueryRequest request, String sql, String status,
                             Long affected, Long elapsed, String error) {
        try {
            QueryHistory h = QueryHistory.builder()
                    .connectionId(request.getConnectionId())
                    .databaseName(request.getDatabaseName())
                    .sqlText(sql.length() > 60000 ? sql.substring(0, 60000) : sql)
                    .status(status)
                    .affectedRows(affected)
                    .elapsedMs(elapsed)
                    .errorMessage(error != null && error.length() > 5000 ? error.substring(0, 5000) : error)
                    .executedAt(java.time.Instant.now())
                    .build();
            historyRepository.save(h);
            long count = historyRepository.count();
            int limit = appProperties.getQuery().getHistoryLimit();
            if (count > limit) {
                long excess = count - limit;
                historyRepository.deleteAllInBatch(
                        historyRepository.findAll().stream()
                                .sorted(Comparator.comparing(QueryHistory::getExecutedAt))
                                .limit(excess)
                                .toList()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to save query history: {}", e.getMessage());
        }
    }
}
