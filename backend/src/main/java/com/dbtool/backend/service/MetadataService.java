package com.dbtool.backend.service;

import com.dbtool.backend.dto.MetadataDto;
import com.dbtool.backend.target.TargetDataSourceManager;
import com.dbtool.backend.util.SqlIdentifiers;
import com.dbtool.backend.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** Reads schema metadata from a target database via JDBC Template. */
@Service
public class MetadataService {

    private final TargetDataSourceManager dataSourceManager;

    public MetadataService(TargetDataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }

    /** Lists databases (schemas) visible to the connection user. */
    public List<String> listDatabases(Long connectionId) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        return jt.queryForList(
                "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA " +
                "WHERE SCHEMA_NAME NOT IN ('information_schema','performance_schema','mysql','sys') " +
                "ORDER BY SCHEMA_NAME", String.class);
    }

    /** Lists tables and views in a database. */
    public List<MetadataDto.TableInfo> listTables(Long connectionId, String database) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        return jt.query(
                "SELECT TABLE_NAME, TABLE_TYPE, TABLE_ROWS, TABLE_COMMENT " +
                "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? " +
                "ORDER BY TABLE_NAME",
                (rs, i) -> {
                    MetadataDto.TableInfo t = new MetadataDto.TableInfo();
                    t.name = rs.getString("TABLE_NAME");
                    t.type = rs.getString("TABLE_TYPE");
                    t.estimatedRows = (Long) rs.getObject("TABLE_ROWS");
                    t.comment = rs.getString("TABLE_COMMENT");
                    return t;
                }, database);
    }

    public MetadataDto.TableStructure getTableStructure(Long connectionId, String database, String table) {
        MetadataDto.TableStructure s = new MetadataDto.TableStructure();
        s.table = table;
        s.columns = listColumns(connectionId, database, table);
        s.indexes = listIndexes(connectionId, database, table);
        s.estimatedRows = estimateRows(connectionId, database, table);
        s.ddl = getCreateDdl(connectionId, database, table);
        return s;
    }

    public List<MetadataDto.ColumnInfo> listColumns(Long connectionId, String database, String table) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        return jt.query(
                "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, " +
                "COLUMN_KEY, EXTRA, ORDINAL_POSITION " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION",
                (rs, i) -> {
                    MetadataDto.ColumnInfo c = new MetadataDto.ColumnInfo();
                    c.name = rs.getString("COLUMN_NAME");
                    c.dataType = rs.getString("COLUMN_TYPE");
                    c.nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    c.defaultValue = rs.getString("COLUMN_DEFAULT");
                    c.comment = rs.getString("COLUMN_COMMENT");
                    c.columnKey = rs.getString("COLUMN_KEY");
                    c.primaryKey = "PRI".equalsIgnoreCase(c.columnKey);
                    c.extra = rs.getString("EXTRA");
                    c.ordinalPosition = rs.getInt("ORDINAL_POSITION");
                    return c;
                }, database, table);
    }

    public List<MetadataDto.IndexInfo> listIndexes(Long connectionId, String database, String table) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        // Aggregate columns per index preserving SEQ_IN_INDEX order
        List<Map<String, Object>> rows = jt.queryForList(
                "SELECT INDEX_NAME, NON_UNIQUE, INDEX_TYPE, COLUMN_NAME, SEQ_IN_INDEX " +
                "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY INDEX_NAME, SEQ_IN_INDEX",
                database, table);

        LinkedHashMap<String, MetadataDto.IndexInfo> byName = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("INDEX_NAME");
            MetadataDto.IndexInfo idx = byName.computeIfAbsent(name, n -> {
                MetadataDto.IndexInfo ix = new MetadataDto.IndexInfo();
                ix.name = n;
                ix.unique = ((Number) row.get("NON_UNIQUE")).intValue() == 0;
                ix.type = (String) row.get("INDEX_TYPE");
                ix.columns = new ArrayList<>();
                return ix;
            });
            idx.columns.add((String) row.get("COLUMN_NAME"));
        }
        return new ArrayList<>(byName.values());
    }

    public Long estimateRows(Long connectionId, String database, String table) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        return jt.queryForObject(
                "SELECT TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                Long.class, database, table);
    }

    public String getCreateDdl(Long connectionId, String database, String table) {
        JdbcTemplate jt = dataSourceManager.jdbcTemplate(connectionId);
        String sql = "SHOW CREATE TABLE " + SqlIdentifiers.quote(database) + "." + SqlIdentifiers.quote(table);
        try {
            return jt.query(sql, rs -> {
                if (rs.next()) {
                    // second column is "Create Table" or "Create View"
                    return rs.getString(2);
                }
                return null;
            });
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read DDL: " + e.getMessage());
        }
    }
}
