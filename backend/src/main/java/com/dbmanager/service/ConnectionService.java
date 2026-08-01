package com.dbmanager.service;

import com.dbmanager.dto.ConnectionRequest;
import com.dbmanager.dto.ConnectionResponse;
import com.dbmanager.dto.TestConnectionRequest;
import com.dbmanager.entity.ConnectionConfig;
import com.dbmanager.entity.ConnectionGroup;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.ConnectionGroupRepository;
import com.dbmanager.repository.ConnectionRepository;
import com.dbmanager.util.AesCryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final ConnectionGroupRepository groupRepository;
    private final AesCryptoUtil aesCryptoUtil;
    private final DynamicDataSourceManager dataSourceManager;

    public List<ConnectionResponse> listConnections() {
        return connectionRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ConnectionResponse getConnection(Long id) {
        ConnectionConfig config = connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found: " + id));
        return toResponse(config);
    }

    @Transactional
    public ConnectionResponse createConnection(ConnectionRequest request) {
        ConnectionConfig config = new ConnectionConfig();
        applyRequest(config, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            config.setPassword(aesCryptoUtil.encrypt(request.getPassword()));
        } else {
            config.setPassword(aesCryptoUtil.encrypt(""));
        }
        config = connectionRepository.save(config);
        return toResponse(config);
    }

    @Transactional
    public ConnectionResponse updateConnection(Long id, ConnectionRequest request) {
        ConnectionConfig config = connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found: " + id));
        applyRequest(config, request);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            config.setPassword(aesCryptoUtil.encrypt(request.getPassword()));
        }
        config = connectionRepository.save(config);
        return toResponse(config);
    }

    @Transactional
    public void deleteConnection(Long id) {
        if (!connectionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Connection not found: " + id);
        }
        connectionRepository.deleteById(id);
    }

    public void testConnection(TestConnectionRequest request) {
        dataSourceManager.testConnection(
                request.getHost(), request.getPort(), request.getUsername(),
                request.getPassword(), request.getDatabaseName());
    }

    public void testConnection(Long id) {
        dataSourceManager.testConnection(id);
    }

    public List<ConnectionGroup> listGroups() {
        return groupRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public ConnectionGroup createGroup(String name, Long parentId, Integer sortOrder) {
        ConnectionGroup group = ConnectionGroup.builder()
                .name(name)
                .parentId(parentId)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .build();
        return groupRepository.save(group);
    }

    @Transactional
    public ConnectionGroup updateGroup(Long id, String name, Long parentId, Integer sortOrder) {
        ConnectionGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));
        if (name != null) group.setName(name);
        group.setParentId(parentId);
        if (sortOrder != null) group.setSortOrder(sortOrder);
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        if (!groupRepository.existsById(id)) {
            throw new ResourceNotFoundException("Group not found: " + id);
        }
        groupRepository.deleteById(id);
    }

    private void applyRequest(ConnectionConfig config, ConnectionRequest request) {
        config.setName(request.getName());
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setUsername(request.getUsername());
        config.setDatabaseName(request.getDatabaseName());
        config.setGroupId(request.getGroupId());
        config.setColor(request.getColor());
        config.setRemark(request.getRemark());
    }

    private ConnectionResponse toResponse(ConnectionConfig config) {
        return ConnectionResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .host(config.getHost())
                .port(config.getPort())
                .username(config.getUsername())
                .hasPassword(config.getPassword() != null && !config.getPassword().isBlank())
                .databaseName(config.getDatabaseName())
                .groupId(config.getGroupId())
                .color(config.getColor())
                .remark(config.getRemark())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
