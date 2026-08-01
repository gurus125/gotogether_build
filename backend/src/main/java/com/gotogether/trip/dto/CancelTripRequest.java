package com.gotogether.trip.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /trips/{id}/cancel} — mandatory reason (Chapter 2 Section 2.7, Chapter 3 Section 3.2). */
public record CancelTripRequest(@NotBlank String reason) {}
