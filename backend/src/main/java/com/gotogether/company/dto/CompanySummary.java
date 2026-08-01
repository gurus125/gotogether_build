package com.gotogether.company.dto;

import java.util.UUID;

/**
 * Minimal branding payload for embedding a Company as a Verified Partner
 * Trip's "Organizer" — Operations Module A's "Organizer assignment: the
 * 'Organizer' role on a Verified Partner Trip is always the Company itself...
 * a traveller interacts with 'Summit Travel Co.,' not with a named employee."
 * Consumed by {@code trip.service.TripService} at the controller-composition
 * layer (mirrors every other cross-module read in this codebase — see {@code
 * TripSummary#withJoinedCount}'s doc for the pattern).
 */
public record CompanySummary(UUID companyId, String displayName, String logoUrl, boolean verified) {
}
