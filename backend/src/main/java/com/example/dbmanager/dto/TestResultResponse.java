package com.example.dbmanager.dto;

public record TestResultResponse(boolean success, String message, long latencyMs) {
}
