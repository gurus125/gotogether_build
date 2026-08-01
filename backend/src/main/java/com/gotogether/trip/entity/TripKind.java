package com.gotogether.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code trip_kind} enum (V1 migration). Chapter 1
 * Section 13's "asymmetric marketplace": Community trips are peer-created
 * with no in-app payment; Verified Partner trips are business-created with
 * fixed pricing and in-app deposit/payment. Both share this one lifecycle and
 * table ("one lifecycle, one table" — Chapter 1 Section 21/Chapter 3).
 *
 * <p>Phase 2 only built the {@code COMMUNITY} path end-to-end. Phase 7 adds
 * {@code VERIFIED_PARTNER}: it requires an active {@code company_users}
 * membership plus the company's status being {@code VERIFIED} (see {@code
 * CompanyService#assertActiveMember}/{@code #assertVerified}, called from
 * {@link com.gotogether.trip.service.TripService#createDraft}) to satisfy
 * {@code chk_trips_company_id_by_kind}.
 */
public enum TripKind {
    COMMUNITY,
    VERIFIED_PARTNER;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<TripKind, String> {
        @Override
        public String convertToDatabaseColumn(TripKind attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public TripKind convertToEntityAttribute(String dbData) {
            return dbData == null ? null : TripKind.valueOf(dbData.toUpperCase());
        }
    }
}
