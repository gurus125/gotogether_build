package com.gotogether.trip.repository;

import com.gotogether.trip.entity.Trip;
import com.gotogether.trip.entity.TripStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface TripRepository extends JpaRepository<Trip, UUID>, JpaSpecificationExecutor<Trip> {

    /**
     * Acquires a {@code SELECT ... FOR UPDATE} row lock on the trip for the
     * remainder of the caller's transaction — the mechanism that makes Join
     * Request Accept-vs-capacity checks atomic (API Spec Section 23 /
     * Backend Architecture Section 22's explicitly flagged race: two
     * concurrent accepts must not both squeeze past {@code max_group_size}).
     * Only ever called from {@code TripService.lockForCapacityChange}, itself
     * only called from within an already-{@code @Transactional} caller in
     * {@code joinrequest}/{@code membership} so the lock's scope covers the
     * whole read-then-write capacity operation, not just this query.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

    List<Trip> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    /** Every trip a Company has ever run, any status — {@code company} module's Trust-adjacent aggregates (rating, completed count). */
    List<Trip> findByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, TripStatus status);

    /** {@code TripLifecycleScheduler}'s "ready to start" query — every non-terminal, non-Draft trip whose {@code start_date} has arrived. */
    List<Trip> findByStatusInAndStartDateLessThanEqual(List<TripStatus> statuses, LocalDate date);

    /** {@code TripLifecycleScheduler}'s "ready to complete" query — every {@code InProgress} trip whose {@code end_date} has fully passed. */
    List<Trip> findByStatusAndEndDateLessThan(TripStatus status, LocalDate date);
}
