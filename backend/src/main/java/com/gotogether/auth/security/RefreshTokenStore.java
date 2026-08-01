package com.gotogether.auth.security;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tracks which refresh-token {@code jti}s are currently valid, in Redis
 * rather than Postgres (see {@link JwtService}'s class comment for why).
 * Rotation on refresh = revoke old jti + save new jti in the same call site;
 * logout = revoke.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final JwtProperties properties;

    public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void save(UUID userId, String jti) {
        redis.opsForValue().set(key(userId, jti), "1", Duration.ofDays(properties.getRefreshTokenTtlDays()));
    }

    public boolean isValid(UUID userId, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(userId, jti)));
    }

    public void revoke(UUID userId, String jti) {
        redis.delete(key(userId, jti));
    }

    /** Revokes every refresh token for a user — used on account deletion/suspension. */
    public void revokeAll(UUID userId) {
        var keys = redis.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String key(UUID userId, String jti) {
        return KEY_PREFIX + userId + ":" + jti;
    }
}
