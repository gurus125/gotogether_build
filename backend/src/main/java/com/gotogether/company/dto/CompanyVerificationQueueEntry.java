package com.gotogether.company.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code GET /admin/companies} (Phase 8, API Spec Section 16) — the business-verification review queue row shape. */
public record CompanyVerificationQueueEntry(
        UUID verificationId,
        UUID companyId,
        String companyDisplayName,
        String submittedDocuments,
        String status,
        boolean reverification,
        OffsetDateTime createdAt) {
}
