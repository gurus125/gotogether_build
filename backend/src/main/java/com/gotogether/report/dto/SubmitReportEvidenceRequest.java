package com.gotogether.report.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /reports/{id}/evidence} (API Spec Section 15). Genuine
 * multipart file upload has no backing endpoint anywhere in this codebase
 * yet (no object-storage upload flow exists — see {@code
 * CompanyApplyScreen}'s identical "manual reference field" note); {@code
 * storageKey} is therefore a caller-supplied reference string, matching the
 * exact deferral already made for Company registration documents in Phase 7.
 */
public record SubmitReportEvidenceRequest(@NotBlank String storageKey, String mimeType, Integer fileSizeBytes) {
}
