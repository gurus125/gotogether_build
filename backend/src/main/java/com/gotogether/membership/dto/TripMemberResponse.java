package com.gotogether.membership.dto;

import com.gotogether.membership.entity.AttendanceStatus;
import com.gotogether.membership.entity.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Raw {@code trip_members} row mirror — the {@code trip_member} field of the Accept and Attendance responses (API Spec Sections 8, 9). */
public record TripMemberResponse(
        UUID id,
        UUID tripId,
        UUID userId,
        MembershipStatus status,
        boolean isOrganizer,
        AttendanceStatus attendanceStatus,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt,
        OffsetDateTime removedAt,
        String removedReason,
        OffsetDateTime completedAt) {
}
