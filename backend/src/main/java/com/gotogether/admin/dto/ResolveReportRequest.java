package com.gotogether.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /admin/reports/{id}/resolve} (API Spec Section 16): {@code { resolution_action, resolution }}. */
public record ResolveReportRequest(@NotBlank String resolutionAction, String resolution) {
}
