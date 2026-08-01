package com.gotogether.storage.controller;

import com.gotogether.storage.dto.PhotoSearchResultResponse;
import com.gotogether.storage.service.PexelsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /photos/search} — the first controller {@code storage} has ever
 * needed (see {@link com.gotogether.storage.service.StorageService}'s class
 * doc on why the module previously had none). Behind the same authentication
 * every other endpoint requires by default (no {@code @AuthenticationPrincipal}
 * param needed here — nothing in the response is user-specific — but an
 * unauthenticated caller still can't reach it, which matters since each call
 * spends a metered third-party API quota).
 */
@RestController
public class PhotoSearchController {

    private final PexelsService pexelsService;

    public PhotoSearchController(PexelsService pexelsService) {
        this.pexelsService = pexelsService;
    }

    @GetMapping("/photos/search")
    public List<PhotoSearchResultResponse> search(
            @RequestParam String query, @RequestParam(defaultValue = "1") int page) {
        return pexelsService.search(query, page);
    }
}
