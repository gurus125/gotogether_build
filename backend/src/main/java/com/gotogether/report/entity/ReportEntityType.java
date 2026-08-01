package com.gotogether.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code report_entity_type} enum (V1 migration) — a
 * fixed, stricter enum for {@code reports.entity_type} specifically, unlike
 * {@code notifications.entity_type}/{@code audit_logs.entity_type}'s loose
 * TEXT (see {@code common.ReferencedEntityType}'s own doc for why those two
 * stayed loose while this one didn't: DB Schema Part 3's own review called
 * Reports "a structurally important table worth enforcing").
 */
public enum ReportEntityType {
    USER,
    TRIP,
    MESSAGE,
    REVIEW,
    COMPANY;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReportEntityType, String> {
        @Override
        public String convertToDatabaseColumn(ReportEntityType attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReportEntityType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportEntityType.valueOf(dbData.toUpperCase());
        }
    }
}
