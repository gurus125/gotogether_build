package com.gotogether.membership.repository;

import com.gotogether.membership.entity.MembershipStatus;
import com.gotogether.membership.entity.TripMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public only because Spring Data requires it — see {@code UserRepository}'s doc for the package-private-in-practice note. */
public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

    boolean existsByTripIdAndOrganizerTrue(UUID tripId);

    long countByTripIdAndStatus(UUID tripId, MembershipStatus status);

    List<TripMember> findByTripIdAndStatusOrderByJoinedAtAsc(UUID tripId, MembershipStatus status);

    /** {@code MembershipService#getRoster} — JOINED for an active trip, or JOINED+COMPLETED so the roster (and the Manage Attendance screen built on it) still has something to show once the trip's concluded. */
    List<TripMember> findByTripIdAndStatusInOrderByJoinedAtAsc(UUID tripId, List<MembershipStatus> statuses);

    List<TripMember> findByTripIdAndStatus(UUID tripId, MembershipStatus status);

    /** My Trips "upcoming"/"past" tabs (API Spec Section 6) — every trip the caller currently holds (or held) an active/completed seat on. */
    List<TripMember> findByUserIdAndStatusInOrderByJoinedAtDesc(UUID userId, List<MembershipStatus> statuses);

    boolean existsByTripIdAndUserId(UUID tripId, UUID userId);
}
