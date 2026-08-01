package com.example.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(
        @NotBlank(message = "database 不能为空") String database,
        @NotBlank(message = "sql 不能为空") String sql,
        String executionId) {
}
