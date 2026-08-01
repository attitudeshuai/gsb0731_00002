package com.dbmanager.service;

import com.dbmanager.dto.ConnectionRequest;
import com.dbmanager.dto.ConnectionResponse;
import com.dbmanager.dto.PageResponse;
import com.dbmanager.entity.DbConnection;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.ConnectionGroupRepository;
import com.dbmanager.repository.DbConnectionRepository;
import com.dbmanager.util.AesEncryptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConnectionService {

    private final DbConnectionRepository dbConnectionRepository;
    private final ConnectionGroupRepository connectionGroupRepository;
    private final AesEncryptor aesEncryptor;
    private final DynamicDataSourceService dynamicDataSourceService;

    @PersistenceContext
    private EntityManager entityManager;

    public ConnectionService(DbConnectionRepository dbConnectionRepository,
                             ConnectionGroupRepository connectionGroupRepository,
                             AesEncryptor aesEncryptor,
                             DynamicDataSourceService dynamicDataSourceService) {
        this.dbConnectionRepository = dbConnectionRepository;
        this.connectionGroupRepository = connectionGroupRepository;
        this.aesEncryptor = aesEncryptor;
        this.dynamicDataSourceService = dynamicDataSourceService;
    }

    public PageResponse<ConnectionResponse> listConnections(String keyword, Long groupId,
                                                             String type, int page, int size) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DbConnection> query = cb.createQuery(DbConnection.class);
        Root<DbConnection> root = query.from(DbConnection.class);

        List<Predicate> predicates = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
            Predicate hostLike = cb.like(cb.lower(root.get("host")), pattern);
            predicates.add(cb.or(nameLike, hostLike));
        }

        if (groupId != null) {
            predicates.add(cb.equal(root.get("groupId"), groupId));
        }

        if (type != null && !type.isBlank()) {
            predicates.add(cb.equal(root.get("type"), type));
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("updatedAt")));

        List<DbConnection> all = entityManager.createQuery(query).getResultList();
        long total = all.size();

        int offset = page * size;
        List<DbConnection> paged = all.stream()
                .skip(offset)
                .limit(size)
                .toList();

        List<ConnectionResponse> content = paged.stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.of(content, total, page, size);
    }

    public ConnectionResponse getConnection(Long id) {
        DbConnection connection = findOrThrow(id);
        return toResponse(connection);
    }

    @Transactional
    public ConnectionResponse createConnection(ConnectionRequest request) {
        DbConnection connection = new DbConnection();
        connection.setName(request.getName());
        connection.setHost(request.getHost());
        connection.setPort(request.getPort());
        connection.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            connection.setPasswordEncrypted(aesEncryptor.encrypt(request.getPassword()));
        } else {
            throw new BusinessException("Password is required for new connection");
        }
        connection.setDatabaseName(request.getDatabaseName());
        connection.setGroupId(request.getGroupId());
        connection.setType(request.getType() != null ? request.getType() : "MYSQL");
        connection.setColor(request.getColor());
        connection.setRemark(request.getRemark());

        DbConnection saved = dbConnectionRepository.save(connection);
        return toResponse(saved);
    }

    @Transactional
    public ConnectionResponse updateConnection(Long id, ConnectionRequest request) {
        DbConnection connection = findOrThrow(id);

        connection.setName(request.getName());
        connection.setHost(request.getHost());
        connection.setPort(request.getPort());
        connection.setUsername(request.getUsername());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            connection.setPasswordEncrypted(aesEncryptor.encrypt(request.getPassword()));
        }

        connection.setDatabaseName(request.getDatabaseName());
        connection.setGroupId(request.getGroupId());
        if (request.getType() != null) {
            connection.setType(request.getType());
        }
        connection.setColor(request.getColor());
        connection.setRemark(request.getRemark());

        DbConnection saved = dbConnectionRepository.save(connection);
        dynamicDataSourceService.evictPool(id);
        return toResponse(saved);
    }

    @Transactional
    public void deleteConnection(Long id) {
        DbConnection connection = findOrThrow(id);
        dbConnectionRepository.delete(connection);
        dynamicDataSourceService.evictPool(id);
    }

    public Map<String, Object> testConnection(ConnectionRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            dynamicDataSourceService.withTemporaryTemplate(request, jdbcTemplate -> {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                return null;
            });
            result.put("success", true);
            result.put("message", "Connection successful");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Transactional
    public void updateLastConnectedAt(Long id) {
        DbConnection connection = findOrThrow(id);
        connection.setLastConnectedAt(Instant.now());
        dbConnectionRepository.save(connection);
    }

    private DbConnection findOrThrow(Long id) {
        return dbConnectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection", "id", id));
    }

    private ConnectionResponse toResponse(DbConnection c) {
        ConnectionResponse resp = new ConnectionResponse();
        resp.setId(c.getId());
        resp.setName(c.getName());
        resp.setHost(c.getHost());
        resp.setPort(c.getPort());
        resp.setUsername(c.getUsername());
        resp.setDatabaseName(c.getDatabaseName());
        resp.setGroupId(c.getGroupId());
        resp.setType(c.getType());
        resp.setColor(c.getColor());
        resp.setRemark(c.getRemark());
        resp.setLastConnectedAt(c.getLastConnectedAt());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setUpdatedAt(c.getUpdatedAt());
        return resp;
    }
}
