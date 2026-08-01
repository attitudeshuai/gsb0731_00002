package com.dbmanager.service;

import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ActiveStatementRegistry {

    private final ConcurrentMap<String, Statement> activeStatements = new ConcurrentHashMap<>();
    private final Set<String> cancelledExecutions = ConcurrentHashMap.newKeySet();
    private final Set<String> timedOutExecutions = ConcurrentHashMap.newKeySet();

    public void register(String executionId, Statement statement) {
        if (executionId != null) {
            activeStatements.put(executionId, statement);
        }
    }

    public void unregister(String executionId, Statement statement) {
        if (executionId != null) {
            activeStatements.remove(executionId, statement);
        }
    }

    public void cancel(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        cancelledExecutions.add(executionId);
        Statement stmt = activeStatements.get(executionId);
        if (stmt != null) {
            try {
                stmt.cancel();
            } catch (SQLException ignored) {
            }
        }
    }

    public boolean isCancelled(String executionId) {
        return executionId != null && cancelledExecutions.contains(executionId);
    }

    public boolean consumeCancelled(String executionId) {
        return executionId != null && cancelledExecutions.remove(executionId);
    }

    public void markTimedOut(String executionId) {
        if (executionId != null) {
            timedOutExecutions.add(executionId);
        }
    }

    public boolean isTimedOut(String executionId) {
        return executionId != null && timedOutExecutions.contains(executionId);
    }

    public boolean consumeTimedOut(String executionId) {
        return executionId != null && timedOutExecutions.remove(executionId);
    }
}
