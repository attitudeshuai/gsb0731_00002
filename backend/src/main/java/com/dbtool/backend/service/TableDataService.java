package com.dbtool.backend.service;

import com.dbtool.backend.dto.MetadataDto;
import com.dbtool.backend.dto.QueryResult;
import com.dbtool.backend.dto.TableDataDto;
import com.dbtool.backend.target.TargetDataSourceManager;
import com.dbtool.backend.util.SqlIdentifiers;
import com.dbtool.backend.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * Browses and edits target-table data through {@link JdbcTemplate}.
 * All dynamic values are bound via parameters; identifiers are backtick-quoted.
 */
@Service
public class TableDataService {

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "eq", "ne", "gt", "ge", "lt", "le", "like", "is_null", "is_not_null");

    private final TargetDataSourceManager dataSourceManager;
    private final MetadataService metadataService;

    public TableDataService(TargetDataSourceManager dataSourceManager, MetadataService metadataService) {
        this.dataSourceManager = dataSourceManager;
        this.metadataService = metadataService;
    }

    public TableDataDto.PageResponse browse(Long connectionId, TableDataDto.PageRequest req) {
        validateTableRef(req.database, req.table);
        List<MetadataDto.ColumnInfo> columns = metadataService.listColumns(connectionId, req.database, req.table);
        if (columns.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Table not found or has no columns");
        }
        Set<String> validColumns = new HashSet<>();
        List<String> pks = new ArrayList<>();
        for (MetadataDto.ColumnInfo c : columns) {
            validColumns.add(c.name);
            if (c.primaryKey) {
                pks.add(c.name);
            }
        }

        String qualified = SqlIdentifiers.quote(req.database) + "." + SqlIdentifiers.quote(req.table);

        // WHERE clause
        List<Object> params = new ArrayList<>();
        String where = buildWhere(req.filters, validColumns, params);

        // total count
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        Long total = jt.queryForObject("SELECT COUNT(*) FROM " + qualified + where,
                Long.class, params.toArray());

        // ORDER BY
        String orderBy = "";
        if (req.sortColumn != null && !req.sortColumn.isBlank()) {
            if (!validColumns.contains(req.sortColumn)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid sort column: " + req.sortColumn);
            }
            String dir = "DESC".equalsIgnoreCase(req.sortDirection) ? "DESC" : "ASC";
            orderBy = " ORDER BY " + SqlIdentifiers.quote(req.sortColumn) + " " + dir;
        }

        int page = Math.max(1, req.page);
        int pageSize = req.pageSize <= 0 ? 100 : Math.min(req.pageSize, 5000);
        int offset = (page - 1) * pageSize;

        String sql = "SELECT * FROM " + qualified + where + orderBy + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageSize);
        dataParams.add(offset);

        QueryResult qr = jt.execute((java.sql.Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < dataParams.size(); i++) {
                    ps.setObject(i + 1, dataParams.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return mapRs(rs);
                }
            }
        });

        TableDataDto.PageResponse resp = new TableDataDto.PageResponse();
        resp.columns = qr.columns;
        resp.rows = qr.rows;
        resp.total = total == null ? 0 : total;
        resp.page = page;
        resp.pageSize = pageSize;
        resp.primaryKeys = pks;
        return resp;
    }

    /**
     * Builds the parameterized statements a save would run, without executing them.
     * Used both by the preview endpoint and internally by {@link #save}.
     */
    public TableDataDto.SaveResponse preview(Long connectionId, TableDataDto.SaveRequest req) {
        List<TableDataDto.PreviewStatement> stmts = buildStatements(connectionId, req);
        TableDataDto.SaveResponse resp = new TableDataDto.SaveResponse();
        resp.preview = stmts;
        resp.statements = new ArrayList<>();
        for (TableDataDto.PreviewStatement s : stmts) {
            resp.statements.add(s.sql);
            switch (s.type) {
                case "insert" -> resp.inserted++;
                case "update" -> resp.updated++;
                case "delete" -> resp.deleted++;
                default -> { }
            }
        }
        resp.executed = false;
        return resp;
    }

    /**
     * Applies row insert/update/delete changes inside a SINGLE manual transaction on
     * the target-database connection. Either every statement commits, or the whole
     * batch is rolled back — no partial writes. The metadata db is not involved here,
     * so no {@code @Transactional} is used (that would only bind the JPA datasource).
     */
    public TableDataDto.SaveResponse save(Long connectionId, TableDataDto.SaveRequest req) {
        List<TableDataDto.PreviewStatement> stmts = buildStatements(connectionId, req);

        TableDataDto.SaveResponse resp = new TableDataDto.SaveResponse();
        resp.statements = new ArrayList<>();
        resp.preview = stmts;
        resp.executed = true;

        if (stmts.isEmpty()) {
            return resp;
        }

        Connection conn = dataSourceManager.borrowConnection(connectionId);
        boolean prevAutoCommit = true;
        try {
            prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);   // begin manual transaction on the TARGET db

            for (TableDataDto.PreviewStatement s : stmts) {
                int affected = executeStatement(conn, s);
                resp.statements.add(s.sql);
                switch (s.type) {
                    case "insert" -> resp.inserted += affected;
                    case "update" -> {
                        if (affected == 0) {
                            throw new ApiException(HttpStatus.CONFLICT,
                                    "UPDATE affected 0 rows (row may have changed); rolling back batch");
                        }
                        resp.updated += affected;
                    }
                    case "delete" -> resp.deleted += affected;
                    default -> { }
                }
            }

            conn.commit();             // all-or-nothing: commit only if the whole batch succeeded
            return resp;
        } catch (ApiException e) {
            rollbackQuietly(conn);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Save failed, rolled back: " + e.getMessage());
        } finally {
            restoreAndClose(conn, prevAutoCommit);
        }
    }

    private int executeStatement(Connection conn, TableDataDto.PreviewStatement s) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(s.sql)) {
            List<Object> params = s.params == null ? List.of() : s.params;
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private void restoreAndClose(Connection conn, boolean prevAutoCommit) {
        try {
            conn.setAutoCommit(prevAutoCommit);
        } catch (SQLException ignored) {
        }
        try {
            conn.close();   // returns the connection to the pool
        } catch (SQLException ignored) {
        }
    }

    /** Turns the requested row changes into an ordered list of parameterized statements. */
    private List<TableDataDto.PreviewStatement> buildStatements(Long connectionId, TableDataDto.SaveRequest req) {
        validateTableRef(req.database, req.table);
        List<MetadataDto.ColumnInfo> columns = metadataService.listColumns(connectionId, req.database, req.table);
        Set<String> validColumns = new HashSet<>();
        for (MetadataDto.ColumnInfo c : columns) {
            validColumns.add(c.name);
        }
        String qualified = SqlIdentifiers.quote(req.database) + "." + SqlIdentifiers.quote(req.table);

        List<TableDataDto.PreviewStatement> out = new ArrayList<>();
        if (req.changes == null) {
            return out;
        }
        for (TableDataDto.RowChange change : req.changes) {
            switch (change.type == null ? "" : change.type.toLowerCase()) {
                case "insert" -> out.add(buildInsert(qualified, change, validColumns));
                case "update" -> out.add(buildUpdate(qualified, change, validColumns));
                case "delete" -> out.add(buildDelete(qualified, change, validColumns));
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown change type: " + change.type);
            }
        }
        return out;
    }

    private TableDataDto.PreviewStatement buildInsert(String qualified, TableDataDto.RowChange change,
                                                      Set<String> validColumns) {
        if (change.values == null || change.values.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSERT requires values");
        }
        List<String> cols = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();
        for (Map.Entry<String, Object> e : change.values.entrySet()) {
            requireColumn(e.getKey(), validColumns);
            cols.add(SqlIdentifiers.quote(e.getKey()));
            params.add(e.getValue());
            if (placeholders.length() > 0) placeholders.append(", ");
            placeholders.append("?");
        }
        String sql = "INSERT INTO " + qualified + " (" + String.join(", ", cols) + ") VALUES (" + placeholders + ")";
        return new TableDataDto.PreviewStatement("insert", sql, params, render(sql, params));
    }

    private TableDataDto.PreviewStatement buildUpdate(String qualified, TableDataDto.RowChange change,
                                                      Set<String> validColumns) {
        if (change.values == null || change.values.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPDATE requires values");
        }
        requireKeys(change);
        List<Object> params = new ArrayList<>();
        StringBuilder set = new StringBuilder();
        for (Map.Entry<String, Object> e : change.values.entrySet()) {
            requireColumn(e.getKey(), validColumns);
            if (set.length() > 0) set.append(", ");
            set.append(SqlIdentifiers.quote(e.getKey())).append(" = ?");
            params.add(e.getValue());
        }
        String where = buildKeyWhere(change.keys, validColumns, params);
        String sql = "UPDATE " + qualified + " SET " + set + where;
        return new TableDataDto.PreviewStatement("update", sql, params, render(sql, params));
    }

    private TableDataDto.PreviewStatement buildDelete(String qualified, TableDataDto.RowChange change,
                                                      Set<String> validColumns) {
        requireKeys(change);
        List<Object> params = new ArrayList<>();
        String where = buildKeyWhere(change.keys, validColumns, params);
        String sql = "DELETE FROM " + qualified + where;
        return new TableDataDto.PreviewStatement("delete", sql, params, render(sql, params));
    }

    /** Renders parameterized SQL with values inlined — for display/confirmation only. */
    private String render(String sql, List<Object> params) {
        StringBuilder sb = new StringBuilder();
        int pi = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && pi < params.size()) {
                sb.append(literal(params.get(pi++)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String literal(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "'" + v.toString().replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private String buildKeyWhere(Map<String, Object> keys, Set<String> validColumns, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE ");
        boolean first = true;
        for (Map.Entry<String, Object> e : keys.entrySet()) {
            requireColumn(e.getKey(), validColumns);
            if (!first) where.append(" AND ");
            if (e.getValue() == null) {
                where.append(SqlIdentifiers.quote(e.getKey())).append(" IS NULL");
            } else {
                where.append(SqlIdentifiers.quote(e.getKey())).append(" = ?");
                params.add(e.getValue());
            }
            first = false;
        }
        return where.toString();
    }

    private String buildWhere(List<TableDataDto.Filter> filters, Set<String> validColumns, List<Object> params) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" WHERE ");
        boolean first = true;
        for (TableDataDto.Filter f : filters) {
            requireColumn(f.column, validColumns);
            String op = f.operator == null ? "eq" : f.operator.toLowerCase();
            if (!ALLOWED_OPERATORS.contains(op)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid operator: " + f.operator);
            }
            if (!first) sb.append(" AND ");
            String col = SqlIdentifiers.quote(f.column);
            switch (op) {
                case "eq" -> { sb.append(col).append(" = ?"); params.add(f.value); }
                case "ne" -> { sb.append(col).append(" <> ?"); params.add(f.value); }
                case "gt" -> { sb.append(col).append(" > ?"); params.add(f.value); }
                case "ge" -> { sb.append(col).append(" >= ?"); params.add(f.value); }
                case "lt" -> { sb.append(col).append(" < ?"); params.add(f.value); }
                case "le" -> { sb.append(col).append(" <= ?"); params.add(f.value); }
                case "like" -> { sb.append(col).append(" LIKE ?"); params.add("%" + f.value + "%"); }
                case "is_null" -> sb.append(col).append(" IS NULL");
                case "is_not_null" -> sb.append(col).append(" IS NOT NULL");
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid operator");
            }
            first = false;
        }
        return sb.toString();
    }

    private void requireColumn(String column, Set<String> validColumns) {
        if (column == null || !validColumns.contains(column)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid column: " + column);
        }
    }

    private void requireKeys(TableDataDto.RowChange change) {
        if (change.keys == null || change.keys.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Row identifier (keys) required for " + change.type);
        }
    }

    private void validateTableRef(String database, String table) {
        if (database == null || database.isBlank() || table == null || table.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "database and table are required");
        }
    }

    private QueryResult mapRs(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        QueryResult result = new QueryResult();
        result.columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            result.columns.add(new QueryResult.ColumnMeta(
                    md.getColumnLabel(i), md.getColumnTypeName(i), md.getColumnType(i),
                    md.isNullable(i) != ResultSetMetaData.columnNoNulls));
        }
        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                Object v = rs.getObject(i);
                if (v instanceof byte[] bytes) {
                    v = "0x" + hex(bytes);
                } else if (v instanceof Timestamp ts) {
                    v = ts.toInstant().toString();
                } else if (v instanceof java.sql.Date || v instanceof java.sql.Time) {
                    v = v.toString();
                }
                row.add(v);
            }
            rows.add(row);
        }
        result.rows = rows;
        result.rowCount = rows.size();
        return result;
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
