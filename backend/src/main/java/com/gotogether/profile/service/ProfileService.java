package com.gotogether.profile.service;

import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.profile.dto.ProfilePublicSummary;
import com.gotogether.profile.dto.ProfileResponse;
import com.gotogether.profile.dto.UpdateProfileRequest;
import com.gotogether.profile.entity.UserProfile;
import com.gotogether.profile.repository.UserProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The profile module's only entry point for other modules (see {@code UserService} for the same pattern). */
@Service
public class ProfileService {

    private final UserProfileRepository profileRepository;

    public ProfileService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /** Called by the auth module right after a new {@code User} row is created. */
    @Transactional
    public void createInitialProfile(UUID userId, String displayName) {
        profileRepository.save(UserProfile.createFor(userId, displayName));
    }

    public ProfileResponse getMyProfile(UUID userId) {
        return toResponse(getOrThrow(userId));
    }

    /** Used by other modules (e.g. {@code trip} for "Hosted by ..." display) — see {@link ProfilePublicSummary}'s doc. */
    public ProfilePublicSummary getPublicSummary(UUID userId) {
        UserProfile profile = getOrThrow(userId);
        return new ProfilePublicSummary(profile.getUserId(), profile.getDisplayName(), profile.getPhotoUrl());
    }

    /**
     * 0-10 completeness score for the {@code trust} module's "Profile
     * completeness" component (5% weight, Business Rules Trust & Discovery
     * Module A: "a minor nudge, not a trust claim in itself"). Checked
     * against 8 optional fields beyond the always-required {@code
     * displayName} — each present field is worth 1.25 points. A deliberately
     * simple, auditable rule rather than a weighted-by-importance one, matching
     * the doc's own framing of this as the smallest, least consequential factor.
     */
    public BigDecimal getCompletenessScore(UUID userId) {
        UserProfile p = getOrThrow(userId);
        int present = 0;
        if (p.getPhotoUrl() != null && !p.getPhotoUrl().isBlank()) present++;
        if (p.getBio() != null && !p.getBio().isBlank()) present++;
        if (p.getCity() != null && !p.getCity().isBlank()) present++;
        if (p.getDateOfBirth() != null) present++;
        if (!p.getLanguages().isEmpty()) present++;
        if (p.getTravelStyle() != null && !p.getTravelStyle().isBlank()) present++;
        if (p.getFoodPreference() != null && !p.getFoodPreference().isBlank()) present++;
        if (p.getAdventureLevel() != null) present++;
        return BigDecimal.valueOf(present * 1.25).setScale(1, RoundingMode.HALF_UP);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = getOrThrow(userId);

        if (request.displayName() != null) profile.setDisplayName(request.displayName());
        if (request.photoUrl() != null) profile.setPhotoUrl(request.photoUrl());
        if (request.bio() != null) profile.setBio(request.bio());
        if (request.city() != null) profile.setCity(request.city());
        if (request.dateOfBirth() != null) profile.setDateOfBirth(request.dateOfBirth());
        if (request.languages() != null) profile.setLanguages(request.languages());
        if (request.travelStyle() != null) profile.setTravelStyle(request.travelStyle());
        if (request.foodPreference() != null) profile.setFoodPreference(request.foodPreference());
        if (request.smokingPreference() != null) profile.setSmokingPreference(request.smokingPreference());
        if (request.drinkingPreference() != null) profile.setDrinkingPreference(request.drinkingPreference());
        if (request.preferredBudgetStyle() != null) profile.setPreferredBudgetStyle(request.preferredBudgetStyle());
        if (request.adventureLevel() != null) profile.setAdventureLevel(request.adventureLevel());
        if (request.emergencyContactName() != null) profile.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactPhone() != null) profile.setEmergencyContactPhone(request.emergencyContactPhone());

        return toResponse(profileRepository.save(profile));
    }

    private UserProfile getOrThrow(UUID userId) {
        return profileRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));
    }

    private ProfileResponse toResponse(UserProfile p) {
        return new ProfileResponse(
                p.getUserId(), p.getDisplayName(), p.getPhotoUrl(), p.getBio(), p.getCity(), p.getDateOfBirth(),
                p.getLanguages(), p.getTravelStyle(), p.getFoodPreference(), p.getSmokingPreference(),
                p.getDrinkingPreference(), p.getPreferredBudgetStyle(), p.getAdventureLevel(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone());
    }
}
