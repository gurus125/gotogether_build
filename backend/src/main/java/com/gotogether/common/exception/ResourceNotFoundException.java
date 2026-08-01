package com.gotogether.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a requested entity does not exist (or is not visible to the caller). */
public class ResourceNotFoundException extends GoTogetherException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String entityName, Object id) {
        return new ResourceNotFoundException(entityName + " not found: " + id);
    }
}
