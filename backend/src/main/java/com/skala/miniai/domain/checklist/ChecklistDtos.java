package com.skala.miniai.domain.checklist;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.miniai.common.Codes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 내 체크리스트 API 의 요청·응답 (S-05). */
public final class ChecklistDtos {

    private ChecklistDtos() { }

    /**
     * 추천 후보를 가리키는 참조. 없으면 직접 추가다.
     *
     * <p>{@code candidateIndex} 는 완료된 추천의 {@code output.items} 에서 <b>0부터 시작하는 위치</b>다.
     * 화면에서 정렬·숨김을 해도 원래 응답 배열의 위치를 쓴다.
     */
    public record RecommendationRef(
            @NotNull(message = "추천 작업 ID는 필수입니다.") Long jobId,
            @NotNull(message = "추천 후보 위치는 필수입니다.")
            @Min(value = 0, message = "추천 후보 위치는 0 이상입니다.") Integer candidateIndex) { }

    public record CreateRequest(
            @NotBlank(message = "물품 이름은 필수입니다.") @Size(max = 100) String name,
            Codes.Category category,
            @Min(value = 1, message = "수량은 1 이상입니다.")
            @Max(value = 99, message = "수량은 99 이하입니다.") Integer qty,
            Codes.Priority priority,
            RecommendationRef recommendation) { }

    /** PATCH — 보낸 필드만 바꾼다. {@code photoStatus} 는 조회 전용이라 받지 않는다. */
    public record UpdateRequest(
            @Size(max = 100, message = "물품 이름은 100자 이하입니다.") String name,
            Codes.Category category,
            @Min(value = 1, message = "수량은 1 이상입니다.")
            @Max(value = 99, message = "수량은 99 이하입니다.") Integer qty,
            Codes.Priority priority, Codes.CheckStatus checkStatus) { }

    public record Item(
            Long itemId, String name, Codes.Category category, Integer qty,
            Codes.Priority priority, Codes.ItemSource source,
            Codes.CheckStatus checkStatus, Codes.PhotoStatus photoStatus) { }

    /**
     * 06: {@code recommendationJobId} 는 가장 최근 <b>완료된</b> 추천 작업이다(없으면 null).
     * {@code unacceptedRequiredCount} 는 완료된 추천이 아예 없으면 null — 0 과 구분한다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ListResponse(
            List<Item> items,
            BigDecimal completionRate,
            Long recommendationJobId,
            Integer unacceptedRequiredCount) { }
}
