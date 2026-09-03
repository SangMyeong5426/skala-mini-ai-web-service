package com.skala.miniai.domain.itinerary;

import java.time.OffsetDateTime;
import java.util.List;

import com.skala.miniai.common.Codes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 여행 일정 API 의 요청·응답 (S-11). 시각은 06 계약대로 ISO 8601 UTC 다. */
public final class ItineraryDtos {

    private ItineraryDtos() { }

    public record CreateRequest(
            @NotNull(message = "일정 종류는 필수입니다.") Codes.ItineraryKind kind,
            @NotBlank(message = "일정 제목은 필수입니다.") @Size(max = 100) String title,
            @Size(max = 100) String place,
            @Size(max = 50) String code,
            @NotNull(message = "시작 시각은 필수입니다.") OffsetDateTime startAt,
            OffsetDateTime endAt,
            String note) { }

    /** PATCH — 보낸 필드만 바꾼다. */
    public record UpdateRequest(
            Codes.ItineraryKind kind, String title, String place, String code,
            OffsetDateTime startAt, OffsetDateTime endAt, String note) { }

    public record Item(
            Long itineraryId, Long tripId,
            Codes.ItineraryKind kind, String title, String place, String code,
            OffsetDateTime startAt, OffsetDateTime endAt, String note) {

        public static Item of(TripItinerary e) {
            return new Item(e.getId(), e.getTripId(), e.getKind(), e.getTitle(),
                    e.getPlace(), e.getCode(), e.getStartAt(), e.getEndAt(), e.getNote());
        }
    }

    public record ListResponse(List<Item> itineraries) { }
}
