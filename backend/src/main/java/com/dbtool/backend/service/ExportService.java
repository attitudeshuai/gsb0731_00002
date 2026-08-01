package com.dbtool.backend.service;

import com.dbtool.backend.dto.QueryResult;
import com.dbtool.backend.entity.ExportLog;
import com.dbtool.backend.repository.ExportLogRepository;
import com.dbtool.backend.util.SqlIdentifiers;
import com.dbtool.backend.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Streams table / query results to CSV, JSON or SQL INSERT.
 * <p>
 * Rows are read from the DB with a server-side cursor (via the isolated export pool)
 * and written straight to the output {@link Writer} one at a time, so memory usage is
 * independent of the total number of rows. Full-table export is NOT subject to the
 * max-result-rows cap: the exported file always contains every row in the table.
 * <p>
 * A progress callback is invoked as rows are written so callers (the async job) can
 * expose live progress, and a query token allows the underlying statement to be
 * cancelled mid-stream.
 */
@Service
public class ExportService {

    private final SqlExecutionService sqlExecutionService;
    private final ExportLogRepository exportLogRepository;

    public ExportService(SqlExecutionService sqlExecutionService, ExportLogRepository exportLogRepository) {
        this.sqlExecutionService = sqlExecutionService;
        this.exportLogRepository = exportLogRepository;
    }

    public long exportQuery(Long connectionId, String sql, String format, Writer out,
                            String queryToken, LongConsumer progress) {
        long rows = stream(connectionId, sql, format, "exported_result", out, queryToken, progress);
        log(connectionId, "query", truncate(sql), format, rows);
        return rows;
    }

    public long exportTable(Long connectionId, String database, String table, String format, Writer out,
                            String queryToken, LongConsumer progress) {
        String qualified = SqlIdentifiers.quote(database) + "." + SqlIdentifiers.quote(table);
        // Full table dump — no LIMIT, streamed row-by-row so the file has every row.
        long rows = stream(connectionId, "SELECT * FROM " + qualified, format, table, out, queryToken, progress);
        log(connectionId, "table", database + "." + table, format, rows);
        return rows;
    }

    /** Streams the query to the writer in the requested format; returns rows written. */
    private long stream(Long connectionId, String sql, String format, String name, Writer out,
                        String queryToken, LongConsumer progress) {
        String fmt = format == null ? "" : format.toLowerCase();
        RowWriter writer = switch (fmt) {
            case "csv" -> new CsvWriter(out);
            case "json" -> new JsonWriter(out);
            case "sql" -> new SqlInsertWriter(out, name);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported format: " + format);
        };
        try {
            long count = sqlExecutionService.streamQuery(connectionId, sql, new SqlExecutionService.RowConsumer() {
                private long written = 0;

                @Override
                public void columns(List<QueryResult.ColumnMeta> columns) throws Exception {
                    writer.begin(columns);
                }

                @Override
                public void row(List<Object> values) throws Exception {
                    writer.row(values);
                    written++;
                    // report progress periodically to avoid contention on every row
                    if (progress != null && (written % 1000 == 0)) {
                        progress.accept(written);
                    }
                }
            }, queryToken);
            writer.end();
            out.flush();
            if (progress != null) {
                progress.accept(count);
            }
            return count;
        } catch (ApiException e) {
            throw e;
        } catch (UncheckedIOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Export failed: " + e.getMessage());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Export failed: " + e.getMessage());
        }
    }

    // --- streaming writers ---

    private interface RowWriter {
        void begin(List<QueryResult.ColumnMeta> columns) throws IOException;
        void row(List<Object> values) throws IOException;
        void end() throws IOException;
    }

    private final class CsvWriter implements RowWriter {
        private final Writer out;
        private int colCount;

        CsvWriter(Writer out) {
            this.out = out;
        }

        @Override
        public void begin(List<QueryResult.ColumnMeta> columns) throws IOException {
            colCount = columns.size();
            StringBuilder header = new StringBuilder();
            for (int i = 0; i < colCount; i++) {
                if (i > 0) header.append(',');
                header.append(csvCell(columns.get(i).name));
            }
            out.write(header.toString());
            out.write("\r\n");
        }

        @Override
        public void row(List<Object> values) throws IOException {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) line.append(',');
                Object v = values.get(i);
                line.append(v == null ? "" : csvCell(v.toString()));
            }
            out.write(line.toString());
            out.write("\r\n");
        }

        @Override
        public void end() {
        }
    }

    private final class JsonWriter implements RowWriter {
        private final Writer out;
        private List<QueryResult.ColumnMeta> columns;
        private boolean first = true;

        JsonWriter(Writer out) {
            this.out = out;
        }

        @Override
        public void begin(List<QueryResult.ColumnMeta> columns) throws IOException {
            this.columns = columns;
            out.write("[");
        }

        @Override
        public void row(List<Object> values) throws IOException {
            if (!first) out.write(",");
            first = false;
            out.write("{");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) out.write(",");
                out.write(jsonString(columns.get(i).name));
                out.write(":");
                out.write(jsonValue(values.get(i)));
            }
            out.write("}");
        }

        @Override
        public void end() throws IOException {
            out.write("]");
        }
    }

    private final class SqlInsertWriter implements RowWriter {
        private final Writer out;
        private final String table;
        private String colsClause;

        SqlInsertWriter(Writer out, String table) {
            this.out = out;
            this.table = table;
        }

        @Override
        public void begin(List<QueryResult.ColumnMeta> columns) {
            StringBuilder cols = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) cols.append(", ");
                cols.append(SqlIdentifiers.quote(columns.get(i).name));
            }
            colsClause = cols.toString();
        }

        @Override
        public void row(List<Object> values) throws IOException {
            StringBuilder vals = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) vals.append(", ");
                vals.append(sqlLiteral(values.get(i)));
            }
            out.write("INSERT INTO " + SqlIdentifiers.quote(table) + " (" + colsClause + ") VALUES (" + vals + ");\r\n");
        }

        @Override
        public void end() {
        }
    }

    // --- value formatting helpers ---

    private String csvCell(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }

    private String jsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return jsonString(v.toString());
    }

    private String sqlLiteral(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "'" + v.toString().replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private void log(Long connectionId, String sourceType, String sourceRef, String format, long rowCount) {
        try {
            ExportLog l = new ExportLog();
            l.setConnectionId(connectionId);
            l.setSourceType(sourceType);
            l.setSourceRef(sourceRef);
            l.setFormat(format == null ? "" : format.toLowerCase());
            l.setRowCount(rowCount);
            l.setStatus("success");
            exportLogRepository.save(l);
        } catch (Exception ignored) {
        }
    }
}
