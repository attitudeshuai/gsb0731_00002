package com.example.dbmanager.dto;

import com.example.dbmanager.service.ExportTaskManager;

import java.time.Instant;

/**
 * 导出任务状态（时间字段为 UTC）。
 */
public record ExportTaskResponse(
        String taskId,
        Long connectionId,
        String databaseName,
        String tableName,
        String format,
        String status,
        long rowsExported,
        Long estimatedRows,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt) {

    public static ExportTaskResponse of(ExportTaskManager.ExportTask task) {
        return new ExportTaskResponse(
                task.getTaskId(),
                task.getConnectionId(),
                task.getDatabase(),
                task.getTable(),
                task.getFormat(),
                task.getStatus().name(),
                task.getRowsExported(),
                task.getEstimatedRows(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getFinishedAt());
    }
}
