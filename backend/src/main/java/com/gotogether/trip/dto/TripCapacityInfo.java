package com.gotogether.trip.dto;

import com.gotogether.trip.entity.TripKind;
import com.gotogether.trip.entity.TripStatus;
import java.util.UUID;

/**
 * The capacity-relevant slice of a Trip, exposed to {@code joinrequest}/{@code
 * membership} so they can drive the Trip lifecycle's group-size-triggered
 * transitions (Chapter 3 Section 3.2: {@code Published -> AcceptingRequests}
 * on first request, {@code -> Confirmed}/{@code Full} on threshold, {@code
 * Full -> AcceptingRequests} on a drop-out) without either module holding a
 * {@code Trip} entity directly (architecture rule: cross-module data is DTOs
 * only, never mapped entities — see {@code ArchitectureTest}).
 */
public record TripCapacityInfo(
        UUID id, UUID organizerId, TripKind kind, TripStatus status, short minGroupSize, short maxGroupSize) {
}
