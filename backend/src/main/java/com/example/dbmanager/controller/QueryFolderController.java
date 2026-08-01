package com.example.dbmanager.controller;

import com.example.dbmanager.dto.QueryFolderRequest;
import com.example.dbmanager.dto.QueryFolderResponse;
import com.example.dbmanager.service.SavedQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏查询文件夹。
 */
@RestController
@RequestMapping("/api/query-folders")
public class QueryFolderController {

    private final SavedQueryService savedQueryService;

    public QueryFolderController(SavedQueryService savedQueryService) {
        this.savedQueryService = savedQueryService;
    }

    @GetMapping
    public List<QueryFolderResponse> list() {
        return savedQueryService.listFolders();
    }

    @PostMapping
    public ResponseEntity<QueryFolderResponse> create(@RequestBody QueryFolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQueryService.createFolder(request));
    }

    @PutMapping("/{id}")
    public QueryFolderResponse update(@PathVariable Long id, @RequestBody QueryFolderRequest request) {
        return savedQueryService.updateFolder(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        savedQueryService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
