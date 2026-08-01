package com.gotogether.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code account_role} enum (V1 migration). */
public enum AccountRole {
    INDIVIDUAL,
    MODERATOR,
    ADMIN;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<AccountRole, String> {
        @Override
        public String convertToDatabaseColumn(AccountRole attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public AccountRole convertToEntityAttribute(String dbData) {
            return dbData == null ? null : AccountRole.valueOf(dbData.toUpperCase());
        }
    }
}
