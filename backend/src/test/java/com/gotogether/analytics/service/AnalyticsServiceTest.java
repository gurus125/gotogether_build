package com.gotogether.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.analytics.entity.AnalyticsEvent;
import com.gotogether.analytics.entity.AnalyticsEventType;
import com.gotogether.analytics.repository.AnalyticsEventRepository;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.trust.entity.TrustLevel;
import com.gotogether.trust.service.TrustService;
import com.gotogether.user.service.UserService;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private AnalyticsEventRepository analyticsEventRepository;
    @Mock private TrustService trustService;
    @Mock private UserService userService;

    private AnalyticsService analyticsService;

    private final UUID userId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(analyticsEventRepository, trustService, userService);
    }

    // --- record() -----------------------------------------------------------

    @Test
    void recordSavesEventForValidType() {
        analyticsService.record("trip_created", userId, "trips", entityId, Map.of("foo", "bar"));

        verify(analyticsEventRepository, times(1)).save(any(AnalyticsEvent.class));
    }

    @Test
    void recordIsCaseInsensitiveOnEventType() {
        analyticsService.record("Trip_Created", userId, "trips", entityId, null);

        verify(analyticsEventRepository, times(1)).save(any(AnalyticsEvent.class));
    }

    @Test
    void recordSwallowsInvalidEventTypeRatherThanThrowing() {
        analyticsService.record("not_a_real_event", userId, "trips", entityId, null);

        verify(analyticsEventRepository, never()).save(any());
    }

    @Test
    void recordSwallowsNullEventTypeRatherThanThrowing() {
        analyticsService.record(null, userId, "trips", entityId, null);

        verify(analyticsEventRepository, never()).save(any());
    }

    @Test
    void recordAcceptsNullMetadataAndNullUserId() {
        analyticsService.record("search_performed", null, "trips", null, null);

        verify(analyticsEventRepository, times(1)).save(any(AnalyticsEvent.class));
    }

    // --- getMetric() ----------------------------------------------------------

    @Test
    void getMetricEventCountsMergesRepositoryRowsWithZeroDefaults() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now();
        // Collections.singletonList(...), not List.of(new Object[]{...}) — a
        // single Object[] argument to List.of's varargs is ambiguous: javac
        // can't tell whether that array IS the varargs array (producing
        // List<Object> with 2 elements) or a single element being wrapped
        // (producing List<Object[]> with 1 element), and failed with
        // "incompatible types: inference variable E has incompatible bounds"
        // when actually compiled with a real JDK — caught only once the user
        // ran a real `mvn spring-boot:run`, since this sandbox has no
        // compiler (see project's sandbox-constraints memory).
        List<Object[]> rows = Collections.singletonList(new Object[] {AnalyticsEventType.TRIP_CREATED, 5L});
        when(analyticsEventRepository.countByEventTypeBetween(eq(from), eq(to))).thenReturn(rows);

        Map<String, Object> result = analyticsService.getMetric("event_counts", from, to);

        @SuppressWarnings("unchecked")
        Map<String, Long> counts = (Map<String, Long>) result.get("counts");
        assertThat(counts.get("TRIP_CREATED")).isEqualTo(5L);
        assertThat(counts.get("TRIP_CANCELLED")).isEqualTo(0L);
        assertThat(counts).hasSize(AnalyticsEventType.values().length);
    }

    @Test
    void getMetricDefaultsToLast30DaysWhenDatesOmitted() {
        when(analyticsEventRepository.countByEventTypeBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> result = analyticsService.getMetric("event_counts", null, null);

        assertThat(result).containsKeys("counts", "from", "to");
    }

    @Test
    void getMetricTrustScoreDistributionDelegatesToTrustService() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (TrustLevel level : TrustLevel.values()) {
            distribution.put(level.name(), 1L);
        }
        when(trustService.getScoreDistribution()).thenReturn(distribution);

        Map<String, Object> result = analyticsService.getMetric("trust_score_distribution", null, null);

        assertThat(result.get("distribution")).isEqualTo(distribution);
    }

    @Test
    void getMetricSignupsDelegatesToUserService() {
        when(userService.countSignupsBetween(any(), any())).thenReturn(42L);

        Map<String, Object> result = analyticsService.getMetric("signups", null, null);

        assertThat(result.get("count")).isEqualTo(42L);
    }

    @Test
    void getMetricThrowsOnUnsupportedMetric() {
        assertThatThrownBy(() -> analyticsService.getMetric("not_a_real_metric", null, null))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void getMetricThrowsOnNullMetric() {
        assertThatThrownBy(() -> analyticsService.getMetric(null, null, null))
                .isInstanceOf(UnprocessableEntityException.class);
    }
}
