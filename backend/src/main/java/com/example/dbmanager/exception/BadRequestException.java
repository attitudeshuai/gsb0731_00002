package com.example.dbmanager.exception;

/**
 * 请求参数非法（HTTP 400）。
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
