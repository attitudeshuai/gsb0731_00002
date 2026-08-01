package com.dbtool.backend.repository;

import com.dbtool.backend.entity.QueryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {
    Page<QueryHistory> findByConnectionIdOrderByExecutedAtDesc(Long connectionId, Pageable pageable);
    Page<QueryHistory> findAllByOrderByExecutedAtDesc(Pageable pageable);
}
