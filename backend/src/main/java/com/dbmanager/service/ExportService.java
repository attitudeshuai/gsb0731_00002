package com.dbmanager.service;

import com.dbmanager.entity.ConnectionConfig;
import com.dbmanager.entity.ExportLog;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.repository.ConnectionRepository;
import com.dbmanager.repository.ExportLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final DynamicDataSourceManager dataSourceManager;
    private final ExportLogRepository exportLogRepository;
    private final QueryCancelRegistry cancelRegistry;
    private final ConnectionRepository connectionRepository;

    public long exportTable(Long connectionId, String database, String table,
                            String format, HttpServletResponse response, String requestId) {
        String sql = "SELECT * FROM `" + table.replace("`", "``") + "`";
        return exportQuery(connectionId, database, sql, format, response, table, requestId);
    }

    public long exportQuery(Long connectionId, String database, String sql,
                            String format, HttpServletResponse response,
                            String sourceName, String requestId) {
        boolean responseCommitted = false;
        long totalRows = 0L;
        ConnectionConfig config = connectionId != null
                ? connectionRepository.findById(connectionId).orElse(null)
                : null;
        try (Connection conn = dataSourceManager.getStreamingConnection(connectionId, database)) {
            setResponseHeaders(response, format, sourceName);

            PreparedStatement ps = conn.prepareStatement(
                    sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);
            if (requestId != null) {
                cancelRegistry.register(requestId, ps, conn, "export", config, database);
            }

            try (ps; ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                String[] columnNames = new String[colCount];
                int[] columnTypes = new int[colCount];
                for (int i = 0; i < colCount; i++) {
                    columnNames[i] = md.getColumnLabel(i + 1);
                    columnTypes[i] = md.getColumnType(i + 1);
                }

                PrintWriter writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8), 8192));
                responseCommitted = true;

                try {
                    totalRows = switch (format.toLowerCase()) {
                        case "csv" -> writeCsv(rs, columnNames, colCount, writer);
                        case "json" -> writeJson(rs, columnNames, colCount, writer);
                        case "sql" -> writeSqlInsert(rs, columnNames, columnTypes,
                                colCount, sourceName, writer);
                        default -> throw new BusinessException("Unsupported export format: " + format);
                    };
                    writer.flush();
                    if (writer.checkError()) {
                        throw new IOException("Client closed connection while exporting");
                    }
                } finally {
                    try { writer.flush(); } catch (Exception ignored) {}
                    if (requestId != null) cancelRegistry.unregister(requestId);
                }
            } catch (SQLException e) {
                if (isCancellation(e)) {
                    throw new IOException("Export was cancelled", e);
                }
                throw e;
            }
        } catch (BusinessException e) {
            if (responseCommitted) {
                throw new RuntimeException(e);
            }
            throw e;
        } catch (IOException e) {
            log.warn("Export I/O error (client likely aborted): {}", e.getMessage());
            if (responseCommitted) {
                throw new RuntimeException(e);
            }
            throw new BusinessException("Export failed: " + e.getMessage());
        } catch (SQLException e) {
            log.error("Export failed", e);
            if (responseCommitted) {
                throw new RuntimeException(e);
            }
            throw new BusinessException("Export failed: " + e.getMessage());
        }

        saveLog(connectionId, database, sourceName, format, totalRows);
        return totalRows;
    }

    private boolean isCancellation(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String upper = msg.toUpperCase();
        return upper.contains("QUERY INTERRUPTED")
                || upper.contains("CANCELLED")
                || (e.getSQLState() != null && e.getSQLState().equals("57014"));
    }

    private long writeCsv(ResultSet rs, String[] columns, int colCount, PrintWriter writer)
            throws SQLException, IOException {
        writer.write('\ufeff');
        for (int i = 0; i < colCount; i++) {
            if (i > 0) writer.print(',');
            writer.print(csvEscape(columns[i]));
        }
        writer.println();
        long rows = 0;
        while (rs.next()) {
            for (int i = 0; i < colCount; i++) {
                if (i > 0) writer.print(',');
                String val = rs.getString(i + 1);
                writer.print(csvEscape(val));
            }
            writer.println();
            rows++;
            if (rows % 1000 == 0) {
                writer.flush();
                if (writer.checkError()) {
                    throw new IOException("Client connection lost during export at row " + rows);
                }
            }
        }
        return rows;
    }

    private long writeJson(ResultSet rs, String[] columns, int colCount, PrintWriter writer)
            throws SQLException, IOException {
        writer.println('[');
        long rows = 0;
        while (rs.next()) {
            if (rows > 0) writer.println(',');
            writer.print("  {");
            for (int i = 0; i < colCount; i++) {
                if (i > 0) writer.print(',');
                Object val = rs.getObject(i + 1);
                writer.print(jsonField(columns[i], val));
            }
            writer.print('}');
            rows++;
            if (rows % 1000 == 0) {
                writer.flush();
                if (writer.checkError()) {
                    throw new IOException("Client connection lost during export at row " + rows);
                }
            }
        }
        writer.println();
        writer.println(']');
        return rows;
    }

    private long writeSqlInsert(ResultSet rs, String[] columns, int[] types,
                                int colCount, String table, PrintWriter writer)
            throws SQLException, IOException {
        String safeTable = table != null ? table.replace("`", "``") : "export_table";
        StringBuilder colList = new StringBuilder();
        for (int i = 0; i < colCount; i++) {
            if (i > 0) colList.append(", ");
            colList.append('`').append(columns[i].replace("`", "``")).append('`');
        }
        String prefix = "INSERT INTO `" + safeTable + "` (" + colList + ") VALUES (";

        long rows = 0;
        while (rs.next()) {
            writer.print(prefix);
            for (int i = 0; i < colCount; i++) {
                if (i > 0) writer.print(',');
                writer.print(sqlValue(rs, i + 1, types[i]));
            }
            writer.println(");");
            rows++;
            if (rows % 500 == 0) {
                writer.flush();
                if (writer.checkError()) {
                    throw new IOException("Client connection lost during export at row " + rows);
                }
            }
        }
        return rows;
    }

    private void setResponseHeaders(HttpServletResponse response, String format, String sourceName) {
        String contentType = switch (format.toLowerCase()) {
            case "json" -> "application/json; charset=UTF-8";
            case "sql" -> "text/plain; charset=UTF-8";
            default -> "text/csv; charset=UTF-8";
        };
        String ext = switch (format.toLowerCase()) {
            case "json" -> "json";
            case "sql" -> "sql";
            default -> "csv";
        };
        String fileName = (sourceName != null ? sourceName : "export") + "." + ext;
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("X-Export-Format", ext);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    private String jsonField(String name, Object value) {
        if (value == null) {
            return "\"" + jsonEscape(name) + "\":null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return "\"" + jsonEscape(name) + "\":" + value;
        }
        return "\"" + jsonEscape(name) + "\":\"" + jsonEscape(String.valueOf(value)) + "\"";
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String sqlValue(ResultSet rs, int idx, int sqlType) throws SQLException {
        Object val = rs.getObject(idx);
        if (val == null) return "NULL";
        return switch (sqlType) {
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT,
                 Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT,
                 Types.REAL, Types.BIT, Types.BOOLEAN -> String.valueOf(val);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] bytes = rs.getBytes(idx);
                if (bytes == null) yield "NULL";
                yield "0x" + bytesToHex(bytes);
            }
            default -> {
                String s = rs.getString(idx);
                if (s == null) yield "NULL";
                yield "'" + s.replace("\\", "\\\\").replace("'", "''") + "'";
            }
        };
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void saveLog(Long connectionId, String database, String table,
                         String format, long rows) {
        try {
            ExportLog logEntry = ExportLog.builder()
                    .connectionId(connectionId)
                    .databaseName(database)
                    .tableName(table)
                    .format(format)
                    .rowCount(rows)
                    .fileName(table + "." + format.toLowerCase())
                    .status("success")
                    .createdAt(java.time.Instant.now())
                    .build();
            exportLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to save export log: {}", e.getMessage());
        }
    }
}
