package com.dbmanager.service;

import com.dbmanager.dto.ConnectionRequest;
import com.dbmanager.entity.DbConnection;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.exception.ResourceNotFoundException;
import com.dbmanager.repository.DbConnectionRepository;
import com.dbmanager.util.AesEncryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@Service
public class DynamicDataSourceService {

    private final AesEncryptor aesEncryptor;
    private final DbConnectionRepository dbConnectionRepository;

    @Value("${app.pool.maximum-pool-size:5}")
    private int maximumPoolSize;
    @Value("${app.pool.minimum-idle:1}")
    private int minimumIdle;
    @Value("${app.pool.connection-timeout:10000}")
    private long connectionTimeout;
    @Value("${app.pool.idle-timeout:300000}")
    private long idleTimeout;
    @Value("${app.pool.max-lifetime:600000}")
    private long maxLifetime;
    @Value("${app.pool.connect-timeout:10000}")
    private int connectTimeoutMs;
    @Value("${app.pool.socket-timeout:60000}")
    private int socketTimeoutMs;

    private final ConcurrentMap<String, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    public DynamicDataSourceService(AesEncryptor aesEncryptor,
                                     DbConnectionRepository dbConnectionRepository) {
        this.aesEncryptor = aesEncryptor;
        this.dbConnectionRepository = dbConnectionRepository;
    }

    public JdbcTemplate createJdbcTemplate(Long connectionId) {
        return createJdbcTemplate(connectionId, null);
    }

    public JdbcTemplate createJdbcTemplate(Long connectionId, String database) {
        DbConnection connection = getConnection(connectionId);
        String password = aesEncryptor.decrypt(connection.getPasswordEncrypted());
        String db = (database != null && !database.isBlank())
                ? database : connection.getDatabaseName();
        String key = poolKey(connection.getId(), db);
        HikariDataSource dataSource = poolCache.computeIfAbsent(key,
                k -> createPool(connection.getHost(), connection.getPort(), db,
                        connection.getUsername(), password, key));
        return new JdbcTemplate(dataSource);
    }

    public <T> T withTemporaryTemplate(ConnectionRequest req, Function<JdbcTemplate, T> callback) {
        HikariDataSource dataSource = null;
        try {
            dataSource = createPool(req.getHost(), req.getPort(), req.getDatabaseName(),
                    req.getUsername(), req.getPassword(), "test-" + System.nanoTime());
            return callback.apply(new JdbcTemplate(dataSource));
        } finally {
            if (dataSource != null) {
                try {
                    dataSource.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void evictPool(Long connectionId) {
        if (connectionId == null) {
            return;
        }
        String prefix = connectionId + "|";
        poolCache.forEach((key, ds) -> {
            if (key.startsWith(prefix)) {
                HikariDataSource removed = poolCache.remove(key);
                if (removed != null) {
                    closeQuietly(removed);
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        poolCache.values().forEach(this::closeQuietly);
        poolCache.clear();
    }

    public DbConnection getConnection(Long id) {
        return dbConnectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection", "id", id));
    }

    private HikariDataSource createPool(String host, int port, String database,
                                         String username, String decryptedPassword, String poolName) {
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC&connectTimeout=%d&socketTimeout=%d"
                        + "&cachePrepStmts=true&useServerPrepStmts=true",
                host, port, database != null ? database : "",
                connectTimeoutMs, socketTimeoutMs
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(decryptedPassword);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName(safePoolName(poolName));
        config.setAutoCommit(true);

        try {
            return new HikariDataSource(config);
        } catch (Exception e) {
            throw new BusinessException("POOL_ERROR", "Failed to create connection pool: " + e.getMessage(), e);
        }
    }

    private String poolKey(Long connectionId, String database) {
        return connectionId + "|" + (database != null ? database : "");
    }

    private String safePoolName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9-_]", "-");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private void closeQuietly(HikariDataSource dataSource) {
        try {
            dataSource.close();
        } catch (Exception ignored) {
        }
    }
}
