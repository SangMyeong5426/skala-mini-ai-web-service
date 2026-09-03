package com.skala.miniai.domain.itinerary;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripItineraryRepository extends JpaRepository<TripItinerary, Long> {

    List<TripItinerary> findByTripIdOrderByStartAt(Long tripId);

    Optional<TripItinerary> findByIdAndTripId(Long id, Long tripId);

    /** 캘린더용. 여러 여행의 일정을 기간으로 한 번에 훑는다. */
    List<TripItinerary> findByTripIdInAndStartAtBetweenOrderByStartAt(
            Collection<Long> tripIds, OffsetDateTime from, OffsetDateTime to);
}
