package com.gotogether.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * All fields optional (PATCH semantics) — only non-null fields are applied.
 * Validation ranges mirror the DB CHECK constraints on {@code user_profiles}
 * (V2 migration) so a bad request is rejected before it ever reaches SQL.
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 50) String displayName,
        String photoUrl,
        @Size(max = 250) String bio,
        String city,
        LocalDate dateOfBirth,
        List<String> languages,
        String travelStyle,
        String foodPreference,
        String smokingPreference,
        String drinkingPreference,
        String preferredBudgetStyle,
        @Min(1) @Max(5) Short adventureLevel,
        String emergencyContactName,
        String emergencyContactPhone) {}
