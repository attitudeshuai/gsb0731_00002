package com.dbmanager.controller;

import com.dbmanager.dto.ApiResponse;
import com.dbmanager.dto.FolderRequest;
import com.dbmanager.dto.FolderResponse;
import com.dbmanager.dto.HistoryResponse;
import com.dbmanager.dto.PageResponse;
import com.dbmanager.dto.QueryExecuteRequest;
import com.dbmanager.dto.QueryResultResponse;
import com.dbmanager.dto.SavedQueryRequest;
import com.dbmanager.dto.SavedQueryResponse;
import com.dbmanager.service.QueryFolderService;
import com.dbmanager.service.QueryHistoryService;
import com.dbmanager.service.QueryService;
import com.dbmanager.service.SavedQueryService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;
    private final QueryHistoryService historyService;
    private final SavedQueryService savedQueryService;
    private final QueryFolderService folderService;

    public QueryController(QueryService queryService,
                           QueryHistoryService historyService,
                           SavedQueryService savedQueryService,
                           QueryFolderService folderService) {
        this.queryService = queryService;
        this.historyService = historyService;
        this.savedQueryService = savedQueryService;
        this.folderService = folderService;
    }

    @PostMapping("/execute")
    public ApiResponse<QueryResultResponse> execute(
            @RequestParam Long connectionId,
            @RequestParam(required = false) String executionId,
            @Valid @RequestBody QueryExecuteRequest request) {
        return ApiResponse.ok(queryService.execute(connectionId, request, executionId));
    }

    @PostMapping("/cancel/{executionId}")
    public ApiResponse<Void> cancel(@PathVariable String executionId) {
        queryService.cancel(executionId);
        return ApiResponse.ok();
    }

    @GetMapping("/history")
    public ApiResponse<PageResponse<HistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(historyService.getHistory(connectionId, keyword, page, size));
    }

    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory() {
        historyService.clearHistory();
        return ApiResponse.ok();
    }

    @GetMapping("/saved")
    public ApiResponse<List<SavedQueryResponse>> listSavedQueries(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(savedQueryService.listSavedQueries(folderId, keyword));
    }

    @GetMapping("/saved/{id}")
    public ApiResponse<SavedQueryResponse> getSavedQuery(@PathVariable Long id) {
        return ApiResponse.ok(savedQueryService.getSavedQuery(id));
    }

    @PostMapping("/saved")
    public ApiResponse<SavedQueryResponse> createSavedQuery(@Valid @RequestBody SavedQueryRequest request) {
        return ApiResponse.ok(savedQueryService.createSavedQuery(request));
    }

    @PutMapping("/saved/{id}")
    public ApiResponse<SavedQueryResponse> updateSavedQuery(
            @PathVariable Long id, @Valid @RequestBody SavedQueryRequest request) {
        return ApiResponse.ok(savedQueryService.updateSavedQuery(id, request));
    }

    @DeleteMapping("/saved/{id}")
    public ApiResponse<Void> deleteSavedQuery(@PathVariable Long id) {
        savedQueryService.deleteSavedQuery(id);
        return ApiResponse.ok();
    }

    @GetMapping("/folders")
    public ApiResponse<List<FolderResponse>> listFolders() {
        return ApiResponse.ok(folderService.listFolders());
    }

    @PostMapping("/folders")
    public ApiResponse<FolderResponse> createFolder(@Valid @RequestBody FolderRequest request) {
        return ApiResponse.ok(folderService.createFolder(request));
    }

    @PutMapping("/folders/{id}")
    public ApiResponse<FolderResponse> updateFolder(
            @PathVariable Long id, @Valid @RequestBody FolderRequest request) {
        return ApiResponse.ok(folderService.updateFolder(id, request));
    }

    @DeleteMapping("/folders/{id}")
    public ApiResponse<Void> deleteFolder(@PathVariable Long id) {
        folderService.deleteFolder(id);
        return ApiResponse.ok();
    }
}
