package com.dbmanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class RowUpdateRequest {

    @NotNull(message = "Primary key is required")
    private Map<String, Object> primaryKey;

    @NotNull(message = "Row data is required")
    private Map<String, Object> row;
}
