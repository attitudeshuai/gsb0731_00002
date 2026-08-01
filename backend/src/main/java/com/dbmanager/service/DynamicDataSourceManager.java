package com.dbmanager.service;

import com.dbmanager.entity.ConnectionConfig;
import com.dbmanager.exception.BusinessException;
import com.dbmanager.repository.ConnectionRepository;
import com.dbmanager.util.AesCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDataSourceManager {

    private final ConnectionRepository connectionRepository;
    private final AesCryptoUtil aesCryptoUtil;

    public Connection getConnection(Long connectionId, String databaseName) {
        ConnectionConfig config = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException("Connection not found: " + connectionId));
        return createConnection(config, databaseName);
    }

    public Connection createConnection(ConnectionConfig config, String databaseName) {
        String db = databaseName != null && !databaseName.isBlank()
                ? databaseName
                : config.getDatabaseName();
        String url = buildJdbcUrl(config.getHost(), config.getPort(), db);
        String password = aesCryptoUtil.decrypt(config.getPassword());
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        if (password != null) {
            props.setProperty("password", password);
        }
        props.setProperty("useSSL", "false");
        props.setProperty("allowPublicKeyRetrieval", "true");
        props.setProperty("serverTimezone", "UTC");
        props.setProperty("characterEncoding", "utf8");
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "60000");
        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            log.error("Failed to connect to target database: {}", url, e);
            throw new BusinessException("Failed to connect: " + e.getMessage());
        }
    }

    public Connection createConnection(String host, Integer port, String username,
                                       String rawPassword, String databaseName) {
        String url = buildJdbcUrl(host, port, databaseName);
        Properties props = new Properties();
        props.setProperty("user", username);
        if (rawPassword != null) {
            props.setProperty("password", rawPassword);
        }
        props.setProperty("useSSL", "false");
        props.setProperty("allowPublicKeyRetrieval", "true");
        props.setProperty("serverTimezone", "UTC");
        props.setProperty("characterEncoding", "utf8");
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "60000");
        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            throw new BusinessException("Connection test failed: " + e.getMessage());
        }
    }

    public void testConnection(String host, Integer port, String username,
                               String rawPassword, String databaseName) {
        try (Connection ignored = createConnection(host, port, username, rawPassword, databaseName)) {
            log.info("Connection test succeeded for {}:{}", host, port);
        } catch (SQLException e) {
            throw new BusinessException("Connection test failed: " + e.getMessage());
        }
    }

    public void testConnection(Long connectionId) {
        try (Connection ignored = getConnection(connectionId, null)) {
            log.info("Connection test succeeded for id={}", connectionId);
        } catch (SQLException e) {
            throw new BusinessException("Connection test failed: " + e.getMessage());
        }
    }

    public Connection getStreamingConnection(Long connectionId, String databaseName) {
        ConnectionConfig config = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException("Connection not found: " + connectionId));
        String db = databaseName != null && !databaseName.isBlank()
                ? databaseName
                : config.getDatabaseName();
        String url = buildStreamingJdbcUrl(config.getHost(), config.getPort(), db);
        String password = aesCryptoUtil.decrypt(config.getPassword());
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        if (password != null) {
            props.setProperty("password", password);
        }
        props.setProperty("useSSL", "false");
        props.setProperty("allowPublicKeyRetrieval", "true");
        props.setProperty("serverTimezone", "UTC");
        props.setProperty("characterEncoding", "utf8");
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "3600000");
        try {
            Connection conn = DriverManager.getConnection(url, props);
            conn.setReadOnly(true);
            return conn;
        } catch (SQLException e) {
            log.error("Failed to open streaming connection: {}", url, e);
            throw new BusinessException("Failed to connect: " + e.getMessage());
        }
    }

    private String buildJdbcUrl(String host, Integer port, String database) {
        StringBuilder sb = new StringBuilder("jdbc:mysql://");
        sb.append(host).append(":").append(port);
        if (database != null && !database.isBlank()) {
            sb.append("/").append(database);
        }
        sb.append("?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8");
        return sb.toString();
    }

    private String buildStreamingJdbcUrl(String host, Integer port, String database) {
        StringBuilder sb = new StringBuilder("jdbc:mysql://");
        sb.append(host).append(":").append(port);
        if (database != null && !database.isBlank()) {
            sb.append("/").append(database);
        }
        sb.append("?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
          .append("&characterEncoding=utf8&useCursorFetch=true");
        return sb.toString();
    }
}
