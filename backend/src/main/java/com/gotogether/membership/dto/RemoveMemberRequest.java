package com.gotogether.membership.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /trips/{id}/members/{user_id}/remove} — reason mandatory even if internal-only (Chapter 2 Section 2.7). */
public record RemoveMemberRequest(@NotBlank String reason) {
}
