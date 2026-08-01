package com.gotogether.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id, UUID actorId, String action, String entityType, UUID entityId,
        String oldValue, String newValue, OffsetDateTime createdAt) {
}
