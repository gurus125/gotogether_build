package com.gotogether.trip.dto;

import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact card representation for list contexts ({@code GET /trips}, {@code
 * GET /trips/recommended}, {@code GET /explore}, Home's "Trips for you" /
 * "Verified partner trips" rows) — matches the Trip Card component the Home
 * Screen and Explore design docs both reuse.
 *
 * <p>{@code joinedCount} is always {@code 0} at Phase 2 — there is no
 * membership data yet ({@code joinrequest}/{@code membership} are Phase 3
 * modules), so this is accurate (zero real joins exist), not a stub value
 * standing in for real data. Once Phase 3 lands, this field's source changes
 * but its meaning doesn't.
 */
public record TripSummary(
        UUID id,
        String title,
        TripKind kind,
        TripStatus status,
        DestinationSummary destination,
        LocalDate startDate,
        LocalDate endDate,
        Integer budgetMin,
        Integer budgetMax,
        Integer fixedPrice,
        short maxGroupSize,
        int joinedCount,
        String coverImageUrl,
        UUID organizerId,
        String organizerDisplayName,
        String organizerPhotoUrl,
        boolean organizerVerified,
        UUID companyId) {

    /**
     * Returns a copy with a real {@code joinedCount} — used at the controller
     * layer (see {@code TripController}) to overlay live counts from {@code
     * MembershipService} onto list results built by {@code TripService}
     * alone. This indirection exists specifically to avoid a circular module
     * dependency: {@code membership} already depends on {@code trip} (to
     * drive capacity-triggered lifecycle transitions), so {@code trip}
     * cannot also depend on {@code membership} without creating a cycle.
     * Composing the two at the controller — which is allowed to depend on
     * both peer services — is the standard fix.
     */
    public TripSummary withJoinedCount(int realJoinedCount) {
        return new TripSummary(
                id, title, kind, status, destination, startDate, endDate, budgetMin, budgetMax, fixedPrice,
                maxGroupSize, realJoinedCount, coverImageUrl, organizerId, organizerDisplayName, organizerPhotoUrl,
                organizerVerified, companyId);
    }
}
