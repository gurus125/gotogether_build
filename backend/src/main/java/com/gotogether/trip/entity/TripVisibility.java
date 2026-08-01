package com.gotogether.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mirrors the Postgres {@code trip_visibility} enum (V1 migration). Only {@code PUBLIC} is exercised at MVP — private trips are not a documented Phase 2 flow, but the column/enum exist in the approved schema so it's modeled faithfully. */
public enum TripVisibility {
    PUBLIC,
    PRIVATE;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<TripVisibility, String> {
        @Override
        public String convertToDatabaseColumn(TripVisibility attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public TripVisibility convertToEntityAttribute(String dbData) {
            return dbData == null ? null : TripVisibility.valueOf(dbData.toUpperCase());
        }
    }
}
