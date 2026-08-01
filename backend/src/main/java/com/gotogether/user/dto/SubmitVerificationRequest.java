package com.gotogether.user.dto;

import com.gotogether.user.entity.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * For {@code GOVERNMENT_ID} (the only type submitted through this endpoint —
 * phone/email are auto-approved as a side effect of the auth flow itself,
 * see {@code AuthService}). {@code documentType} matches Business Rules
 * Module 1 Section 4 (aadhaar / passport / driving_licence).
 */
public record SubmitVerificationRequest(
        @NotNull VerificationType type,
        @NotBlank String documentType,
        @NotBlank String documentImageUrl) {}
