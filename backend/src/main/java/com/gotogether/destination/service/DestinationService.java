package com.gotogether.destination.service;

import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.destination.dto.DestinationSummary;
import com.gotogether.destination.entity.Destination;
import com.gotogether.destination.entity.DestinationCategory;
import com.gotogether.destination.repository.DestinationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The destination module's only entry point for other modules (see {@code
 * UserService}'s doc for the same pattern) — {@code trip} calls this to
 * validate a {@code destination_id} and to embed destination info in trip
 * responses, never touching {@code DestinationRepository}/{@code Destination}
 * directly.
 *
 * <p>Destinations are seed data (V6 migration) with no create/update/delete
 * endpoint at MVP (Chapter 1 Section 9b) — this service is read-only.
 */
@Service
public class DestinationService {

    /** {@code GET /destinations/featured} has no documented selection rule beyond
     * "editorially curated set for Home category rows" (API Spec Section 5) — since
     * there's no separate "featured" flag/table in the schema, this picks the
     * single best-ranked (lowest {@code popularity_rank}) active destination per
     * category, giving exactly one representative per Home category row. Flagged
     * as an interpretation, not a literal spec requirement. */
    private static final int FEATURED_LIMIT_PER_CATEGORY = 1;

    private final DestinationRepository destinationRepository;

    public DestinationService(DestinationRepository destinationRepository) {
        this.destinationRepository = destinationRepository;
    }

    public List<DestinationSummary> list(DestinationCategory category) {
        List<Destination> destinations = category == null
                ? destinationRepository.findByActiveTrueOrderByPopularityRankAscNameAsc()
                : destinationRepository.findByActiveTrueAndCategoryOrderByPopularityRankAscNameAsc(category);
        return destinations.stream().map(this::toSummary).toList();
    }

    /** Instant-suggest as the user types (Chapter 1 Section 14, "Google Maps-like"). */
    public List<DestinationSummary> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return destinationRepository.search(query).stream().map(this::toSummary).toList();
    }

    /** "Popular from Delhi NCR" — ranked by {@code popularity_rank} (Create Trip Destination screen). */
    public List<DestinationSummary> popular(int limit) {
        return destinationRepository.findPopular().stream().limit(limit).map(this::toSummary).toList();
    }

    /**
     * See {@link #FEATURED_LIMIT_PER_CATEGORY}'s doc for the interpretation
     * this implements. Deliberately queries the <em>full</em> active list
     * (not {@link #popular}, which excludes any destination with a {@code
     * null} popularity_rank) — otherwise a category with no ranked
     * destinations yet (e.g. "Weekend Escapes" and "Adventure" in the current
     * V6 seed data) would be silently missing from Home's category rows
     * entirely. The list is already ordered (popularity_rank ascending, nulls
     * last, then name) so grouping by category and taking the first of each
     * group picks the best-ranked destination, falling back to alphabetical
     * for a category with no ranked entries yet.
     */
    public List<DestinationSummary> featured() {
        Map<DestinationCategory, Destination> firstPerCategory = new LinkedHashMap<>();
        for (Destination d : destinationRepository.findByActiveTrueOrderByPopularityRankAscNameAsc()) {
            firstPerCategory.putIfAbsent(d.getCategory(), d);
        }
        return firstPerCategory.values().stream().map(this::toSummary).toList();
    }

    /** Used by {@code TripService} to validate a {@code destination_id} on trip creation and to embed destination info in responses. */
    public DestinationSummary getSummary(UUID destinationId) {
        return toSummary(getOrThrow(destinationId));
    }

    private Destination getOrThrow(UUID destinationId) {
        return destinationRepository.findById(destinationId)
                .filter(Destination::isActive)
                .orElseThrow(() -> ResourceNotFoundException.of("Destination", destinationId));
    }

    private DestinationSummary toSummary(Destination d) {
        return new DestinationSummary(d.getId(), d.getName(), d.getCategory(), d.getCoverImageUrl());
    }
}
