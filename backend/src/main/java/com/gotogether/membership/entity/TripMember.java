package com.gotogether.membership.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * The confirmed roster of a Trip (Chapter 3 Section 3.4, DB Schema Part 2) —
 * the entity Chat access, Review eligibility, and capacity counters all key
 * off. {@code userId}/{@code tripId}/{@code joinRequestId} are plain {@code
 * UUID} fields, not JPA relationships, per the same cross-module-by-id-only
 * rule as {@code Trip}'s organizer/destination fields.
 *
 * <p>Membership is a <em>derived</em> state (Chapter 3 Section 3.4's own
 * framing) — it only ever exists as the consequence of a Join Request
 * reaching Accepted, or of the Trip reaching Completed while still a member.
 * Nothing outside {@code MembershipService} constructs or mutates this
 * entity directly.
 */
@Entity
@Table(name = "trip_members")
public class TripMember extends AuditableEntity {

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Null only for the Organizer's own row, which has no Join Request (Chapter 2 Section 2.1). */
    @Column(name = "join_request_id")
    private UUID joinRequestId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "membership_status")
    private MembershipStatus status = MembershipStatus.JOINED;

    @Column(name = "is_organizer", nullable = false)
    private boolean organizer;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "attendance_status", columnDefinition = "attendance_status")
    private AttendanceStatus attendanceStatus;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @Column(name = "removed_reason")
    private String removedReason;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected TripMember() {
        // JPA
    }

    /** The Organizer's own seat, created alongside the Trip itself — never via a Join Request. */
    public static TripMember organizerSeat(UUID tripId, UUID organizerId) {
        TripMember member = new TripMember();
        member.tripId = tripId;
        member.userId = organizerId;
        member.organizer = true;
        return member;
    }

    /** A Member admitted via an accepted (or waitlist-promoted) Join Request. */
    public static TripMember fromAcceptedRequest(UUID tripId, UUID userId, UUID joinRequestId) {
        TripMember member = new TripMember();
        member.tripId = tripId;
        member.userId = userId;
        member.joinRequestId = joinRequestId;
        return member;
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getJoinRequestId() {
        return joinRequestId;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public boolean isOrganizer() {
        return organizer;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    public OffsetDateTime getRemovedAt() {
        return removedAt;
    }

    public String getRemovedReason() {
        return removedReason;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public boolean isActive() {
        return status == MembershipStatus.JOINED;
    }

    /** {@code Joined -> Left}: voluntary exit (Chapter 3 Section 3.4). Trust Score impact (neutral if >72h before departure, negative within 72h) is a Phase 5 {@code trust} module concern, not computed here. */
    public void leave() {
        this.status = MembershipStatus.LEFT;
        this.leftAt = OffsetDateTime.now();
    }

    /** {@code Joined -> Removed}: Organizer/Moderator-initiated, reason mandatory (Business Rules Core User Features Module B). */
    public void remove(String reason) {
        this.status = MembershipStatus.REMOVED;
        this.removedAt = OffsetDateTime.now();
        this.removedReason = reason;
    }

    /** {@code Joined -> CompletedMember}: the trip reached Completed while this row was still active. */
    public void markCompleted() {
        this.status = MembershipStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }
}
