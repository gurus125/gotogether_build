package com.gotogether.joinrequest.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * A single Verified User's request to join a Trip, tracked as full history —
 * not overwritten on re-request — so the 7-day reject-cooldown and Chapter 3
 * Section 3.3's Expired path are auditable (DB Schema Part 2). Every row here
 * is a snapshot of {@link JoinRequestStatus}'s state machine; the "no
 * duplicate open request per applicant per trip" rule is enforced twice — a
 * friendly check in {@code JoinRequestService} and a partial-unique DB index
 * (V3 migration) as the real backstop.
 */
@Entity
@Table(name = "join_requests")
public class JoinRequest extends AuditableEntity {

    @Column(name = "applicant_id", nullable = false, updatable = false)
    private UUID applicantId;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "join_request_status")
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "request_message")
    private String requestMessage;

    @Column(name = "organizer_response_note")
    private String organizerResponseNote;

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @Column(name = "reopened_from_id")
    private UUID reopenedFromId;

    protected JoinRequest() {
        // JPA
    }

    public static JoinRequest create(UUID applicantId, UUID tripId, String requestMessage, UUID reopenedFromId, Duration slaWindow) {
        JoinRequest jr = new JoinRequest();
        jr.applicantId = applicantId;
        jr.tripId = tripId;
        jr.requestMessage = requestMessage;
        jr.reopenedFromId = reopenedFromId;
        jr.expiresAt = OffsetDateTime.now().plus(slaWindow);
        return jr;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public String getOrganizerResponseNote() {
        return organizerResponseNote;
    }

    public Integer getWaitlistPosition() {
        return waitlistPosition;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public UUID getReopenedFromId() {
        return reopenedFromId;
    }

    public boolean isOpen() {
        return status == JoinRequestStatus.PENDING || status == JoinRequestStatus.WAITING_LIST;
    }

    public boolean isExpiredButNotMarked() {
        return status == JoinRequestStatus.PENDING && expiresAt.isBefore(OffsetDateTime.now());
    }

    /** Chapter 3 Section 3.3: SLA window elapses with no Organizer response. Lazily applied — see {@code JoinRequestService}'s class doc for why there's no scheduled sweep yet. */
    public void expire() {
        this.status = JoinRequestStatus.EXPIRED;
        this.decidedAt = OffsetDateTime.now();
    }

    /** {@code Pending -> Accepted}: Organizer approves, capacity confirmed available. */
    public void accept() {
        this.status = JoinRequestStatus.ACCEPTED;
        this.decidedAt = OffsetDateTime.now();
        this.waitlistPosition = null;
    }

    /** {@code Pending -> Rejected}: Organizer declines, with an optional note. */
    public void reject(String organizerResponseNote) {
        this.status = JoinRequestStatus.REJECTED;
        this.organizerResponseNote = organizerResponseNote;
        this.decidedAt = OffsetDateTime.now();
    }

    /** Requester-initiated cancellation before any decision — no penalty, no record visible to others (Business Rules Core User Features Module B). */
    public void withdraw() {
        this.status = JoinRequestStatus.WITHDRAWN;
        this.withdrawnAt = OffsetDateTime.now();
    }

    /** {@code Pending -> WaitingList}: the trip was already at capacity when this request was decided (Accept-time race, or created while Full). */
    public void moveToWaitingList(int position) {
        this.status = JoinRequestStatus.WAITING_LIST;
        this.waitlistPosition = position;
    }

    /** {@code WaitingList -> Accepted}: a spot opened and this (FIFO-oldest) request was promoted — a distinct transition from {@link #accept()} per Chapter 3 Section 3.3's diagram, even though the resulting status is the same. */
    public void promoteFromWaitlist() {
        this.status = JoinRequestStatus.ACCEPTED;
        this.decidedAt = OffsetDateTime.now();
        this.waitlistPosition = null;
    }
}
