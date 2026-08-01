package com.dbtool.backend.repository;

import com.dbtool.backend.entity.ExportLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportLogRepository extends JpaRepository<ExportLog, Long> {
    Page<ExportLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
