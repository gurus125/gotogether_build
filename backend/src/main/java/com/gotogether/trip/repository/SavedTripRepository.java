package com.gotogether.trip.repository;

import com.gotogether.trip.entity.SavedTrip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedTripRepository extends JpaRepository<SavedTrip, UUID> {

    Optional<SavedTrip> findByUserIdAndTripId(UUID userId, UUID tripId);

    boolean existsByUserIdAndTripId(UUID userId, UUID tripId);

    List<SavedTrip> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
