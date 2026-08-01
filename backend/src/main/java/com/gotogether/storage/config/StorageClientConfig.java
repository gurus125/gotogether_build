package com.gotogether.storage.config;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@code S3Client}/{@code S3Presigner} SDK clients (both part of
 * the single {@code software.amazon.awssdk:s3} Maven artifact — there is no
 * separate {@code s3-presigner} artifact, despite the class living under the
 * {@code .presigner} sub-package) against {@link
 * StorageProperties}. {@code forcePathStyle(true)} on both clients is
 * required for MinIO (the local-dev target implied by {@code
 * application.yml}'s default {@code http://localhost:9000} endpoint) — MinIO
 * doesn't support virtual-hosted-style addressing
 * ({@code bucket.endpoint/key}) the way real AWS S3 does, only
 * {@code endpoint/bucket/key}. This also happens to work fine against real
 * S3, so it's left on unconditionally rather than branched on environment.
 */
@Configuration
public class StorageClientConfig {

    private final StorageProperties properties;

    public StorageClientConfig(StorageProperties properties) {
        this.properties = properties;
    }

    private AwsCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider())
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                // publicEndpoint, not endpoint — this host gets baked into
                // every presigned URL handed to the mobile client (AWS SigV4
                // signs the Host header, so it can't be swapped after the
                // fact client-side the way a plain unsigned URL could). Must
                // be whatever host the calling device can actually reach,
                // which is NOT always the same as this backend's own view of
                // MinIO — see StorageProperties' class doc.
                .endpointOverride(URI.create(properties.getPublicEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider())
                // Missing here previously — s3Client() above had it, this
                // bean didn't, so every presigned PUT URL came back
                // virtual-hosted-style ({bucket}.{endpoint}/key, e.g.
                // "gotogether-dev.localhost:9000") instead of path-style
                // ({endpoint}/{bucket}/key). MinIO doesn't serve the former
                // at all — the mobile app's PUT to that URL just failed DNS
                // resolution outright ("Failed host lookup:
                // gotogether-dev.localhost"), which is what actually
                // surfaced this: PhotoSearchScreen's upload was the first
                // path that got exercised against a real MinIO instance
                // end-to-end. Same fix as s3Client(), same reason.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // Not exposed as a config property — 15 minutes is generous
                // enough for a mobile client to pick up the URL and PUT the
                // file without needing a refresh flow, short enough that a
                // leaked URL doesn't stay valid indefinitely.
                .build();
    }

    /** How long a presigned PUT URL remains valid. Kept here rather than on the properties class since it's an implementation detail, not deployment config. */
    public static Duration uploadUrlTtl() {
        return Duration.ofMinutes(15);
    }
}
