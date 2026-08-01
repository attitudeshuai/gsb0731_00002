package com.example.dbmanager.controller;

import com.example.dbmanager.dto.CancelResponse;
import com.example.dbmanager.dto.ExportTaskResponse;
import com.example.dbmanager.service.ExportTaskManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 异步导出任务：创建 / 查询进度 / 取消 / 下载。
 */
@RestController
public class ExportTaskController {

    private final ExportTaskManager exportTaskManager;

    public ExportTaskController(ExportTaskManager exportTaskManager) {
        this.exportTaskManager = exportTaskManager;
    }

    @PostMapping("/api/connections/{connId}/databases/{db}/tables/{table}/export-tasks")
    public ResponseEntity<ExportTaskResponse> create(@PathVariable Long connId,
                                                     @PathVariable String db,
                                                     @PathVariable String table,
                                                     @RequestParam String format,
                                                     @RequestParam(required = false) String filters) {
        ExportTaskManager.ExportTask task =
                exportTaskManager.createTask(connId, db, table, format, filters);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ExportTaskResponse.of(task));
    }

    @GetMapping("/api/export-tasks/{taskId}")
    public ExportTaskResponse get(@PathVariable String taskId) {
        return ExportTaskResponse.of(exportTaskManager.requireTask(taskId));
    }

    @PostMapping("/api/export-tasks/{taskId}/cancel")
    public CancelResponse cancel(@PathVariable String taskId) {
        return exportTaskManager.cancel(taskId);
    }

    /**
     * 流式下载：分块写响应，不把整个文件读入内存；传输结束（无论成败）后清理任务与临时文件。
     */
    @GetMapping("/api/export-tasks/{taskId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String taskId) throws IOException {
        ExportTaskManager.ExportTask task = exportTaskManager.prepareDownload(taskId);
        long contentLength = Files.size(task.getFilePath());
        String filename = URLEncoder.encode(task.getTable() + "." + task.getFormat(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        StreamingResponseBody body = outputStream -> {
            try {
                Files.copy(task.getFilePath(), outputStream);
            } finally {
                exportTaskManager.cleanupAfterDownload(taskId);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(body);
    }
}
