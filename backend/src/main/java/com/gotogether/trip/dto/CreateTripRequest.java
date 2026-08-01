package com.gotogether.trip.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code POST /trips} (API Specification Section 6) — creates a Draft.
 * {@code description} is optional here (nullable) but required before {@code
 * publish} (Chapter 3 Section 3.2: Draft has the fewest requirements of any
 * state); {@link com.gotogether.trip.service.TripService#publish} enforces that.
 *
 * <p>{@code startDate}/{@code endDate} are always concrete calendar dates —
 * the Create Trip wizard's "Flexible" date mode (month chips + a day-count,
 * no exact date) is resolved client-side into a concrete date pair before
 * this request is sent (see the mobile app's Create Trip wizard code for the
 * exact derivation rule), with {@code isFlexibleDates} staying {@code true}
 * so the UI can still label the trip as approximate. The exact "start_date
 * must be at least tomorrow" rule (API Specification Section 20) isn't
 * expressible as a bean-validation annotation on its own (it's relative to
 * "now," not a fixed value) — {@code TripService} enforces it instead.
 *
 * <p>{@code companyId}/{@code fixedPrice} (Phase 7) are the one shared
 * endpoint's Verified Partner Trip path — "one lifecycle, one table"
 * (Operations Module A) means there is deliberately no separate
 * company-trip-creation endpoint. Both null -> Community trip (uses {@code
 * budgetMin}/{@code budgetMax} instead); both non-null -> Verified Partner
 * trip, gated by {@code TripService} on the caller being an active staff
 * member of a {@code VERIFIED} company (DB {@code
 * chk_trips_pricing_model_by_kind}/{@code chk_trips_company_id_by_kind}).
 */
public record CreateTripRequest(
        @NotNull UUID destinationId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean isFlexibleDates,
        @PositiveOrZero Integer budgetMin,
        @PositiveOrZero Integer budgetMax,
        @NotNull @Size(min = 5, max = 60) String title,
        @Size(max = 300) String description,
        UUID companyId,
        @PositiveOrZero Integer fixedPrice) {
}
