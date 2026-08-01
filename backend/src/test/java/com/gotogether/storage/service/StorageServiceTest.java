package com.gotogether.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.storage.config.StorageProperties;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedPutObjectRequest presignedRequest;
    @Mock private PresignedGetObjectRequest presignedGetRequest;

    private StorageService storageService;
    private StorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setBucket("gotogether-dev");
        properties.setEndpoint("http://localhost:9000");
        // Deliberately different from endpoint (mirrors the real Android
        // emulator override, STORAGE_PUBLIC_ENDPOINT=http://10.0.2.2:9000)
        // so the assertions below actually prove StorageService reads
        // publicEndpoint for the client-facing URL rather than endpoint —
        // they'd have silently passed either way if both were the same
        // value, which is exactly how the "photo not uploading" bug slipped
        // through before this property split existed.
        properties.setPublicEndpoint("http://10.0.2.2:9000");
        properties.setRegion("us-east-1");
        properties.setAccessKey("gotogether");
        properties.setSecretKey("gotogether123");
        storageService = new StorageService(s3Presigner, properties);
    }

    @Test
    void rejectsAContentTypeThatIsNotAnAllowedImageType() {
        assertThatThrownBy(() -> storageService.createPresignedImageUploadUrl("profile-photos/abc", "application/pdf"))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void rejectsANullContentType() {
        assertThatThrownBy(() -> storageService.createPresignedImageUploadUrl("profile-photos/abc", null))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void generatesAKeyUnderTheGivenPrefixWithTheCorrectExtensionAndPublicUrl() throws Exception {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(URI.create("https://signed.example.com/put?sig=abc").toURL());

        var response = storageService.createPresignedImageUploadUrl("profile-photos/user-123", "image/png");

        assertThat(response.key()).startsWith("profile-photos/user-123/").endsWith(".png");
        assertThat(response.publicUrl()).isEqualTo("http://10.0.2.2:9000/gotogether-dev/" + response.key());
        assertThat(response.uploadUrl()).isEqualTo("https://signed.example.com/put?sig=abc");
    }

    @Test
    void mapsEachAllowedContentTypeToItsOwnExtension() throws Exception {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(URI.create("https://signed.example.com/put").toURL());

        assertThat(storageService.createPresignedImageUploadUrl("p", "image/jpeg").key()).endsWith(".jpg");
        assertThat(storageService.createPresignedImageUploadUrl("p", "image/webp").key()).endsWith(".webp");
    }

    @Test
    void defaultsToARawBucketUrlWhenServeViaProxyIsOff() throws Exception {
        // properties.serveViaProxy is false by default (never set in setUp())
        // — this is the existing local-MinIO behavior and must stay exactly
        // as it was before the proxy path was added.
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(URI.create("https://signed.example.com/put").toURL());

        var response = storageService.createPresignedImageUploadUrl("profile-photos/user-123", "image/png");

        assertThat(response.publicUrl()).isEqualTo("http://10.0.2.2:9000/gotogether-dev/" + response.key());
    }

    @Test
    void returnsABackendRedirectUrlWhenServeViaProxyIsOn() throws Exception {
        properties.setServeViaProxy(true);
        properties.setApiPublicBaseUrl("https://gotogether-backend-production.up.railway.app");
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(URI.create("https://signed.example.com/put").toURL());

        var response = storageService.createPresignedImageUploadUrl("profile-photos/user-123", "image/png");

        assertThat(response.publicUrl())
                .isEqualTo("https://gotogether-backend-production.up.railway.app/storage/view?key="
                        + java.net.URLEncoder.encode(response.key(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void presignGetUrlDelegatesToTheS3PresignerForTheGivenKey() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetRequest);
        when(presignedGetRequest.url())
                .thenReturn(URI.create("https://signed.example.com/get?sig=xyz").toURL());

        String url = storageService.presignGetUrl("profile-photos/user-123/abc.png");

        assertThat(url).isEqualTo("https://signed.example.com/get?sig=xyz");
    }
}
