package com.skala.miniai.domain.itinerary;

import java.time.OffsetDateTime;
import java.util.List;

import com.skala.miniai.common.Codes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * PATCH — 보낸 필드만 바꾼다.
     *
     * <p>nullable 이지만 <b>길이 제약은 그대로</b> 건다. 없으면 너무 긴 값이 DB 까지 가서
     * {@code 409 CONSTRAINT_VIOLATION} 이 나온다 — 어느 필드가 문제인지 알려주지 못한다.
     */
    public record UpdateRequest(
            Codes.ItineraryKind kind,
            @Size(max = 100, message = "일정 제목은 100자 이하입니다.")
            @Pattern(regexp = ".*\\S.*", message = "일정 제목은 공백일 수 없습니다.") String title,
            @Size(max = 100, message = "장소는 100자 이하입니다.") String place,
            @Size(max = 50, message = "코드는 50자 이하입니다.") String code,
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
