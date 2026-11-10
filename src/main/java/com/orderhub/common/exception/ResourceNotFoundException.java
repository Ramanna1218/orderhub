package com.orderhub.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s with id %s not found".formatted(resource, id));
    }
}
