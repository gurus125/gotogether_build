package com.gotogether.company.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full self-service view of a company — {@code POST /companies/apply}'s
 * response and (composed with {@link com.gotogether.company.entity.CompanyUser})
 * anything a company's own staff reads about their own account. Includes
 * internal-only fields ({@code legalName}, {@code registrationNumber}, {@code
 * gstNumber}) that {@link CompanyProfileResponse} (the public view) never
 * exposes.
 */
public record CompanyResponse(
        UUID id,
        String displayName,
        String legalName,
        String registrationNumber,
        String gstNumber,
        String logoUrl,
        String description,
        String websiteUrl,
        String supportEmail,
        String supportPhone,
        String cancellationPolicy,
        String status,
        OffsetDateTime suspendedAt,
        String suspensionReason,
        OffsetDateTime createdAt) {
}
