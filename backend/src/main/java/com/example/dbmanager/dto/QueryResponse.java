package com.example.dbmanager.dto;

import java.util.List;

public record QueryResponse(String executionId, List<StatementResult> results, long totalDurationMs) {
}
