package com.dbtool.backend.service;

import com.dbtool.backend.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight SQL statements so they can be cancelled server-side.
 * <p>
 * The client supplies (or receives) a query token; while the statement runs it is
 * registered here. A cancel request looks up the token and calls
 * {@link Statement#cancel()} on the actual JDBC statement — the DB stops execution,
 * not merely the HTTP connection.
 */
@Component
public class RunningQueryRegistry {

    private static final Logger log = LoggerFactory.getLogger(RunningQueryRegistry.class);

    private final Map<String, Statement> running = new ConcurrentHashMap<>();

    public void register(String token, Statement statement) {
        if (token != null && !token.isBlank()) {
            running.put(token, statement);
        }
    }

    public void unregister(String token) {
        if (token != null) {
            running.remove(token);
        }
    }

    /** Cancels the statement bound to the token. Returns true if a statement was found. */
    public boolean cancel(String token) {
        Statement stmt = running.get(token);
        if (stmt == null) {
            return false;
        }
        try {
            stmt.cancel();
            log.info("Cancelled running query token={}", token);
            return true;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to cancel query: " + e.getMessage());
        }
    }
}
