package com.gotogether.report.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code { report }} shape returned by every Report-module endpoint (API Spec Sections 15-16). */
public record ReportResponse(
        UUID id,
        UUID reporterId,
        String entityType,
        UUID entityId,
        String reason,
        String details,
        String status,
        String priority,
        UUID assignedModeratorId,
        String resolution,
        String resolutionAction,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt) {
}
