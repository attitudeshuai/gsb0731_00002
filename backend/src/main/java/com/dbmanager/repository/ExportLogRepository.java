package com.dbmanager.repository;

import com.dbmanager.entity.ExportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportLogRepository extends JpaRepository<ExportLog, Long> {

    List<ExportLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ExportLog> findByExecutionId(String executionId);
}
