package com.example.dbmanager.controller;

import com.example.dbmanager.dto.SavedQueryRequest;
import com.example.dbmanager.dto.SavedQueryResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏的查询。
 */
@RestController
@RequestMapping("/api/saved-queries")
public class SavedQueryController {

    private final SavedQueryService savedQueryService;

    public SavedQueryController(SavedQueryService savedQueryService) {
        this.savedQueryService = savedQueryService;
    }

    @GetMapping
    public List<SavedQueryResponse> list(@RequestParam(required = false) Long folderId) {
        return savedQueryService.listQueries(folderId);
    }

    @PostMapping
    public ResponseEntity<SavedQueryResponse> create(@RequestBody SavedQueryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQueryService.createQuery(request));
    }

    @PutMapping("/{id}")
    public SavedQueryResponse update(@PathVariable Long id, @RequestBody SavedQueryRequest request) {
        return savedQueryService.updateQuery(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        savedQueryService.deleteQuery(id);
        return ResponseEntity.noContent().build();
    }
}
