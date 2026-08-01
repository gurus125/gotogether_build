package com.gotogether.company.repository;

import com.gotogether.company.entity.CompanyStatus;
import com.gotogether.company.entity.TravelCompany;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface TravelCompanyRepository extends JpaRepository<TravelCompany, UUID> {

    boolean existsByRegistrationNumber(String registrationNumber);

    /** {@code GET /admin/companies} (Phase 8) — {@code status} optional, so this is only used when the caller actually filters; an unfiltered list falls back to the inherited {@code findAll(Pageable)}. */
    Page<TravelCompany> findByStatus(CompanyStatus status, Pageable pageable);

    /**
     * {@code TripService.explore}/{@code recommended}/{@code listTrips}'s
     * discovery-exclusion filter — see {@code CompanyService
     * #getDiscoveryExcludedCompanyIds}'s doc. Explicit {@code @Query} rather
     * than a derived {@code findIdByStatusIn}, since Spring Data's method-name
     * derivation ignores the "Id" subject token (it's ignored, not treated as
     * a projection) and would return full {@code TravelCompany} rows instead
     * of a {@code List<UUID>}.
     */
    @Query("SELECT c.id FROM TravelCompany c WHERE c.status IN :statuses")
    List<UUID> findIdByStatusIn(@Param("statuses") List<CompanyStatus> statuses);
}
