package com.dbtool.backend.repository;

import com.dbtool.backend.entity.Connection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {
    List<Connection> findByGroupId(Long groupId);
}
