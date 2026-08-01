package com.gotogether.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a caller exceeds a rate limit (e.g. OTP requests per phone number per hour). */
public class RateLimitedException extends GoTogetherException {

    public RateLimitedException(String message) {
        super(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
