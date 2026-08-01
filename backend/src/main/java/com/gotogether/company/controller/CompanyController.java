package com.gotogether.company.controller;

import com.gotogether.auth.security.UserPrincipal;
import com.gotogether.common.dto.CursorPageResponse;
import com.gotogether.company.dto.ApplyCompanyRequest;
import com.gotogether.company.dto.CompanyProfileResponse;
import com.gotogether.company.dto.CompanyResponse;
import com.gotogether.company.dto.CompanyUserResponse;
import com.gotogether.company.dto.CompanyVerificationStatusResponse;
import com.gotogether.company.dto.InviteStaffRequest;
import com.gotogether.company.service.CompanyService;
import com.gotogether.review.service.ReviewService;
import com.gotogether.trip.dto.TripSummary;
import com.gotogether.trip.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Travel Company APIs (API Specification Section 14). {@code GET
 * /companies/{id}}'s {@code aggregate_rating}/{@code trips_completed_count}
 * are composed here from {@code TripService}/{@code ReviewService} — the same
 * controller-layer-composition pattern used everywhere else in this codebase
 * (see {@code TripSummary#withJoinedCount}'s doc) — rather than {@code
 * CompanyService} depending on either module directly.
 */
@RestController
public class CompanyController {

    private final CompanyService companyService;
    private final TripService tripService;
    private final ReviewService reviewService;

    public CompanyController(CompanyService companyService, TripService tripService, ReviewService reviewService) {
        this.companyService = companyService;
        this.tripService = tripService;
        this.reviewService = reviewService;
    }

    @PostMapping("/companies/apply")
    public ResponseEntity<CompanyResponse> apply(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ApplyCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.apply(principal.userId(), request));
    }

    @GetMapping("/companies/{id}")
    public CompanyProfileResponse profile(@PathVariable UUID id) {
        int tripsCompletedCount = tripService.countCompletedTripsForCompany(id);
        List<UUID> tripIds = tripService.listCompanyTripIds(id);
        Double aggregateRating = reviewService.averageOverallRatingForTrips(tripIds).orElse(null);
        return companyService.getPublicProfile(id, aggregateRating, tripsCompletedCount);
    }

    @GetMapping("/companies/me/verification-status")
    public CompanyVerificationStatusResponse myVerificationStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return companyService.getMyVerificationStatus(principal.userId());
    }

    @GetMapping("/companies/me/trips")
    public CursorPageResponse<TripSummary> myTrips(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        UUID companyId = companyService.getMyCompanyId(principal.userId());
        return tripService.listCompanyTrips(companyId, status, cursor, limit);
    }

    @GetMapping("/companies/me/staff")
    public List<CompanyUserResponse> staff(@AuthenticationPrincipal UserPrincipal principal) {
        return companyService.listStaff(principal.userId());
    }

    @PostMapping("/companies/me/staff")
    public ResponseEntity<CompanyUserResponse> inviteStaff(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody InviteStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.inviteStaff(principal.userId(), request));
    }
}
