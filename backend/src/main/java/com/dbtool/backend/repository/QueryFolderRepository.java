package com.dbtool.backend.repository;

import com.dbtool.backend.entity.QueryFolder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryFolderRepository extends JpaRepository<QueryFolder, Long> {
}
