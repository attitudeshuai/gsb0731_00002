package com.example.dbmanager.controller;

import com.example.dbmanager.entity.ExportLog;
import com.example.dbmanager.service.ExportLogService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出记录。
 */
@RestController
@RequestMapping("/api/export-logs")
public class ExportLogController {

    private final ExportLogService exportLogService;

    public ExportLogController(ExportLogService exportLogService) {
        this.exportLogService = exportLogService;
    }

    @GetMapping
    public Page<ExportLog> page(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return exportLogService.page(page, size);
    }
}
