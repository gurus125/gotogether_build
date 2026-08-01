package com.gotogether.company.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcType;

/**
 * The business entity behind Verified Partner Trips (Operations Module A) —
 * distinct from any individual {@code users} row. "A Company can never create
 * a Community Trip and never appears as an individual peer... this boundary
 * is absolute, protecting the 'real travellers' brand promise for the
 * Community side" (Operations Module A's own Purpose section).
 *
 * <p>Reaching {@link CompanyStatus#VERIFIED} requires a Moderator/Admin
 * approving a {@link CompanyVerification} row — "Manual Moderator/Admin
 * review... never automated at MVP scale, given low volume and high stakes of
 * a fraudulent 'verified' operator" (Operations Module A). That review
 * workflow belongs to Phase 8's {@code admin} module, which doesn't exist yet
 * — see {@code CompanyService}'s class doc for the same dev-only escape hatch
 * {@code TripService}'s {@code enforceIdApproval} already uses for the
 * identical "no Moderator UI exists yet" problem on the individual-ID-approval
 * side.
 */
@Entity
@Table(name = "travel_companies")
public class TravelCompany extends AuditableEntity {

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "description")
    private String description;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "support_email", nullable = false)
    private String supportEmail;

    @Column(name = "support_phone", nullable = false)
    private String supportPhone;

    @Column(name = "cancellation_policy", nullable = false)
    private String cancellationPolicy;

    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "company_status")
    private CompanyStatus status = CompanyStatus.APPLICATION_SUBMITTED;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    protected TravelCompany() {
        // JPA
    }

    /** {@code POST /companies/apply} (API Spec Section 14) — always starts {@link CompanyStatus#APPLICATION_SUBMITTED}, the DB column default. */
    public static TravelCompany apply(
            String displayName, String legalName, String registrationNumber, String gstNumber,
            String supportEmail, String supportPhone, String cancellationPolicy) {
        TravelCompany company = new TravelCompany();
        company.displayName = displayName;
        company.legalName = legalName;
        company.registrationNumber = registrationNumber;
        company.gstNumber = gstNumber;
        company.supportEmail = supportEmail;
        company.supportPhone = supportPhone;
        company.cancellationPolicy = cancellationPolicy;
        company.termsAcceptedAt = OffsetDateTime.now();
        return company;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getSupportPhone() {
        return supportPhone;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public boolean isVerified() {
        return status == CompanyStatus.VERIFIED;
    }

    /**
     * {@code POST /admin/companies/{id}/verify} (Phase 8, API Spec Section
     * 16) — the transition this class's own doc named as needing a real
     * Moderator/Admin decision. Capabilities table: "Moderator review/
     * recommend, Admin final decision" — enforced by {@code AdminService}
     * requiring {@code ADMIN} for this specific action, not here.
     */
    public void verify() {
        this.status = CompanyStatus.VERIFIED;
    }

    /** Initial-application rejection (terminal for that attempt; re-applying is a brand new {@link TravelCompany} row per {@code CompanyService#apply} — Operations Module A: "immediate reapplication once fixed, no cooldown"). */
    public void reject() {
        this.status = CompanyStatus.REJECTED;
    }

    /** Operations Module A's Company Suspension feature: "protect travellers without necessarily terminating the business relationship" — active listings hidden, already-accepted travellers unaffected (enforced by {@code AdminService}, not here). */
    public void suspend(String reason) {
        this.status = CompanyStatus.SUSPENDED;
        this.suspendedAt = OffsetDateTime.now();
        this.suspensionReason = reason;
    }

    /** Escalation from {@link #suspend}: "repeat violation escalates to Removed, which DOES force-cancel open trips" (Operations Module A) — the force-cancel cascade itself is {@code AdminService}'s job. */
    public void remove(String reason) {
        this.status = CompanyStatus.REMOVED;
        this.suspendedAt = OffsetDateTime.now();
        this.suspensionReason = reason;
    }
}
