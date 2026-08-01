package com.dbmanager.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class HistoryResponse {

    private Long id;
    private Long connectionId;
    private String sqlText;
    private Long executionTimeMs;
    private Integer rowCount;
    private boolean success;
    private String errorMessage;
    private Instant executedAt;
    private Long userId;
    private Instant createdAt;
    private Instant updatedAt;
}
