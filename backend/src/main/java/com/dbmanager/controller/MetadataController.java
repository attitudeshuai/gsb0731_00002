package com.dbmanager.controller;

import com.dbmanager.dto.*;
import com.dbmanager.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    @GetMapping("/{connectionId}/databases")
    public ApiResponse<List<String>> listDatabases(@PathVariable Long connectionId) {
        return ApiResponse.ok(metadataService.listDatabases(connectionId));
    }

    @GetMapping("/{connectionId}/databases/{database}/tables")
    public ApiResponse<List<TableMeta>> listTables(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(metadataService.listTables(connectionId, database, keyword));
    }

    @GetMapping("/{connectionId}/databases/{database}/tables/{table}/columns")
    public ApiResponse<List<ColumnMeta>> describeTable(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table) {
        return ApiResponse.ok(metadataService.describeTable(connectionId, database, table));
    }

    @GetMapping("/{connectionId}/databases/{database}/tables/{table}/indexes")
    public ApiResponse<List<IndexInfo>> listIndexes(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table) {
        return ApiResponse.ok(metadataService.listIndexes(connectionId, database, table));
    }

    @GetMapping("/{connectionId}/databases/{database}/tables/{table}/ddl")
    public ApiResponse<Map<String, String>> showCreateTable(
            @PathVariable Long connectionId,
            @PathVariable String database,
            @PathVariable String table) {
        String ddl = metadataService.showCreateTable(connectionId, database, table);
        return ApiResponse.ok(Map.of("ddl", ddl));
    }
}
