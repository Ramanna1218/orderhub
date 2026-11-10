package com.orderhub.common.exception;

/** Base type for requests that are well-formed but conflict with current state (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
