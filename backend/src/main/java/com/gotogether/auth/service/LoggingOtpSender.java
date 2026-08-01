package com.gotogether.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Development-only OTP delivery — logs the code instead of sending a real
 * SMS. Replace with a real provider (Twilio, MSG91, Firebase Phone Auth,
 * etc.) before any environment beyond local dev; this class exists so the
 * OTP flow is fully exercisable without a paid SMS account, not as the
 * intended production implementation.
 */
@Service
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String phoneNumber, String code) {
        log.info("[DEV OTP] Would send SMS to {}: your GoTogether code is {}", phoneNumber, code);
    }
}
