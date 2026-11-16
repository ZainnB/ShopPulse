package com.shoppulse.common;

/** A rule the caller broke: maps to HTTP 409 Conflict. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
