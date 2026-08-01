package com.gotogether.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.storage.config.PhotoSearchProperties;
import com.gotogether.storage.dto.PhotoSearchResultResponse;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PexelsServiceTest {

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> httpResponse;

    private PhotoSearchProperties properties;
    private PexelsService pexelsService;

    @BeforeEach
    void setUp() {
        properties = new PhotoSearchProperties();
        pexelsService = new PexelsService(properties, new ObjectMapper(), httpClient);
    }

    @Test
    void rejectsSearchWhenApiKeyIsNotConfigured() {
        properties.setPexelsApiKey(null);
        assertThatThrownBy(() -> pexelsService.search("Andaman", 1)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void rejectsSearchWhenApiKeyIsBlank() {
        properties.setPexelsApiKey("   ");
        assertThatThrownBy(() -> pexelsService.search("Andaman", 1)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void mapsAPexelsSuccessResponseIntoSearchResults() throws Exception {
        properties.setPexelsApiKey("test-key");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {
                  "photos": [
                    {
                      "id": 2014422,
                      "photographer": "Joey Farina",
                      "photographer_url": "https://www.pexels.com/@joey",
                      "src": {
                        "medium": "https://images.pexels.com/photos/2014422/pexels-photo-2014422.jpeg?h=350",
                        "large2x": "https://images.pexels.com/photos/2014422/pexels-photo-2014422.jpeg?h=650&w=940"
                      }
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        List<PhotoSearchResultResponse> results = pexelsService.search("Andaman", 1);

        assertThat(results).hasSize(1);
        PhotoSearchResultResponse result = results.get(0);
        assertThat(result.id()).isEqualTo("2014422");
        assertThat(result.photographerName()).isEqualTo("Joey Farina");
        assertThat(result.photographerUrl()).isEqualTo("https://www.pexels.com/@joey");
        assertThat(result.thumbnailUrl()).contains("h=350");
        assertThat(result.fullUrl()).contains("h=650&w=940");
    }

    @Test
    void returnsAnEmptyListWhenPexelsHasNoMatches() throws Exception {
        properties.setPexelsApiKey("test-key");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{ \"photos\": [] }");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThat(pexelsService.search("zzzznonexistentplace", 1)).isEmpty();
    }

    @Test
    void surfacesAClearErrorWhenPexelsReturnsANonSuccessStatus() throws Exception {
        properties.setPexelsApiKey("test-key");
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"error\": \"invalid key\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThatThrownBy(() -> pexelsService.search("Andaman", 1)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void surfacesAClearErrorWhenTheHttpCallItselfFails() throws Exception {
        properties.setPexelsApiKey("test-key");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        assertThatThrownBy(() -> pexelsService.search("Andaman", 1)).isInstanceOf(UnprocessableEntityException.class);
    }
}
