package com.gotogether.auth.config;

import com.gotogether.auth.service.LoggingOtpSender;
import com.gotogether.auth.service.OtpSender;
import com.gotogether.auth.service.TwilioOtpSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses which {@link OtpSender} implementation the app runs with, based on
 * whether Twilio credentials are actually configured. Deliberately a plain
 * {@code @Bean} method rather than {@code @ConditionalOnProperty} on each
 * implementation — {@code gotogether.twilio.account-sid} is always bound to
 * <em>some</em> value via {@code ${TWILIO_ACCOUNT_SID:}} in {@code
 * application.yml} (blank when unset), and {@code @ConditionalOnProperty}
 * without an explicit {@code havingValue} treats "resolves to anything other
 * than the literal string 'false'" as present — which a blank string
 * satisfies, defeating the whole point. Checking {@code isBlank()} directly
 * here has no such gotcha.
 */
@Configuration
public class OtpSenderConfig {

    @Bean
    public OtpSender otpSender(TwilioProperties twilioProperties) {
        if (twilioProperties.getAccountSid() != null && !twilioProperties.getAccountSid().isBlank()) {
            return new TwilioOtpSender(twilioProperties);
        }
        return new LoggingOtpSender();
    }
}
