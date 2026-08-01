package com.gotogether.storage.controller;

import com.gotogether.storage.service.StorageService;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /storage/view?key=...} — only reachable at all when {@code
 * gotogether.storage.serve-via-proxy=true} (see {@link
 * com.gotogether.storage.config.StorageProperties}'s class doc); when it's
 * false (local dev default), {@code StorageService} never hands out a URL
 * pointing here in the first place, so this controller simply goes unused.
 *
 * <p>Deliberately a 302 redirect to a freshly-presigned GET URL rather than
 * this backend streaming the image bytes itself — keeps the "no image bytes
 * ever pass through this backend" rule from {@code StorageService}'s class
 * doc intact for reads, not just writes, and costs this service nothing
 * (bandwidth-wise) per image load.
 *
 * <p>Permitted without authentication in {@code SecurityConfig} — {@code
 * Image.network}/{@code CachedNetworkImage} on the Flutter side load this
 * directly and never attach a JWT header, the same reasoning already
 * documented for the raw-bucket-URL path this replaces (profile/trip photos
 * are not private data, unlike verification documents).
 */
@RestController
public class StorageViewController {

    private final StorageService storageService;

    public StorageViewController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/storage/view")
    public ResponseEntity<Void> view(@RequestParam String key) {
        String presignedGetUrl = storageService.presignGetUrl(key);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(presignedGetUrl).toString())
                .build();
    }
}
