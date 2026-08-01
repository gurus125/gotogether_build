package com.gotogether.membership.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code membership_status} enum (V1 migration) and Chapter 3 Section 3.4's Trip Membership lifecycle — a derived state, never independently editable (see {@link TripMember}'s class doc). */
public enum MembershipStatus {
    JOINED,
    LEFT,
    REMOVED,
    COMPLETED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<MembershipStatus, String> {
        @Override
        public String convertToDatabaseColumn(MembershipStatus attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public MembershipStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : MembershipStatus.valueOf(dbData.toUpperCase());
        }
    }
}
