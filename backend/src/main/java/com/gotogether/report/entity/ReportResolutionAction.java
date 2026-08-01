package com.gotogether.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors {@code reports.resolution_action} — a plain TEXT column with an
 * app-level {@code chk_reports_resolution_action} CHECK constraint (DB
 * Schema Part 3), <b>not</b> a native Postgres enum type like {@link
 * ReportStatus}/{@link ReportPriority}/{@link ReportReason}/{@link
 * ReportEntityType} are. Its converter is therefore a plain lowercase-string
 * {@link AttributeConverter} with no {@code NativeEnumJdbcType}/{@code
 * columnDefinition} pairing — using that pairing here would tell Hibernate
 * to bind against a Postgres enum type that doesn't exist for this column.
 *
 * <p>Directly maps to Operations Module B's Warning &amp; Enforcement Ladder:
 * {@link #WARNED} (Warning tier), {@link #RESTRICTED} (Restricted tier),
 * {@link #SUSPENDED} (Suspended tier), {@link #REMOVED} (Permanently removed
 * tier) — plus {@link #DISMISSED} (report found unsubstantiated) and {@link
 * #CONTENT_REMOVED} (a message/review/listing taken down without an
 * account-level enforcement action). See {@code AdminService}'s class doc
 * for exactly which of these require {@code ADMIN} over {@code MODERATOR}.
 */
public enum ReportResolutionAction {
    DISMISSED,
    WARNED,
    RESTRICTED,
    SUSPENDED,
    REMOVED,
    CONTENT_REMOVED;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ReportResolutionAction, String> {
        @Override
        public String convertToDatabaseColumn(ReportResolutionAction attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ReportResolutionAction convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ReportResolutionAction.valueOf(dbData.toUpperCase());
        }
    }
}
