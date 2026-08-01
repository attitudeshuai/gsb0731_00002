package com.dbmanager.service;

import com.dbmanager.dto.ColumnInfo;
import com.dbmanager.dto.RowUpdateRequest;
import com.dbmanager.dto.TableDataResponse;
import com.dbmanager.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TableDataService {

    private final DynamicDataSourceService dynamicDataSourceService;
    private final MetadataService metadataService;
    private final ObjectMapper objectMapper;

    public TableDataService(DynamicDataSourceService dynamicDataSourceService,
                            MetadataService metadataService) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.metadataService = metadataService;
        this.objectMapper = new ObjectMapper();
    }

    public TableDataResponse getTableData(Long connectionId, String database, String table,
                                           int page, int size, String sort, String filter) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<ColumnInfo> columns = metadataService.getTableStructure(connectionId, database, table);
        Set<String> columnNames = new HashSet<>();
        for (ColumnInfo col : columns) {
            columnNames.add(col.getName());
        }

        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();

        if (filter != null && !filter.isBlank()) {
            try {
                JsonNode filterNode = objectMapper.readTree(filter);
                List<String> conditions = new ArrayList<>();
                parseFilter(filterNode, columnNames, conditions, params);
                if (!conditions.isEmpty()) {
                    whereClause.append(" WHERE ").append(String.join(" AND ", conditions));
                }
            } catch (Exception e) {
                throw new BusinessException("Invalid filter format: " + e.getMessage());
            }
        }

        String countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s`%s",
                escapeIdentifier(database), escapeIdentifier(table), whereClause);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) {
            total = 0L;
        }

        StringBuilder orderClause = new StringBuilder();
        if (sort != null && !sort.isBlank()) {
            String[] sortParts = sort.trim().split("\\s+");
            if (sortParts.length >= 1) {
                String sortCol = sortParts[0];
                if (!columnNames.contains(sortCol)) {
                    throw new BusinessException("Invalid sort column: " + sortCol);
                }
                String direction = "ASC";
                if (sortParts.length >= 2) {
                    String dir = sortParts[1].toUpperCase();
                    if (!dir.equals("ASC") && !dir.equals("DESC")) {
                        throw new BusinessException("Invalid sort direction: " + dir);
                    }
                    direction = dir;
                }
                orderClause.append(" ORDER BY `").append(escapeIdentifier(sortCol)).append("` ").append(direction);
            }
        }

        int offset = page * size;
        String dataSql = String.format("SELECT * FROM `%s`.`%s`%s%s LIMIT ?, ?",
                escapeIdentifier(database), escapeIdentifier(table), whereClause, orderClause);
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(offset);
        dataParams.add(size);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, dataParams.toArray());

        TableDataResponse response = new TableDataResponse();
        response.setColumns(columns);
        response.setRows(rows);
        response.setTotal(total);
        response.setPage(page);
        response.setSize(size);
        return response;
    }

    public int updateRow(Long connectionId, String database, String table, RowUpdateRequest request) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<ColumnInfo> columns = metadataService.getTableStructure(connectionId, database, table);
        Set<String> validColumns = new HashSet<>();
        for (ColumnInfo col : columns) {
            validColumns.add(col.getName());
        }

        Map<String, Object> row = request.getRow();
        Map<String, Object> primaryKey = request.getPrimaryKey();

        List<String> setClauses = new ArrayList<>();
        List<Object> setParams = new ArrayList<>();

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String col = entry.getKey();
            if (!validColumns.contains(col)) {
                throw new BusinessException("Invalid column: " + col);
            }
            if (primaryKey.containsKey(col)) {
                continue;
            }
            setClauses.add("`" + escapeIdentifier(col) + "` = ?");
            setParams.add(entry.getValue());
        }

        if (setClauses.isEmpty()) {
            throw new BusinessException("No columns to update");
        }

        List<String> whereClauses = new ArrayList<>();
        List<Object> whereParams = new ArrayList<>();

        for (Map.Entry<String, Object> entry : primaryKey.entrySet()) {
            String col = entry.getKey();
            if (!validColumns.contains(col)) {
                throw new BusinessException("Invalid primary key column: " + col);
            }
            whereClauses.add("`" + escapeIdentifier(col) + "` = ?");
            whereParams.add(entry.getValue());
        }

        if (whereClauses.isEmpty()) {
            throw new BusinessException("Primary key is required");
        }

        String sql = String.format("UPDATE `%s`.`%s` SET %s WHERE %s",
                escapeIdentifier(database), escapeIdentifier(table),
                String.join(", ", setClauses),
                String.join(" AND ", whereClauses));

        List<Object> allParams = new ArrayList<>();
        allParams.addAll(setParams);
        allParams.addAll(whereParams);

        return jdbcTemplate.update(sql, allParams.toArray());
    }

    public int insertRow(Long connectionId, String database, String table, Map<String, Object> row) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<ColumnInfo> columns = metadataService.getTableStructure(connectionId, database, table);
        Set<String> validColumns = new HashSet<>();
        for (ColumnInfo col : columns) {
            validColumns.add(col.getName());
        }

        List<String> colNames = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String col = entry.getKey();
            if (!validColumns.contains(col)) {
                throw new BusinessException("Invalid column: " + col);
            }
            colNames.add("`" + escapeIdentifier(col) + "`");
            values.add(entry.getValue());
        }

        if (colNames.isEmpty()) {
            throw new BusinessException("No data to insert");
        }

        String placeholders = String.join(", ", values.stream().map(v -> "?").toArray(String[]::new));
        String sql = String.format("INSERT INTO `%s`.`%s` (%s) VALUES (%s)",
                escapeIdentifier(database), escapeIdentifier(table),
                String.join(", ", colNames), placeholders);

        return jdbcTemplate.update(sql, values.toArray());
    }

    public int deleteRow(Long connectionId, String database, String table, Map<String, Object> primaryKey) {
        JdbcTemplate jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, database);
        List<ColumnInfo> columns = metadataService.getTableStructure(connectionId, database, table);
        Set<String> validColumns = new HashSet<>();
        for (ColumnInfo col : columns) {
            validColumns.add(col.getName());
        }

        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : primaryKey.entrySet()) {
            String col = entry.getKey();
            if (!validColumns.contains(col)) {
                throw new BusinessException("Invalid primary key column: " + col);
            }
            whereClauses.add("`" + escapeIdentifier(col) + "` = ?");
            params.add(entry.getValue());
        }

        if (whereClauses.isEmpty()) {
            throw new BusinessException("Primary key is required");
        }

        String sql = String.format("DELETE FROM `%s`.`%s` WHERE %s",
                escapeIdentifier(database), escapeIdentifier(table),
                String.join(" AND ", whereClauses));

        return jdbcTemplate.update(sql, params.toArray());
    }

    @SuppressWarnings("unchecked")
    private void parseFilter(JsonNode node, Set<String> validColumns,
                              List<String> conditions, List<Object> params) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String column = entry.getKey();
                if (!validColumns.contains(column)) {
                    throw new BusinessException("Invalid filter column: " + column);
                }
                JsonNode valueNode = entry.getValue();

                if (valueNode.isObject()) {
                    String op = "eq";
                    JsonNode opNode = valueNode.get("op");
                    JsonNode valNode = valueNode.get("value");
                    if (opNode != null) {
                        op = opNode.asText().toLowerCase();
                    }
                    Object value = valNode != null ? nodeToValue(valNode) : null;
                    addCondition(conditions, params, column, op, value);
                } else {
                    Object value = nodeToValue(valueNode);
                    addCondition(conditions, params, column, "eq", value);
                }
            }
        }
    }

    private void addCondition(List<String> conditions, List<Object> params,
                               String column, String op, Object value) {
        String escapedCol = "`" + escapeIdentifier(column) + "`";
        switch (op) {
            case "like":
                conditions.add(escapedCol + " LIKE ?");
                params.add("%" + value + "%");
                break;
            case "gt":
                conditions.add(escapedCol + " > ?");
                params.add(value);
                break;
            case "lt":
                conditions.add(escapedCol + " < ?");
                params.add(value);
                break;
            case "gte":
                conditions.add(escapedCol + " >= ?");
                params.add(value);
                break;
            case "lte":
                conditions.add(escapedCol + " <= ?");
                params.add(value);
                break;
            case "ne":
                conditions.add(escapedCol + " != ?");
                params.add(value);
                break;
            case "eq":
            default:
                conditions.add(escapedCol + " = ?");
                params.add(value);
                break;
        }
    }

    private Object nodeToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }

    private String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.replace("`", "``");
    }
}
