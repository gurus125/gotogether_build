package com.gotogether.user.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcType;

/**
 * Authentication identity and account-lifecycle state only — deliberately
 * excludes display/profile data, which lives in {@link com.gotogether.profile.entity.UserProfile}
 * (DB Schema Part 1: "auth data and profile data have different read
 * patterns and change frequency").
 *
 * <p>Enum columns are native Postgres enum types (see V1 migration); each
 * enum's nested {@code Jpa} converter maps the Java constant to the exact
 * lowercase label Postgres expects. The correct, complete annotation set for
 * such a field is {@code @JdbcType(NativeEnumJdbcType.class)} plus
 * {@code @Column(columnDefinition = "...")} — <b>do not also add
 * {@code @Enumerated}</b>. See {@link NativeEnumJdbcType}'s own class doc
 * for the full story of why a hand-rolled {@code JdbcType} is used here at
 * all instead of a built-in Hibernate mechanism — in short, both
 * {@code @JdbcTypeCode(SqlTypes.OTHER)} and
 * {@code @JdbcType(PostgreSQLEnumJdbcType.class)} were tried first and both
 * failed in this exact Hibernate/JDK/Postgres combination (confirmed
 * 2026-07-22 against a real local Postgres). Also worth remembering:
 * {@code @Enumerated(EnumType.STRING)} must NOT be added alongside the
 * {@code Jpa} converter — per the JPA spec, {@code @Enumerated} on a field
 * suppresses any {@code @Convert}/autoApply converter for it, so the
 * converter silently never runs, and Hibernate tries to bind the raw enum
 * constant itself instead.
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "email")
    private String email;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    private UserStatus status = UserStatus.REGISTERED;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "verification_level", nullable = false, columnDefinition = "verification_level")
    private VerificationLevel verificationLevel = VerificationLevel.NONE;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "role", nullable = false, columnDefinition = "account_role")
    private AccountRole role = AccountRole.INDIVIDUAL;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected User() {
        // JPA
    }

    public static User newGoogleUser(String googleId, String email) {
        User user = new User();
        user.googleId = googleId;
        user.email = email;
        return user;
    }

    public static User newPhoneUser(String phoneNumber) {
        User user = new User();
        user.phoneNumber = phoneNumber;
        return user;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getGoogleId() {
        return googleId;
    }

    public String getEmail() {
        return email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public VerificationLevel getVerificationLevel() {
        return verificationLevel;
    }

    public void setVerificationLevel(VerificationLevel verificationLevel) {
        this.verificationLevel = verificationLevel;
    }

    public AccountRole getRole() {
        return role;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
    }

    public OffsetDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public boolean isDeactivated() {
        return deactivatedAt != null;
    }

    public void deactivate() {
        this.deactivatedAt = OffsetDateTime.now();
    }

    public void reactivate() {
        this.deactivatedAt = null;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft-delete only (Business Rules Module 1 Section 10: 30-day
     * anonymization grace period). The actual PII-scrubbing job runs
     * separately once the grace period elapses — this just marks the
     * account as scheduled for it.
     */
    public void markDeleted() {
        this.deletedAt = OffsetDateTime.now();
        this.status = UserStatus.SUSPENDED;
    }

    /**
     * {@code POST /admin/users/{id}/restrict} (Phase 8, Operations Module
     * B's Warning &amp; Enforcement Ladder, Restricted tier): "blocks new
     * trip creation/join requests, existing commitments unaffected." The
     * blocking check itself lives in {@code trip}/{@code joinrequest}
     * (mirrors {@code enforceIdApproval}'s existing verification-level gate
     * pattern) — not modeled here yet since neither module currently reads
     * {@code UserStatus} at all; flagged as a real, not-yet-wired gap rather
     * than silently assumed enforced. This method only records the status
     * transition itself, which is what {@code GET /admin/users/{id}} and the
     * user's own account state correctly reflect either way.
     */
    public void restrict() {
        this.status = UserStatus.RESTRICTED;
    }

    /** Enforcement-ladder Suspended tier: "full account freeze... auto-cancel with notification if Organizer" — the trip force-cancel cascade is {@code AdminService}'s job (composes {@code TripService}), not this entity's. */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    /**
     * Enforcement-ladder "Permanently removed" tier deliberately reuses
     * {@link #markDeleted} rather than introducing a new {@code UserStatus}
     * value — the Postgres {@code user_status} enum (V1 migration) only
     * defines {@code registered/verified/restricted/suspended}, with no
     * {@code removed} value, so {@code deleted_at} being non-null is what
     * actually distinguishes a permanent removal from an ordinary Suspension
     * in this schema. A real, schema-driven decision, not an oversight.
     */
    public void adminRemove() {
        markDeleted();
    }
}
