package com.gotogether.auth.service;

import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies Google ID tokens against Google's own {@code tokeninfo} endpoint.
 *
 * <p>Deliberately not using the {@code google-api-client} SDK's local JWKS
 * verification here to keep Phase 1's dependency footprint small — this is
 * simple and correct, but calls out to Google on every sign-in. If sign-in
 * volume ever makes that latency/dependency unacceptable, swap this
 * implementation for local JWK-based verification; nothing else in the
 * codebase depends on how verification happens (see {@link GoogleTokenVerifier}).
 */
@Service
public class GoogleTokenInfoVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenInfoVerifier.class);
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestClient restClient = RestClient.create();

    // Must be set to the app's real OAuth 2.0 Web/Android client ID before
    // Google Sign-In works against anything but no client configured yet —
    // left blank-safe (skips audience check) only so local Phase 1 wiring
    // can be exercised before that value exists.
    @Value("${gotogether.auth.google-client-id:}")
    private String expectedClientId;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<GoogleIdentity> verify(String idToken) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(Map.class);

            if (body == null || body.get("sub") == null) {
                return Optional.empty();
            }

            if (!expectedClientId.isBlank() && !expectedClientId.equals(body.get("aud"))) {
                log.warn("Google ID token audience mismatch");
                return Optional.empty();
            }

            String googleId = (String) body.get("sub");
            String email = (String) body.get("email");
            return Optional.of(new GoogleIdentity(googleId, email));
        } catch (RestClientException e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
