package com.gotogether.auth.service;

import com.gotogether.auth.config.TwilioProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real SMS delivery for phone OTP via Twilio's REST Messages API — the
 * opt-in alternative to {@link LoggingOtpSender} (see {@code
 * OtpSenderConfig} for which one actually gets wired up, based on whether
 * {@link TwilioProperties#getAccountSid()} is set).
 *
 * <p>Uses the JDK's built-in {@code java.net.http.HttpClient} rather than
 * Twilio's own Java SDK — same reasoning as {@code PexelsService}: one
 * small, synchronous, server-to-server call doesn't justify a new Maven
 * dependency, and Twilio's Messages API is a plain REST endpoint with
 * HTTP Basic Auth, nothing the SDK does that's hard to replicate directly.
 *
 * <p>Failures here are logged but deliberately NOT rethrown as an exception
 * that would fail the whole OTP request — {@code OtpService.requestOtp}
 * already persisted the code to Redis before calling {@code send}, so a
 * transient Twilio outage shouldn't make the endpoint itself return an
 * error the client has to handle specially; it just means the code never
 * arrives, which surfaces naturally when the user's "verify" attempt fails.
 */
public class TwilioOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioOtpSender.class);

    private final TwilioProperties properties;
    private final HttpClient httpClient;

    public TwilioOtpSender(TwilioProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Package-private overload so tests can inject a mock {@link HttpClient}, same pattern as {@code PexelsService}. */
    TwilioOtpSender(TwilioProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public void send(String phoneNumber, String code) {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + properties.getAccountSid() + "/Messages.json";

        String body = "To=" + encode(phoneNumber)
                + "&From=" + encode(properties.getFromNumber())
                + "&Body=" + encode("Your GoTogether verification code is " + code + ". It expires in 10 minutes.");

        String credentials = properties.getAccountSid() + ":" + properties.getAuthToken();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // Twilio returns 201 Created on success, same convention as
            // most REST APIs that create a resource (here, the Message).
            if (response.statusCode() != 201) {
                log.error("Twilio OTP send returned HTTP {} for {}: {}", response.statusCode(), phoneNumber, response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Twilio OTP send failed for {}", phoneNumber, e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
