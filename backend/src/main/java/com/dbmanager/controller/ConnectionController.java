package com.dbmanager.controller;

import com.dbmanager.dto.*;
import com.dbmanager.entity.ConnectionGroup;
import com.dbmanager.service.ConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public ApiResponse<List<ConnectionResponse>> list() {
        return ApiResponse.ok(connectionService.listConnections());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConnectionResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(connectionService.getConnection(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok("Connection created", connectionService.createConnection(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConnectionResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok("Connection updated", connectionService.updateConnection(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ApiResponse.ok("Connection deleted", null);
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Boolean>> test(@Valid @RequestBody TestConnectionRequest request) {
        connectionService.testConnection(request);
        return ApiResponse.ok("Connection successful", Map.of("success", true));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Boolean>> testExisting(@PathVariable Long id) {
        connectionService.testConnection(id);
        return ApiResponse.ok("Connection successful", Map.of("success", true));
    }

    @GetMapping("/groups")
    public ApiResponse<List<ConnectionGroup>> listGroups() {
        return ApiResponse.ok(connectionService.listGroups());
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConnectionGroup> createGroup(@Valid @RequestBody FolderRequest request) {
        return ApiResponse.ok("Group created",
                connectionService.createGroup(request.getName(), request.getParentId(), request.getSortOrder()));
    }

    @PutMapping("/groups/{id}")
    public ApiResponse<ConnectionGroup> updateGroup(@PathVariable Long id,
                                                     @Valid @RequestBody FolderRequest request) {
        return ApiResponse.ok("Group updated",
                connectionService.updateGroup(id, request.getName(), request.getParentId(), request.getSortOrder()));
    }

    @DeleteMapping("/groups/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        connectionService.deleteGroup(id);
        return ApiResponse.ok("Group deleted", null);
    }
}
