package com.gotogether.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.auth.dto.GoogleSignInRequest;
import com.gotogether.auth.dto.PhoneOtpVerifyRequest;
import com.gotogether.auth.dto.RefreshRequest;
import com.gotogether.auth.security.JwtService;
import com.gotogether.auth.security.RefreshTokenStore;
import com.gotogether.common.exception.ForbiddenException;
import com.gotogether.profile.service.ProfileService;
import com.gotogether.user.dto.UserSummary;
import com.gotogether.user.entity.AccountRole;
import com.gotogether.user.entity.UserStatus;
import com.gotogether.user.entity.VerificationLevel;
import com.gotogether.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private OtpService otpService;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private UserService userService;
    @Mock private ProfileService profileService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                googleTokenVerifier, otpService, jwtService, refreshTokenStore, userService, profileService);
    }

    @Test
    void signInWithGoogleBootstrapsAProfileForANewlyCreatedUser() {
        UUID userId = UUID.randomUUID();
        UserSummary summary = new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.EMAIL);

        when(googleTokenVerifier.verify("good-token"))
                .thenReturn(Optional.of(new GoogleTokenVerifier.GoogleIdentity("g-123", "traveller@example.com")));
        when(userService.findOrCreateByGoogleId("g-123", "traveller@example.com"))
                .thenReturn(new UserService.FindOrCreateResult(summary, true));
        when(jwtService.issueAccessToken(userId, AccountRole.INDIVIDUAL)).thenReturn("access-token");
        when(jwtService.issueRefreshToken(userId)).thenReturn(new JwtService.IssuedRefreshToken("refresh-token", "jti-1"));

        var response = authService.signInWithGoogle(new GoogleSignInRequest("good-token"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.newUser()).isTrue();
        verify(profileService).createInitialProfile(userId, "New Traveller");
        verify(userService).recordLogin(userId);
        verify(refreshTokenStore).save(userId, "jti-1");
    }

    @Test
    void signInWithGoogleDoesNotBootstrapAProfileForAReturningUser() {
        UUID userId = UUID.randomUUID();
        UserSummary summary = new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.EMAIL);

        when(googleTokenVerifier.verify("good-token"))
                .thenReturn(Optional.of(new GoogleTokenVerifier.GoogleIdentity("g-123", "traveller@example.com")));
        when(userService.findOrCreateByGoogleId("g-123", "traveller@example.com"))
                .thenReturn(new UserService.FindOrCreateResult(summary, false));
        when(jwtService.issueAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.issueRefreshToken(any())).thenReturn(new JwtService.IssuedRefreshToken("refresh-token", "jti-1"));

        var response = authService.signInWithGoogle(new GoogleSignInRequest("good-token"));

        assertThat(response.newUser()).isFalse();
        verify(profileService, never()).createInitialProfile(any(), any());
    }

    @Test
    void signInWithGoogleRejectsAnInvalidIdToken() {
        when(googleTokenVerifier.verify("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signInWithGoogle(new GoogleSignInRequest("bad-token")))
                .isInstanceOf(ForbiddenException.class);

        verify(userService, never()).findOrCreateByGoogleId(any(), any());
    }

    @Test
    void verifyPhoneOtpRejectsAnInvalidCode() {
        when(otpService.verifyOtp("+919999999999", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyPhoneOtp(new PhoneOtpVerifyRequest("+919999999999", "000000")))
                .isInstanceOf(ForbiddenException.class);

        verify(userService, never()).findOrCreateByPhoneNumber(any());
    }

    @Test
    void verifyPhoneOtpIssuesTokensOnAValidCode() {
        UUID userId = UUID.randomUUID();
        UserSummary summary = new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.PHONE);

        when(otpService.verifyOtp("+919999999999", "123456")).thenReturn(true);
        when(userService.findOrCreateByPhoneNumber("+919999999999"))
                .thenReturn(new UserService.FindOrCreateResult(summary, false));
        when(jwtService.issueAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.issueRefreshToken(any())).thenReturn(new JwtService.IssuedRefreshToken("refresh-token", "jti-2"));

        var response = authService.verifyPhoneOtp(new PhoneOtpVerifyRequest("+919999999999", "123456"));

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void refreshRejectsATokenWhoseJtiHasBeenRevoked() {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseRefreshToken("stale-token"))
                .thenReturn(Optional.of(new JwtService.RefreshTokenClaims(userId, "jti-old")));
        when(refreshTokenStore.isValid(userId, "jti-old")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("stale-token")))
                .isInstanceOf(ForbiddenException.class);

        verify(userService, never()).getSummary(any());
    }

    @Test
    void refreshRotatesTheJtiOnSuccess() {
        UUID userId = UUID.randomUUID();
        UserSummary summary = new UserSummary(userId, AccountRole.INDIVIDUAL, UserStatus.REGISTERED, VerificationLevel.PHONE);

        when(jwtService.parseRefreshToken("old-token"))
                .thenReturn(Optional.of(new JwtService.RefreshTokenClaims(userId, "jti-old")));
        when(refreshTokenStore.isValid(userId, "jti-old")).thenReturn(true);
        when(userService.getSummary(userId)).thenReturn(summary);
        when(jwtService.issueAccessToken(userId, AccountRole.INDIVIDUAL)).thenReturn("new-access");
        when(jwtService.issueRefreshToken(userId)).thenReturn(new JwtService.IssuedRefreshToken("new-refresh", "jti-new"));

        var response = authService.refresh(new RefreshRequest("old-token"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore, times(1)).revoke(userId, "jti-old");
        verify(refreshTokenStore, times(1)).save(userId, "jti-new");
    }

    @Test
    void logoutRevokesTheJtiCarriedByTheRefreshTokenItself() {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseRefreshToken("some-token"))
                .thenReturn(Optional.of(new JwtService.RefreshTokenClaims(userId, "jti-x")));

        authService.logout("some-token");

        verify(refreshTokenStore).revoke(userId, "jti-x");
    }

    @Test
    void logoutIsANoOpWhenTheRefreshTokenIsAlreadyInvalid() {
        when(jwtService.parseRefreshToken("garbage")).thenReturn(Optional.empty());

        authService.logout("garbage");

        verify(refreshTokenStore, never()).revoke(any(), eq("jti-x"));
    }
}
