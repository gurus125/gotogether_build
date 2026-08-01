package com.gotogether.membership.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code attendance_status} enum — null until the trip Completes, then set via {@code PATCH /trips/{id}/members/{user_id}/attendance} (API Spec Section 9). */
public enum AttendanceStatus {
    ATTENDED,
    NO_SHOW;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<AttendanceStatus, String> {
        @Override
        public String convertToDatabaseColumn(AttendanceStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public AttendanceStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : AttendanceStatus.valueOf(dbData.toUpperCase());
        }
    }
}
