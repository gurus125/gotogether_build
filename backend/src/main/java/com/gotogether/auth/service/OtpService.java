package com.gotogether.auth.service;

import com.gotogether.common.exception.RateLimitedException;
import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Phone OTP generation/verification (Business Rules Module 1 Section 2):
 * 6-digit code, 10-minute expiry, 5-per-hour rate limit per phone number.
 * Both the code and the rate-limit counter live in Redis with TTLs matching
 * the business rule directly — no separate cleanup job needed.
 */
@Service
public class OtpService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_WINDOW = 5;

    private final StringRedisTemplate redis;
    private final OtpSender sender;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redis, OtpSender sender) {
        this.redis = redis;
        this.sender = sender;
    }

    public void requestOtp(String phoneNumber) {
        String rateLimitKey = "otp:rate:" + phoneNumber;
        Long attempts = redis.opsForValue().increment(rateLimitKey);
        if (attempts != null && attempts == 1L) {
            redis.expire(rateLimitKey, RATE_LIMIT_WINDOW);
        }
        if (attempts != null && attempts > MAX_REQUESTS_PER_WINDOW) {
            throw new RateLimitedException("Too many OTP requests for this number — try again later.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(phoneNumber), code, CODE_TTL);
        sender.send(phoneNumber, code);
    }

    public boolean verifyOtp(String phoneNumber, String code) {
        String stored = redis.opsForValue().get(codeKey(phoneNumber));
        boolean valid = stored != null && stored.equals(code);
        if (valid) {
            redis.delete(codeKey(phoneNumber));
        }
        return valid;
    }

    private String codeKey(String phoneNumber) {
        return "otp:code:" + phoneNumber;
    }
}
