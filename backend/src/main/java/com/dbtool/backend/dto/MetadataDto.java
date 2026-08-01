package com.dbtool.backend.dto;

import java.util.List;

/** Metadata DTOs for schema browsing. */
public class MetadataDto {

    public static class TableInfo {
        public String name;
        public String type;   // BASE TABLE | VIEW
        public Long estimatedRows;
        public String comment;
    }

    public static class ColumnInfo {
        public String name;
        public String dataType;
        public boolean nullable;
        public String defaultValue;
        public String comment;
        public boolean primaryKey;
        public String columnKey;    // PRI | UNI | MUL | ''
        public String extra;        // auto_increment, etc.
        public int ordinalPosition;
    }

    public static class IndexInfo {
        public String name;
        public boolean unique;
        public String type;
        public List<String> columns;
    }

    public static class TableStructure {
        public String table;
        public List<ColumnInfo> columns;
        public List<IndexInfo> indexes;
        public Long estimatedRows;
        public String ddl;
    }
}
