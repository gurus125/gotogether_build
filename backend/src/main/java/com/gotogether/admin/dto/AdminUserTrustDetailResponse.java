package com.gotogether.admin.dto;

import com.gotogether.trust.dto.TrustScoreResponse;
import com.gotogether.user.dto.AdminUserDetailResponse;
import java.util.List;

/** {@code GET /admin/users/{id}} (API Spec Section 16: "Full account view incl. Trust Score history") — {@code AdminService}'s composition of {@code UserService}'s account view with {@code TrustService}'s score/history (see {@code AdminUserDetailResponse}'s own doc for why that composition can't live in {@code user} itself). */
public record AdminUserTrustDetailResponse(
        AdminUserDetailResponse account, TrustScoreResponse trustScore, List<com.gotogether.trust.dto.TrustScoreHistoryEntry> trustScoreHistory) {
}
