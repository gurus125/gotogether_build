package com.gotogether.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gotogether.analytics.entity.AnalyticsEvent;
import com.gotogether.analytics.entity.AnalyticsEventType;
import com.gotogether.analytics.repository.AnalyticsEventRepository;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.trust.service.TrustService;
import com.gotogether.user.service.UserService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The analytics module's only entry point for other modules (see {@code
 * UserService}'s doc for the pattern). Two distinct halves:
 *
 * <p><b>Write side ({@link #record}):</b> called directly from controllers
 * across almost every other module (Trip, JoinRequest, Membership, Review,
 * Explore, Notification, Admin) at the exact moment an {@code
 * analytics_event_type} (V1 migration) event happens — the same
 * controller-layer-composition convention already established for {@code
 * notification.service.NotificationService} (see that class's usage in
 * e.g. {@code TripController.cancel}'s doc: "notification has no outbound
 * dependencies, so there's no cycle risk calling it directly here either").
 * {@code eventType} is a {@code String}, not this module's own {@link
 * AnalyticsEventType}, for the identical cross-module-entity-access reason
 * {@code NotificationService#create} takes a {@code String type} — every
 * calling module lives outside {@code analytics}. Deliberately swallows any
 * failure (bad event type, JSON serialization error) rather than
 * propagating it: a broken analytics write must never fail the real
 * user-facing transaction it's attached to (creating a trip, submitting a
 * review, etc.) — logged instead, so a wiring bug is visible in ops without
 * being customer-visible.
 *
 * <p><b>Read side ({@link #getMetric}):</b> backs {@code GET
 * /admin/analytics} (API Spec Section 16), composed into {@code
 * admin.service.AdminService}. Depends directly on {@code trust} (score
 * distribution) and {@code user} (signup counts) — safe one-directional
 * edges, since neither of those modules depends back on {@code analytics}
 * and nothing depends on {@code analytics} itself (it is the most
 * "downstream" module in the system, same standing as {@code admin} — see
 * that class's doc).
 *
 * <p><b>Scoped down from Operations Module D's full Metric Set</b> (flagged
 * here rather than silently narrowed): this pass implements exactly three
 * metric keys — {@code event_counts} (every {@link AnalyticsEventType}'s
 * count in a date range: directly answers most of "Trip health," "Reviews,"
 * "Search," and "Growth"'s verification-completion half), {@code
 * trust_score_distribution} ("Trust" category), and {@code signups}
 * ("Growth" category's new-signups count). Not implemented, and why:
 * <ul>
 * <li><b>Activity</b> (WAU/MAU, "trips created per active organizer") needs
 * session/login-history data this schema doesn't have — {@code
 * users.last_login_at} is a single timestamp, not a series (the identical
 * gap {@code TrustService}'s "Account age & activity" component already
 * flagged).</li>
 * <li><b>Growth's Guest→Registered→Verified funnel</b> needs guest-mode
 * tracking that was never built (this product has no unauthenticated guest
 * browsing state in the API).</li>
 * <li><b>Conversion's "view → request rate on Trip Details"</b> needs a
 * {@code trip_viewed} event — not one of the ten values in {@code
 * analytics_event_type}, so it cannot be captured without a schema change.</li>
 * <li><b>Destinations, Company performance, Organizer performance,
 * Retention, Engagement</b> each need a new cross-module aggregate query
 * (grouping trips/reviews by destination or company, cohort-based retention
 * curves, per-trip chat message counts) that doesn't exist anywhere yet —
 * real, bounded follow-up work, not attempted as a shallow stand-in here.</li>
 * <li><b>Notifications' push open rate</b> needs FCM push receipts — Phase 6
 * explicitly never wired FCM ("in-app delivery only"), so there is nothing
 * to measure yet.</li>
 * <li><b>Search's zero-result rate / most-applied filters</b> could be
 * derived from {@code search_performed} events' {@code metadata} JSON going
 * forward (this pass's {@code ExploreController} wiring does record result
 * count and filters used), but reading back out of a JSONB column needs
 * native SQL, not plain JPQL — left for a follow-up once real data has
 * accumulated to aggregate.</li>
 * </ul>
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsEventRepository analyticsEventRepository;
    private final TrustService trustService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyticsService(AnalyticsEventRepository analyticsEventRepository, TrustService trustService, UserService userService) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.trustService = trustService;
        this.userService = userService;
    }

    /**
     * Fire-and-forget event capture — see this class's doc for why failures
     * are swallowed rather than propagated. {@code metadata} may be {@code
     * null} (recorded as {@code {}}).
     */
    @Transactional
    public void record(String eventTypeRaw, UUID userId, String entityType, UUID entityId, Map<String, Object> metadata) {
        try {
            AnalyticsEventType type = AnalyticsEventType.valueOf(eventTypeRaw.toUpperCase());
            String metadataJson = metadata == null ? "{}" : objectMapper.writeValueAsString(metadata);
            analyticsEventRepository.save(AnalyticsEvent.of(type, userId, entityType, entityId, metadataJson));
        } catch (IllegalArgumentException | JsonProcessingException e) {
            log.warn("Failed to record analytics event '{}': {}", eventTypeRaw, e.getMessage());
        }
    }

    /** {@code GET /admin/analytics?metric=X&date_from=&date_to=} (API Spec Section 16) — see this class's doc for exactly which {@code metric} values are supported and why the rest are scoped down. */
    public Map<String, Object> getMetric(String metric, OffsetDateTime dateFrom, OffsetDateTime dateTo) {
        OffsetDateTime from = dateFrom == null ? OffsetDateTime.now().minusDays(30) : dateFrom;
        OffsetDateTime to = dateTo == null ? OffsetDateTime.now() : dateTo;

        return switch (metric == null ? "" : metric) {
            case "event_counts" -> eventCounts(from, to);
            case "trust_score_distribution" -> Map.of("distribution", trustService.getScoreDistribution());
            case "signups" -> Map.of("count", userService.countSignupsBetween(from, to), "from", from, "to", to);
            default -> throw new UnprocessableEntityException(
                    "'" + metric + "' is not a supported analytics metric. Supported: event_counts, trust_score_distribution, signups.");
        };
    }

    private Map<String, Object> eventCounts(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AnalyticsEventType type : AnalyticsEventType.values()) {
            counts.put(type.name(), 0L);
        }
        for (Object[] row : analyticsEventRepository.countByEventTypeBetween(from, to)) {
            counts.put(((AnalyticsEventType) row[0]).name(), (Long) row[1]);
        }
        return Map.of("counts", counts, "from", from, "to", to);
    }
}
