package com.dbtool.backend.controller;

import com.dbtool.backend.entity.ExportLog;
import com.dbtool.backend.repository.ExportLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MiscController {

    private final ExportLogRepository exportLogRepository;

    public MiscController(ExportLogRepository exportLogRepository) {
        this.exportLogRepository = exportLogRepository;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/export-logs")
    public Page<ExportLog> exportLogs(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return exportLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200)));
    }
}
