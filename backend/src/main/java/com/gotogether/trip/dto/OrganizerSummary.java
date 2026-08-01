package com.gotogether.trip.dto;

import java.util.UUID;

/** The "Organizer" section of Trip Details (tripdetailsv1 design) — transfers trust from platform to a specific person. */
public record OrganizerSummary(UUID id, String displayName, String photoUrl, boolean idVerified) {}
