package com.gotogether.chat.dto;

import jakarta.validation.constraints.Size;

/** {@code PATCH /messages/{id}/pin} (API Spec Section 10) — {@code null} clears the pin. */
public record PinMessageRequest(@Size(max = 50) String pinCategory) {
}
