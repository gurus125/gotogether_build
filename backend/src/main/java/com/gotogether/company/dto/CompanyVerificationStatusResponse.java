package com.gotogether.company.dto;

/** {@code GET /companies/me/verification-status} (API Specification Section 14) — Chapter 3 Section 3.11's application lifecycle state, plus the reason if rejected. */
public record CompanyVerificationStatusResponse(String status, String decisionNotes) {
}
