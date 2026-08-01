package com.gotogether.joinrequest.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /trips/{id}/join-status} — the caller's current relationship to
 * a trip (API Spec Section 8), driving the Trip Details CTA state.
 * {@code status} is one of {@code NOT_REQUESTED} (synthetic — no row exists
 * yet) or a {@link com.gotogether.joinrequest.entity.JoinRequestStatus} name.
 * {@code joinRequestId} is a pragmatic addition beyond the API Spec's literal
 * field list — {@code null} when {@code status = NOT_REQUESTED}, otherwise
 * lets the Trip Details "Withdraw" action call {@code POST
 * /join-requests/{id}/withdraw} directly instead of needing a second lookup.
 */
public record JoinStatusResponse(UUID joinRequestId, String status, Integer waitlistPosition, OffsetDateTime canReapplyAt) {

    public static final String NOT_REQUESTED = "NOT_REQUESTED";
}
