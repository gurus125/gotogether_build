package com.gotogether.trip.repository;

import com.gotogether.trip.entity.TripImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripImageRepository extends JpaRepository<TripImage, UUID> {

    List<TripImage> findByTripIdOrderByDisplayOrderAsc(UUID tripId);

    List<TripImage> findByTripIdAndPrimaryTrue(UUID tripId);
}
