package com.gotogether.company.entity;

import com.gotogether.common.entity.AuditableEntity;
import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;

/**
 * Links a human {@code users} row to the Company it administers/works for
 * (DB Schema Part 3 Section 2). The schema structurally supports multiple
 * {@code owner}/{@code manager}/{@code support} rows per company, but MVP
 * product rules ("Multiple admins: Not supported for MVP... one human Admin
 * per Company account at launch," Operations Module A) cap it at exactly one
 * active {@link CompanyUserRole#OWNER} row at the application layer — see
 * {@code CompanyService#inviteStaff}.
 */
@Entity
@Table(name = "company_users")
public class CompanyUser extends AuditableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "role", nullable = false, columnDefinition = "company_user_role")
    private CompanyUserRole role = CompanyUserRole.OWNER;

    /** Plain {@code String} (DB CHECK IN ('active','removed')), matching the table's own loose typing — same pattern as {@code notification.entity.Notification#priority}. */
    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected CompanyUser() {
        // JPA
    }

    /** The founding Owner row created atomically with {@link TravelCompany#apply}. {@code createdBy} is {@code null} — nobody invited the founder (DB Schema Part 3's own note). */
    public static CompanyUser owner(UUID companyId, UUID userId) {
        CompanyUser cu = new CompanyUser();
        cu.companyId = companyId;
        cu.userId = userId;
        cu.role = CompanyUserRole.OWNER;
        return cu;
    }

    /** {@code POST /companies/me/staff} — a manager/support invite by an existing Owner. */
    public static CompanyUser invite(UUID companyId, UUID userId, CompanyUserRole role, UUID invitedBy) {
        CompanyUser cu = new CompanyUser();
        cu.companyId = companyId;
        cu.userId = userId;
        cu.role = role;
        cu.createdBy = invitedBy;
        return cu;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public CompanyUserRole getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public void remove() {
        this.status = "removed";
    }
}
