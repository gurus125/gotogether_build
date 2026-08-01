package com.gotogether.common;

/**
 * The fixed set of table names valid for the "loose reference" columns used
 * across {@code notifications.entity_type}, {@code analytics_events.entity_type},
 * and {@code audit_logs.entity_type} (DB Schema Part 2 Section 4 / Part 3
 * Section 3 / Part 3's own review: "worth a single shared application-level
 * validation utility so the known-valid entity_type values is maintained
 * once, not three times").
 *
 * <p>Every code path that writes one of those three {@code entity_type}
 * columns must validate against this enum rather than inventing its own
 * string. {@code reports.entity_type} is a separate, stricter DB-level enum
 * ({@code report_entity_type}) and does not use this class.
 */
public enum ReferencedEntityType {
    TRIPS("trips"),
    JOIN_REQUESTS("join_requests"),
    MESSAGES("messages"),
    REVIEWS("reviews"),
    VERIFICATIONS("verifications"),
    NOTIFICATIONS("notifications");

    private final String tableName;

    ReferencedEntityType(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
