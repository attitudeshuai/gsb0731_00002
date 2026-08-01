package com.example.dbmanager.service;

import com.example.dbmanager.dto.ColumnInfoDto;
import com.example.dbmanager.dto.IndexInfoDto;
import com.example.dbmanager.dto.TableInfoDto;
import com.example.dbmanager.dto.TableStructureResponse;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.util.SqlUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 目标库元数据浏览：数据库列表、表 / 视图列表、表结构、DDL。
 */
@Service
public class BrowserService {

    private static final Set<String> SYSTEM_DATABASES =
            Set.of("information_schema", "performance_schema", "mysql", "sys");

    private final TargetJdbcService targetJdbcService;

    public BrowserService(TargetJdbcService targetJdbcService) {
        this.targetJdbcService = targetJdbcService;
    }

    /**
     * 目标库中所有数据库名（过滤系统库）。
     */
    public List<String> listDatabases(Long connectionId) {
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        return jt.queryForList("SHOW DATABASES", String.class).stream()
                .filter(db -> !SYSTEM_DATABASES.contains(db.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * 指定库中的表和视图列表，支持关键字模糊过滤。
     */
    public List<TableInfoDto> listTables(Long connectionId, String database, String keyword) {
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        StringBuilder sql = new StringBuilder(
                "SELECT TABLE_NAME, TABLE_TYPE, TABLE_ROWS, TABLE_COMMENT " +
                "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?");
        List<Object> params = new ArrayList<>();
        params.add(database);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND TABLE_NAME LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY TABLE_NAME");
        return jt.query(sql.toString(),
                (rs, rowNum) -> new TableInfoDto(
                        rs.getString("TABLE_NAME"),
                        rs.getString("TABLE_TYPE"),
                        rs.getObject("TABLE_ROWS", Long.class),
                        rs.getString("TABLE_COMMENT")),
                params.toArray());
    }

    /**
     * 表结构：DDL + 列定义 + 索引 + 估算行数。
     */
    public TableStructureResponse getStructure(Long connectionId, String database, String table) {
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);

        List<ColumnInfoDto> columns = jt.query(
                "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, COLUMN_KEY, EXTRA " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                (rs, rowNum) -> new ColumnInfoDto(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("COLUMN_TYPE"),
                        "YES".equals(rs.getString("IS_NULLABLE")),
                        rs.getString("COLUMN_DEFAULT"),
                        rs.getString("COLUMN_COMMENT"),
                        rs.getString("COLUMN_KEY"),
                        rs.getString("EXTRA")),
                database, table);
        if (columns.isEmpty()) {
            throw new NotFoundException("表不存在: " + database + "." + table);
        }

        Map<String, IndexAccumulator> indexMap = new LinkedHashMap<>();
        jt.query(
                "SELECT INDEX_NAME, NON_UNIQUE, INDEX_TYPE, COLUMN_NAME " +
                "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY INDEX_NAME, SEQ_IN_INDEX",
                rs -> {
                    String name = rs.getString("INDEX_NAME");
                    String type = rs.getString("INDEX_TYPE");
                    boolean unique = rs.getInt("NON_UNIQUE") == 0;
                    String column = rs.getString("COLUMN_NAME");
                    indexMap.computeIfAbsent(name, n -> new IndexAccumulator(n, type, unique))
                            .columns.add(column);
                },
                database, table);
        List<IndexInfoDto> indexes = indexMap.values().stream()
                .map(acc -> new IndexInfoDto(acc.name, acc.type, acc.columns, acc.unique))
                .toList();

        List<Long> rows = jt.query(
                "SELECT TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                (rs, rowNum) -> rs.getObject("TABLE_ROWS", Long.class),
                database, table);
        Long estimatedRows = rows.isEmpty() ? null : rows.get(0);

        return new TableStructureResponse(showCreateTable(jt, database, table), columns, indexes, estimatedRows);
    }

    /**
     * 仅获取 SHOW CREATE TABLE 结果。
     */
    public String getDdl(Long connectionId, String database, String table) {
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        try {
            return showCreateTable(jt, database, table);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("表不存在: " + database + "." + table);
        }
    }

    private String showCreateTable(JdbcTemplate jt, String database, String table) {
        Map<String, Object> row = jt.queryForMap("SHOW CREATE TABLE " + SqlUtils.tableRef(database, table));
        // 普通表键为 "Create Table"，视图键为 "Create View"
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).startsWith("create") && entry.getValue() != null) {
                return entry.getValue().toString();
            }
        }
        return "";
    }

    private static final class IndexAccumulator {
        private final String name;
        private final String type;
        private final boolean unique;
        private final List<String> columns = new ArrayList<>();

        private IndexAccumulator(String name, String type, boolean unique) {
            this.name = name;
            this.type = type;
            this.unique = unique;
        }
    }
}
