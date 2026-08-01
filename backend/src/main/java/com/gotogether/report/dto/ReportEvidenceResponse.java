package com.gotogether.report.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportEvidenceResponse(
        UUID id, UUID reportId, String storageKey, String mimeType, Integer fileSizeBytes, OffsetDateTime createdAt) {
}
