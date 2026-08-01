package com.dbtool.backend.repository;

import com.dbtool.backend.entity.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {
    List<SavedQuery> findByFolderId(Long folderId);
}
