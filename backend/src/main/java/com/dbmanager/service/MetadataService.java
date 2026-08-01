package com.dbmanager.service;

import com.dbmanager.dto.ColumnMeta;
import com.dbmanager.dto.IndexInfo;
import com.dbmanager.dto.TableMeta;
import com.dbmanager.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final DynamicDataSourceManager dataSourceManager;

    public List<String> listDatabases(Long connectionId) {
        try (Connection conn = dataSourceManager.getConnection(connectionId, null)) {
            List<String> databases = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT schema_name FROM information_schema.schemata " +
                                 "WHERE schema_name NOT IN ('information_schema','performance_schema','mysql','sys') " +
                                 "ORDER BY schema_name")) {
                while (rs.next()) {
                    databases.add(rs.getString(1));
                }
            }
            return databases;
        } catch (SQLException e) {
            throw new BusinessException("Failed to list databases: " + e.getMessage());
        }
    }

    public List<TableMeta> listTables(Long connectionId, String database, String keyword) {
        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            List<TableMeta> tables = new ArrayList<>();
            DatabaseMetaData meta = conn.getMetaData();
            String[] types = {"TABLE", "VIEW"};
            try (ResultSet rs = meta.getTables(database, null,
                    keyword != null && !keyword.isBlank() ? "%" + keyword + "%" : "%", types)) {
                while (rs.next()) {
                    TableMeta t = TableMeta.builder()
                            .name(rs.getString("TABLE_NAME"))
                            .type(rs.getString("TABLE_TYPE"))
                            .comment(rs.getString("REMARKS"))
                            .build();
                    tables.add(t);
                }
            }
            for (TableMeta t : tables) {
                t.setEstimatedRows(estimateRowCount(conn, database, t.getName()));
            }
            return tables;
        } catch (SQLException e) {
            throw new BusinessException("Failed to list tables: " + e.getMessage());
        }
    }

    private Long estimateRowCount(Connection conn, String database, String table) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_rows FROM information_schema.tables " +
                             "WHERE table_schema = '" + escape(database) + "' " +
                             "AND table_name = '" + escape(table) + "'")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public List<ColumnMeta> describeTable(Long connectionId, String database, String table) {
        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            DatabaseMetaData meta = conn.getMetaData();
            Map<String, ColumnMeta> columns = new LinkedHashMap<>();

            try (ResultSet rs = meta.getColumns(database, null, table, null)) {
                while (rs.next()) {
                    ColumnMeta c = ColumnMeta.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .type(rs.getString("TYPE_NAME"))
                            .typeName(rs.getString("TYPE_NAME"))
                            .precision(getInteger(rs, "COLUMN_SIZE"))
                            .scale(getInteger(rs, "DECIMAL_DIGITS"))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .defaultValue(rs.getString("COLUMN_DEF"))
                            .comment(rs.getString("REMARKS"))
                            .autoIncrement("YES".equals(rs.getString("IS_AUTOINCREMENT")))
                            .primaryKey(false)
                            .build();
                    columns.put(c.getName(), c);
                }
            }

            try (ResultSet rs = meta.getPrimaryKeys(database, null, table)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    ColumnMeta c = columns.get(col);
                    if (c != null) {
                        c.setPrimaryKey(true);
                    }
                }
            }
            return new ArrayList<>(columns.values());
        } catch (SQLException e) {
            throw new BusinessException("Failed to describe table: " + e.getMessage());
        }
    }

    public List<IndexInfo> listIndexes(Long connectionId, String database, String table) {
        try (Connection conn = dataSourceManager.getConnection(connectionId, database)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<IndexInfo> indexes = new ArrayList<>();
            try (ResultSet rs = meta.getIndexInfo(database, null, table, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;
                    indexes.add(IndexInfo.builder()
                            .indexName(indexName)
                            .columnName(rs.getString("COLUMN_NAME"))
                            .nonUnique(rs.getBoolean("NON_UNIQUE"))
                            .indexType(rs.getString("TYPE"))
                            .seqInIndex(getInteger(rs, "ORDINAL_POSITION"))
                            .build());
                }
            }
            return indexes;
        } catch (SQLException e) {
            throw new BusinessException("Failed to list indexes: " + e.getMessage());
        }
    }

    public String showCreateTable(Long connectionId, String database, String table) {
        try (Connection conn = dataSourceManager.getConnection(connectionId, database);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE `" + escape(table) + "`")) {
            if (rs.next()) {
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String val = rs.getString(i);
                    if (val != null && val.contains("CREATE")) {
                        return val;
                    }
                }
                return rs.getString(2);
            }
            throw new BusinessException("No DDL returned for table " + table);
        } catch (SQLException e) {
            throw new BusinessException("Failed to get DDL: " + e.getMessage());
        }
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("'", "''").replace("`", "``");
    }
}
