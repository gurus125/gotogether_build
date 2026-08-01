package com.gotogether.chat.dto;

/** {@code PATCH /chat-rooms/{id}/mute} (API Spec Section 10, Chapter 3 Section 3.5) — per-user, notification-only. */
public record MuteRequest(boolean isMuted) {
}
