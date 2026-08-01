package com.gotogether.destination.controller;

import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.destination.entity.DestinationCategory;
import com.gotogether.destination.service.DestinationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Destination APIs (API Specification Section 5) — all Bearer-authenticated, no write endpoints (Chapter 1 Section 9b). */
@RestController
@RequestMapping("/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    /** Full curated list, optionally grouped by category (Chapter 1 Section 14). */
    @GetMapping
    public List<DestinationSummary> list(@RequestParam(required = false) DestinationCategory category) {
        return destinationService.list(category);
    }

    /** Instant-suggest as the user types, Google-Maps-like (Chapter 1 Section 14). */
    @GetMapping("/search")
    public List<DestinationSummary> search(@RequestParam("q") String query) {
        return destinationService.search(query);
    }

    /** "Popular from Delhi NCR" (Create Trip Destination screen). */
    @GetMapping("/popular")
    public List<DestinationSummary> popular(@RequestParam(defaultValue = "10") int limit) {
        return destinationService.popular(limit);
    }

    /** Editorially curated set for Home category rows (Chapter 1 Section 18). */
    @GetMapping("/featured")
    public List<DestinationSummary> featured() {
        return destinationService.featured();
    }
}
