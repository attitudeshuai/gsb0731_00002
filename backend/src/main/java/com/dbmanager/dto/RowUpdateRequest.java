package com.dbmanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RowUpdateRequest {

    @NotNull
    private Long connectionId;

    private String databaseName;

    @NotNull
    private String tableName;

    private Map<String, Object> primaryKey;

    private Map<String, Object> data;

    private Map<String, Object> newRow;

    private List<Map<String, Object>> batchRows;
}
