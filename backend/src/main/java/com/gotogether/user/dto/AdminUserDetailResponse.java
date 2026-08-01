package com.gotogether.user.dto;

import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /admin/users/{id}} (API Spec Section 16: "Full account view
 * incl. Trust Score history"). This module only fills the account-identity
 * half; {@code admin.service.AdminService} composes the Trust Score half on
 * top (see {@code ReportService}'s class doc for why cross-module
 * composition for the {@code admin} module lives in {@code AdminService}
 * rather than here — {@code user} cannot depend on {@code trust} without
 * cycling, since {@code trust} already depends on {@code user}).
 */
public record AdminUserDetailResponse(
        UUID id, String phoneNumber, String email, UserStatus status, VerificationLevel verificationLevel,
        AccountRole role, OffsetDateTime createdAt, OffsetDateTime lastLoginAt, OffsetDateTime deactivatedAt,
        OffsetDateTime deletedAt, List<VerificationResponse> verifications) {
}
