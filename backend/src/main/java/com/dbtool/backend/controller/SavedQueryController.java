package com.dbtool.backend.controller;

import com.dbtool.backend.entity.QueryFolder;
import com.dbtool.backend.entity.SavedQuery;
import com.dbtool.backend.repository.QueryFolderRepository;
import com.dbtool.backend.repository.SavedQueryRepository;
import com.dbtool.backend.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saved-queries")
public class SavedQueryController {

    private final SavedQueryRepository savedQueryRepository;
    private final QueryFolderRepository folderRepository;

    public SavedQueryController(SavedQueryRepository savedQueryRepository,
                                QueryFolderRepository folderRepository) {
        this.savedQueryRepository = savedQueryRepository;
        this.folderRepository = folderRepository;
    }

    public static class SavedQueryRequest {
        @NotBlank
        public String name;
        public Long folderId;
        public Long connectionId;
        @NotBlank
        public String sqlText;
        public String description;
    }

    public static class FolderRequest {
        @NotBlank
        public String name;
        public Integer sortOrder = 0;
    }

    // --- saved queries ---

    @GetMapping
    public List<SavedQuery> list(@RequestParam(required = false) Long folderId) {
        if (folderId != null) {
            return savedQueryRepository.findByFolderId(folderId);
        }
        return savedQueryRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<SavedQuery> create(@Valid @RequestBody SavedQueryRequest req) {
        SavedQuery q = new SavedQuery();
        apply(q, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQueryRepository.save(q));
    }

    @PutMapping("/{id}")
    public SavedQuery update(@PathVariable Long id, @Valid @RequestBody SavedQueryRequest req) {
        SavedQuery q = savedQueryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Saved query not found: " + id));
        apply(q, req);
        return savedQueryRepository.save(q);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!savedQueryRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Saved query not found: " + id);
        }
        savedQueryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- folders ---

    @GetMapping("/folders")
    public List<QueryFolder> listFolders() {
        return folderRepository.findAll();
    }

    @PostMapping("/folders")
    public ResponseEntity<QueryFolder> createFolder(@Valid @RequestBody FolderRequest req) {
        QueryFolder f = new QueryFolder();
        f.setName(req.name);
        f.setSortOrder(req.sortOrder == null ? 0 : req.sortOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(folderRepository.save(f));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long id) {
        if (!folderRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Folder not found: " + id);
        }
        folderRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void apply(SavedQuery q, SavedQueryRequest req) {
        q.setName(req.name);
        q.setFolderId(req.folderId);
        q.setConnectionId(req.connectionId);
        q.setSqlText(req.sqlText);
        q.setDescription(req.description);
    }
}
