package com.example.dbmanager.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建 / 更新连接。password 为明文：不传表示不修改，传空串表示清空，非空则 AES 加密存储。
 */
public record ConnectionRequest(
        @NotBlank(message = "name 不能为空") String name,
        Long groupId,
        @NotBlank(message = "host 不能为空") String host,
        @NotNull(message = "port 不能为空") @Min(1) @Max(65535) Integer port,
        String username,
        String password,
        @JsonAlias("databaseName") String defaultDatabase,
        @Min(1) @Max(3600) Integer queryTimeoutSeconds) {
}
