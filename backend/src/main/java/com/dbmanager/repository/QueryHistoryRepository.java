package com.dbmanager.repository;

import com.dbmanager.entity.QueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    List<QueryHistory> findByUserIdOrderByExecutedAtDesc(Long userId);

    List<QueryHistory> findByConnectionIdOrderByExecutedAtDesc(Long connectionId);

    @Transactional
    void deleteByUserId(Long userId);
}
