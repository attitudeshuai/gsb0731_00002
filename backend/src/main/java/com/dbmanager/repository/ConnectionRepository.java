package com.dbmanager.repository;

import com.dbmanager.entity.ConnectionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionRepository extends JpaRepository<ConnectionConfig, Long> {
    List<ConnectionConfig> findAllByOrderByNameAsc();
    List<ConnectionConfig> findByGroupIdOrderByNameAsc(Long groupId);
}
