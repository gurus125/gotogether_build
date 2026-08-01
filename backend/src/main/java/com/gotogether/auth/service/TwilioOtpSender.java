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
 * Real SMS delivery for phone OTP via Twilio's Verify API — the opt-in
 * alternative to {@link LoggingOtpSender} (see {@code OtpSenderConfig} for
 * which one actually gets wired up, based on whether {@link
 * TwilioProperties#getAccountSid()} is set).
 *
 * <p>Deliberately Verify's "Start Verification" endpoint, not the plain
 * Messages API this originally called — Twilio trial accounts reject
 * arbitrary custom message bodies ("Invalid template name. Trial accounts
 * can only use predefined SMS templates," error 572006, hit in practice
 * 2026-08-01), since Verify sends through Twilio's own pre-approved
 * template instead of a caller-supplied body. Crucially, {@code
 * CustomCode} lets us keep supplying OUR OWN pre-generated code rather than
 * letting Twilio generate one — meaning {@code OtpService}'s existing
 * Redis-based generate/store/compare flow needed zero changes; only the
 * outbound HTTP call here changed shape. (Twilio's Verify Check endpoint,
 * which would validate a code Twilio itself generated, is deliberately
 * unused — we already know the correct code and compare it ourselves.)
 *
 * <p>Uses the JDK's built-in {@code java.net.http.HttpClient} rather than
 * Twilio's own Java SDK — same reasoning as {@code PexelsService}: one
 * small, synchronous, server-to-server call doesn't justify a new Maven
 * dependency, and Verify's REST endpoint is plain HTTP Basic Auth, nothing
 * the SDK does that's hard to replicate directly.
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
        String url = "https://verify.twilio.com/v2/Services/" + properties.getVerifyServiceSid() + "/Verifications";

        String body = "To=" + encode(phoneNumber) + "&Channel=sms&CustomCode=" + encode(code);

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
            // Twilio returns 201 Created for a new Verification resource,
            // but checked as a range rather than hard-coded — this endpoint
            // wasn't originally what this class called (see class doc), and
            // being lenient about the exact 2xx code avoids false-alarm logs
            // if Twilio's actual behavior differs slightly from the docs.
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Twilio Verify send returned HTTP {} for {}: {}", response.statusCode(), phoneNumber, response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Twilio Verify send failed for {}", phoneNumber, e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
