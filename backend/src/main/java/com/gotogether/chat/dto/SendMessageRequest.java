package com.gotogether.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code POST /chat-rooms/{id}/messages} (API Spec Section 10). {@code type}
 * must be {@code "TEXT"} this pass — see {@code ChatService.sendMessage}'s
 * doc for the other {@code MessageType} values, all rejected for now with a
 * clear "not supported yet" error rather than silently coercing to text.
 * {@code attachment_ids} is intentionally absent — attachments are deferred
 * (see {@code ChatService}'s class doc).
 */
public record SendMessageRequest(
        @NotBlank String type,
        @Size(max = 2000) String body,
        UUID replyToMessageId) {
}
