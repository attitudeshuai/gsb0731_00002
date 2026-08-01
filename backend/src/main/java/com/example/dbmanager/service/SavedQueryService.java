package com.example.dbmanager.service;

import com.example.dbmanager.dto.QueryFolderRequest;
import com.example.dbmanager.dto.QueryFolderResponse;
import com.example.dbmanager.dto.SavedQueryRequest;
import com.example.dbmanager.dto.SavedQueryResponse;
import com.example.dbmanager.entity.QueryFolder;
import com.example.dbmanager.entity.SavedQuery;
import com.example.dbmanager.exception.BadRequestException;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.QueryFolderRepository;
import com.example.dbmanager.repository.SavedQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏查询：文件夹 + 收藏 SQL。
 */
@Service
public class SavedQueryService {

    private final QueryFolderRepository folderRepository;
    private final SavedQueryRepository savedQueryRepository;

    public SavedQueryService(QueryFolderRepository folderRepository,
                             SavedQueryRepository savedQueryRepository) {
        this.folderRepository = folderRepository;
        this.savedQueryRepository = savedQueryRepository;
    }

    // ---------- 文件夹 ----------

    public List<QueryFolderResponse> listFolders() {
        return folderRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toFolderResponse)
                .toList();
    }

    @Transactional
    public QueryFolderResponse createFolder(QueryFolderRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("文件夹名称不能为空");
        }
        QueryFolder folder = new QueryFolder();
        folder.setName(request.name().trim());
        folder.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return toFolderResponse(folderRepository.save(folder));
    }

    @Transactional
    public QueryFolderResponse updateFolder(Long id, QueryFolderRequest request) {
        QueryFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("文件夹不存在: " + id));
        if (request.name() != null && !request.name().isBlank()) {
            folder.setName(request.name().trim());
        }
        if (request.sortOrder() != null) {
            folder.setSortOrder(request.sortOrder());
        }
        return toFolderResponse(folderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(Long id) {
        if (!folderRepository.existsById(id)) {
            throw new NotFoundException("文件夹不存在: " + id);
        }
        // saved_queries.folder_id 外键 ON DELETE SET NULL，由数据库自动解绑
        folderRepository.deleteById(id);
    }

    // ---------- 收藏查询 ----------

    public List<SavedQueryResponse> listQueries(Long folderId) {
        List<SavedQuery> queries = folderId == null
                ? savedQueryRepository.findAllByOrderByIdAsc()
                : savedQueryRepository.findByFolderIdOrderByIdAsc(folderId);
        return queries.stream().map(this::toQueryResponse).toList();
    }

    @Transactional
    public SavedQueryResponse createQuery(SavedQueryRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("查询名称不能为空");
        }
        if (request.sqlText() == null || request.sqlText().isBlank()) {
            throw new BadRequestException("SQL 内容不能为空");
        }
        SavedQuery query = new SavedQuery();
        query.setName(request.name().trim());
        query.setSqlText(request.sqlText());
        query.setFolderId(request.folderId());
        return toQueryResponse(savedQueryRepository.save(query));
    }

    @Transactional
    public SavedQueryResponse updateQuery(Long id, SavedQueryRequest request) {
        SavedQuery query = savedQueryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("收藏查询不存在: " + id));
        if (request.name() != null && !request.name().isBlank()) {
            query.setName(request.name().trim());
        }
        if (request.sqlText() != null && !request.sqlText().isBlank()) {
            query.setSqlText(request.sqlText());
        }
        if (request.folderId() != null) {
            query.setFolderId(request.folderId());
        }
        return toQueryResponse(savedQueryRepository.save(query));
    }

    @Transactional
    public void deleteQuery(Long id) {
        if (!savedQueryRepository.existsById(id)) {
            throw new NotFoundException("收藏查询不存在: " + id);
        }
        savedQueryRepository.deleteById(id);
    }

    private QueryFolderResponse toFolderResponse(QueryFolder folder) {
        return new QueryFolderResponse(folder.getId(), folder.getName(), folder.getSortOrder(),
                folder.getCreatedAt(), folder.getUpdatedAt());
    }

    private SavedQueryResponse toQueryResponse(SavedQuery query) {
        return new SavedQueryResponse(query.getId(), query.getFolderId(), query.getName(),
                query.getSqlText(), query.getCreatedAt(), query.getUpdatedAt());
    }
}
