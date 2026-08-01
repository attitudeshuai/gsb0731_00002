package com.example.dbmanager.service;

import com.example.dbmanager.dto.ColumnMeta;
import com.example.dbmanager.dto.FilterCondition;
import com.example.dbmanager.dto.TableDataResponse;
import com.example.dbmanager.exception.BadRequestException;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.util.SqlUtils;
import com.example.dbmanager.util.ValueMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表数据浏览 / 编辑。导出见 {@link ExportTaskManager}（异步任务式）。
 * 防注入：所有标识符反引号包裹并转义且经白名单校验，所有值使用 PreparedStatement 参数绑定。
 */
@Service
public class TableDataService {

    private static final Set<String> OPERATORS =
            Set.of("=", "!=", ">", ">=", "<", "<=", "LIKE", "IS NULL", "IS NOT NULL");
    private static final int MAX_PAGE_SIZE = 1000;

    private final TargetJdbcService targetJdbcService;
    private final ObjectMapper objectMapper;

    public TableDataService(TargetJdbcService targetJdbcService,
                            ObjectMapper objectMapper) {
        this.targetJdbcService = targetJdbcService;
        this.objectMapper = objectMapper;
    }

    // ---------- 数据浏览 ----------

    public TableDataResponse getData(Long connectionId, String database, String table,
                                     int page, int size, String sort, String order, String filtersJson) {
        if (page < 0) {
            throw new BadRequestException("page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size 必须在 1 ~ " + MAX_PAGE_SIZE + " 之间");
        }
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        List<TargetColumn> columns = requireColumns(jt, database, table);
        Set<String> columnNames = columnNames(columns);

        WhereClause where = buildWhere(parseFilters(filtersJson), columnNames);

        String orderBy = "";
        if (sort != null && !sort.isBlank()) {
            if (!columnNames.contains(sort)) {
                throw new BadRequestException("排序列不存在: " + sort);
            }
            String direction = (order == null || order.isBlank())
                    ? "ASC" : order.trim().toUpperCase(Locale.ROOT);
            if (!direction.equals("ASC") && !direction.equals("DESC")) {
                throw new BadRequestException("order 只能是 asc 或 desc");
            }
            orderBy = " ORDER BY " + SqlUtils.quoteIdent(sort) + " " + direction;
        }

        String tableRef = SqlUtils.tableRef(database, table);
        Long total = jt.queryForObject(
                "SELECT COUNT(*) FROM " + tableRef + where.sql(), Long.class, where.params().toArray());

        List<Object> params = new ArrayList<>(where.params());
        params.add(size);
        params.add((long) page * size);
        List<Map<String, Object>> result = jt.queryForList(
                "SELECT * FROM " + tableRef + where.sql() + orderBy + " LIMIT ? OFFSET ?",
                params.toArray());

        List<List<Object>> rows = result.stream()
                .map(map -> columns.stream()
                        .map(col -> ValueMapper.normalize(map.get(col.name())))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        return new TableDataResponse(
                columns.stream().map(col -> new ColumnMeta(col.name(), col.type())).toList(),
                primaryKeys(columns),
                rows,
                total == null ? 0 : total,
                page,
                size);
    }

    // ---------- 行编辑 ----------

    public int insertRow(Long connectionId, String database, String table, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            throw new BadRequestException("values 不能为空");
        }
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        Set<String> columnNames = columnNames(requireColumns(jt, database, table));
        validateColumns(values.keySet(), columnNames);

        String columnList = values.keySet().stream()
                .map(SqlUtils::quoteIdent)
                .collect(Collectors.joining(", "));
        String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        String sql = "INSERT INTO " + SqlUtils.tableRef(database, table)
                + " (" + columnList + ") VALUES (" + placeholders + ")";
        return jt.update(sql, values.values().toArray());
    }

    public int updateRow(Long connectionId, String database, String table,
                         Map<String, Object> keys, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            throw new BadRequestException("values 不能为空");
        }
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        List<TargetColumn> columns = requireColumns(jt, database, table);
        List<String> pks = primaryKeys(columns);
        if (pks.isEmpty()) {
            throw new BadRequestException("该表没有主键，不支持行编辑");
        }
        requirePrimaryKeys(keys, pks);
        Set<String> columnNames = columnNames(columns);
        validateColumns(values.keySet(), columnNames);
        validateColumns(keys.keySet(), columnNames);

