package com.gotogether.storage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gotogether.common.exception.UnprocessableEntityException;
import com.gotogether.storage.config.PhotoSearchProperties;
import com.gotogether.storage.dto.PhotoSearchResultResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Stock-photo search for trip cover/gallery photos (Pexels API) — lets an
 * organizer attach a real destination photo without owning one themselves,
 * as a second option alongside the existing gallery upload. Deliberately
 * search-only: this service never downloads/stores the chosen photo's bytes
 * itself, matching {@link StorageService}'s own "no module uploads bytes
 * through the backend" principle (see that class's doc) — once a result is
 * picked, the mobile app downloads it directly from Pexels' CDN and pushes
 * it through the exact same presigned-upload flow a gallery photo already
 * uses ({@code GET /photos/search} only ever returns Pexels' own public
 * image URLs, never anything this backend re-hosts).
 *
 * <p>Uses the JDK's built-in {@code java.net.http.HttpClient} rather than
 * adding a new HTTP-client Maven dependency (no {@code WebClient}/
 * {@code RestTemplate}/{@code OkHttp} was already present in this backend —
 * see the kickoff research for this feature) — one small, synchronous,
 * server-to-server call doesn't need more than that.
 */
@Service
public class PexelsService {

    private static final Logger log = LoggerFactory.getLogger(PexelsService.class);
    private static final String SEARCH_URL = "https://api.pexels.com/v1/search";

    private final PhotoSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PexelsService(PhotoSearchProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /**
     * Package-private overload so tests can inject a mock {@link HttpClient}
     * — {@code send} isn't otherwise mockable without one, since the 2-arg
     * constructor above builds its own internally. The {@code @Autowired}
     * above disambiguates which constructor Spring uses now that there are
     * two (Spring throws at startup on multiple constructors with none
     * marked, absent a no-arg fallback).
     */
    PexelsService(PhotoSearchProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * @param query free-text search term (e.g. a destination name — "Andaman", "Manali").
     * @param page  1-based, matching Pexels' own pagination.
     */
    public List<PhotoSearchResultResponse> search(String query, int page) {
        String apiKey = properties.getPexelsApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // Fails clearly and immediately rather than sending an
            // unauthenticated request upstream and surfacing Pexels' own
            // opaque 401 to the mobile app — see PhotoSearchProperties' doc.
            throw new UnprocessableEntityException("Photo search isn't configured on this server (missing PEXELS_API_KEY).");
        }

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create(SEARCH_URL + "?query=" + encodedQuery + "&per_page=20&page=" + page);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", apiKey)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Pexels search request failed for query '{}'", query, e);
            throw new UnprocessableEntityException("Photo search is temporarily unavailable — try again shortly.");
        }

        if (response.statusCode() != 200) {
            log.error("Pexels search returned HTTP {} for query '{}': {}", response.statusCode(), query, response.body());
            throw new UnprocessableEntityException("Photo search is temporarily unavailable — try again shortly.");
        }

        return parse(response.body());
    }

    private List<PhotoSearchResultResponse> parse(String body) {
        List<PhotoSearchResultResponse> results = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            log.error("Could not parse Pexels response body", e);
            throw new UnprocessableEntityException("Photo search is temporarily unavailable — try again shortly.");
        }

        for (JsonNode photo : root.path("photos")) {
            JsonNode src = photo.path("src");
            results.add(new PhotoSearchResultResponse(
                    photo.path("id").asText(),
                    src.path("medium").asText(null),
                    // "large2x" is the highest-resolution variant Pexels'
                    // free API tier serves without an extra per-photo call —
                    // good enough for a trip cover photo, no need for the
                    // (much larger) "original" full-resolution file.
                    src.path("large2x").asText(null),
                    photo.path("photographer").asText(null),
                    photo.path("photographer_url").asText(null)));
        }
        return results;
    }
}
