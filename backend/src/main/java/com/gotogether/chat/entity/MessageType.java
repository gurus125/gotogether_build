package com.gotogether.chat.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors Postgres {@code message_type} (V1 migration) — the full value set
 * the DB already supports. This module (core Trip Chat pass) only ever
 * creates {@link #TEXT} rows; {@link #IMAGE}/{@link #VOICE}/{@link
 * #DOCUMENT}/{@link #LOCATION} need the attachment-upload endpoint (deferred
 * — no {@code StorageService} wired yet, same gap as trip/profile image
 * upload), {@link #POLL}/{@link #EXPENSE} need dedicated business logic
 * beyond a plain message row (confirmed in-scope by Business Rules Module C
 * but not built this pass), and {@link #SYSTEM} is modelled for completeness
 * only — nothing in this pass generates one. See {@code ChatService}'s class
 * doc for the full deferred-scope list.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    VOICE,
    DOCUMENT,
    LOCATION,
    POLL,
    EXPENSE,
    SYSTEM;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<MessageType, String> {
        @Override
        public String convertToDatabaseColumn(MessageType attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public MessageType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : MessageType.valueOf(dbData.toUpperCase());
        }
    }
}
