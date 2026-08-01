package com.dbmanager.repository;

import com.dbmanager.entity.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    List<SavedQuery> findByUserId(Long userId);

    List<SavedQuery> findByFolderId(Long folderId);

    List<SavedQuery> findByUserIdAndNameContaining(Long userId, String name);
}
