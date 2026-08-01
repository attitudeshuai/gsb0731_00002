package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExportRequest {

    private String sql;

    private String database;

    @NotBlank(message = "Format is required")
    private String format;

    private String tableName;
}
