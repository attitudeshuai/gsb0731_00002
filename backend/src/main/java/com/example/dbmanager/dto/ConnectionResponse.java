package com.example.dbmanager.dto;

import java.time.Instant;

/**
 * 连接信息响应，永不包含密码（仅 hasPassword 标记是否已设置）。
 */
public record ConnectionResponse(
        Long id,
        String name,
        Long groupId,
        String groupName,
        String host,
        Integer port,
        String username,
        String defaultDatabase,
        String databaseName,
        Integer queryTimeoutSeconds,
        boolean hasPassword,
        Instant createdAt,
        Instant updatedAt) {
}
