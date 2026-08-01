package com.gotogether.company.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code company_user_role} enum (V1 migration). The
 * schema supports {@code owner}/{@code manager}/{@code support} structurally
 * (DB Schema Part 3 Section 2's {@code company_users} note: "resolving Business
 * Rules Operations Module A's Open Question 1 in favor of MVP-supporting
 * multiple admins structurally now... even though the product/business-rule
 * documents currently constrain launch to a single admin per company"), but
 * {@code CompanyService} enforces the MVP product rule of exactly one active
 * {@code OWNER} row per company at the application layer — see {@code
 * CompanyService#inviteStaff}'s {@code MULTI_ADMIN_NOT_ENABLED} check.
 */
public enum CompanyUserRole {
    OWNER,
    MANAGER,
    SUPPORT;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<CompanyUserRole, String> {
        @Override
        public String convertToDatabaseColumn(CompanyUserRole attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public CompanyUserRole convertToEntityAttribute(String dbData) {
            return dbData == null ? null : CompanyUserRole.valueOf(dbData.toUpperCase());
        }
    }
}
