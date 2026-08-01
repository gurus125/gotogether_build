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
 *
 * <p>{@code verifyServiceSid} is a one-time setup artifact — Twilio Verify
 * (see {@link com.gotogether.auth.service.TwilioOtpSender}) sends through a
 * "Verify Service" resource, created once in the console under Verify &gt;
 * Services (starts with {@code VA}), rather than directly off a specific
 * phone number the way the plain Messages API this originally called did —
 * no {@code fromNumber} property exists here for that reason.
 */
@Component
@ConfigurationProperties(prefix = "gotogether.twilio")
public class TwilioProperties {

    private String accountSid;
    private String authToken;
    private String verifyServiceSid;

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

    public String getVerifyServiceSid() {
        return verifyServiceSid;
    }

    public void setVerifyServiceSid(String verifyServiceSid) {
        this.verifyServiceSid = verifyServiceSid;
    }
}
