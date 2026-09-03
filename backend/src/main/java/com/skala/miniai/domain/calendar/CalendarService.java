package com.skala.miniai.domain.calendar;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.CurrentUser;
import com.skala.miniai.domain.itinerary.ItineraryDtos;
import com.skala.miniai.domain.itinerary.TripItinerary;
import com.skala.miniai.domain.itinerary.TripItineraryRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripRepository;

/**
 * 캘린더 한 화면을 한 번의 요청으로 만든다.
 *
 * <p>여행 구간과 일정을 <b>따로 두 번 부르게 하지 않는다.</b> 달력은 둘을 겹쳐 그리므로
 * 나눠 받으면 화면이 두 응답의 도착 순서에 따라 깜빡인다.
 *
 * <p>날짜 묶음은 <b>UTC 기준</b>이다. 06 이 시각을 ISO 8601 UTC 로 못박았고, 현지 시간대
 * 변환은 화면이 한다 — 서버가 어느 시간대로 자를지 추측하지 않는다.
 */
@Service
public class CalendarService {

    /** 한 번에 볼 수 있는 범위. 달력은 한 달을 그리므로 넉넉히 잡되 무한 조회는 막는다. */
    private static final int MAX_DAYS = 366;

    private final TripRepository trips;
    private final TripItineraryRepository itineraries;
    private final CurrentUser currentUser;

    public CalendarService(TripRepository trips, TripItineraryRepository itineraries, CurrentUser currentUser) {
        this.trips = trips;
        this.itineraries = itineraries;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CalendarDtos.Response between(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("to 가 from 보다 빠릅니다.", "to");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw ApiException.badRequest("한 번에 조회할 수 있는 범위는 " + MAX_DAYS + "일입니다.", "to");
        }

        List<Trip> overlapping = trips
                .findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDate(
                        currentUser.id(), to, from);

        List<CalendarDtos.TripBand> bands = overlapping.stream()
                .map(t -> new CalendarDtos.TripBand(
                        t.getId(), t.getOrigin(), t.getDestination(),
                        t.getStartDate(), t.getEndDate(), t.getTransport(), t.getStatus()))
                .toList();

        // 날짜 → 그 날의 여행·일정. TreeMap 이라 날짜순으로 나온다.
        Map<LocalDate, List<Long>> tripsByDay = new TreeMap<>();
        for (Trip t : overlapping) {
            LocalDate d = t.getStartDate().isBefore(from) ? from : t.getStartDate();
            LocalDate last = t.getEndDate().isAfter(to) ? to : t.getEndDate();
            while (!d.isAfter(last)) {
                tripsByDay.computeIfAbsent(d, k -> new ArrayList<>()).add(t.getId());
                d = d.plusDays(1);
            }
        }

        Map<LocalDate, List<ItineraryDtos.Item>> itinerariesByDay = new TreeMap<>();
        if (!overlapping.isEmpty()) {
            OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            List<Long> tripIds = overlapping.stream().map(Trip::getId).toList();

            for (TripItinerary e : itineraries.findByTripIdInAndStartAtBetweenOrderByStartAt(tripIds, start, end)) {
                LocalDate day = e.getStartAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
                itinerariesByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(ItineraryDtos.Item.of(e));
            }
        }

        // 둘 중 하나라도 있는 날만 담는다.
        Map<LocalDate, Boolean> allDays = new TreeMap<>();
        tripsByDay.keySet().forEach(d -> allDays.put(d, true));
        itinerariesByDay.keySet().forEach(d -> allDays.put(d, true));

        List<CalendarDtos.Day> days = new ArrayList<>();
        for (LocalDate d : new LinkedHashMap<>(allDays).keySet()) {
            days.add(new CalendarDtos.Day(
                    d,
                    tripsByDay.getOrDefault(d, List.of()),
                    itinerariesByDay.getOrDefault(d, List.of())));
        }
        return new CalendarDtos.Response(from, to, bands, days);
    }
}
