package com.gotogether.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code gotogether.photos} block in {@code application.yml} — the
 * Pexels API key backing {@code PexelsService} (stock-photo search for trip
 * cover/gallery photos, so an organizer isn't limited to their own camera
 * roll). Same "one property class per env-var-backed block" pattern as
 * {@code StorageProperties}/{@code auth.security.JwtProperties}.
 *
 * <p>Deliberately empty-string default ({@code ${PEXELS_API_KEY:}}), not a
 * fake placeholder key like {@code storage}'s local-dev defaults — there's
 * no equivalent "just works out of the box" local stand-in for a third-party
 * API key the way docker-compose provides one for MinIO. {@code
 * PexelsService} checks for a blank key itself and fails clearly (422) rather
 * than sending an unauthenticated request upstream and surfacing Pexels' own
 * opaque 401.
 */
@Component
@ConfigurationProperties(prefix = "gotogether.photos")
public class PhotoSearchProperties {

    private String pexelsApiKey;

    public String getPexelsApiKey() {
        return pexelsApiKey;
    }

    public void setPexelsApiKey(String pexelsApiKey) {
        this.pexelsApiKey = pexelsApiKey;
    }
}
