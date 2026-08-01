package com.gotogether.user.dto;

import com.gotogether.user.entity.RejectionReason;
import com.gotogether.user.entity.VerificationStatus;
import com.gotogether.user.entity.VerificationType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VerificationResponse(
        UUID id,
        VerificationType type,
        VerificationStatus status,
        RejectionReason rejectionReason,
        OffsetDateTime createdAt,
        OffsetDateTime reviewedAt) {}