        String setClause = values.keySet().stream()
                .map(col -> SqlUtils.quoteIdent(col) + " = ?")
                .collect(Collectors.joining(", "));
        String whereClause = keys.keySet().stream()
                .map(col -> SqlUtils.quoteIdent(col) + " = ?")
                .collect(Collectors.joining(" AND "));
        List<Object> params = new ArrayList<>(values.values());
        params.addAll(keys.values());
        String sql = "UPDATE " + SqlUtils.tableRef(database, table)
                + " SET " + setClause + " WHERE " + whereClause;
        return jt.update(sql, params.toArray());
    }

    public int deleteRows(Long connectionId, String database, String table, List<Map<String, Object>> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new BadRequestException("keys 不能为空");
        }
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        List<TargetColumn> columns = requireColumns(jt, database, table);
        List<String> pks = primaryKeys(columns);
        if (pks.isEmpty()) {
            throw new BadRequestException("该表没有主键，不支持行删除");
        }
        Set<String> columnNames = columnNames(columns);
        for (Map<String, Object> keySet : keys) {
            requirePrimaryKeys(keySet, pks);
            validateColumns(keySet.keySet(), columnNames);
        }

        String condition = keys.stream()
                .map(keySet -> keySet.keySet().stream()
                        .map(col -> SqlUtils.quoteIdent(col) + " = ?")
                        .collect(Collectors.joining(" AND ", "(", ")")))
                .collect(Collectors.joining(" OR "));
        List<Object> params = new ArrayList<>();
        keys.forEach(keySet -> params.addAll(keySet.values()));
        String sql = "DELETE FROM " + SqlUtils.tableRef(database, table) + " WHERE " + condition;
        return jt.update(sql, params.toArray());
    }

    // ---------- 内部工具（包级可见，供 ExportTaskManager 复用） ----------

    record TargetColumn(String name, String type, String key) {
    }

    record WhereClause(String sql, List<Object> params) {
    }

    private List<TargetColumn> loadColumns(JdbcTemplate jt, String database, String table) {
        return jt.query(
                "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                (rs, rowNum) -> new TargetColumn(rs.getString(1), rs.getString(2), rs.getString(3)),
                database, table);
    }

    List<TargetColumn> requireColumns(JdbcTemplate jt, String database, String table) {
        List<TargetColumn> columns = loadColumns(jt, database, table);
        if (columns.isEmpty()) {
            throw new NotFoundException("表不存在: " + database + "." + table);
        }
        return columns;
    }

    static Set<String> columnNames(List<TargetColumn> columns) {
        return columns.stream().map(TargetColumn::name).collect(Collectors.toSet());
    }

    private static List<String> primaryKeys(List<TargetColumn> columns) {
        return columns.stream()
                .filter(col -> "PRI".equals(col.key()))
                .map(TargetColumn::name)
                .toList();
    }

    List<FilterCondition> parseFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return List.of();
        }
        try {
            List<FilterCondition> filters =
                    objectMapper.readValue(filtersJson, new TypeReference<>() {
                    });
            return filters == null ? List.of() : filters;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("filters 参数不是合法的 JSON 数组");
        }
    }

    WhereClause buildWhere(List<FilterCondition> filters, Set<String> columnNames) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (FilterCondition filter : filters) {
            if (filter.column() == null || !columnNames.contains(filter.column())) {
                throw new BadRequestException("筛选列不存在: " + filter.column());
            }
            String op = filter.operator() == null
                    ? "" : filter.operator().trim().toUpperCase(Locale.ROOT);
            if (!OPERATORS.contains(op)) {
                throw new BadRequestException("不支持的筛选操作符: " + filter.operator());
            }
            where.append(" AND ").append(SqlUtils.quoteIdent(filter.column())).append(' ').append(op);
            if (!op.startsWith("IS")) {
                where.append(" ?");
                params.add(filter.value());
            }
        }
        return new WhereClause(where.toString(), params);
    }

    private static void validateColumns(Set<String> columns, Set<String> columnNames) {
        for (String column : columns) {
            if (!columnNames.contains(column)) {
                throw new BadRequestException("列不存在: " + column);
            }
        }
    }

    private static void requirePrimaryKeys(Map<String, Object> keys, List<String> pks) {
        if (keys == null || keys.isEmpty()) {
            throw new BadRequestException("keys 必须包含主键列: " + String.join(", ", pks));
        }
        for (String pk : pks) {
            if (!keys.containsKey(pk)) {
                throw new BadRequestException("keys 缺少主键列: " + pk);
            }
        }
    }
}
