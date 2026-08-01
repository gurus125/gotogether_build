package com.gotogether.destination.dto;

import com.gotogether.destination.entity.DestinationCategory;
import java.util.UUID;

/**
 * The destination module's cross-module-safe view — reused directly as the
 * API response shape too (Destination APIs, API Specification Section 5),
 * since a destination has no private/sensitive fields to strip the way
 * {@code UserSummary} strips PII off {@code User}. Also what {@code trip}
 * embeds into a Trip Details / Trip Summary response instead of touching
 * {@code Destination} directly (module-boundary rule, enforced by
 * {@code ArchitectureTest}).
 */
public record DestinationSummary(UUID id, String name, DestinationCategory category, String coverImageUrl) {}
