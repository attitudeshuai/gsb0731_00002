package com.example.dbmanager.repository;

import com.example.dbmanager.entity.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    List<SavedQuery> findByFolderIdOrderByIdAsc(Long folderId);

    List<SavedQuery> findAllByOrderByIdAsc();
}
