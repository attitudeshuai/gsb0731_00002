package com.example.dbmanager.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询执行登记：executionId → 执行记录（连接 id、MySQL 服务端连接 id、执行状态）。
 * 取消接口据此找到目标执行并 KILL 数据库侧查询；执行完成后移除登记。
 */
@Component
public class QueryExecutionRegistry {

    public enum Status {
        RUNNING,
        DONE,
        CANCELLED
    }

    /**
     * 一次查询执行的登记信息。
     */
    public static class QueryExecution {

        private final String executionId;
        private final Long connectionId;
        private volatile Long serverConnectionId;
        private volatile Status status = Status.RUNNING;

        QueryExecution(String executionId, Long connectionId) {
            this.executionId = executionId;
            this.connectionId = connectionId;
        }

        public String getExecutionId() {
            return executionId;
        }

        public Long getConnectionId() {
            return connectionId;
        }

        public Long getServerConnectionId() {
            return serverConnectionId;
        }

        void setServerConnectionId(Long serverConnectionId) {
            this.serverConnectionId = serverConnectionId;
        }

        public Status getStatus() {
            return status;
        }

        void setStatus(Status status) {
            this.status = status;
        }
    }

    private final Map<String, QueryExecution> executions = new ConcurrentHashMap<>();

    public QueryExecution register(String executionId, Long connectionId) {
        QueryExecution execution = new QueryExecution(executionId, connectionId);
        executions.put(executionId, execution);
        return execution;
    }

    public Optional<QueryExecution> find(String executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    public void updateServerConnectionId(String executionId, long serverConnectionId) {
        QueryExecution execution = executions.get(executionId);
        if (execution != null) {
            execution.setServerConnectionId(serverConnectionId);
        }
    }

    public void markCancelled(String executionId) {
        QueryExecution execution = executions.get(executionId);
        if (execution != null) {
            execution.setStatus(Status.CANCELLED);
        }
    }

    public boolean isCancelled(String executionId) {
        QueryExecution execution = executions.get(executionId);
        return execution != null && execution.getStatus() == Status.CANCELLED;
    }

    /**
     * 执行结束：标记 DONE 并移除登记（登记只保留运行中的执行）。
     */
    public void unregister(String executionId) {
        QueryExecution execution = executions.remove(executionId);
        if (execution != null && execution.getStatus() == Status.RUNNING) {
            execution.setStatus(Status.DONE);
        }
    }
}
