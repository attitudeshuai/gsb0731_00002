package com.dbtool.backend.controller;

import com.dbtool.backend.entity.ConnectionGroup;
import com.dbtool.backend.repository.ConnectionGroupRepository;
import com.dbtool.backend.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connection-groups")
public class ConnectionGroupController {

    private final ConnectionGroupRepository repository;

    public ConnectionGroupController(ConnectionGroupRepository repository) {
        this.repository = repository;
    }

    public static class GroupRequest {
        @NotBlank
        public String name;
        public Integer sortOrder = 0;
    }

    @GetMapping
    public List<ConnectionGroup> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<ConnectionGroup> create(@Valid @RequestBody GroupRequest req) {
        ConnectionGroup g = new ConnectionGroup();
        g.setName(req.name);
        g.setSortOrder(req.sortOrder == null ? 0 : req.sortOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(g));
    }

    @PutMapping("/{id}")
    public ConnectionGroup update(@PathVariable Long id, @Valid @RequestBody GroupRequest req) {
        ConnectionGroup g = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group not found: " + id));
        g.setName(req.name);
        g.setSortOrder(req.sortOrder == null ? 0 : req.sortOrder);
        return repository.save(g);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Group not found: " + id);
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
