package com.dbmanager.controller;

import com.dbmanager.dto.ApiResponse;
import com.dbmanager.dto.ExportRequest;
import com.dbmanager.dto.ExportResultResponse;
import com.dbmanager.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    public void export(
            @RequestParam Long connectionId,
            @RequestParam(required = false) String executionId,
            @Valid @RequestBody ExportRequest request,
            HttpServletResponse response) throws IOException {

        String execId = (executionId == null || executionId.isBlank())
                ? UUID.randomUUID().toString() : executionId;

        ExportService.OnReadyCallback onReady = (contentType, filename) -> {
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setStatus(HttpServletResponse.SC_OK);
        };

        try {
            exportService.export(connectionId, request, execId,
                    response.getOutputStream(), onReady);
        } catch (IOException e) {
            if (!response.isCommitted()) {
                throw e;
            }
        } catch (RuntimeException e) {
            if (!response.isCommitted()) {
                throw e;
            }
        }
    }

    @GetMapping("/result/{executionId}")
    public ApiResponse<ExportResultResponse> getResult(@PathVariable String executionId) {
        return ApiResponse.ok(exportService.getResult(executionId));
    }
}
