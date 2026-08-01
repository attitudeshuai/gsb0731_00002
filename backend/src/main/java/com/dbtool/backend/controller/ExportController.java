package com.dbtool.backend.controller;

import com.dbtool.backend.dto.ExportProgress;
import com.dbtool.backend.service.export.ExportJob;
import com.dbtool.backend.service.export.ExportJobManager;
import com.dbtool.backend.web.ApiException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Asynchronous export API: start a job, poll its progress, cancel it, then download
 * the produced file. Long full-table dumps run on an isolated connection pool so they
 * don't slow down interactive queries.
 */
@RestController
@RequestMapping("/api/connections/{connectionId}/export")
public class ExportController {

    private final ExportJobManager exportJobManager;

    public ExportController(ExportJobManager exportJobManager) {
        this.exportJobManager = exportJobManager;
    }

    public static class QueryExportRequest {
        @NotBlank
        public String sql;
        public String format = "csv";
    }

    public static class TableExportRequest {
        @NotBlank
        public String database;
        @NotBlank
        public String table;
        public String format = "csv";
    }

    /** Start a query-result export job; returns immediately with job progress. */
    @PostMapping("/query")
    public ExportProgress startQuery(@PathVariable Long connectionId,
                                     @RequestBody QueryExportRequest req) {
        ExportJob job = exportJobManager.startQueryExport(connectionId, req.sql, req.format);
        return ExportProgress.from(job);
    }

    /** Start a full-table export job; returns immediately with job progress. */
    @PostMapping("/table")
    public ExportProgress startTable(@PathVariable Long connectionId,
                                     @RequestBody TableExportRequest req) {
        ExportJob job = exportJobManager.startTableExport(connectionId, req.database, req.table, req.format);
        return ExportProgress.from(job);
    }

    /** Poll the progress of a running/finished export job. */
    @GetMapping("/jobs/{jobId}")
    public ExportProgress status(@PathVariable Long connectionId, @PathVariable String jobId) {
        return ExportProgress.from(exportJobManager.get(jobId));
    }

    /** Cancel a running export; the DB statement is actually aborted. */
    @PostMapping("/jobs/{jobId}/cancel")
    public Map<String, Object> cancel(@PathVariable Long connectionId, @PathVariable String jobId) {
        exportJobManager.cancel(jobId);
        return Map.of("cancelled", true);
    }

    /** Download the file produced by a completed export job. */
    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long connectionId, @PathVariable String jobId) {
        ExportJob job = exportJobManager.get(jobId);
        if (job.getStatus() != ExportJob.Status.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Export is not ready for download (status=" + job.getStatus() + ")");
        }
        Resource resource = new FileSystemResource(job.getFilePath());
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Export file no longer available");
        }
        MediaType type = switch (job.getFormat()) {
            case "json" -> MediaType.APPLICATION_JSON;
            default -> MediaType.TEXT_PLAIN;
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + job.getFilename() + "\"")
                .contentType(type)
                .body(resource);
    }
}
