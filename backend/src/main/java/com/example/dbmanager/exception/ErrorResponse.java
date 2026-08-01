package com.example.dbmanager.exception;

import java.time.Instant;

/**
 * 统一错误响应体：{timestamp, status, error, message, path}。
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
