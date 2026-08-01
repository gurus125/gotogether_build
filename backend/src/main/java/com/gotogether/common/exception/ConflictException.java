package com.gotogether.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request is individually valid but conflicts with current
 * state — e.g. a trip is already full, a join request already exists, a
 * duplicate review is attempted. Maps to HTTP 409.
 */
public class ConflictException extends GoTogetherException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
