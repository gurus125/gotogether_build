package com.gotogether.membership.dto;

import com.gotogether.membership.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/** {@code PATCH /trips/{id}/members/{user_id}/attendance} (API Spec Section 9). */
public record MarkAttendanceRequest(@NotNull AttendanceStatus attendanceStatus) {
}
