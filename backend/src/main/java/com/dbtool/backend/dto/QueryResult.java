package com.dbtool.backend.dto;

import java.util.List;

/** Generic tabular result of a SELECT / metadata query. */
public class QueryResult {
    public List<ColumnMeta> columns;
    public List<List<Object>> rows;
    public int rowCount;
    public long durationMs;
    public Integer affectedRows;   // for DML
    public boolean truncated;      // true if result was capped by max-result-rows

    public static class ColumnMeta {
        public String name;
        public String typeName;
        public int jdbcType;
        public boolean nullable;

        public ColumnMeta() {
        }

        public ColumnMeta(String name, String typeName, int jdbcType, boolean nullable) {
            this.name = name;
            this.typeName = typeName;
            this.jdbcType = jdbcType;
            this.nullable = nullable;
        }
    }
}
