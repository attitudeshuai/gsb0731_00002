package com.dbtool.backend.dto;

import java.util.List;
import java.util.Map;

/** Request/response types for browsing and editing table data. */
public class TableDataDto {

    /** A single column filter. */
    public static class Filter {
        public String column;
        public String operator;   // eq, ne, gt, ge, lt, le, like, is_null, is_not_null
        public Object value;
    }

    public static class PageRequest {
        public String database;
        public String table;
        public int page = 1;              // 1-based
        public int pageSize = 100;
        public String sortColumn;
        public String sortDirection = "ASC";   // ASC | DESC
        public List<Filter> filters;
    }

    public static class PageResponse {
        public List<QueryResult.ColumnMeta> columns;
        public List<List<Object>> rows;
        public long total;
        public int page;
        public int pageSize;
        public List<String> primaryKeys;
    }

    /** Row change for edit/insert/delete. */
    public static class RowChange {
        public String type;                 // insert | update | delete
        public Map<String, Object> values;  // new values (insert/update)
        public Map<String, Object> keys;    // primary-key identifiers (update/delete)
    }

    public static class SaveRequest {
        public String database;
        public String table;
        public List<RowChange> changes;
    }

    /** A single statement rendered for user preview. */
    public static class PreviewStatement {
        public String type;      // insert | update | delete
        public String sql;       // parameterized SQL (with ? placeholders)
        public List<Object> params;
        public String rendered;  // human-readable SQL with values inlined (display only)

        public PreviewStatement() {
        }

        public PreviewStatement(String type, String sql, List<Object> params, String rendered) {
            this.type = type;
            this.sql = sql;
            this.params = params;
            this.rendered = rendered;
        }
    }

    public static class SaveResponse {
        public int inserted;
        public int updated;
        public int deleted;
        /** parameterized SQL strings actually run (or that would be run, for preview) */
        public List<String> statements;
        /** rendered statements for user confirmation; populated by the preview endpoint */
        public List<PreviewStatement> preview;
        /** false when this is a dry-run preview (nothing was executed) */
        public boolean executed = true;
    }
}
