package com.gotogether.storage.service;

import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.storage.config.StorageClientConfig;
import com.gotogether.storage.config.StorageProperties;
import com.gotogether.storage.dto.PresignedUploadResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * The one place in the backend that talks to the S3-compatible bucket
 * ({@code gotogether.storage}). No module uploads bytes through this
 * service — it only issues short-lived presigned PUT URLs; the client
 * (Flutter app) uploads directly to the bucket, and the resulting public URL
 * is persisted by the calling module through its own existing write path
 * ({@code UpdateProfileRequest.photoUrl}, {@code POST /trips/{id}/images}) —
 * this service has no database dependency of its own on purpose.
 *
 * <p>Not in the {@code ArchitectureTest} {@code MODULES} list requiring
 * repository/entity isolation — this module has neither, by design, so
 * those two rules are vacuous for it. It's still listed there for the
 * general "don't reach into another module's internals" controller rule.
 */
@Service
public class StorageService {

    private static final Map<String, String> ALLOWED_IMAGE_CONTENT_TYPES =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public StorageService(S3Presigner s3Presigner, StorageProperties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * @param keyPrefix logical folder, e.g. {@code "profile-photos"} or
     *     {@code "trip-images/" + tripId} — callers own the prefix so two
     *     modules' uploads can never collide or be confused for each other.
     * @param contentType must be one of {@link #ALLOWED_IMAGE_CONTENT_TYPES};
     *     anything else (video, arbitrary files) is rejected before a
     *     presigned URL is even generated — this service is scoped to
     *     photo upload only, not general file storage.
     */
    public PresignedUploadResponse createPresignedImageUploadUrl(String keyPrefix, String contentType) {
        // Map.of(...) throws NPE on a null-key lookup rather than just
        // returning null like a plain HashMap would — checked explicitly
        // here rather than relying on a ternary feeding null into .get().
        String extension = contentType == null ? null : ALLOWED_IMAGE_CONTENT_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new UnprocessableEntityException(
                    "content_type must be one of: " + String.join(", ", ALLOWED_IMAGE_CONTENT_TYPES.keySet()));
        }

        String key = keyPrefix + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(StorageClientConfig.uploadUrlTtl())
                .putObjectRequest(putRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        String publicUrl = buildPublicUrl(key);

        return new PresignedUploadResponse(presigned.url().toString(), publicUrl, key);
    }

    /**
     * See {@link StorageProperties}' class doc for the {@code serveViaProxy}
     * / {@code apiPublicBaseUrl} trade-off this branches on. Either way the
     * result is a stable, non-expiring URL safe to persist in the DB
     * ({@code trip.cover_image_url}, {@code profile.photo_url}, etc.) — the
     * proxy branch defers actually signing anything until the URL is loaded
     * (see {@link #presignGetUrl}), it just points at where that will
     * happen.
     */
    private String buildPublicUrl(String key) {
        if (properties.isServeViaProxy()) {
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
            return properties.getApiPublicBaseUrl() + "/storage/view?key=" + encodedKey;
        }
        // publicEndpoint, not endpoint — this is a read URL the mobile app
        // will load images from later (CachedNetworkImage, CircleAvatar),
        // same client-reachability requirement as the presigned upload URL
        // above. See StorageProperties' class doc. Only correct when the
        // bucket is actually public-read (true of local MinIO, NOT true of
        // every S3-compatible target — see serveViaProxy).
        return properties.getPublicEndpoint() + "/" + properties.getBucket() + "/" + key;
    }

    /**
     * Called by {@code StorageViewController} at request time, never stored
     * — the whole point of the proxy path is that the DB only ever holds the
     * stable {@code /storage/view?key=...} URL from {@link
     * #buildPublicUrl}, and a fresh short-lived signature gets generated
     * every time that URL is actually loaded. 10 minutes is comfortably
     * enough for a mobile client to follow a redirect and fetch the image
     * bytes; short enough that a leaked URL (server logs, browser history)
     * doesn't stay valid for long.
     */
    public String presignGetUrl(String key) {
        GetObjectRequest getRequest =
                GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
