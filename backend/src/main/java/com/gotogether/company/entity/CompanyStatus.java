package com.gotogether.company.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code company_status} enum (V1 migration) — Chapter 3
 * Section 3.11's Company lifecycle: {@code application_submitted -> under_review
 * -> verified}, with {@code suspended}/{@code removed} as enforcement states and
 * {@code rejected} as the terminal failure of the initial application (Operations
 * Module A). Only a Moderator/Admin action can move a company past {@code
 * under_review} — see {@code CompanyService}'s class doc for why that transition
 * has no real endpoint yet in this pass (Phase 8's {@code admin} module).
 */
public enum CompanyStatus {
    APPLICATION_SUBMITTED,
    UNDER_REVIEW,
    VERIFIED,
    SUSPENDED,
    REJECTED,
    REMOVED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<CompanyStatus, String> {
        @Override
        public String convertToDatabaseColumn(CompanyStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public CompanyStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : CompanyStatus.valueOf(dbData.toUpperCase());
        }
    }
}
