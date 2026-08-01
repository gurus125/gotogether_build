package com.gotogether.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code gotogether.storage} block in {@code application.yml} —
 * that block already existed (bucket/endpoint/region/access-key/secret-key,
 * shaped for an S3-compatible target like MinIO in local dev), but nothing
 * bound it to a Java type and no code read it, so it sat unused alongside an
 * equally-unused {@code s3} SDK dependency. This is the missing piece, same
 * pattern as {@code auth.security.JwtProperties} for {@code gotogether.jwt}.
 *
 * <p>{@code endpoint} vs {@code publicEndpoint}: this backend and the mobile
 * client do NOT always reach MinIO the same way, the exact same problem
 * {@code AppConfig.apiBaseUrl} already solves on the Flutter side for the
 * main API (Android emulators can't resolve the host machine's {@code
 * localhost}, only {@code 10.0.2.2}). {@code endpoint} is what this backend
 * process itself uses for any real {@code S3Client} calls; {@code
 * publicEndpoint} is what gets baked into every presigned URL {@code
 * StorageService} hands back to the mobile app, and must be whatever host
 * *that device* can actually reach — they diverged the moment "photo not
 * uploading" turned out to be the emulator silently failing to connect to
 * {@code localhost:9000} (which, from inside the emulator, isn't this
 * machine at all). Overridable independently via {@code STORAGE_ENDPOINT}
 * and {@code STORAGE_PUBLIC_ENDPOINT} — set the latter to {@code
 * http://10.0.2.2:9000} when testing against the Android emulator, or your
 * machine's LAN IP for a physical device (same as {@code API_BASE_URL}).
 *
 * <p>{@code serveViaProxy} / {@code apiPublicBaseUrl}: local MinIO is made
 * public-read once, by hand, after setup ({@code mc anonymous set download}
 * — see the comment in {@code application.yml}), so a raw unsigned bucket
 * URL works fine for local dev. Not every S3-compatible target supports
 * that: Railway's own Storage Buckets, for one, explicitly don't support
 * public buckets at all (confirmed in their docs, 2026-08-01) — every read
 * needs a presigned GET. Rather than always paying that cost (an extra
 * hop through this backend on every image load, even locally where it's
 * unnecessary), this is opt-in: {@code serveViaProxy=false} (the default)
 * keeps today's exact behavior — a raw {@code publicEndpoint/bucket/key}
 * URL. {@code serveViaProxy=true} instead returns a URL pointing back at
 * this backend's own {@code GET /storage/view} endpoint (see {@code
 * StorageViewController}), which presigns a fresh short-lived GET and
 * redirects — so set {@code apiPublicBaseUrl} to whatever host the mobile
 * client reaches THIS backend at (the same value Flutter's own {@code
 * AppConfig.apiBaseUrl} resolves to for that device/environment; on
 * Railway, its generated public domain).
 */
@Component
@ConfigurationProperties(prefix = "gotogether.storage")
public class StorageProperties {

    private String bucket;
    private String endpoint;
    private String publicEndpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private boolean serveViaProxy;
    private String apiPublicBaseUrl;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isServeViaProxy() {
        return serveViaProxy;
    }

    public void setServeViaProxy(boolean serveViaProxy) {
        this.serveViaProxy = serveViaProxy;
    }

    public String getApiPublicBaseUrl() {
        return apiPublicBaseUrl;
    }

    public void setApiPublicBaseUrl(String apiPublicBaseUrl) {
        this.apiPublicBaseUrl = apiPublicBaseUrl;
    }
}
