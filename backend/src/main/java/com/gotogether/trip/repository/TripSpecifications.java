package com.gotogether.trip.repository;

import com.gotogether.trip.entity.Trip;
import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic-query building blocks for {@code GET /trips} and {@code GET
 * /explore} (API Specification Sections 6, 7) — each filter is optional, so
 * {@link Specification#allOf} composes only the ones the caller actually
 * supplied rather than one giant hand-written JPQL string per combination.
 */
public final class TripSpecifications {

    private TripSpecifications() {
    }

    public static Specification<Trip> destinationId(UUID destinationId) {
        return destinationId == null ? null : (root, query, cb) -> cb.equal(root.get("destinationId"), destinationId);
    }

    public static Specification<Trip> kind(TripKind kind) {
        return kind == null ? null : (root, query, cb) -> cb.equal(root.get("kind"), kind);
    }

    public static Specification<Trip> status(TripStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Trip> statusIn(List<TripStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? null : (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Trip> organizerId(UUID organizerId) {
        return organizerId == null ? null : (root, query, cb) -> cb.equal(root.get("organizerId"), organizerId);
    }

    public static Specification<Trip> companyId(UUID companyId) {
        return companyId == null ? null : (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    /**
     * Excludes Verified Partner Trips belonging to a suspended/removed
     * Company from public discovery — see {@code CompanyService
     * #getDiscoveryExcludedCompanyIds}'s doc for the cross-module rule this
     * implements. Community trips ({@code companyId IS NULL}) are always
     * kept; only a listed company's trips are filtered out.
     */
    public static Specification<Trip> companyIdNotIn(List<UUID> excludedCompanyIds) {
        return (excludedCompanyIds == null || excludedCompanyIds.isEmpty())
                ? null
                : (root, query, cb) -> cb.or(cb.isNull(root.get("companyId")), cb.not(root.get("companyId").in(excludedCompanyIds)));
    }

    public static Specification<Trip> organizerIdNot(UUID organizerId) {
        return organizerId == null ? null : (root, query, cb) -> cb.notEqual(root.get("organizerId"), organizerId);
    }

    public static Specification<Trip> budgetMinAtLeast(Integer budgetMin) {
        return budgetMin == null ? null : (root, query, cb) -> cb.or(
                cb.isNull(root.get("budgetMax")), cb.greaterThanOrEqualTo(root.get("budgetMax"), budgetMin));
    }

    public static Specification<Trip> budgetMaxAtMost(Integer budgetMax) {
        return budgetMax == null ? null : (root, query, cb) -> cb.or(
                cb.isNull(root.get("budgetMin")), cb.lessThanOrEqualTo(root.get("budgetMin"), budgetMax));
    }

    public static Specification<Trip> dateFrom(LocalDate dateFrom) {
        return dateFrom == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startDate"), dateFrom);
    }

    public static Specification<Trip> dateTo(LocalDate dateTo) {
        return dateTo == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("endDate"), dateTo);
    }

    public static Specification<Trip> tripType(String tripType) {
        return tripType == null || tripType.isBlank() ? null : (root, query, cb) -> cb.equal(root.get("tripType"), tripType);
    }

    // Duration (trip length in days) is deliberately NOT a Specification here — a
    // cross-database-portable "date diff in days" predicate is awkward via the
    // Criteria API, and the result sets involved are small enough at MVP scale
    // (Chapter 1 Section 14: single-city launch) that TripService filters by
    // duration in-memory after the DB query instead. See TripService's doc.

    public static Specification<Trip> verifiedOnly(Boolean verifiedOnly) {
        return (verifiedOnly == null || !verifiedOnly)
                ? null
                : (root, query, cb) -> cb.equal(root.get("kind"), TripKind.VERIFIED_PARTNER);
    }
}
