package com.gotogether.company.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code company_verification_status} enum (V1
 * migration) — structurally parallel to {@code user.entity.VerificationStatus}
 * but for businesses (DB Schema Part 3 Section 2's {@code
 * company_verifications} note), since the two have entirely different
 * document types and reviewer-of-record expectations.
 */
public enum CompanyVerificationStatus {
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<CompanyVerificationStatus, String> {
        @Override
        public String convertToDatabaseColumn(CompanyVerificationStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public CompanyVerificationStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : CompanyVerificationStatus.valueOf(dbData.toUpperCase());
        }
    }
}
