package com.gotogether.trip.controller;

import com.gotogether.analytics.service.AnalyticsService;
import com.gotogether.common.ReferencedEntityType;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.membership.service.MembershipService;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.service.TripService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explore API (API Specification Section 7) — full search+filter+sort engine
 * backing the Explore screen. Lives in the {@code trip} module (not a
 * separate module) since it's purely a read query over {@code trips}, same
 * as the {@code Trip APIs} controller. Age range / Gender pref. / Minimum
 * organizer trust score (shown in the approved Explore design's filter
 * sheet) are deliberately not query parameters here — the API Specification
 * never defines them and Chapter 4's declared-preference matching is still
 * undefined (flagged in the Phase 2 docs review); the Flutter Explore screen
 * renders those controls but they're inert until that's specified.
 */
@RestController
@RequestMapping("/explore")
public class ExploreController {

    private final TripService tripService;
    private final MembershipService membershipService;
    private final AnalyticsService analyticsService;

    public ExploreController(TripService tripService, MembershipService membershipService, AnalyticsService analyticsService) {
        this.tripService = tripService;
        this.membershipService = membershipService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public CursorPageResponse<TripSummary> explore(
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) Integer budgetMin,
            @RequestParam(required = false) Integer budgetMax,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Integer durationMinDays,
            @RequestParam(required = false) Integer durationMaxDays,
            @RequestParam(required = false) String tripType,
            @RequestParam(required = false) TripKind kind,
            @RequestParam(defaultValue = "false") boolean verifiedOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        CursorPageResponse<TripSummary> page = tripService.explore(
                destinationId, budgetMin, budgetMax, dateFrom, dateTo, durationMinDays, durationMaxDays,
                tripType, kind, verifiedOnly, sort, cursor, limit);
        CursorPageResponse<TripSummary> result = withLiveCounts(page);
        recordSearch(destinationId, budgetMin, budgetMax, dateFrom, dateTo, tripType, kind, verifiedOnly, sort, result);
        return result;
    }

    /**
     * Fire-and-forget capture of every Explore query's filters + result shape
     * (no {@code userId} — this endpoint has no {@code @AuthenticationPrincipal}
     * param, so it's recorded anonymously) into {@code search_performed}'s
     * {@code metadata} JSON, so a future zero-result-rate / most-applied-filter
     * metric can be built from real accumulated data — see {@code
     * AnalyticsService}'s class doc, "Search's zero-result rate" bullet.
     */
    private void recordSearch(
            UUID destinationId, Integer budgetMin, Integer budgetMax, LocalDate dateFrom, LocalDate dateTo,
            String tripType, TripKind kind, boolean verifiedOnly, String sort, CursorPageResponse<TripSummary> result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("destinationId", destinationId == null ? null : destinationId.toString());
        metadata.put("budgetMin", budgetMin);
        metadata.put("budgetMax", budgetMax);
        metadata.put("dateFrom", dateFrom == null ? null : dateFrom.toString());
        metadata.put("dateTo", dateTo == null ? null : dateTo.toString());
        metadata.put("tripType", tripType);
        metadata.put("kind", kind == null ? null : kind.name());
        metadata.put("verifiedOnly", verifiedOnly);
        metadata.put("sort", sort);
        metadata.put("resultCount", result.items().size());
        metadata.put("hasMore", result.hasMore());
        analyticsService.record("search_performed", null, ReferencedEntityType.TRIPS.tableName(), null, metadata);
    }

    /** See {@code TripController.withLiveCounts}'s doc — same composition, duplicated rather than shared since these are two distinct controllers. */
    private CursorPageResponse<TripSummary> withLiveCounts(CursorPageResponse<TripSummary> page) {
        if (page.items().isEmpty()) {
            return page;
        }
        Map<UUID, Integer> counts = membershipService.countActiveMembersByTripIds(page.items().stream().map(TripSummary::id).toList());
        List<TripSummary> updated = page.items().stream().map(t -> t.withJoinedCount(counts.getOrDefault(t.id(), 0))).toList();
        return new CursorPageResponse<>(updated, page.nextCursor(), page.hasMore());
    }
}
