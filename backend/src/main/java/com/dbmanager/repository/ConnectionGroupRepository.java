package com.dbmanager.repository;

import com.dbmanager.entity.ConnectionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionGroupRepository extends JpaRepository<ConnectionGroup, Long> {
    List<ConnectionGroup> findAllByOrderBySortOrderAscNameAsc();
}
