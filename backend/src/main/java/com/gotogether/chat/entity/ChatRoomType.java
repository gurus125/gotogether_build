package com.gotogether.chat.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors Postgres {@code chat_room_type} (V1 migration). Only {@link #TRIP}
 * is ever created by this module at MVP — {@link #DIRECT} exists in the
 * schema for the Business Rules Module C-documented "Direct Messages between
 * two users who share (or have shared) Accepted Membership on the same trip"
 * feature, deferred to a follow-up pass (see {@code ChatService}'s class doc).
 */
public enum ChatRoomType {
    TRIP,
    DIRECT;

    @Converter(autoApply = true)
    public static class Jpa implements AttributeConverter<ChatRoomType, String> {
        @Override
        public String convertToDatabaseColumn(ChatRoomType attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public ChatRoomType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ChatRoomType.valueOf(dbData.toUpperCase());
        }
    }
}
