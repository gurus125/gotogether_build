package com.gotogether.company.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /companies/me/staff} (API Specification Section 14). {@code
 * role} is one of {@code manager}/{@code support} — requesting {@code owner}
 * always fails with {@code 409 MULTI_ADMIN_NOT_ENABLED} (Operations Module
 * A's single-active-owner MVP cap), so it's accepted here as a plain string
 * rather than a Java enum specifically to let {@code CompanyService} return
 * that clean business-rule error instead of a generic 400 deserialization
 * failure for an "invalid" enum value that is actually just not allowed yet.
 */
public record InviteStaffRequest(@NotNull UUID userId, @NotNull String role) {
}
