package com.shoppulse.common;

/** A resource that does not exist: maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String type, Object id) {
        return new NotFoundException(type + " " + id + " not found");
    }
}
