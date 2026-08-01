package com.gotogether.auth.service;

import com.gotogether.auth.dto.AuthResponse;
import com.gotogether.auth.dto.GoogleSignInRequest;
import com.gotogether.auth.dto.PhoneOtpRequestRequest;
import com.gotogether.auth.dto.PhoneOtpVerifyRequest;
import com.gotogether.auth.dto.RefreshRequest;
import com.gotogether.auth.security.JwtService;
import com.gotogether.auth.security.RefreshTokenStore;
import com.gotogether.common.exception.ConflictException;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.service.UserService;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the two MVP sign-in paths (Business Rules Module 1: Google
 * Sign-In or Phone OTP, no passwords) plus refresh/logout. Deliberately thin
 * — all persistence goes through {@link UserService} / {@link ProfileService}
 * (different modules), never through a repository directly, per the
 * module-boundary rule.
 */
@Service
public class AuthService {

    private static final String DEFAULT_DISPLAY_NAME = "New Traveller";

    private final GoogleTokenVerifier googleTokenVerifier;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final UserService userService;
    private final ProfileService profileService;

    public AuthService(
            GoogleTokenVerifier googleTokenVerifier,
            OtpService otpService,
            JwtService jwtService,
            RefreshTokenStore refreshTokenStore,
            UserService userService,
            ProfileService profileService) {
        this.googleTokenVerifier = googleTokenVerifier;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.userService = userService;
        this.profileService = profileService;
    }

    public AuthResponse signInWithGoogle(GoogleSignInRequest request) {
        var identity = googleTokenVerifier.verify(request.idToken())
                .orElseThrow(() -> new ForbiddenException("Invalid Google ID token"));

        var result = userService.findOrCreateByGoogleId(identity.googleId(), identity.email());
        bootstrapProfileIfNew(result);
        return issueTokensFor(result);
    }

    public void requestPhoneOtp(PhoneOtpRequestRequest request) {
        otpService.requestOtp(request.phoneNumber());
    }

    public AuthResponse verifyPhoneOtp(PhoneOtpVerifyRequest request) {
        if (!otpService.verifyOtp(request.phoneNumber(), request.code())) {
            throw new ForbiddenException("Invalid or expired code");
        }

        var result = userService.findOrCreateByPhoneNumber(request.phoneNumber());
        bootstrapProfileIfNew(result);
        return issueTokensFor(result);
    }

    public AuthResponse refresh(RefreshRequest request) {
        var claims = jwtService.parseRefreshToken(request.refreshToken())
                .orElseThrow(() -> new ForbiddenException("Invalid refresh token"));

        if (!refreshTokenStore.isValid(claims.userId(), claims.jti())) {
            throw new ForbiddenException("Refresh token has been revoked or expired");
        }

        // Rotation: the old jti is single-use — revoke it before issuing the replacement.
        refreshTokenStore.revoke(claims.userId(), claims.jti());

        UserSummary summary = userService.getSummary(claims.userId());
        if (!summary.isActive()) {
            throw new ForbiddenException("Account is not active");
        }

        String accessToken = jwtService.issueAccessToken(summary.id(), summary.role());
        var refreshToken = jwtService.issueRefreshToken(summary.id());
        refreshTokenStore.save(summary.id(), refreshToken.jti());

        return new AuthResponse(accessToken, refreshToken.token(), summary.id(), false);
    }

    /**
     * Revokes the given refresh token's jti. Deliberately takes only the
     * refresh token itself (not an access-token-derived principal) — logout
     * must work even if the access token has already expired, and the
     * refresh token's own claims already identify the user unambiguously.
     */
    public void logout(String refreshToken) {
        jwtService.parseRefreshToken(refreshToken)
                .ifPresent(claims -> refreshTokenStore.revoke(claims.userId(), claims.jti()));
    }

    private void bootstrapProfileIfNew(UserService.FindOrCreateResult result) {
        if (result.newlyCreated()) {
            profileService.createInitialProfile(result.summary().id(), DEFAULT_DISPLAY_NAME);
        }
    }

    private AuthResponse issueTokensFor(UserService.FindOrCreateResult result) {
        UserSummary summary = result.summary();
        if (!summary.isActive()) {
            throw new ConflictException("Account is suspended");
        }

        userService.recordLogin(summary.id());
        String accessToken = jwtService.issueAccessToken(summary.id(), summary.role());
        var refreshToken = jwtService.issueRefreshToken(summary.id());
        refreshTokenStore.save(summary.id(), refreshToken.jti());

        return new AuthResponse(accessToken, refreshToken.token(), summary.id(), result.newlyCreated());
    }
}
