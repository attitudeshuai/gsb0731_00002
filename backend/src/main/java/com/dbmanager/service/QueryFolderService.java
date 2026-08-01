package com.dbmanager.service;

import com.dbmanager.dto.FolderRequest;
import com.dbmanager.dto.FolderResponse;
import com.dbmanager.entity.QueryFolder;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.QueryFolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryFolderService {

    private final QueryFolderRepository queryFolderRepository;

    public QueryFolderService(QueryFolderRepository queryFolderRepository) {
        this.queryFolderRepository = queryFolderRepository;
    }

    public List<FolderResponse> listFolders() {
        return queryFolderRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public FolderResponse getFolder(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public FolderResponse createFolder(FolderRequest request) {
        QueryFolder folder = new QueryFolder();
        folder.setName(request.getName());
        folder.setParentId(request.getParentId());
        folder.setSortOrder(request.getSortOrder());
        folder.setIcon(request.getIcon());
        folder.setColor(request.getColor());
        QueryFolder saved = queryFolderRepository.save(folder);
        return toResponse(saved);
    }

    @Transactional
    public FolderResponse updateFolder(Long id, FolderRequest request) {
        QueryFolder folder = findOrThrow(id);
        folder.setName(request.getName());
        folder.setParentId(request.getParentId());
        folder.setSortOrder(request.getSortOrder());
        folder.setIcon(request.getIcon());
        folder.setColor(request.getColor());
        QueryFolder saved = queryFolderRepository.save(folder);
        return toResponse(saved);
    }

    @Transactional
    public void deleteFolder(Long id) {
        QueryFolder folder = findOrThrow(id);
        queryFolderRepository.delete(folder);
    }

    private QueryFolder findOrThrow(Long id) {
        return queryFolderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", "id", id));
    }

    private FolderResponse toResponse(QueryFolder f) {
        FolderResponse resp = new FolderResponse();
        resp.setId(f.getId());
        resp.setName(f.getName());
        resp.setParentId(f.getParentId());
        resp.setUserId(f.getUserId());
        resp.setSortOrder(f.getSortOrder());
        resp.setIcon(f.getIcon());
        resp.setColor(f.getColor());
        resp.setCreatedAt(f.getCreatedAt());
        resp.setUpdatedAt(f.getUpdatedAt());
        return resp;
    }
}
