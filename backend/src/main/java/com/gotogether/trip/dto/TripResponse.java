package com.gotogether.trip.dto;

import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import com.gotogether.trip.entity.TripVisibility;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full trip payload, used both standalone ({@code GET /trips/{id}} nests this
 * as the {@code trip} field alongside {@code organizer}/{@code
 * members_preview}/etc.) and as the create/publish/cancel response body.
 *
 * <p>Embeds the full {@link DestinationSummary} rather than just {@code
 * destination_id} — a pragmatic deviation from the API Specification's literal
 * "fields match DB columns 1:1" phrasing, made so Trip Details doesn't need a
 * second round-trip to render the destination name/category the Hero section
 * needs immediately.
 */
public record TripResponse(
        UUID id,
        UUID organizerId,
        UUID companyId,
        DestinationSummary destination,
        TripKind kind,
        TripStatus status,
        TripVisibility visibility,
        String title,
        String description,
        String tripType,
        boolean isFlexibleDates,
        LocalDate startDate,
        LocalDate endDate,
        Integer budgetMin,
        Integer budgetMax,
        Integer fixedPrice,
        short minGroupSize,
        short maxGroupSize,
        boolean isApprovalRequired,
        boolean isWaitlistAllowed,
        String meetingPoint,
        OffsetDateTime publishedAt,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        OffsetDateTime completedAt,
        List<TripImageResponse> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
