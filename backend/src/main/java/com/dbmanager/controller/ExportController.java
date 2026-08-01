package com.dbmanager.controller;

import com.dbmanager.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/{connectionId}/databases/{database}/tables/{table}")
    public void exportTable(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table,
            @RequestParam(defaultValue = "csv") String format,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) {
        exportService.exportTable(connectionId, database, table, format, response, requestId);
    }

    @PostMapping("/query")
    public void exportQuery(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) {
        Long connectionId = Long.valueOf(body.get("connectionId").toString());
        String database = body.get("databaseName") != null ? body.get("databaseName").toString() : null;
        String sql = body.get("sql") != null ? body.get("sql").toString() : "";
        String format = body.get("format") != null ? body.get("format").toString() : "csv";
        String sourceName = body.get("fileName") != null ? body.get("fileName").toString() : "query_result";
        exportService.exportQuery(connectionId, database, sql, format, response, sourceName, requestId);
    }
}
