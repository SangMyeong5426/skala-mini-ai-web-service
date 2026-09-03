package com.skala.miniai.domain.itinerary;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.domain.trip.TripService;

/**
 * 여행 일정 (S-11).
 *
 * <p>모든 메서드가 {@link TripService#mustOwn} 을 먼저 거친다. 일정 ID 만으로 조회하면
 * 남의 여행 일정을 열 수 있다 — 경로의 {@code tripId} 와 함께 확인한다.
 */
@Service
public class ItineraryService {

    private final TripItineraryRepository itineraries;
    private final TripService tripService;

    public ItineraryService(TripItineraryRepository itineraries, TripService tripService) {
        this.itineraries = itineraries;
        this.tripService = tripService;
    }

    @Transactional(readOnly = true)
    public ItineraryDtos.ListResponse list(Long tripId) {
        tripService.mustOwn(tripId);
        List<ItineraryDtos.Item> items = itineraries.findByTripIdOrderByStartAt(tripId).stream()
                .map(ItineraryDtos.Item::of)
                .toList();
        return new ItineraryDtos.ListResponse(items);
    }

    @Transactional
    public ItineraryDtos.Item create(Long tripId, ItineraryDtos.CreateRequest req) {
        tripService.mustOwnForUpdate(tripId);
        validateRange(req.startAt(), req.endAt());

        TripItinerary e = new TripItinerary(tripId, req.kind(), req.title().trim(), req.startAt());
        e.setPlace(req.place());
        e.setCode(req.code());
        e.setEndAt(req.endAt());
        e.setNote(req.note());
        return ItineraryDtos.Item.of(itineraries.save(e));
    }

    @Transactional
    public ItineraryDtos.Item update(Long tripId, Long itineraryId, ItineraryDtos.UpdateRequest req) {
        tripService.mustOwnForUpdate(tripId);
        TripItinerary e = itineraries.findByIdAndTripId(itineraryId, tripId)
                .orElseThrow(() -> ApiException.notFound("일정", itineraryId));

        if (req.kind() != null) e.setKind(req.kind());
        if (req.title() != null) e.setTitle(req.title().trim());
        if (req.place() != null) e.setPlace(req.place());
        if (req.code() != null) e.setCode(req.code());
        if (req.startAt() != null) e.setStartAt(req.startAt());
        if (req.endAt() != null) e.setEndAt(req.endAt());
        if (req.note() != null) e.setNote(req.note());

        // 한쪽만 바꿔도 역전될 수 있다. 바꾼 뒤에 검사한다.
        validateRange(e.getStartAt(), e.getEndAt());
        return ItineraryDtos.Item.of(e);
    }

    @Transactional
    public void delete(Long tripId, Long itineraryId) {
        tripService.mustOwnForUpdate(tripId);
        TripItinerary e = itineraries.findByIdAndTripId(itineraryId, tripId)
                .orElseThrow(() -> ApiException.notFound("일정", itineraryId));
        itineraries.delete(e);
    }

    private void validateRange(java.time.OffsetDateTime startAt, java.time.OffsetDateTime endAt) {
        if (endAt != null && startAt.isAfter(endAt)) {
            throw ApiException.badRequest("종료 시각이 시작 시각보다 빠릅니다.", "endAt");
        }
    }
}
