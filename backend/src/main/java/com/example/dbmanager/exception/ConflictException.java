package com.example.dbmanager.exception;

/**
 * 资源状态冲突（HTTP 409）。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
