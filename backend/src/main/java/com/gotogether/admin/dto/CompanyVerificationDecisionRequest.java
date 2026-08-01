package com.gotogether.admin.dto;

/**
 * {@code POST /admin/companies/{id}/verify} (API Spec Section 16 — the
 * table's own Request/Response columns say "varies" for this row, which
 * this reads as: one endpoint, a {@code decision} field choosing the
 * outcome, since {@link com.gotogether.company.entity.CompanyStatus} must
 * be able to reach {@code REJECTED} somehow and no separate {@code /reject}
 * path exists anywhere in Section 16's table — adding a whole new
 * undocumented path for that would be exactly the kind of change {@code
 * ReviewService}'s class doc already flagged as against this project's
 * process rule ("Section 19 states that table is the complete contract").
 * {@code decision} defaults to {@code "approve"} when absent.
 */
public record CompanyVerificationDecisionRequest(String decision, String notes) {

    public boolean isReject() {
        return "reject".equalsIgnoreCase(decision);
    }
}
