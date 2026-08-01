package com.gotogether.trip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code PATCH /trips/{id}} (API Specification Section 6) — PATCH semantics,
 * only non-null fields are applied (mirrors {@code UpdateProfileRequest}).
 * Changing {@code destinationId}/dates after members have joined is meant to
 * trigger a mandatory notification (Core Features Module A) — not wired yet
 * since the {@code notification} module doesn't exist until Phase 6; {@code
 * TripService} only enforces the {@code TRIP_IN_PROGRESS_LOCKED} edit-lock
 * for now (Chapter 3 Section 3.2's "In Progress" row).
 *
 * <p>{@code minGroupSize}/{@code maxGroupSize}/{@code meetingPoint}/{@code
 * isApprovalRequired}/{@code isWaitlistAllowed} are the "Manage Trip" fields
 * the Create Trip wizard's Review step always promised were "added right
 * after publishing" but that, until now, had nowhere to actually be edited —
 * see the mobile app's {@code create_trip_screen.dart} Review step and {@code
 * my_trips_screen.dart}'s class doc. {@code visibility} is deliberately not
 * exposed here: only {@code PUBLIC} is a documented Phase 2 flow (see {@code
 * TripVisibility}'s class doc) — adding a PRIVATE toggle without a design doc
 * behind it would be scope invention, not a fix. There is likewise no {@code
 * itinerary} field — no such column exists anywhere in the approved schema,
 * so it can't be edited here either; it would need its own schema + design
 * review first.
 */
public record UpdateTripRequest(
        UUID destinationId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isFlexibleDates,
        @PositiveOrZero Integer budgetMin,
        @PositiveOrZero Integer budgetMax,
        @Size(min = 5, max = 60) String title,
        @Size(max = 300) String description,
        @Min(1) Integer minGroupSize,
        @Max(50) Integer maxGroupSize,
        @Size(max = 200) String meetingPoint,
        Boolean isApprovalRequired,
        Boolean isWaitlistAllowed) {
}
