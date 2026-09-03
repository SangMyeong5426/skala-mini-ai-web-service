package com.skala.miniai.domain.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.miniai.common.Codes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 여행 API 의 요청·응답 모양. <b>필드명이 계약이다</b> (06-api-spec.md).
 *
 * <p>엔티티를 그대로 내보내지 않는다 — {@code userId} 같은 내부 값이 새 나가고,
 * 컬럼을 바꿀 때마다 API 가 조용히 바뀐다 (AGENTS.md "구현 시 지킬 것").
 */
public final class TripDtos {

    private TripDtos() { }

    /** 06: {@code origin}·{@code destination} 은 <b>이동수단과 무관하게 필수</b>다. */
    public record CreateRequest(
            @NotBlank(message = "출발지는 필수입니다.") @Size(max = 100) String origin,
            @NotBlank(message = "도착지는 필수입니다.") @Size(max = 100) String destination,
            @Size(min = 2, max = 2, message = "국가 코드는 2자입니다.") String countryCode,
            @NotNull(message = "출발일은 필수입니다.") LocalDate startDate,
            @NotNull(message = "귀국일은 필수입니다.") LocalDate endDate,
            @NotNull(message = "여행 목적은 필수입니다.") Codes.Purpose purpose,
            @NotNull(message = "이동수단은 필수입니다.") Codes.Transport transport,
            @Size(max = 50) String airline,
            @Size(min = 3, max = 3, message = "공항 코드는 3자입니다.") String departureAirport,
            @Size(min = 3, max = 3, message = "공항 코드는 3자입니다.") String arrivalAirport,
            Codes.BagType bagType,
            @Positive(message = "빈 가방 무게는 0보다 커야 합니다.") Integer bagEmptyG,
            @Positive(message = "무게 한도는 0보다 커야 합니다.") Integer weightLimitG,
            String note) { }

    /** PATCH — <b>보낸 필드만</b> 바꾼다. 전부 nullable 이라 검증은 서비스에서 한다. */
    public record UpdateRequest(
            String origin, String destination, String countryCode,
            LocalDate startDate, LocalDate endDate,
            Codes.Purpose purpose, Codes.Transport transport,
            String airline, String departureAirport, String arrivalAirport,
            Codes.BagType bagType, Integer bagEmptyG, Integer weightLimitG,
            String note, Codes.TripStatus status) { }

    /** S-01 홈 카드. {@code completionRate} 는 조회 시 계산값이다. */
    public record Summary(
            Long tripId, String origin, String destination,
            LocalDate startDate, LocalDate endDate,
            Codes.Transport transport, Codes.TripStatus status,
            BigDecimal completionRate) { }

    public record ListResponse(List<Summary> trips) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            Long tripId, String origin, String destination,
            LocalDate startDate, LocalDate endDate,
            Codes.Transport transport, Codes.TripStatus status,
            BigDecimal completionRate,
            String countryCode, Codes.Purpose purpose,
            String airline, String departureAirport, String arrivalAirport,
            Codes.BagType bagType, Integer bagEmptyG, Integer weightLimitG,
            String note) { }

    /** 06 의 201 응답. 목록·상세와 달리 {@code createdAt} 이 있고 완료율은 없다(막 만들어 0이다). */
    public record CreateResponse(
            Long tripId, String origin, String destination,
            LocalDate startDate, LocalDate endDate,
            Codes.Transport transport, Codes.TripStatus status,
            OffsetDateTime createdAt) { }
}
