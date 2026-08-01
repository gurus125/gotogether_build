package com.gotogether.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request is well-formed (passes bean validation) but violates
 * a business rule that can't be expressed as a field-level annotation — e.g.
 * "start_date must be at least tomorrow," "a trip must have a description
 * before it can be published," "a cancellation must include a reason." Maps
 * to HTTP 422, matching the API Specification's error model (Section 21).
 */
public class UnprocessableEntityException extends GoTogetherException {

    public UnprocessableEntityException(String message) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
