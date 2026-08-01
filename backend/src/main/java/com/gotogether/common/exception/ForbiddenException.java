package com.gotogether.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when the caller is authenticated but not permitted to perform the action. */
public class ForbiddenException extends GoTogetherException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}
