package com.gotogether.company.repository;

import com.gotogether.company.entity.CompanyUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {

    /** "Which company do I administer" — a user's own (first, and per MVP business rule the only) active membership row, any role. */
    Optional<CompanyUser> findFirstByUserIdAndStatus(UUID userId, String status);

    Optional<CompanyUser> findByCompanyIdAndUserIdAndStatus(UUID companyId, UUID userId, String status);

    List<CompanyUser> findByCompanyIdAndStatus(UUID companyId, String status);

    boolean existsByCompanyIdAndUserIdAndStatus(UUID companyId, UUID userId, String status);
}
