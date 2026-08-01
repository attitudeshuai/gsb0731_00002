package com.dbtool.backend.service.export;

import com.dbtool.backend.service.ExportService;
import com.dbtool.backend.service.MetadataService;
import com.dbtool.backend.service.RunningQueryRegistry;
import com.dbtool.backend.web.ApiException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs exports asynchronously so the HTTP request returns immediately with a job id.
 * The client then polls progress, can cancel, and finally downloads the produced file.
 * <p>
 * The heavy streaming query runs on the isolated export connection pool (see
 * {@code TargetDataSourceManager}), and can be cancelled server-side via the
 * {@link RunningQueryRegistry} query token — which aborts the DB statement and frees
 * the export connection back to its pool.
 */
@Service
public class ExportJobManager {

    private static final Logger log = LoggerFactory.getLogger(ExportJobManager.class);

    private final ExportService exportService;
    private final MetadataService metadataService;
    private final RunningQueryRegistry runningQueryRegistry;

    private final Map<String, ExportJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "export-worker");
        t.setDaemon(true);
        return t;
    });

    public ExportJobManager(ExportService exportService,
                            MetadataService metadataService,
                            RunningQueryRegistry runningQueryRegistry) {
        this.exportService = exportService;
        this.metadataService = metadataService;
        this.runningQueryRegistry = runningQueryRegistry;
    }

    /** Starts a full-table export job. Returns the created job immediately. */
    public ExportJob startTableExport(Long connectionId, String database, String table, String format) {
        String fmt = normalize(format);
        Long total = safeEstimate(connectionId, database, table);
        ExportJob job = newJob(connectionId, fmt, table + "." + ext(fmt), total);
        submit(job, out -> exportService.exportTable(
                connectionId, database, table, fmt, out, job.getQueryToken(), job::setRowsWritten));
        return job;
    }

    /** Starts a query-result export job. Returns the created job immediately. */
    public ExportJob startQueryExport(Long connectionId, String sql, String format) {
        String fmt = normalize(format);
        ExportJob job = newJob(connectionId, fmt, "query_export." + ext(fmt), null);
        submit(job, out -> exportService.exportQuery(
                connectionId, sql, fmt, out, job.getQueryToken(), job::setRowsWritten));
        return job;
    }

    public ExportJob get(String jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Export job not found: " + jobId);
        }
        return job;
    }

    /** Cancels a running export: aborts the DB statement, marks the job cancelled. */
    public void cancel(String jobId) {
        ExportJob job = get(jobId);
        if (job.getStatus() == ExportJob.Status.RUNNING) {
            job.setStatus(ExportJob.Status.CANCELLED);
            // actually cancel the streaming statement in the DB; frees the export connection
            runningQueryRegistry.cancel(job.getQueryToken());
        }
    }

    private interface StreamTask {
        long run(java.io.Writer out) throws Exception;
    }

    private void submit(ExportJob job, StreamTask task) {
        executor.submit(() -> {
            Path file = job.getFilePath();
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                task.run(w);
                if (job.getStatus() == ExportJob.Status.CANCELLED) {
                    // cancelled mid-stream: discard the partial file
                    finish(job, ExportJob.Status.CANCELLED, null);
                } else {
                    finish(job, ExportJob.Status.COMPLETED, null);
                }
            } catch (Exception e) {
                if (job.getStatus() == ExportJob.Status.CANCELLED) {
                    finish(job, ExportJob.Status.CANCELLED, null);
                } else {
                    log.warn("Export job {} failed", job.getId(), e);
                    finish(job, ExportJob.Status.FAILED, e.getMessage());
                }
            }
        });
    }

    private void finish(ExportJob job, ExportJob.Status status, String error) {
        job.setStatus(status);
        job.setError(error);
        job.setFinishedAt(Instant.now());
        if (status != ExportJob.Status.COMPLETED) {
            // remove partial/failed file
            deleteQuietly(job.getFilePath());
        }
    }

    private ExportJob newJob(Long connectionId, String fmt, String filename, Long total) {
        String id = UUID.randomUUID().toString();
        String token = "export-" + id;
        ExportJob job = new ExportJob(id, connectionId, token, fmt, filename, total);
        try {
            job.setFilePath(Files.createTempFile("dbtool-export-" + id + "-", "." + ext(fmt)));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create export temp file");
        }
        jobs.put(id, job);
        return job;
    }

    private Long safeEstimate(Long connectionId, String database, String table) {
        try {
            return metadataService.estimateRows(connectionId, database, table);
        } catch (Exception e) {
            return null;   // estimate is best-effort; progress falls back to raw row count
        }
    }

    private String normalize(String format) {
        return format == null ? "csv" : format.toLowerCase();
    }

    private String ext(String fmt) {
        return switch (fmt) {
            case "json" -> "json";
            case "sql" -> "sql";
            default -> "csv";
        };
    }

    private void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        jobs.values().forEach(j -> deleteQuietly(j.getFilePath()));
        jobs.clear();
    }
}
