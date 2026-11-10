package com.orderhub.common.exception;

public class DuplicateResourceException extends ConflictException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
