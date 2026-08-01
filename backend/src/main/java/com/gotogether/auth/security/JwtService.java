package com.gotogether.auth.security;

import com.gotogether.user.entity.AccountRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates the two JWT types (Backend Architecture: 15-minute
 * access token, 90-day rotating refresh token). Refresh tokens carry a
 * {@code jti} whose validity is tracked in Redis by {@link RefreshTokenStore}
 * — the schema has no {@code refresh_tokens} table, so revocation/rotation
 * is Redis-backed rather than Postgres-backed (see Phase 1 kickoff note).
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId, AccountRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Returns the signed token together with its {@code jti}, which the caller persists via {@link RefreshTokenStore}. */
    public IssuedRefreshToken issueRefreshToken(UUID userId) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTokenTtlDays(), ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
        return new IssuedRefreshToken(token, jti);
    }

    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS)
                .map(claims -> new AccessTokenClaims(
                        UUID.fromString(claims.getSubject()),
                        AccountRole.valueOf(claims.get(CLAIM_ROLE, String.class))));
    }

    public Optional<RefreshTokenClaims> parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH)
                .map(claims -> new RefreshTokenClaims(UUID.fromString(claims.getSubject()), claims.getId()));
    }

    private Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedRefreshToken(String token, String jti) {}

    public record AccessTokenClaims(UUID userId, AccountRole role) {}

    public record RefreshTokenClaims(UUID userId, String jti) {}
}
