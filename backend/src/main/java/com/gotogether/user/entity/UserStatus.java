package com.gotogether.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code user_status} enum (V1 migration). */
public enum UserStatus {
    REGISTERED,
    VERIFIED,
    RESTRICTED,
    SUSPENDED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<UserStatus, String> {
        @Override
        public String convertToDatabaseColumn(UserStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public UserStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : UserStatus.valueOf(dbData.toUpperCase());
        }
    }
}
