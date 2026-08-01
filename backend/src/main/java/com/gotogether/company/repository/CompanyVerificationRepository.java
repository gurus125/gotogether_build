package com.gotogether.company.repository;

import com.gotogether.company.entity.CompanyVerification;
import com.gotogether.company.entity.CompanyVerificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface CompanyVerificationRepository extends JpaRepository<CompanyVerification, UUID> {

    /** Most recent attempt for a company — {@code GET /companies/me/verification-status}. */
    Optional<CompanyVerification> findFirstByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** {@code GET /admin/companies} (Phase 8) — the business-verification review queue, distinct from {@code GET /admin/verifications}' individual ID queue. */
    Page<CompanyVerification> findByStatusOrderByCreatedAtAsc(CompanyVerificationStatus status, Pageable pageable);

    long countByStatus(CompanyVerificationStatus status);
}
