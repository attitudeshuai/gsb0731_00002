package com.example.dbmanager.repository;

import com.example.dbmanager.entity.DbConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionRepository extends JpaRepository<DbConnection, Long> {

    List<DbConnection> findAllByOrderByIdAsc();
}
