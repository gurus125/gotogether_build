package com.gotogether.user.dto;

import com.gotogether.user.entity.VerificationType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code GET /admin/verifications} (Phase 8, API Spec Section 16) — unlike {@link VerificationResponse} (a self-service "my own submissions" shape with no {@code userId}), this is the cross-user Moderator queue row, so it must name whose submission it is. */
public record VerificationQueueEntry(
        UUID verificationId, UUID userId, VerificationType type, String documentType,
        String documentImageUrl, OffsetDateTime createdAt) {
}
