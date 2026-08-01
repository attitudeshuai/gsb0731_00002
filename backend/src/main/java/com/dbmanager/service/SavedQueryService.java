package com.dbmanager.service;

import com.dbmanager.dto.FolderRequest;
import com.dbmanager.dto.SavedQueryRequest;
import com.dbmanager.entity.QueryFolder;
import com.dbmanager.entity.SavedQuery;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.QueryFolderRepository;
import com.dbmanager.repository.SavedQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedQueryService {

    private final SavedQueryRepository savedQueryRepository;
    private final QueryFolderRepository folderRepository;

    public List<SavedQuery> listAll() {
        return savedQueryRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<SavedQuery> listByFolder(Long folderId) {
        return savedQueryRepository.findByFolderIdOrderByUpdatedAtDesc(folderId);
    }

    public SavedQuery get(Long id) {
        return savedQueryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Saved query not found: " + id));
    }

    @Transactional
    public SavedQuery create(SavedQueryRequest request) {
        SavedQuery q = new SavedQuery();
        apply(q, request);
        return savedQueryRepository.save(q);
    }

    @Transactional
    public SavedQuery update(Long id, SavedQueryRequest request) {
        SavedQuery q = get(id);
        apply(q, request);
        return savedQueryRepository.save(q);
    }

    @Transactional
    public void delete(Long id) {
        if (!savedQueryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Saved query not found: " + id);
        }
        savedQueryRepository.deleteById(id);
    }

    public List<QueryFolder> listFolders() {
        return folderRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public QueryFolder createFolder(FolderRequest request) {
        QueryFolder f = new QueryFolder();
        f.setName(request.getName());
        f.setParentId(request.getParentId());
        f.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        return folderRepository.save(f);
    }

    @Transactional
    public QueryFolder updateFolder(Long id, FolderRequest request) {
        QueryFolder f = folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + id));
        if (request.getName() != null) f.setName(request.getName());
        f.setParentId(request.getParentId());
        if (request.getSortOrder() != null) f.setSortOrder(request.getSortOrder());
        return folderRepository.save(f);
    }

    @Transactional
    public void deleteFolder(Long id) {
        if (!folderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Folder not found: " + id);
        }
        folderRepository.deleteById(id);
    }

    private void apply(SavedQuery q, SavedQueryRequest request) {
        q.setTitle(request.getTitle());
        q.setSqlText(request.getSqlText());
        q.setFolderId(request.getFolderId());
        q.setConnectionId(request.getConnectionId());
        q.setDatabaseName(request.getDatabaseName());
        q.setTags(request.getTags());
    }
}
