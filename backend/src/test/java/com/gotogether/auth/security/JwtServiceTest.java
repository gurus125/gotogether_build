package com.gotogether.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotogether.user.entity.AccountRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-at-least-32-bytes-long-for-hs256!!");
        properties.setAccessTokenTtlMinutes(15);
        properties.setRefreshTokenTtlDays(90);
        jwtService = new JwtService(properties);
    }

    @Test
    void accessTokenRoundTripsUserIdAndRole() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, AccountRole.INDIVIDUAL);

        var claims = jwtService.parseAccessToken(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(userId);
        assertThat(claims.get().role()).isEqualTo(AccountRole.INDIVIDUAL);
    }

    @Test
    void refreshTokenRoundTripsUserIdAndCarriesAJti() {
        UUID userId = UUID.randomUUID();
        var issued = jwtService.issueRefreshToken(userId);

        var claims = jwtService.parseRefreshToken(issued.token());

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(userId);
        assertThat(claims.get().jti()).isEqualTo(issued.jti());
    }

    @Test
    void accessTokenIsRejectedWhenParsedAsARefreshToken() {
        String accessToken = jwtService.issueAccessToken(UUID.randomUUID(), AccountRole.INDIVIDUAL);

        assertThat(jwtService.parseRefreshToken(accessToken)).isEmpty();
    }

    @Test
    void refreshTokenIsRejectedWhenParsedAsAnAccessToken() {
        String refreshToken = jwtService.issueRefreshToken(UUID.randomUUID()).token();

        assertThat(jwtService.parseAccessToken(refreshToken)).isEmpty();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(jwtService.parseAccessToken("not-a-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-of-32-bytes!!");
        JwtService otherService = new JwtService(otherProperties);

        String token = otherService.issueAccessToken(UUID.randomUUID(), AccountRole.INDIVIDUAL);

        assertThat(jwtService.parseAccessToken(token)).isEmpty();
    }
}
