package com.gotogether.admin.dto;

/** {@code POST /admin/verifications/{id}/approve} / {@code /reject} (API Spec Section 16): {@code { rejection_reason? }} — absent/ignored on approve. */
public record VerificationDecisionRequest(String rejectionReason, String notes) {
}
