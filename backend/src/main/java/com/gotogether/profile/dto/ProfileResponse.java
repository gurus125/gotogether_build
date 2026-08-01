package com.gotogether.profile.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Self-view only for now (backs {@code GET/PATCH /profile/me}) — includes
 * emergency contact fields, which Business Rules Module 1 Section 7 says
 * must never be exposed to other users. A separate, stripped-down DTO for
 * viewing someone else's profile arrives in Phase 5 alongside Trust Score /
 * Reviews, since that's what the "other traveller's profile" screen actually
 * needs beyond what this module owns.
 */
public record ProfileResponse(
        UUID userId,
        String displayName,
        String photoUrl,
        String bio,
        String city,
        LocalDate dateOfBirth,
        List<String> languages,
        String travelStyle,
        String foodPreference,
        String smokingPreference,
        String drinkingPreference,
        String preferredBudgetStyle,
        Short adventureLevel,
        String emergencyContactName,
        String emergencyContactPhone) {}
