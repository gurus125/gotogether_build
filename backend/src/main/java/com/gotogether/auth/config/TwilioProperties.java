package com.gotogether.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code gotogether.twilio} — real SMS delivery for phone OTP
 * (Twilio's REST Messages API), an opt-in alternative to {@link
 * com.gotogether.auth.service.LoggingOtpSender} (the dev-only default,
 * which just logs the code instead of texting it). See {@code
 * OtpSenderConfig} for exactly how the choice between the two is made —
 * {@code accountSid} blank/unset means Twilio was never configured, so
 * logging stays the fallback rather than the app failing to start or
 * silently sending nothing.
 */
@Component
@ConfigurationProperties(prefix = "gotogether.twilio")
public class TwilioProperties {

    private String accountSid;
    private String authToken;
    private String fromNumber;

    public String getAccountSid() {
        return accountSid;
    }

    public void setAccountSid(String accountSid) {
        this.accountSid = accountSid;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }
}
