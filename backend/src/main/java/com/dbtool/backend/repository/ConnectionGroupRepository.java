package com.dbtool.backend.repository;

import com.dbtool.backend.entity.ConnectionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionGroupRepository extends JpaRepository<ConnectionGroup, Long> {
}
