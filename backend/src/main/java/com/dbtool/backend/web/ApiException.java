package com.dbtool.backend.web;

import org.springframework.http.HttpStatus;

/** Application-level exception carrying an HTTP status. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
