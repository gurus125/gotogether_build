package com.gotogether.company.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of {@code GET /companies/me/staff} / the response of {@code POST /companies/me/staff}. */
public record CompanyUserResponse(UUID id, UUID companyId, UUID userId, String role, String status, OffsetDateTime createdAt) {
}
