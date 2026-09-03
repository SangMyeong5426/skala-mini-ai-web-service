package com.skala.miniai.domain.calendar;

import java.time.LocalDate;
import java.util.List;

import com.skala.miniai.common.Codes;
import com.skala.miniai.domain.itinerary.ItineraryDtos;

/**
 * 여행 캘린더 (S-11).
 *
 * <p><b>캘린더는 테이블이 아니다.</b> {@code trips} 의 기간과 {@code trip_itineraries} 를
 * 날짜로 묶어 만든 조회 전용 모양이다. 같은 일정을 두 곳에 저장하면 한쪽만 고쳤을 때
 * 달력과 상세가 어긋난다 (docs/05-erd.md).
 */
public final class CalendarDtos {

    private CalendarDtos() { }

    /** 달력에 색칠할 여행 구간. <b>목적지가 여기 있다</b> — 일정마다 다시 적지 않는다. */
    public record TripBand(
            Long tripId, String origin, String destination,
            LocalDate startDate, LocalDate endDate,
            Codes.Transport transport, Codes.TripStatus status) { }

    /** 무언가 있는 날만 담는다. 빈 날을 다 채우면 한 달에 31개 빈 칸이 오간다. */
    public record Day(
            LocalDate date,
            List<Long> tripIds,
            List<ItineraryDtos.Item> itineraries) { }

    public record Response(
            LocalDate from, LocalDate to,
            List<TripBand> trips,
            List<Day> days) { }
}
