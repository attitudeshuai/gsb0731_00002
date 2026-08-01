package com.dbmanager.controller;

import com.dbmanager.dto.ApiResponse;
import com.dbmanager.dto.ColumnInfo;
import com.dbmanager.dto.ConnectionRequest;
import com.dbmanager.dto.ConnectionResponse;
import com.dbmanager.dto.GroupRequest;
import com.dbmanager.dto.GroupResponse;
import com.dbmanager.dto.IndexInfo;
import com.dbmanager.dto.PageResponse;
import com.dbmanager.dto.RowUpdateRequest;
import com.dbmanager.dto.TableDataResponse;
import com.dbmanager.dto.TableInfo;
import com.dbmanager.service.ConnectionGroupService;
import com.dbmanager.service.ConnectionService;
import com.dbmanager.service.MetadataService;
import com.dbmanager.service.TableDataService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final ConnectionGroupService groupService;
    private final MetadataService metadataService;
    private final TableDataService tableDataService;

    public ConnectionController(ConnectionService connectionService,
                                ConnectionGroupService groupService,
                                MetadataService metadataService,
                                TableDataService tableDataService) {
        this.connectionService = connectionService;
        this.groupService = groupService;
        this.metadataService = metadataService;
        this.tableDataService = tableDataService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ConnectionResponse>> listConnections(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(connectionService.listConnections(keyword, groupId, type, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectionResponse> getConnection(@PathVariable Long id) {
        return ApiResponse.ok(connectionService.getConnection(id));
    }

    @PostMapping
    public ApiResponse<ConnectionResponse> createConnection(@Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok(connectionService.createConnection(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConnectionResponse> updateConnection(
            @PathVariable Long id, @Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok(connectionService.updateConnection(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ApiResponse.ok();
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection(@Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok(connectionService.testConnection(request));
    }

    @GetMapping("/groups")
    public ApiResponse<List<GroupResponse>> listGroups() {
        return ApiResponse.ok(groupService.listGroups());
    }

    @PostMapping("/groups")
    public ApiResponse<GroupResponse> createGroup(@Valid @RequestBody GroupRequest request) {
        return ApiResponse.ok(groupService.createGroup(request));
    }

    @PutMapping("/groups/{id}")
    public ApiResponse<GroupResponse> updateGroup(
            @PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return ApiResponse.ok(groupService.updateGroup(id, request));
    }

    @DeleteMapping("/groups/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/databases")
    public ApiResponse<List<String>> getDatabases(@PathVariable Long id) {
        return ApiResponse.ok(metadataService.getDatabases(id));
    }

    @GetMapping("/{id}/tables")
    public ApiResponse<List<TableInfo>> getTables(
            @PathVariable Long id,
            @RequestParam String database) {
        return ApiResponse.ok(metadataService.getTables(id, database));
    }

    @GetMapping("/{id}/tables/{table}/structure")
    public ApiResponse<List<ColumnInfo>> getTableStructure(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database) {
        return ApiResponse.ok(metadataService.getTableStructure(id, database, table));
    }

    @GetMapping("/{id}/tables/{table}/indexes")
    public ApiResponse<List<IndexInfo>> getIndexes(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database) {
        return ApiResponse.ok(metadataService.getIndexes(id, database, table));
    }

    @GetMapping("/{id}/tables/{table}/ddl")
    public ApiResponse<String> getTableDDL(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database) {
        return ApiResponse.ok(metadataService.getTableDDL(id, database, table));
    }

    @GetMapping("/{id}/tables/{table}/data")
    public ApiResponse<TableDataResponse> getTableData(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String filter) {
        return ApiResponse.ok(tableDataService.getTableData(id, database, table, page, size, sort, filter));
    }

    @PutMapping("/{id}/tables/{table}/data")
    public ApiResponse<Integer> updateRow(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database,
            @Valid @RequestBody RowUpdateRequest request) {
        return ApiResponse.ok(tableDataService.updateRow(id, database, table, request));
    }

    @PostMapping("/{id}/tables/{table}/data")
    public ApiResponse<Integer> insertRow(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database,
            @RequestBody Map<String, Object> row) {
        return ApiResponse.ok(tableDataService.insertRow(id, database, table, row));
    }

    @DeleteMapping("/{id}/tables/{table}/data")
    public ApiResponse<Integer> deleteRow(
            @PathVariable Long id,
            @PathVariable String table,
            @RequestParam String database,
            @RequestBody Map<String, Object> primaryKey) {
        return ApiResponse.ok(tableDataService.deleteRow(id, database, table, primaryKey));
    }
}
