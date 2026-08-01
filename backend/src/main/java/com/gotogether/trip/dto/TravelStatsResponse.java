package com.gotogether.trip.dto;

/**
 * Backs {@code GET /users/me/travel-stats} — the My Profile / Other
 * Traveller's Profile mockup's "Travel stats" card (JOINED / COMPLETED /
 * ORGANIZED). Not part of the original API Specification's documented
 * endpoint table; added when building Phase 5's Flutter Profile screen
 * because the mockup shows these three counts on both the self and
 * other-traveller cards and no existing endpoint could supply them without
 * fabricating numbers. Composed entirely from already-existing {@code
 * MembershipService}/{@code TripService} methods at the controller layer
 * (see {@code TripController#travelStats}), so this is additive only — no
 * schema change, no new module, no new inter-service dependency (`trip`'s
 * controller already calls {@code MembershipService} directly for the My
 * Trips tabs).
 */
public record TravelStatsResponse(int joined, int completed, int organized) {
}
