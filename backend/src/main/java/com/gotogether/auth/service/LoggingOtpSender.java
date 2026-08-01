package com.gotogether.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only OTP delivery — logs the code instead of sending a real
 * SMS. No longer the intended production implementation now that {@link
 * TwilioOtpSender} exists — see {@code OtpSenderConfig} for how the two are
 * chosen between (Twilio when {@code TWILIO_ACCOUNT_SID} is set, this class
 * otherwise). Kept deliberately dependency-free so local dev and tests never
 * need real SMS credentials.
 *
 * <p>Not a {@code @Service} — both this and {@code TwilioOtpSender} are
 * plain classes instantiated explicitly by {@code OtpSenderConfig}'s single
 * {@code @Bean} method, rather than both being auto-registered components
 * Spring would then have to disambiguate between.
 */
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String phoneNumber, String code) {
        log.info("[DEV OTP] Would send SMS to {}: your GoTogether code is {}", phoneNumber, code);
    }
}
