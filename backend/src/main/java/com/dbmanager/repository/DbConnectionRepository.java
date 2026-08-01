package com.dbmanager.repository;

import com.dbmanager.entity.DbConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DbConnectionRepository extends JpaRepository<DbConnection, Long> {

    List<DbConnection> findByUserId(Long userId);

    List<DbConnection> findByGroupId(Long groupId);

    List<DbConnection> findByUserIdAndNameContaining(Long userId, String name);
}
