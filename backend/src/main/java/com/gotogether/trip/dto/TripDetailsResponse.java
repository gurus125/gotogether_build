package com.gotogether.trip.dto;

import java.util.List;

/**
 * {@code GET /trips/{id}} (API Specification Section 6) — "Trip Details
 * screen full payload." Per the approved tripdetailsv1 design, the full
 * screen also has Members preview, Compatibility score, Itinerary,
 * Budget breakdown, Reviews, Safety information, and Similar trips sections —
 * every one of those depends on a module that doesn't exist yet at Phase 2
 * ({@code membership}/{@code joinrequest} for members and join status,
 * Chapter 4's undefined compatibility formula, {@code review} for Reviews).
 * Rather than fabricate placeholder data for them, this response only
 * includes what Phase 2 can honestly compute: {@code membersPreview} is
 * always empty (accurate — no memberships exist yet), {@code
 * compatibilityScore} and {@code joinStatus} are always {@code null}. The
 * Flutter Trip Details screen hides those sections when the field is
 * empty/null rather than rendering a fake empty state.
 */
public record TripDetailsResponse(
        TripResponse trip,
        OrganizerSummary organizer,
        List<MemberPreview> membersPreview,
        Integer compatibilityScore,
        String joinStatus) {

    public record MemberPreview(String displayName, String photoUrl) {}
}
