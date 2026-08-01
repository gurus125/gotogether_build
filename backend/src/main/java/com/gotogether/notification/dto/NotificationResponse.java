package com.gotogether.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Mirrors {@code notification.entity.Notification} (API Spec Section 13). */
public record NotificationResponse(
        UUID id,
        UUID actorId,
        String type,
        String entityType,
        UUID entityId,
        String title,
        String body,
        String priority,
        String status,
        boolean unread,
        OffsetDateTime createdAt) {
}
