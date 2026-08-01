package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryExecuteRequest {

    @NotBlank(message = "SQL is required")
    private String sql;

    private String database;

    private Integer maxRows;

    private Integer timeoutSeconds;
}
