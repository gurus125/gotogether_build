package com.gotogether.common.exception;

/**
 * Machine-readable error codes for the API's uniform error envelope
 * (Backend Architecture / API Specification: "one error envelope with
 * machine-readable codes"). Generic, cross-cutting codes live here in
 * common; each module adds its own domain-specific codes to its own enum
 * (e.g. a future {@code TripErrorCode.TRIP_FULL}) as that module is built —
 * this file is intentionally not a dumping ground for every future code.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    RESOURCE_NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    UNAUTHORIZED,
    RATE_LIMITED,
    INTERNAL_ERROR
}
