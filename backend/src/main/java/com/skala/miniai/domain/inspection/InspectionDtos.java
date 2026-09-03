package com.skala.miniai.domain.inspection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.miniai.common.Codes;

/**
 * 검수 결과 (S-06). <b>이 서비스의 차별점 셋이 한 응답에 있다</b> — 준비 상태 · 예상 무게 · 반입 판정.
 *
 * <p>{@code null} 도 의미가 있다. {@code weight} 가 {@code null} 이면 "현재 입력에 맞는 결과가
 * 아직 없다"는 뜻이라 화면이 재계산을 요청한다. 그래서 필드를 지우지 않는다.
 */
public final class InspectionDtos {

    private InspectionDtos() { }

    public record ReadyItem(Long itemId, String name, Integer qty, Codes.PhotoStatus photoStatus) { }

    /** 내 목록을 <b>완료 여부로만</b> 나눈다. 미채택 추천·미승인 인식 후보는 여기 안 들어온다. */
    public record Readiness(
            List<ReadyItem> prepared,
            List<ReadyItem> unprepared,
            BigDecimal completionRate,
            Integer unacceptedRequiredCount) { }

    /** 06 투영 — 07 output 에서 {@code excluded} 를 빼고 {@code contributions} 는 위 3개만. */
    public record Contribution(String name, Integer typicalG, Integer qty, Integer subtotalG) { }

    public record Weight(
            Integer minG, Integer typicalG, Integer maxG, Integer limitG,
            Codes.WeightVerdict verdict, Codes.ConfidenceLevel confidence,
            String confidenceReason, Integer excludedCount,
            List<Contribution> contributions) { }

    public record Customs(
            Long itemId, String name, Codes.RuleVerdict verdict, String missingInfo,
            String reason, String sourceUrl, LocalDate checkedAt) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Response(
            Long tripId,
            Readiness readiness,
            Weight weight,
            List<Customs> customs,
            String notice) { }
}
