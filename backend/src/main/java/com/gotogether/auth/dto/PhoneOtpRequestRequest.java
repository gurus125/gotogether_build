package com.gotogether.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** E.164-formatted phone number (Chapter 1 Section 14), e.g. {@code +919876543210}. */
public record PhoneOtpRequestRequest(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone number must be in E.164 format") String phoneNumber) {}
