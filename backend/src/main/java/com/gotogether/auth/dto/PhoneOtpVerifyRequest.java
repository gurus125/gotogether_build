package com.gotogether.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneOtpVerifyRequest(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone number must be in E.164 format") String phoneNumber,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits") String code) {}
