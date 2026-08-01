package com.gotogether.membership.dto;

import com.gotogether.membership.entity.AttendanceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /trips/{id}/members} row shape (API Spec Section 6: "{ user,
 * is_organizer, joined_at, trust_score }"). {@code trustScore} is always
 * {@code null} — the {@code trust} module (Phase 5) doesn't exist yet, and a
 * fabricated number would undermine the platform's trust-first premise (same
 * reasoning as Home Screen's omitted greeting trust score, Phase 2).
 *
 * <p>{@code attendanceStatus} added alongside the Manage Attendance screen —
 * null for a still-in-progress trip (nothing to mark yet), {@code ATTENDED}/
 * {@code NO_SHOW} once the organizer has recorded it, null-but-markable once
 * the trip is Completed and it hasn't been recorded yet. Lets that screen
 * show current marks without a second round-trip per member.
 */
public record RosterMemberResponse(
        UUID userId, String displayName, String photoUrl, boolean isOrganizer, OffsetDateTime joinedAt,
        Double trustScore, AttendanceStatus attendanceStatus) {
}
