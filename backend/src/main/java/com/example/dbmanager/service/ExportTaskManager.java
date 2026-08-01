package com.example.dbmanager.service;

import com.example.dbmanager.dto.CancelResponse;
import com.example.dbmanager.entity.ExportLog;
import com.example.dbmanager.exception.BadRequestException;
import com.example.dbmanager.exception.ConflictException;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.ExportLogRepository;
import com.example.dbmanager.service.TableDataService.TargetColumn;
import com.example.dbmanager.service.TableDataService.WhereClause;
import com.example.dbmanager.util.SqlUtils;
import com.example.dbmanager.util.ValueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 异步整表导出任务管理：任务在线程池中执行，流式读取（fetchSize=Integer.MIN_VALUE）
 * 逐行写临时文件，支持进度查询 / 取消（中断查询）/ 完成后下载。
 * 任务记录保存在内存 map 中；下载成功后删除临时文件并移除任务记录。
 */
@Component
public class ExportTaskManager {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskManager.class);
    private static final Set<String> EXPORT_FORMATS = Set.of("csv", "json", "sql");
    private static final int EXPORT_BATCH_SIZE = 100;
    /** 临时文件前缀，启动清理时按此前缀识别本应用残留文件 */
    private static final String TEMP_FILE_PREFIX = "dbmanager-export-";

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * 一个导出任务的运行时记录。
     */
    public static class ExportTask {

        private final String taskId;
        private final Long connectionId;
        private final String database;
        private final String table;
        private final String format;
        private final String filters;
        private final AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
        private final AtomicLong rowsExported = new AtomicLong();
        private volatile Long estimatedRows;
        private volatile Path filePath;
        private volatile String errorMessage;
        private final Instant createdAt = Instant.now();
        private volatile Instant finishedAt;
        private volatile boolean cancelRequested;
        private volatile Statement currentStatement;
        private volatile boolean downloadInProgress;

        ExportTask(String taskId, Long connectionId, String database, String table,
                   String format, String filters) {
            this.taskId = taskId;
            this.connectionId = connectionId;
            this.database = database;
            this.table = table;
            this.format = format;
            this.filters = filters;
        }

        public String getTaskId() {
            return taskId;
        }

        public Long getConnectionId() {
            return connectionId;
        }

        public String getDatabase() {
            return database;
        }

        public String getTable() {
            return table;
        }

        public String getFormat() {
            return format;
        }

        public String getFilters() {
            return filters;
        }

        public Status getStatus() {
            return status.get();
        }

        boolean compareAndSetStatus(Status expect, Status update) {
            return status.compareAndSet(expect, update);
        }

        void setStatus(Status update) {
            status.set(update);
        }

        public long getRowsExported() {
            return rowsExported.get();
        }

        void incrementRowsExported() {
            rowsExported.incrementAndGet();
        }

        public Long getEstimatedRows() {
            return estimatedRows;
        }

        void setEstimatedRows(Long estimatedRows) {
            this.estimatedRows = estimatedRows;
        }

        public Path getFilePath() {
            return filePath;
        }

        void setFilePath(Path filePath) {
            this.filePath = filePath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getFinishedAt() {
            return finishedAt;
        }

        void setFinishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
        }

        boolean isCancelRequested() {
            return cancelRequested;
        }

        void setCancelRequested(boolean cancelRequested) {
            this.cancelRequested = cancelRequested;
        }

        Statement getCurrentStatement() {
            return currentStatement;
        }

        void setCurrentStatement(Statement currentStatement) {
            this.currentStatement = currentStatement;
        }

        boolean isDownloadInProgress() {
            return downloadInProgress;
        }

        void setDownloadInProgress(boolean downloadInProgress) {
            this.downloadInProgress = downloadInProgress;
        }
    }

    /**
     * 任务被取消时抛出，用于中断导出流程。
     */
    private static class ExportCancelledException extends RuntimeException {
    }

    private final Map<String, ExportTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    /** 导出查询超时（秒），0 表示不限制；独立于连接级交互查询超时 */
    private final int exportTimeoutSeconds;
    /** 终态任务保留时间（分钟），到期自动清理记录与临时文件 */
    private final long retentionMinutes;

    private final TargetJdbcService targetJdbcService;
    private final TableDataService tableDataService;
    private final ExportLogRepository exportLogRepository;
    private final ObjectMapper objectMapper;

    public ExportTaskManager(TargetJdbcService targetJdbcService,
                             TableDataService tableDataService,
                             ExportLogRepository exportLogRepository,
                             ObjectMapper objectMapper,
                             @Value("${app.export.timeout-seconds:0}") int exportTimeoutSeconds,
                             @Value("${app.export.retention-minutes:30}") long retentionMinutes) {
        this.targetJdbcService = targetJdbcService;
        this.tableDataService = tableDataService;
        this.exportLogRepository = exportLogRepository;
        this.objectMapper = objectMapper;
        this.exportTimeoutSeconds = exportTimeoutSeconds;
        this.retentionMinutes = retentionMinutes;
    }

    /**
     * 启动时清理上次运行残留的导出临时文件（进程重启后内存任务记录已丢失）。
     */
    @PostConstruct
    public void cleanupOrphanTempFiles() {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (var stream = Files.list(tempDir)) {
            stream.filter(path -> path.getFileName().toString().startsWith(TEMP_FILE_PREFIX))
                  .forEach(ExportTaskManager::deleteQuietly);
        } catch (IOException e) {
            log.warn("清理残留导出临时文件失败: {}", e.getMessage());
        }
    }

    /**
     * 定期清理超过保留期的终态任务：移除内存记录并删除临时文件。
     */
    @Scheduled(fixedDelay = 60_000)
    public void evictExpiredTasks() {
        Instant deadline = Instant.now().minusSeconds(retentionMinutes * 60);
        tasks.values().removeIf(task -> {
            Status status = task.getStatus();
            Instant finishedAt = task.getFinishedAt();
            if (status == Status.PENDING || status == Status.RUNNING
                    || finishedAt == null || finishedAt.isAfter(deadline)
                    || task.isDownloadInProgress()) {
                return false;
            }
            deleteQuietly(task.getFilePath());
            log.info("清理过期导出任务 taskId={} status={}", task.getTaskId(), status);
            return true;
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 创建导出任务并提交线程池执行，返回 PENDING 状态的任务记录。
     */
    public ExportTask createTask(Long connectionId, String database, String table,
                                 String format, String filters) {
        String fmt = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!EXPORT_FORMATS.contains(fmt)) {
            throw new BadRequestException("format 只支持 csv / json / sql");
        }
        ExportTask task = new ExportTask(
                UUID.randomUUID().toString(), connectionId, database, table, fmt, filters);
        task.setEstimatedRows(estimateRows(connectionId, database, table));
        tasks.put(task.getTaskId(), task);
        executor.submit(() -> runTask(task));
        return task;
    }

    public ExportTask requireTask(String taskId) {
        ExportTask task = tasks.get(taskId);
        if (task == null) {
            throw new NotFoundException("导出任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 取消任务：PENDING 直接置 CANCELLED；RUNNING 置取消标志并中断当前查询。
     */
    public CancelResponse cancel(String taskId) {
        ExportTask task = requireTask(taskId);
        if (task.compareAndSetStatus(Status.PENDING, Status.CANCELLED)) {
            task.setCancelRequested(true);
            task.setFinishedAt(Instant.now());
            saveExportLog(task);
            return new CancelResponse(true, "任务已取消 " + taskId);
        }
        if (task.getStatus() == Status.RUNNING) {
            task.setCancelRequested(true);
            Statement statement = task.getCurrentStatement();
            if (statement != null) {
                try {
                    statement.cancel();
                } catch (SQLException e) {
                    log.debug("中断导出查询失败 taskId={}: {}", taskId, e.getMessage());
                }
            }
            return new CancelResponse(true, "任务取消中 " + taskId);
        }
        throw new BadRequestException("任务已结束，无法取消，当前状态: " + task.getStatus());
    }

    /**
     * 校验任务已完成并返回任务记录（供下载），否则 409；同时标记下载中，防止保留期清理误删文件。
     */
    public ExportTask prepareDownload(String taskId) {
        ExportTask task = requireTask(taskId);
        if (task.getStatus() != Status.COMPLETED || task.getFilePath() == null) {
            throw new ConflictException("任务未完成，无法下载，当前状态: " + task.getStatus());
        }
        task.setDownloadInProgress(true);
        return task;
    }

    /**
     * 下载成功后清理：删除临时文件并移除任务记录。
     */
    public void cleanupAfterDownload(String taskId) {
        ExportTask task = tasks.remove(taskId);
        if (task != null) {
            Path filePath = task.getFilePath();
            task.setFilePath(null);
            deleteQuietly(filePath);
        }
    }

    // ---------- 任务执行 ----------

    private void runTask(ExportTask task) {
        if (!task.compareAndSetStatus(Status.PENDING, Status.RUNNING)) {
            return; // 排队期间已被取消
        }
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(
                    TEMP_FILE_PREFIX + task.getTable() + "-", "." + task.getFormat());
            task.setFilePath(tempFile);
            doExport(task, tempFile);
            task.setStatus(Status.COMPLETED);
            log.info("导出任务完成 taskId={} rows={}", task.getTaskId(), task.getRowsExported());
        } catch (Exception e) {
            if (task.isCancelRequested() || e instanceof ExportCancelledException) {
                task.setStatus(Status.CANCELLED);
                log.info("导出任务已取消 taskId={}", task.getTaskId());
            } else {
                task.setStatus(Status.FAILED);
                task.setErrorMessage(describeFailure(e));
                log.warn("导出任务失败 taskId={}: {}", task.getTaskId(), e.getMessage());
            }
            task.setFilePath(null);
            deleteQuietly(tempFile);
        } finally {
            task.setCurrentStatement(null);
            task.setFinishedAt(Instant.now());
            saveExportLog(task); // 终态统一记录（COMPLETED/FAILED/CANCELLED）
        }
    }

    /**
     * 流式读取整表数据并逐行写入临时文件（格式与原同步导出一致）。
     */
    private void doExport(ExportTask task, Path tempFile) throws Exception {
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(task.getConnectionId());
        List<TargetColumn> columns =
                tableDataService.requireColumns(jt, task.getDatabase(), task.getTable());
        WhereClause where = tableDataService.buildWhere(
                tableDataService.parseFilters(task.getFilters()), TableDataService.columnNames(columns));
        String sql = "SELECT * FROM " + SqlUtils.tableRef(task.getDatabase(), task.getTable()) + where.sql();

        HikariDataSource dataSource = targetJdbcService.getDataSource(task.getConnectionId());
        try (java.sql.Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            ps.setFetchSize(Integer.MIN_VALUE); // MySQL 流式读取，避免整表载入内存
            // 导出是长任务，使用独立的导出超时（0 = 不限制），不套用连接级交互查询超时
            ps.setQueryTimeout(exportTimeoutSeconds);
            List<Object> params = where.params();
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            task.setCurrentStatement(ps);
            try (ResultSet rs = ps.executeQuery()) {
                switch (task.getFormat()) {
                    case "csv" -> writeCsv(task, rs, columns, writer);
                    case "json" -> writeJson(task, rs, columns, writer);
                    case "sql" -> writeSql(task, rs, columns, writer);
                    default -> throw new BadRequestException("format 只支持 csv / json / sql");
                }
            }
        }
    }

    private void writeCsv(ExportTask task, ResultSet rs, List<TargetColumn> columns,
                          BufferedWriter writer) throws IOException, SQLException {
        writer.write(columns.stream().map(col -> csvCell(col.name()))
                .collect(Collectors.joining(",")));
        writer.write('\n');
        while (rs.next()) {
            checkCancelled(task);
            StringBuilder line = new StringBuilder();
            for (int i = 1; i <= columns.size(); i++) {
                if (i > 1) {
                    line.append(',');
                }
                line.append(csvCell(ValueMapper.stringify(rs.getObject(i))));
            }
            writer.write(line.toString());
            writer.write('\n');
            task.incrementRowsExported();
        }
    }

    private void writeJson(ExportTask task, ResultSet rs, List<TargetColumn> columns,
                           BufferedWriter writer) throws IOException, SQLException {
        writer.write("[\n");
        boolean first = true;
        while (rs.next()) {
            checkCancelled(task);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns.size(); i++) {
                row.put(columns.get(i - 1).name(), ValueMapper.normalize(rs.getObject(i)));
            }
            if (!first) {
                writer.write(",\n");
            }
            first = false;
            writer.write(objectMapper.writeValueAsString(row));
            task.incrementRowsExported();
        }
        writer.write("\n]");
    }

    private void writeSql(ExportTask task, ResultSet rs, List<TargetColumn> columns,
                          BufferedWriter writer) throws IOException, SQLException {
        String columnList = columns.stream()
                .map(col -> SqlUtils.quoteIdent(col.name()))
                .collect(Collectors.joining(", "));
        List<String> batch = new ArrayList<>();
        while (rs.next()) {
            checkCancelled(task);
            StringBuilder rowLiteral = new StringBuilder("(");
            for (int i = 1; i <= columns.size(); i++) {
                if (i > 1) {
                    rowLiteral.append(", ");
                }
                rowLiteral.append(ValueMapper.toSqlLiteral(rs.getObject(i)));
            }
            rowLiteral.append(')');
            batch.add(rowLiteral.toString());
            task.incrementRowsExported();
            if (batch.size() >= EXPORT_BATCH_SIZE) {
                flushInsertBatch(writer, task.getTable(), columnList, batch);
            }
        }
        if (!batch.isEmpty()) {
            flushInsertBatch(writer, task.getTable(), columnList, batch);
        }
    }

    private void flushInsertBatch(BufferedWriter writer, String table,
                                  String columnList, List<String> batch) throws IOException {
        writer.write("INSERT INTO " + SqlUtils.quoteIdent(table) + " (" + columnList + ") VALUES\n");
        writer.write(String.join(",\n", batch));
        writer.write(";\n\n");
        batch.clear();
    }

    /**
     * 每行检查取消标志：被取消时中断当前查询并终止任务。
     */
    private void checkCancelled(ExportTask task) {
        if (task.isCancelRequested()) {
            Statement statement = task.getCurrentStatement();
            if (statement != null) {
                try {
                    statement.cancel();
                } catch (SQLException e) {
                    log.debug("中断导出查询失败 taskId={}: {}", task.getTaskId(), e.getMessage());
                }
            }
            throw new ExportCancelledException();
        }
    }

    private Long estimateRows(Long connectionId, String database, String table) {
        JdbcTemplate jt = targetJdbcService.getJdbcTemplate(connectionId);
        List<Long> result = jt.queryForList(
                "SELECT TABLE_ROWS FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                Long.class, database, table);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * 记录导出日志：COMPLETED/FAILED/CANCELLED 三种终态全部落库，便于排查。
     */
    private void saveExportLog(ExportTask task) {
        try {
            ExportLog exportLog = new ExportLog();
            exportLog.setConnectionId(task.getConnectionId());
            exportLog.setDatabaseName(task.getDatabase());
            exportLog.setTableName(task.getTable());
            exportLog.setFormat(task.getFormat());
            exportLog.setStatus(task.getStatus().name());
            exportLog.setErrorMessage(task.getErrorMessage());
            exportLog.setRowCount((int) Math.min(task.getRowsExported(), Integer.MAX_VALUE));
            exportLogRepository.save(exportLog);
        } catch (Exception e) {
            log.warn("写入导出日志失败: {}", e.getMessage());
        }
    }

    /**
     * 失败原因：语句超时单独说明，其余取根因消息。
     */
    private String describeFailure(Exception e) {
        if (QueryService.isTimeout(e)) {
            return exportTimeoutSeconds > 0
                    ? "Export timeout after " + exportTimeoutSeconds + "s"
                    : "Export query timeout";
        }
        return rootMessage(e);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除导出临时文件失败 {}: {}", path, e.getMessage());
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null ? message : throwable.toString();
    }
}
