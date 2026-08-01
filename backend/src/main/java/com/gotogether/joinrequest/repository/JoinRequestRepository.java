package com.gotogether.joinrequest.repository;

import com.gotogether.joinrequest.entity.JoinRequest;
import com.gotogether.joinrequest.entity.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    Optional<JoinRequest> findByApplicantIdAndTripIdAndStatusIn(UUID applicantId, UUID tripId, List<JoinRequestStatus> statuses);

    Optional<JoinRequest> findFirstByApplicantIdAndTripIdOrderByCreatedAtDesc(UUID applicantId, UUID tripId);

    /** FIFO waiting-list head for a trip (Chapter 3 Section 3.3: "promotion is strict FIFO by request timestamp"). */
    Optional<JoinRequest> findFirstByTripIdAndStatusOrderByCreatedAtAsc(UUID tripId, JoinRequestStatus status);

    long countByTripIdAndStatus(UUID tripId, JoinRequestStatus status);

    /** Organizer's request queue (API Spec Section 8), oldest-first. */
    Page<JoinRequest> findByTripIdOrderByCreatedAtAsc(UUID tripId, Pageable pageable);

    Page<JoinRequest> findByTripIdAndStatusOrderByCreatedAtAsc(UUID tripId, JoinRequestStatus status, Pageable pageable);

    /** "My pending requests" (API Spec Section 8), newest-first. */
    Page<JoinRequest> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId, Pageable pageable);

    Page<JoinRequest> findByApplicantIdAndStatusOrderByCreatedAtDesc(UUID applicantId, JoinRequestStatus status, Pageable pageable);

    /** Organizer-reliability aggregation ({@code trust} module) — every decided-or-expired request across a set of the organizer's own trips. */
    List<JoinRequest> findByTripIdInAndStatusIn(List<UUID> tripIds, List<JoinRequestStatus> statuses);
}
