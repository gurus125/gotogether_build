package com.gotogether.analytics.repository;

import com.gotogether.analytics.entity.AnalyticsEvent;
import com.gotogether.analytics.entity.AnalyticsEventType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    /** {@code GET /admin/analytics?metric=event_counts} — every event type's count in one query rather than ten. */
    @Query("SELECT e.eventType, COUNT(e) FROM AnalyticsEvent e WHERE e.occurredAt BETWEEN :from AND :to GROUP BY e.eventType")
    List<Object[]> countByEventTypeBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    long countByEventTypeAndOccurredAtBetween(AnalyticsEventType eventType, OffsetDateTime from, OffsetDateTime to);
}
