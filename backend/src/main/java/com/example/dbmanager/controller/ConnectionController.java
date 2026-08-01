package com.example.dbmanager.controller;

import com.example.dbmanager.dto.ConnectionRequest;
import com.example.dbmanager.dto.ConnectionResponse;
import com.example.dbmanager.dto.TestConnectionRequest;
import com.example.dbmanager.dto.TestResultResponse;
import com.example.dbmanager.service.ConnectionService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionResponse> list() {
        return connectionService.list();
    }

    @GetMapping("/{id}")
    public ConnectionResponse get(@PathVariable Long id) {
        return connectionService.get(id);
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(request));
    }

    @PutMapping("/{id}")
    public ConnectionResponse update(@PathVariable Long id, @Valid @RequestBody ConnectionRequest request) {
        return connectionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 测试已保存的连接。
     */
    @PostMapping("/{id}/test")
    public TestResultResponse testSaved(@PathVariable Long id) {
        return connectionService.testSaved(id);
    }

    /**
     * 不保存配置，直接测试连接。
     */
    @PostMapping("/test")
    public TestResultResponse testConfig(@Valid @RequestBody TestConnectionRequest request) {
        return connectionService.testConfig(request);
    }
}
