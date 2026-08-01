package com.gotogether.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotogether.auth.service.LoggingOtpSender;
import com.gotogether.auth.service.OtpSender;
import com.gotogether.auth.service.TwilioOtpSender;
import org.junit.jupiter.api.Test;

class OtpSenderConfigTest {

    private final OtpSenderConfig config = new OtpSenderConfig();

    @Test
    void fallsBackToLoggingSenderWhenTwilioAccountSidIsNotSet() {
        TwilioProperties properties = new TwilioProperties();
        // accountSid left null — the local-dev/test default, since
        // application.yml binds it to ${TWILIO_ACCOUNT_SID:} (blank).

        OtpSender sender = config.otpSender(properties);

        assertThat(sender).isInstanceOf(LoggingOtpSender.class);
    }

    @Test
    void fallsBackToLoggingSenderWhenTwilioAccountSidIsBlank() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid("   ");

        OtpSender sender = config.otpSender(properties);

        assertThat(sender).isInstanceOf(LoggingOtpSender.class);
    }

    @Test
    void usesTwilioSenderWhenAccountSidIsConfigured() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAccountSid("ACtest123");
        properties.setAuthToken("secret-token");
        properties.setFromNumber("+15551234567");

        OtpSender sender = config.otpSender(properties);

        assertThat(sender).isInstanceOf(TwilioOtpSender.class);
    }
}
