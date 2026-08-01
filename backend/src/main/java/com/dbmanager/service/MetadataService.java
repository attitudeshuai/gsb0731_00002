package com.dbmanager.service;

import com.dbmanager.dto.ColumnInfo;
import com.dbmanager.dto.IndexInfo;
import com.dbmanager.dto.TableInfo;
import com.dbmanager.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MetadataService {

    private static final Set<String> SYSTEM_DATABASES = Set.of(
            "information_schema", "performance_schema", "mysql", "sys"
    );

    private final DynamicDataSourceService dynamicDataSourceService;

    public MetadataService(DynamicDataSourceService dynamicDataSourceService) {
        this.dynamicDataSourceService = dynamicDataSourceService;
    }

    public List<String> getDatabases(Long connectionId) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId);
        List<String> databases = jdbcTemplate.queryForList("SHOW DATABASES", String.class);
        return databases.stream()
                .filter(db -> !SYSTEM_DATABASES.contains(db.toLowerCase()))
                .toList();
    }

    public List<TableInfo> getTables(Long connectionId, String database) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<TableInfo> tables = new ArrayList<>();

        jdbcTemplate.execute((Connection conn) -> {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(database, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    TableInfo table = new TableInfo();
                    table.setName(rs.getString("TABLE_NAME"));
                    String tableType = rs.getString("TABLE_TYPE");
                    table.setType("VIEW".equalsIgnoreCase(tableType) ? "VIEW" : "TABLE");
                    table.setSchema(rs.getString("TABLE_SCHEM"));
                    table.setComment(rs.getString("REMARKS"));
                    tables.add(table);
                }
            }

            for (TableInfo table : tables) {
                try {
                    Long rowCount = jdbcTemplate.queryForObject(
                            "SELECT TABLE_ROWS FROM information_schema.tables WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                            Long.class, database, table.getName()
                    );
                    table.setRowCountEstimate(rowCount != null ? rowCount : 0L);
                } catch (Exception e) {
                    table.setRowCountEstimate(0L);
                }
            }
            return null;
        });

        return tables;
    }

    public List<ColumnInfo> getTableStructure(Long connectionId, String database, String table) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<ColumnInfo> columns = new ArrayList<>();
        Set<String> primaryKeys = new HashSet<>();
        Set<String> autoIncrementColumns = new HashSet<>();

        jdbcTemplate.execute((Connection conn) -> {
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet pkRs = metaData.getPrimaryKeys(database, null, table)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            try (ResultSet colRs = metaData.getColumns(database, null, table, "%")) {
                while (colRs.next()) {
                    ColumnInfo col = new ColumnInfo();
                    col.setName(colRs.getString("COLUMN_NAME"));
                    col.setType(colRs.getString("TYPE_NAME"));
                    col.setNullable("YES".equalsIgnoreCase(colRs.getString("IS_NULLABLE")));
                    col.setDefaultValue(colRs.getString("COLUMN_DEF"));
                    col.setComment(colRs.getString("REMARKS"));
                    col.setOrdinalPosition(colRs.getInt("ORDINAL_POSITION"));
                    col.setPrimaryKey(primaryKeys.contains(col.getName()));

                    String isAutoIncrement = colRs.getString("IS_AUTOINCREMENT");
                    col.setAutoIncrement("YES".equalsIgnoreCase(isAutoIncrement));

                    if (col.isAutoIncrement()) {
                        autoIncrementColumns.add(col.getName());
                    }

                    columns.add(col);
                }
            }
            return null;
        });

        return columns;
    }

    public List<IndexInfo> getIndexes(Long connectionId, String database, String table) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<IndexInfo> indexes = new ArrayList<>();

        jdbcTemplate.execute((Connection conn) -> {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getIndexInfo(database, null, table, false, true)) {
                while (rs.next()) {
                    IndexInfo index = new IndexInfo();
                    index.setIndexName(rs.getString("INDEX_NAME"));
                    index.setColumnName(rs.getString("COLUMN_NAME"));
                    index.setNonUnique(!rs.getBoolean("NON_UNIQUE"));
                    index.setIndexType(rs.getString("TYPE"));
                    index.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                    if (index.getIndexName() != null) {
                        indexes.add(index);
                    }
                }
            }
            return null;
        });

        return indexes;
    }

    public String getTableDDL(Long connectionId, String database, String table) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);

        try {
            return jdbcTemplate.query(connection -> {
                Connection conn = connection.unwrap(Connection.class);
                DatabaseMetaData metaData = conn.getMetaData();
                try (ResultSet rs = metaData.getTables(database, null, table, new String[]{"TABLE", "VIEW"})) {
                    if (rs.next()) {
                        String tableType = rs.getString("TABLE_TYPE");
                        if ("VIEW".equalsIgnoreCase(tableType)) {
                            return conn.prepareStatement("SHOW CREATE VIEW `" + database + "`.`" + table + "`");
                        }
                    }
                }
                return conn.prepareStatement("SHOW CREATE TABLE `" + database + "`.`" + table + "`");
            }, rs -> {
                if (rs.next()) {
                    return rs.getString(2);
                }
                return null;
            });
        } catch (Exception e) {
            throw new BusinessException("Failed to get DDL: " + e.getMessage());
        }
    }
}
