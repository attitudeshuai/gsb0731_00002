package com.dbmanager.controller;

import com.dbmanager.dto.ApiResponse;
import com.dbmanager.dto.FolderRequest;
import com.dbmanager.dto.SavedQueryRequest;
import com.dbmanager.entity.QueryFolder;
import com.dbmanager.entity.SavedQuery;
import com.dbmanager.service.SavedQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/saved-queries")
@RequiredArgsConstructor
public class SavedQueryController {

    private final SavedQueryService savedQueryService;

    @GetMapping
    public ApiResponse<List<SavedQuery>> list(@RequestParam(required = false) Long folderId) {
        if (folderId != null) {
            return ApiResponse.ok(savedQueryService.listByFolder(folderId));
        }
        return ApiResponse.ok(savedQueryService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SavedQuery> get(@PathVariable Long id) {
        return ApiResponse.ok(savedQueryService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavedQuery> create(@Valid @RequestBody SavedQueryRequest request) {
        return ApiResponse.ok("Saved query created", savedQueryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SavedQuery> update(@PathVariable Long id,
                                           @Valid @RequestBody SavedQueryRequest request) {
        return ApiResponse.ok("Saved query updated", savedQueryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        savedQueryService.delete(id);
        return ApiResponse.ok("Saved query deleted", null);
    }

    @GetMapping("/folders")
    public ApiResponse<List<QueryFolder>> listFolders() {
        return ApiResponse.ok(savedQueryService.listFolders());
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QueryFolder> createFolder(@Valid @RequestBody FolderRequest request) {
        return ApiResponse.ok("Folder created", savedQueryService.createFolder(request));
    }

    @PutMapping("/folders/{id}")
    public ApiResponse<QueryFolder> updateFolder(@PathVariable Long id,
                                                  @Valid @RequestBody FolderRequest request) {
        return ApiResponse.ok("Folder updated", savedQueryService.updateFolder(id, request));
    }

    @DeleteMapping("/folders/{id}")
    public ApiResponse<Void> deleteFolder(@PathVariable Long id) {
        savedQueryService.deleteFolder(id);
        return ApiResponse.ok("Folder deleted", null);
    }
}
