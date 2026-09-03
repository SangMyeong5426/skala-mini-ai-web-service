package com.skala.miniai.domain.photo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPhotoRepository extends JpaRepository<TripPhoto, Long> {

    List<TripPhoto> findByTripIdOrderById(Long tripId);

    Optional<TripPhoto> findByIdAndTripId(Long id, Long tripId);
}
