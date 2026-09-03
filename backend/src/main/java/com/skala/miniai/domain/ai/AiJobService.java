package com.skala.miniai.domain.ai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.CurrentUser;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.photo.TripPhoto;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripService;

/**
 * AI 작업 접수·조회 (AI-Ready 원칙 3).
 *
 * <p><b>접수만 하고 즉시 응답한다.</b> Mock 이 곧바로 답할 수 있어도 {@code 202} → 폴링 구조를
 * 지킨다. 실제 LLM 을 붙이면 {@code pollAfterMs} 만 늘리면 되고 프런트엔드는 안 고친다.
 *
 * <p>{@code WEIGHT_ESTIMATE} 만 입력 불일치를 {@code 409} 로 막는다. 무게는 "지금 상태" 의
 * 계산이라 오래된 입력으로 만든 결과를 보여주면 사용자가 잘못된 여유를 믿는다.
 * 추천은 그렇지 않아서 조용히 보정한다 (06).
 */
@Service
public class AiJobService {

    private final AiJobRepository jobs;
    private final TripPhotoRepository photos;
    private final AiInputBuilder inputBuilder;
    private final TripService tripService;
    private final CurrentUser currentUser;
    private final Json json;
    private final ApplicationEventPublisher events;
    private final long pollAfterMs;

    public AiJobService(AiJobRepository jobs, TripPhotoRepository photos, AiInputBuilder inputBuilder,
                        TripService tripService, CurrentUser currentUser, Json json,
                        ApplicationEventPublisher events,
                        @Value("${app.ai.poll-after-ms:500}") long pollAfterMs) {
        this.jobs = jobs;
        this.photos = photos;
        this.inputBuilder = inputBuilder;
        this.tripService = tripService;
        this.currentUser = currentUser;
        this.json = json;
        this.events = events;
        this.pollAfterMs = pollAfterMs;
    }

    /** 트랜잭션이 커밋된 뒤에만 Mock 을 돌리기 위한 신호. 커밋 전이면 작업 행이 아직 없다. */
    public record JobAccepted(Long jobId) { }

    @Transactional
    public AiJobDtos.Accepted create(AiJobDtos.CreateRequest req) {
        Codes.JobType type = req.jobType();

        // RULE_CHECK 만 여행 없이 된다 — 챗봇(UC-08)은 여행을 등록하지 않아도 쓰는 보조 흐름이다.
        if (type != Codes.JobType.RULE_CHECK && req.tripId() == null) {
            throw ApiException.badRequest(type + " 는 tripId 가 필요합니다.", "tripId");
        }

        Trip trip = req.tripId() == null ? null : tripService.mustOwn(req.tripId());
        JsonNode input = resolveInput(type, trip, req.input());

        AiJob job = jobs.save(new AiJob(currentUser.id(), trip == null ? null : trip.getId(), type, json.write(input)));

        // 커밋 후에 실행한다. 여기서 바로 돌리면 아직 없는 행을 다른 트랜잭션이 찾는다.
        events.publishEvent(new JobAccepted(job.getId()));

        return new AiJobDtos.Accepted(job.getId(), job.getJobType(), job.getStatus(),
                job.getCreatedAt(), pollAfterMs);
    }

    @Transactional(readOnly = true)
    public AiJobDtos.Status status(Long jobId) {
        AiJob job = jobs.findByIdAndUserId(jobId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("AI 작업", jobId));

        return new AiJobDtos.Status(
                job.getId(), job.getJobType(), job.getStatus(),
                json.read(job.getOutputPayload()),
                job.getModelName(), job.getErrorMessage(),
                job.getCreatedAt(), job.getCompletedAt(),
                // 아직 진행 중일 때만 다음 폴링 간격을 알려준다.
                job.getStatus() == Codes.JobStatus.PENDING ? pollAfterMs : null);
    }

    /**
     * 06: 서버가 최종 입력을 결정한다.
     *
     * <ul>
     *   <li>{@code PACKING_LIST} — 현재 PREPARED 목록으로 {@code alreadyPacked} 를 덮어쓴다.
     *       값이 오래됐거나 {@code []} 여도 409 를 내지 않는다.
     *   <li>{@code WEIGHT_ESTIMATE} — 현재 목록·가방 상태와 다르면 {@code 409} 다.
     *   <li>{@code BAG_CHECK} — 이 여행의 사진 전부를 대상으로 한다.
     *   <li>{@code RULE_CHECK} — 챗봇 질문이라 화면이 준 입력을 그대로 쓴다.
     * </ul>
     */
    private JsonNode resolveInput(Codes.JobType type, Trip trip, JsonNode clientInput) {
        return switch (type) {
            case PACKING_LIST -> inputBuilder.packingList(trip);

            case WEIGHT_ESTIMATE -> {
                ObjectNode server = inputBuilder.weightEstimate(trip);
                if (clientInput != null && !clientInput.isNull()
                        && !json.canonical(server).equals(json.canonical(clientInput))) {
                    throw ApiException.conflict("STALE_WEIGHT_INPUT",
                            "체크리스트나 가방 정보가 그 사이 바뀌었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
                }
                yield server;
            }

            case BAG_CHECK -> {
                List<Long> photoIds = photos.findByTripIdOrderById(trip.getId()).stream()
                        .map(TripPhoto::getId).toList();
                if (photoIds.isEmpty()) {
                    throw ApiException.badRequest("분석할 사진이 없습니다. 사진을 먼저 올려 주세요.", "input.photoIds");
                }
                yield inputBuilder.bagCheck(photoIds);
            }

            case RULE_CHECK -> {
                if (clientInput == null || clientInput.isNull()) {
                    throw ApiException.badRequest("input 은 필수입니다.", "input");
                }
                yield clientInput;
            }
        };
    }
}
