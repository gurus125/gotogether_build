package com.gotogether.trust.controller;

import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.trust.dto.TrustScoreHistoryEntry;
import com.gotogether.trust.dto.TrustScoreResponse;
import com.gotogether.trust.service.TrustService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trust APIs (API Specification Section 12) plus the self-view variant from Section 4 ({@code GET /users/me/trust-score}). */
@RestController
public class TrustController {

    private final TrustService trustService;

    public TrustController(TrustService trustService) {
        this.trustService = trustService;
    }

    @GetMapping("/users/me/trust-score")
    public TrustScoreResponse mine(@AuthenticationPrincipal UserPrincipal principal) {
        return trustService.getSelfBreakdown(principal.userId());
    }

    @GetMapping("/users/{id}/trust-score")
    public TrustScoreResponse breakdown(@PathVariable UUID id) {
        return trustService.getPublicBreakdown(id);
    }

    @GetMapping("/users/me/trust-score/history")
    public CursorPageResponse<TrustScoreHistoryEntry> history(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return trustService.getHistory(principal.userId(), cursor, limit);
    }
}
