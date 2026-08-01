package com.gotogether.company.dto;

import java.util.UUID;

/**
 * {@code GET /companies/{id}} (API Specification Section 14) — the public
 * Company Profile (Operations Module A: "Distinct from an individual Profile
 * — shows business name/logo, Verified Partner badge, an aggregate
 * traveller-satisfaction rating..., past trips run, a published cancellation
 * policy, and business contact information, shown publicly since a Company
 * has no personal-privacy concern unlike an individual traveller"). Never
 * includes {@code legalName}/{@code registrationNumber}/{@code gstNumber} —
 * those are verification-only fields, see {@link CompanyResponse}.
 *
 * <p>{@code aggregateRating} is {@code null} until at least one Published
 * review exists against one of this company's trips (see {@code
 * CompanyService#getPublicProfile}) — never fabricated as {@code 0}, which
 * would misrepresent "no data yet" as "worst possible rating."
 */
public record CompanyProfileResponse(
        UUID id,
        String displayName,
        String logoUrl,
        String description,
        String websiteUrl,
        String supportEmail,
        String supportPhone,
        String cancellationPolicy,
        String status,
        Double aggregateRating,
        int tripsCompletedCount) {
}
