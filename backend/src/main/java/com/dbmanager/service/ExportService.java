package com.dbmanager.service;

import com.dbmanager.dto.ExportRequest;
import com.dbmanager.entity.ExportLog;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.exception.ExportCancelledException;
import com.dbmanager.repository.ExportLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ExportService {

    private static final String TIMEOUT_MESSAGE = "Query timeout (%d s), aborted.";

    private final DynamicDataSourceService dynamicDataSourceService;
    private final ExportLogRepository exportLogRepository;
    private final ActiveStatementRegistry statementRegistry;
    private final ObjectMapper objectMapper;

    @Value("${app.export.timeout-seconds:600}")
    private int exportTimeoutSeconds;

    public ExportService(DynamicDataSourceService dynamicDataSourceService,
                         ExportLogRepository exportLogRepository,
                         ActiveStatementRegistry statementRegistry) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.exportLogRepository = exportLogRepository;
        this.statementRegistry = statementRegistry;
        this.objectMapper = new ObjectMapper();
    }

    @FunctionalInterface
    public interface OnReadyCallback {
        void onReady(String contentType, String filename) throws IOException;
    }

    public record ExportResult(int rowCount, long fileSize, boolean cancelled, String fileName) {}

    public ExportResult export(Long connectionId, ExportRequest request,
                                String executionId, OutputStream outputStream,
                                OnReadyCallback onReady) throws IOException {
        String sql = resolveSql(request);
        String rawFormat = request.getFormat().toUpperCase();
        if ("SQL".equals(rawFormat)) {
            rawFormat = "SQL_INSERT";
        }
        final String format = rawFormat;
        final String fileName = generateFileName(format);
        final String contentType = resolveContentType(format);
        final String timeoutMessage = String.format(TIMEOUT_MESSAGE, exportTimeoutSeconds);

        JdbcTemplate jdbcTemplate;
        if (request.getDatabase() != null && !request.getDatabase().isBlank()) {
            jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId, request.getDatabase());
        } else {
            jdbcTemplate = dynamicDataSourceService.createJdbcTemplate(connectionId);
        }

        CountingOutputStream countingOut = new CountingOutputStream(outputStream);

        try {
            int rowCount = jdbcTemplate.execute((ConnectionCallback<Integer>) con -> {
                try (Statement stmt = con.createStatement()) {
                    stmt.setQueryTimeout(exportTimeoutSeconds);
                    stmt.setFetchSize(Integer.MIN_VALUE);

                    statementRegistry.register(executionId, stmt);
                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();

                        // Read the first row BEFORE committing the response.
                        // Slow-query timeouts, SQL errors, and cancellations surface here
                        // while the response is still uncommitted, so a proper JSON
                        // error can be returned.
                        boolean hasData = rs.next();
                        checkCancelled(executionId);

                        Object[] firstRow = null;
                        if (hasData) {
                            firstRow = new Object[colCount];
                            for (int i = 1; i <= colCount; i++) {
                                firstRow[i - 1] = rs.getObject(i);
                            }
                        }

                        // Response commits here (headers sent). After this point we can
                        // no longer send a normal JSON error response.
                        onReady.onReady(contentType, fileName);

                        try (Writer writer = new OutputStreamWriter(countingOut, StandardCharsets.UTF_8)) {
                            int count = switch (format) {
                                case "CSV" -> writeCsv(rs, meta, writer, executionId, firstRow, hasData, colCount);
                                case "JSON" -> writeJson(rs, meta, writer, executionId, firstRow, hasData, colCount);
                                case "SQL_INSERT" -> writeSqlInserts(rs, meta, writer, executionId,
                                        firstRow, hasData, colCount, request.getTableName());
                                default -> throw new BusinessException("Unsupported export format: " + format);
                            };
                            writer.flush();
                            return count;
                        }
                    } finally {
                        statementRegistry.unregister(executionId, stmt);
                    }
                } catch (SQLException e) {
                    // Timeout must be checked BEFORE cancellation: MySQL kills the
                    // statement on timeout and reports "interrupted", which would
                    // otherwise be mistaken for a user cancellation.
                    if (isTimeoutException(e)) {
                        statementRegistry.markTimedOut(executionId);
                        throw new BusinessException("EXPORT_TIMEOUT", timeoutMessage, e);
                    }
                    if (statementRegistry.isCancelled(executionId)) {
                        throw new ExportCancelledException();
                    }
                    throw new BusinessException("EXPORT_ERROR", "Export query failed: " + e.getMessage(), e);
                } catch (IOException e) {
                    if (isTimeoutException(e)) {
                        statementRegistry.markTimedOut(executionId);
                        throw new BusinessException("EXPORT_TIMEOUT", timeoutMessage, e);
                    }
                    if (statementRegistry.isCancelled(executionId)) {
                        throw new ExportCancelledException();
                    }
                    throw new UncheckedIOException(e);
                }
            });

            long fileSize = countingOut.getCount();
            saveExportLog(connectionId, request, sql, executionId, fileName, fileSize, rowCount, "SUCCESS", null);
            return new ExportResult(rowCount, fileSize, false, fileName);

        } catch (ExportCancelledException e) {
            long fileSize = countingOut.getCount();
            saveExportLog(connectionId, request, sql, executionId, fileName, fileSize, 0, "CANCELLED", "Query was cancelled by user");
            return new ExportResult(0, fileSize, true, fileName);
        } catch (BusinessException e) {
            String errorMsg = "EXPORT_TIMEOUT".equals(e.getCode()) ? timeoutMessage : e.getMessage();
            saveExportLog(connectionId, request, sql, executionId, fileName, countingOut.getCount(), 0, "FAILED", errorMsg);
            throw e;
        } catch (UncheckedIOException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String errorMsg = isTimeoutException(cause) ? timeoutMessage
                    : (cause.getMessage() != null ? cause.getMessage() : e.getMessage());
            saveExportLog(connectionId, request, sql, executionId, fileName, countingOut.getCount(), 0, "FAILED", errorMsg);
            if (cause instanceof IOException io) {
                throw io;
            }
            throw e;
        } catch (Exception e) {
            String errorMsg = isTimeoutException(e) ? timeoutMessage : e.getMessage();
            saveExportLog(connectionId, request, sql, executionId, fileName, countingOut.getCount(), 0, "FAILED", errorMsg);
            throw new BusinessException("EXPORT_ERROR", "Export failed: " + e.getMessage(), e);
        }
    }

    public com.dbmanager.dto.ExportResultResponse getResult(String executionId) {
        return exportLogRepository.findByExecutionId(executionId)
                .map(log -> new com.dbmanager.dto.ExportResultResponse(
                        log.getRowCount(), log.getFileSize(), log.getStatus(), log.getFileName(), log.getErrorMessage()))
                .orElse(null);
    }

    private String resolveSql(ExportRequest request) {
        String sql = request.getSql();
        if (sql != null && !sql.isBlank()) {
            return sql.trim();
        }
        if (request.getTableName() == null || request.getTableName().isBlank()) {
            throw new BusinessException("Either sql or tableName must be provided");
        }
        StringBuilder sb = new StringBuilder("SELECT * FROM ");
        if (request.getDatabase() != null && !request.getDatabase().isBlank()) {
            sb.append('`').append(escapeIdentifier(request.getDatabase())).append("`.`");
        } else {
            sb.append('`');
        }
        sb.append(escapeIdentifier(request.getTableName())).append('`');
        return sb.toString();
    }

    private int writeCsv(ResultSet rs, ResultSetMetaData meta, Writer writer,
                          String executionId, Object[] firstRow, boolean hasData, int colCount)
            throws SQLException, IOException {
        writer.write('\uFEFF');
        for (int i = 1; i <= colCount; i++) {
            if (i > 1) {
                writer.write(',');
            }
            writer.write(csvEscape(meta.getColumnLabel(i)));
        }
        writer.write('\n');

        int rowCount = 0;
        if (hasData) {
            writeCsvRow(writer, firstRow, colCount);
            rowCount++;
        }
        while (rs.next()) {
            checkCancelled(executionId);
            Object[] row = new Object[colCount];
            for (int i = 1; i <= colCount; i++) {
                row[i - 1] = rs.getObject(i);
            }
            writeCsvRow(writer, row, colCount);
            rowCount++;
            if (rowCount % 100 == 0) {
                writer.flush();
            }
        }
        writer.flush();
        return rowCount;
    }

    private void writeCsvRow(Writer writer, Object[] row, int colCount) throws IOException {
        for (int i = 0; i < colCount; i++) {
            if (i > 0) {
                writer.write(',');
            }
            Object val = row[i];
            writer.write(csvEscape(val != null ? val.toString() : ""));
        }
        writer.write('\n');
    }

    private int writeJson(ResultSet rs, ResultSetMetaData meta, Writer writer,
                           String executionId, Object[] firstRow, boolean hasData, int colCount)
            throws SQLException, IOException {
        writer.write('[');
        boolean firstRowWritten = false;
        int rowCount = 0;

        if (hasData) {
            writeJsonRow(writer, meta, firstRow, colCount);
            firstRowWritten = true;
            rowCount++;
        }
        while (rs.next()) {
            checkCancelled(executionId);
            if (firstRowWritten) {
                writer.write(',');
            }
            firstRowWritten = false;
            Object[] row = new Object[colCount];
            for (int i = 1; i <= colCount; i++) {
                row[i - 1] = rs.getObject(i);
            }
            writeJsonRow(writer, meta, row, colCount);
            firstRowWritten = true;
            rowCount++;
            if (rowCount % 100 == 0) {
                writer.flush();
            }
        }
        writer.write(']');
        writer.flush();
        return rowCount;
    }

    private void writeJsonRow(Writer writer, ResultSetMetaData meta, Object[] row, int colCount) throws IOException, SQLException {
        Map<String, Object> obj = new LinkedHashMap<>();
        for (int i = 0; i < colCount; i++) {
            obj.put(meta.getColumnLabel(i + 1), row[i]);
        }
        writer.write(objectMapper.writeValueAsString(obj));
    }

    private int writeSqlInserts(ResultSet rs, ResultSetMetaData meta, Writer writer,
                                 String executionId, Object[] firstRow, boolean hasData,
                                 int colCount, String tableName)
            throws SQLException, IOException {
        String targetTable = (tableName != null && !tableName.isBlank()) ? tableName : "export_table";

        StringBuilder colList = new StringBuilder();
        for (int i = 1; i <= colCount; i++) {
            if (i > 1) {
                colList.append(", ");
            }
            colList.append('`').append(escapeIdentifier(meta.getColumnLabel(i))).append('`');
        }

        int rowCount = 0;
        if (hasData) {
            writeSqlInsert(writer, targetTable, colList, firstRow, colCount);
            rowCount++;
        }
        while (rs.next()) {
            checkCancelled(executionId);
            Object[] row = new Object[colCount];
            for (int i = 1; i <= colCount; i++) {
                row[i - 1] = rs.getObject(i);
            }
            writeSqlInsert(writer, targetTable, colList, row, colCount);
            rowCount++;
            if (rowCount % 100 == 0) {
                writer.flush();
            }
        }
        writer.flush();
        return rowCount;
    }

    private void writeSqlInsert(Writer writer, String table, StringBuilder colList,
                                 Object[] row, int colCount) throws IOException {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < colCount; i++) {
            if (i > 0) {
                values.append(", ");
            }
            values.append(formatSqlValue(row[i]));
        }
        writer.write("INSERT INTO `");
        writer.write(escapeIdentifier(table));
        writer.write("` (");
        writer.write(colList.toString());
        writer.write(") VALUES (");
        writer.write(values.toString());
        writer.write(");\n");
    }

    private void checkCancelled(String executionId) {
        if (statementRegistry.isCancelled(executionId) || Thread.currentThread().isInterrupted()) {
            throw new ExportCancelledException();
        }
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof byte[]) {
            return "0x" + bytesToHex((byte[]) value);
        }
        String str = value.toString();
        return "'" + str.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.replace("`", "``");
    }

    private String resolveContentType(String format) {
        return switch (format) {
            case "CSV" -> "text/csv; charset=UTF-8";
            case "JSON" -> "application/json; charset=UTF-8";
            case "SQL_INSERT" -> "application/sql; charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    /**
     * Walks the full cause chain to detect timeouts. This covers:
     * - SQLTimeoutException from setQueryTimeout
     * - "Query execution was interrupted, max_execution_time exceeded" (MySQL kills statement)
     * - "Communications link failure" with cause "Read timed out" (socket timeout)
     * Timeout is checked BEFORE cancellation because MySQL timeout message contains
     * "interrupted", which must not be mistaken for a user cancellation.
     */
    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current instanceof SQLTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout")
                        || lower.contains("timed out")
                        || lower.contains("read timed out")
                        || lower.contains("max_execution_time")
                        || lower.contains("query execution was interrupted")) {
                    return true;
                }
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private void saveExportLog(Long connectionId, ExportRequest request,
                                String sql, String executionId, String fileName,
                                long fileSize, int rowCount, String status, String errorMessage) {
        try {
            ExportLog log = new ExportLog();
            log.setConnectionId(connectionId);
            log.setTableName(request.getTableName());
            log.setQuerySql(sql);
            log.setExecutionId(executionId);
            log.setExportFormat(request.getFormat());
            log.setFileName(fileName);
            log.setFileSize(fileSize);
            log.setRowCount(rowCount);
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            log.setCompletedAt(Instant.now());
            exportLogRepository.save(log);
        } catch (Exception e) {
            // silently ignore log errors
        }
    }

    private String generateFileName(String format) {
        String ext = switch (format.toUpperCase()) {
            case "CSV" -> "csv";
            case "JSON" -> "json";
            case "SQL_INSERT" -> "sql";
            default -> "txt";
        };
        return "export-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-") + "." + ext;
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        long getCount() {
            return count;
        }
    }
}
