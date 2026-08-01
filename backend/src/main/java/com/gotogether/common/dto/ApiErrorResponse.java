package com.gotogether.common.dto;

import com.gotogether.common.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uniform error envelope returned by every failed API call, per the API
 * Specification's error-handling section. {@code fieldErrors} is populated
 * only for {@link com.gotogether.common.exception.ErrorCode#VALIDATION_ERROR}.
 */
public record ApiErrorResponse(
        ErrorCode code,
        String message,
        OffsetDateTime timestamp,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}

    public static ApiErrorResponse of(ErrorCode code, String message, String path) {
        return new ApiErrorResponse(code, message, OffsetDateTime.now(), path, List.of());
    }

    public static ApiErrorResponse ofValidation(String message, String path, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(ErrorCode.VALIDATION_ERROR, message, OffsetDateTime.now(), path, fieldErrors);
    }
}
