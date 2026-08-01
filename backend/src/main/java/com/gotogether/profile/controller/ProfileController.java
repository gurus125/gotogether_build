package com.gotogether.profile.controller;

import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.profile.dto.ProfileResponse;
import com.gotogether.profile.dto.UpdateProfileRequest;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.storage.dto.PresignedUploadResponse;
import com.gotogether.storage.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile/me")
public class ProfileController {

    private final ProfileService profileService;
    private final StorageService storageService;

    public ProfileController(ProfileService profileService, StorageService storageService) {
        this.profileService = profileService;
        this.storageService = storageService;
    }

    @GetMapping
    public ProfileResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.getMyProfile(principal.userId());
    }

    @PatchMapping
    public ProfileResponse updateProfile(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(principal.userId(), request);
    }

    /**
     * Step 1 of profile-photo upload: returns a presigned PUT URL. The
     * client uploads the image bytes directly to {@code upload_url}, then
     * calls the existing {@code PATCH /profile/me} above with {@code
     * photo_url} set to this response's {@code public_url} — there's no
     * separate "confirm" endpoint since {@code UpdateProfileRequest} already
     * accepts {@code photoUrl} and this backend never needs to see the
     * actual image bytes.
     */
    @PostMapping("/photo/upload-url")
    public PresignedUploadResponse createPhotoUploadUrl(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam("content_type") String contentType) {
        return storageService.createPresignedImageUploadUrl("profile-photos/" + principal.userId(), contentType);
    }
}
