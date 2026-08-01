package com.example.dbmanager.service;

import com.example.dbmanager.dto.ConnectionRequest;
import com.example.dbmanager.dto.ConnectionResponse;
import com.example.dbmanager.dto.TestConnectionRequest;
import com.example.dbmanager.dto.TestResultResponse;
import com.example.dbmanager.entity.ConnectionGroup;
import com.example.dbmanager.entity.DbConnection;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.ConnectionGroupRepository;
import com.example.dbmanager.repository.ConnectionRepository;
import com.example.dbmanager.util.AesEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final ConnectionGroupRepository groupRepository;
    private final AesEncryptor aesEncryptor;
    private final TargetJdbcService targetJdbcService;

    public ConnectionService(ConnectionRepository connectionRepository,
                             ConnectionGroupRepository groupRepository,
                             AesEncryptor aesEncryptor,
                             TargetJdbcService targetJdbcService) {
        this.connectionRepository = connectionRepository;
        this.groupRepository = groupRepository;
        this.aesEncryptor = aesEncryptor;
        this.targetJdbcService = targetJdbcService;
    }

    public List<ConnectionResponse> list() {
        Map<Long, String> groupNames = groupNameMap();
        return connectionRepository.findAllByOrderByIdAsc().stream()
                .map(conn -> toResponse(conn, groupNames))
                .toList();
    }

    public ConnectionResponse get(Long id) {
        return toResponse(findOrThrow(id), groupNameMap());
    }

    @Transactional
    public ConnectionResponse create(ConnectionRequest request) {
        DbConnection conn = new DbConnection();
        apply(conn, request);
        if (request.username() != null) {
            conn.setUsername(request.username());
        }
        conn.setPasswordEnc(encryptIfPresent(request.password()));
        return toResponse(connectionRepository.save(conn), groupNameMap());
    }

    @Transactional
    public ConnectionResponse update(Long id, ConnectionRequest request) {
        DbConnection conn = findOrThrow(id);
        apply(conn, request);
        // password 不传(null)表示不修改；空串表示清空；非空则重新加密
        if (request.password() != null) {
            conn.setPasswordEnc(request.password().isEmpty() ? null : aesEncryptor.encrypt(request.password()));
        }
        DbConnection saved = connectionRepository.save(conn);
        // 配置变更，销毁旧连接池
        targetJdbcService.evict(id);
        return toResponse(saved, groupNameMap());
    }

    @Transactional
    public void delete(Long id) {
        if (!connectionRepository.existsById(id)) {
            throw new NotFoundException("连接不存在: " + id);
        }
        targetJdbcService.evict(id);
        connectionRepository.deleteById(id);
    }

    /**
     * 测试已保存的连接。
     */
    public TestResultResponse testSaved(Long id) {
        DbConnection conn = findOrThrow(id);
        String plainPassword = aesEncryptor.decrypt(conn.getPasswordEnc());
        return targetJdbcService.testConnection(
                conn.getHost(), conn.getPort(), conn.getUsername(), plainPassword, conn.getDatabaseName());
    }

    /**
     * 不保存配置，直接测试连接。
     */
    public TestResultResponse testConfig(TestConnectionRequest request) {
        return targetJdbcService.testConnection(
                request.host(), request.port(), request.username(), request.password(), request.defaultDatabase());
    }

    private DbConnection findOrThrow(Long id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("连接不存在: " + id));
    }

    private void apply(DbConnection conn, ConnectionRequest request) {
        conn.setName(request.name().trim());
        conn.setGroupId(request.groupId());
        conn.setHost(request.host().trim());
        conn.setPort(request.port());
        if (request.username() != null) {
            conn.setUsername(request.username());
        }
        conn.setDatabaseName(normalizeDatabase(request.defaultDatabase()));
        // 不传 / null 时回落到默认 60 秒
        conn.setQueryTimeoutSeconds(
                request.queryTimeoutSeconds() == null ? 60 : request.queryTimeoutSeconds());
    }

    private String encryptIfPresent(String plainPassword) {
        return (plainPassword == null || plainPassword.isEmpty()) ? null : aesEncryptor.encrypt(plainPassword);
    }

    private static String normalizeDatabase(String database) {
        return (database == null || database.isBlank()) ? null : database.trim();
    }

    private Map<Long, String> groupNameMap() {
        return groupRepository.findAll().stream()
                .collect(Collectors.toMap(ConnectionGroup::getId, ConnectionGroup::getName));
    }

    private ConnectionResponse toResponse(DbConnection conn, Map<Long, String> groupNames) {
        boolean hasPassword = conn.getPasswordEnc() != null && !conn.getPasswordEnc().isBlank();
        String groupName = conn.getGroupId() == null ? null : groupNames.get(conn.getGroupId());
        return new ConnectionResponse(
                conn.getId(),
                conn.getName(),
                conn.getGroupId(),
                groupName,
                conn.getHost(),
                conn.getPort(),
                conn.getUsername(),
                conn.getDatabaseName(),
                conn.getDatabaseName(),
                conn.getQueryTimeoutSeconds(),
                hasPassword,
                conn.getCreatedAt(),
                conn.getUpdatedAt());
    }
}
