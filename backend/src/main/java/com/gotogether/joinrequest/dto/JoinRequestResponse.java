package com.gotogether.joinrequest.dto;

import com.gotogether.joinrequest.entity.JoinRequestStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code applicantDisplayName}/{@code applicantPhotoUrl} are always {@code
 * null} coming out of {@code JoinRequestService} — {@code joinrequest} never
 * depends on {@code profile} (see this module's own dependency direction).
 * {@link #withApplicantProfile} lets {@code JoinRequestController} overlay
 * the real values afterward, exactly like {@code TripSummary#withJoinedCount}
 * overlays live join counts from {@code membership} onto {@code trip}-built
 * cards — same cycle-avoidance reasoning, same fix. Populated on the
 * Organizer's queue ({@code GET /trips/{id}/join-requests}) so a "Traveller
 * request" card can actually show who's asking, not just a status pill.
 */
public record JoinRequestResponse(
        UUID id,
        UUID tripId,
        UUID applicantId,
        JoinRequestStatus status,
        String requestMessage,
        String organizerResponseNote,
        Integer waitlistPosition,
        OffsetDateTime decidedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        String applicantDisplayName,
        String applicantPhotoUrl) {

    /** Canonical constructor overload — every existing call site (only {@code JoinRequestService.toResponse}) predates these two fields. */
    public JoinRequestResponse(
            UUID id, UUID tripId, UUID applicantId, JoinRequestStatus status, String requestMessage,
            String organizerResponseNote, Integer waitlistPosition, OffsetDateTime decidedAt,
            OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        this(id, tripId, applicantId, status, requestMessage, organizerResponseNote, waitlistPosition,
                decidedAt, expiresAt, createdAt, null, null);
    }

    public JoinRequestResponse withApplicantProfile(String displayName, String photoUrl) {
        return new JoinRequestResponse(
                id, tripId, applicantId, status, requestMessage, organizerResponseNote, waitlistPosition,
                decidedAt, expiresAt, createdAt, displayName, photoUrl);
    }
}
