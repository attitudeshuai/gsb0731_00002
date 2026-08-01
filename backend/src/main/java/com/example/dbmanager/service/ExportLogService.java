package com.example.dbmanager.service;

import com.example.dbmanager.entity.ExportLog;
import com.example.dbmanager.repository.ExportLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ExportLogService {

    private final ExportLogRepository exportLogRepository;

    public ExportLogService(ExportLogRepository exportLogRepository) {
        this.exportLogRepository = exportLogRepository;
    }

    public Page<ExportLog> page(int page, int size) {
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return exportLogRepository.findAll(pageable);
    }
}
