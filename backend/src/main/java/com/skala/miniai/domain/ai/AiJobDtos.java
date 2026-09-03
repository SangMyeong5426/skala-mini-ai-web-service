package com.skala.miniai.domain.ai;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import com.skala.miniai.common.Codes;

import jakarta.validation.constraints.NotNull;

/**
 * AI 작업의 <b>봉투</b> (06). 알맹이({@code input}·{@code output})의 구조는 07 이 정한다.
 *
 * <p>이 봉투는 {@code jobType} 이 늘어도 바뀌지 않는다 — 그래서 엔드포인트가 둘뿐이다 (ADR 0003).
 */
public final class AiJobDtos {

    private AiJobDtos() { }

    public record CreateRequest(
            @NotNull(message = "jobType 은 필수입니다.") Codes.JobType jobType,
            Long tripId,
            JsonNode input) { }

    /** {@code 202 Accepted} 응답. 접수만 했고 아직 안 끝났다 — {@code output} 이 아예 없다. */
    public record Accepted(
            Long jobId, Codes.JobType jobType, Codes.JobStatus status,
            OffsetDateTime createdAt, long pollAfterMs) { }

    /**
     * {@code GET} 응답. <b>{@code FAILED} 도 200 이다</b> — 조회 자체는 성공했기 때문이다.
     * 500 을 쓰면 프런트엔드가 네트워크 오류와 AI 실패를 구분하지 못한다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(
            Long jobId, Codes.JobType jobType, Codes.JobStatus status,
            JsonNode output, String modelName, String errorMessage,
            OffsetDateTime createdAt, OffsetDateTime completedAt,
            Long pollAfterMs) { }
}
