package com.gotogether.admin.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors the Postgres {@code audit_action} enum (V1 migration) — the fixed
 * set of moderator/admin actions this codebase logs. Notably has no {@code
 * trust_score_unfrozen} value distinct from {@link #TRUST_SCORE_FROZEN} —
 * {@code AdminService} reuses this same value for both the freeze and the
 * unfreeze event (distinguishing them by reading {@code new_value} on the
 * row itself), since the DB enum only defines the one value and adding a
 * second is a schema change outside this pass's scope. Also has no
 * message-moderation value at all ({@code message_hidden}/{@code
 * message_removed}) — see {@code AdminService}'s class doc for why message
 * content moderation is a flagged, not-yet-wired gap in this pass.
 */
public enum AuditAction {
    USER_RESTRICTED,
    USER_SUSPENDED,
    USER_REMOVED,
    TRIP_HIDDEN,
    TRIP_FORCE_CANCELLED,
    REVIEW_HIDDEN,
    REVIEW_REMOVED,
    COMPANY_VERIFIED,
    COMPANY_SUSPENDED,
    VERIFICATION_APPROVED,
    VERIFICATION_REJECTED,
    TRUST_SCORE_FROZEN,
    ROLE_ASSIGNED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<AuditAction, String> {
        @Override
        public String convertToDatabaseColumn(AuditAction attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public AuditAction convertToEntityAttribute(String dbData) {
            return dbData == null ? null : AuditAction.valueOf(dbData.toUpperCase());
        }
    }
}
