package com.gotogether.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code POST /reports} / {@code POST /reports/emergency} (API Spec Section 15). {@code entityType}/{@code reason} are validated against {@code report_entity_type}/{@code report_reason} in the service layer (a bad value throws {@code UnprocessableEntityException("INVALID_ENTITY_TYPE: ...")}), not via {@code @Pattern}, so the 422 body can name the exact offending value. */
public record CreateReportRequest(
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String reason,
        @Size(max = 2000) String details) {
}
