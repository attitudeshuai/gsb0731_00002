package com.dbmanager.repository;

import com.dbmanager.entity.QueryFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryFolderRepository extends JpaRepository<QueryFolder, Long> {

    List<QueryFolder> findByUserIdOrderBySortOrderAsc(Long userId);

    List<QueryFolder> findByParentId(Long parentId);
}
