package com.example.dbmanager.repository;

import com.example.dbmanager.entity.QueryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    Page<QueryHistory> findByConnectionId(Long connectionId, Pageable pageable);

    long deleteByConnectionId(Long connectionId);
}
