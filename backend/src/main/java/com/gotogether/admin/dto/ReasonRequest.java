package com.gotogether.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** Generic {@code { reason }} body shared by every Admin enforcement endpoint that takes one (restrict, suspend, hide, force-cancel, suspend company) — API Spec Section 16. */
public record ReasonRequest(@NotBlank String reason) {
}
