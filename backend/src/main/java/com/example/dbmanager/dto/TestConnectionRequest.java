package com.example.dbmanager.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 不保存配置直接测试连接的请求体。
 */
public record TestConnectionRequest(
        @NotBlank(message = "host 不能为空") String host,
        @NotNull(message = "port 不能为空") @Min(1) @Max(65535) Integer port,
        String username,
        String password,
        @JsonAlias("databaseName") String defaultDatabase) {
}
