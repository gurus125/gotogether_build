package com.gotogether.trust.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code trust_level} enum (V1 migration) and Trust & Discovery Module A's Trust Score Scale table. */
public enum TrustLevel {
    EXCELLENT,
    GOOD,
    BUILDING,
    CAUTION,
    RESTRICTED_TRIGGER;

    /** Module A's Trust Score Scale: 9.0-10 Excellent, 7.5-8.9 Good, 6.0-7.4 Building, 4.0-5.9 Caution, &lt;4.0 Restricted-triggering. */
    public static TrustLevel forScore(double score) {
        if (score >= 9.0) return EXCELLENT;
        if (score >= 7.5) return GOOD;
        if (score >= 6.0) return BUILDING;
        if (score >= 4.0) return CAUTION;
        return RESTRICTED_TRIGGER;
    }

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<TrustLevel, String> {
        @Override
        public String convertToDatabaseColumn(TrustLevel attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public TrustLevel convertToEntityAttribute(String dbData) {
            return dbData == null ? null : TrustLevel.valueOf(dbData.toUpperCase());
        }
    }
}
