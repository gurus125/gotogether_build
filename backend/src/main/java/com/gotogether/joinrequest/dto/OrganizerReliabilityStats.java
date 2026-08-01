package com.gotogether.joinrequest.dto;

/**
 * Aggregate Organizer behaviour across every trip they've organized — feeds
 * the {@code trust} module's "Organizer reliability" component (10% weight,
 * organizers only, Business Rules Trust & Discovery Module A: "Response Time,
 * approval consistency"). {@code decidedCount} is Accepted + Rejected
 * requests (the Organizer actually responded); {@code expiredCount} is
 * requests left to rot past the SLA window (Chapter 3 Section 3.3) — a
 * negative signal distinct from a Rejected decision. {@code
 * avgResponseHours} is {@code null} when {@code decidedCount} is zero
 * (nothing to average).
 */
public record OrganizerReliabilityStats(int decidedCount, int expiredCount, Double avgResponseHours) {

    public double responseRate() {
        int total = decidedCount + expiredCount;
        return total == 0 ? 1.0 : (double) decidedCount / total;
    }
}
