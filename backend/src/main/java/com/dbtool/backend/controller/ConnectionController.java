package com.dbtool.backend.controller;

import com.dbtool.backend.dto.ConnectionRequest;
import com.dbtool.backend.dto.ConnectionResponse;
import com.dbtool.backend.entity.Connection;
import com.dbtool.backend.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConnectionResponse> list() {
        return service.findAll().stream().map(ConnectionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ConnectionResponse get(@PathVariable Long id) {
        return ConnectionResponse.from(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest req) {
        Connection created = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectionResponse.from(created));
    }

    @PutMapping("/{id}")
    public ConnectionResponse update(@PathVariable Long id, @Valid @RequestBody ConnectionRequest req) {
        return ConnectionResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Test a saved connection. */
    @PostMapping("/{id}/test")
    public Map<String, Object> testExisting(@PathVariable Long id) {
        service.testExisting(id);
        return Map.of("success", true, "message", "Connection successful");
    }

    /** Test an ad-hoc connection with credentials in the body. */
    @PostMapping("/test")
    public Map<String, Object> testAdHoc(@Valid @RequestBody ConnectionRequest req) {
        service.testAdHoc(req);
        return Map.of("success", true, "message", "Connection successful");
    }
}
