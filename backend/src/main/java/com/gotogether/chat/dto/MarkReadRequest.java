package com.gotogether.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code POST /chat-rooms/{id}/read} (API Spec Section 10) — read receipt. */
public record MarkReadRequest(@NotNull UUID upToMessageId) {
}
