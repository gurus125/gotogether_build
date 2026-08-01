package com.gotogether.joinrequest.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code join_request_status} enum (V1 migration) and Chapter 3 Section 3.3's Join Request lifecycle exactly. */
public enum JoinRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED,
    WAITING_LIST;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<JoinRequestStatus, String> {
        @Override
        public String convertToDatabaseColumn(JoinRequestStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public JoinRequestStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : JoinRequestStatus.valueOf(dbData.toUpperCase());
        }
    }
}
