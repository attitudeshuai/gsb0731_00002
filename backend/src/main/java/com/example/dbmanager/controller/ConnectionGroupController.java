package com.example.dbmanager.controller;

import com.example.dbmanager.dto.ConnectionGroupRequest;
import com.example.dbmanager.dto.ConnectionGroupResponse;
import com.example.dbmanager.service.ConnectionGroupService;
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

@RestController
@RequestMapping("/api/connection-groups")
public class ConnectionGroupController {

    private final ConnectionGroupService groupService;

    public ConnectionGroupController(ConnectionGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<ConnectionGroupResponse> list() {
        return groupService.list();
    }

    @PostMapping
    public ResponseEntity<ConnectionGroupResponse> create(@RequestBody ConnectionGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(request));
    }

    @PutMapping("/{id}")
    public ConnectionGroupResponse update(@PathVariable Long id, @RequestBody ConnectionGroupRequest request) {
        return groupService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
