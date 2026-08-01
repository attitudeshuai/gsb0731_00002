package com.dbmanager.repository;

import com.dbmanager.entity.ExportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportLogRepository extends JpaRepository<ExportLog, Long> {
}
