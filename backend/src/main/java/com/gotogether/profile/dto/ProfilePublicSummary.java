package com.gotogether.profile.dto;

import java.util.UUID;

/**
 * The profile module's cross-module-safe view — deliberately much narrower
 * than {@link ProfileResponse} (which is self-view only, via {@code GET
 * /users/me}... actually {@code GET /profile/me}, and includes sensitive
 * fields like emergency contact info). Other modules (e.g. {@code trip},
 * displaying "Hosted by Maya R." on Trip Details) get only what's safe to
 * show about a user to other users — mirrors {@code UserSummary}'s role for
 * the user module.
 */
public record ProfilePublicSummary(UUID userId, String displayName, String photoUrl) {}
