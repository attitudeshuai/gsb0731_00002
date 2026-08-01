package com.dbmanager.repository;

import com.dbmanager.entity.QueryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {
    Page<QueryHistory> findAllByOrderByExecutedAtDesc(Pageable pageable);
    Page<QueryHistory> findByConnectionIdOrderByExecutedAtDesc(Long connectionId, Pageable pageable);
}
