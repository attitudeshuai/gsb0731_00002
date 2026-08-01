package com.dbmanager.service;

import com.dbmanager.entity.ConnectionConfig;
import com.dbmanager.util.AesCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryCancelRegistry {

    private final AesCryptoUtil aesCryptoUtil;

    private final ConcurrentHashMap<String, RunningStatements> registry = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public void register(String requestId, Statement statement, Connection connection, String type,
                         ConnectionConfig config, String database) {
        if (cancelled.contains(requestId)) {
            try {
                statement.cancel();
            } catch (SQLException ignored) {
            }
            return;
        }
        RunningStatements rs = registry.computeIfAbsent(requestId,
                k -> new RunningStatements(connection, config, database));
        rs.add(statement, type);
    }

    public boolean cancel(String requestId) {
        cancelled.add(requestId);
        RunningStatements rs = registry.get(requestId);
        if (rs == null) {
            return false;
        }
        boolean anyCancelled = false;
        for (StatementEntry entry : rs.statements) {
            try {
                entry.statement.cancel();
                anyCancelled = true;
                log.info("Cancelled {} statement for request {}", entry.type, requestId);
            } catch (SQLException e) {
                log.warn("Failed to cancel {} statement for request {}: {}", entry.type, requestId, e.getMessage());
            }
        }
        Long threadId = rs.getThreadId();
        if (threadId != null && rs.config != null) {
            killQueryOnNewConnection(rs.config, rs.database, threadId, requestId);
        }
        return anyCancelled;
    }

    private void killQueryOnNewConnection(ConnectionConfig config, String database,
                                          long threadId, String requestId) {
        String url = "jdbc:mysql://" + config.getHost() + ":" + config.getPort()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8&connectTimeout=5000&socketTimeout=10000";
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        String password = aesCryptoUtil.decrypt(config.getPassword());
        if (password != null) {
            props.setProperty("password", password);
        }
        try (Connection killConn = DriverManager.getConnection(url, props);
             Statement killStmt = killConn.createStatement()) {
            killStmt.execute("KILL QUERY " + threadId);
            log.info("Issued KILL QUERY {} via separate connection for request {}", threadId, requestId);
        } catch (Exception e) {
            log.warn("KILL QUERY {} via separate connection failed: {}", threadId, e.getMessage());
        }
    }

    public boolean isCancelled(String requestId) {
        return requestId != null && cancelled.contains(requestId);
    }

    public void unregister(String requestId) {
        registry.remove(requestId);
        cancelled.remove(requestId);
    }

    public void unregister(String requestId, Statement statement) {
        RunningStatements rs = registry.get(requestId);
        if (rs != null) {
            rs.statements.removeIf(e -> e.statement == statement);
            if (rs.statements.isEmpty() && !cancelled.contains(requestId)) {
                registry.remove(requestId);
            }
        }
    }

    public boolean isRunning(String requestId) {
        return registry.containsKey(requestId);
    }

    private static class RunningStatements {
        final Connection connection;
        final ConnectionConfig config;
        final String database;
        final List<StatementEntry> statements = new CopyOnWriteArrayList<>();
        final AtomicLong threadId = new AtomicLong(0);

        RunningStatements(Connection connection, ConnectionConfig config, String database) {
            this.connection = connection;
            this.config = config;
            this.database = database;
        }

        void add(Statement statement, String type) {
            statements.add(new StatementEntry(statement, type));
            if (threadId.get() == 0) {
                try {
                    Object mysqlConn = connection.unwrap(Class.forName("com.mysql.cj.jdbc.JdbcConnection"));
                    Object session = mysqlConn.getClass().getMethod("getSession").invoke(mysqlConn);
                    Object id = session.getClass().getMethod("getThreadId").invoke(session);
                    if (id instanceof Number n) {
                        threadId.set(n.longValue());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Long getThreadId() {
            long id = threadId.get();
            return id > 0 ? id : null;
        }
    }

    private record StatementEntry(Statement statement, String type) { }
}
