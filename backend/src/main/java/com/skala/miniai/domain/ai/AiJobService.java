package com.skala.miniai.domain.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.CurrentUser;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.photo.DetectedObjectRepository;
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
    private final RuleCheckContract ruleCheckContract;
    private final ChecklistItemRepository items;
    private final DetectedObjectRepository detections;
    private final ApplicationEventPublisher events;
    private final long pollAfterMs;

    public AiJobService(AiJobRepository jobs, TripPhotoRepository photos,
                        ChecklistItemRepository items, DetectedObjectRepository detections,
                        AiInputBuilder inputBuilder,
                        TripService tripService, CurrentUser currentUser, Json json,
                        RuleCheckContract ruleCheckContract,
                        ApplicationEventPublisher events,
                        @Value("${app.ai.poll-after-ms:500}") long pollAfterMs) {
        this.jobs = jobs;
        this.photos = photos;
        this.inputBuilder = inputBuilder;
        this.tripService = tripService;
        this.currentUser = currentUser;
        this.json = json;
        this.ruleCheckContract = ruleCheckContract;
        this.items = items;
        this.detections = detections;
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

    /**
     * 작업을 <b>별도 트랜잭션</b>에서 실패로 표시한다.
     *
     * <p>{@link AiJobRunner} 안에서 직접 {@code job.fail()} 을 부르면 안 된다. 예외가 DB 작업에서
     * 났다면 그 트랜잭션은 이미 rollback-only 라, 커밋 시점에 통째로 되돌아가면서
     * <b>실패 표시까지 사라진다.</b> 그러면 작업이 {@code PENDING} 에 남고 화면은 06 의 폴링
     * 규약대로 끝없이 폴링한다 — {@code FAILED} 분기가 영영 실행되지 않는다.
     *
     * <p>{@code REQUIRES_NEW} 가 먹으려면 <b>다른 빈</b>에 있어야 한다. 같은 클래스 안에서
     * 부르면 프록시를 타지 않아 전파 설정이 무시된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String message) {
        jobs.findById(jobId).ifPresent(job -> job.fail(message));
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
                yield checkOwnership(trip, ruleCheckContract.validateInput(clientInput));
            }
        };
    }

    /**
     * 07 「로그인과 AI 작업의 경계」 — <b>남의 자료를 가리키지 못하게 막는다.</b>
     *
     * <p>지금까지 {@code RULE_CHECK} 는 모양만 검사하고 {@code itemId}·{@code detectionId} 가
     * 누구 것인지 보지 않았다. 07 이 <i>"tripId·photoIds·itemIds·추천 jobId가 본인 자료이며 서로
     * 같은 여행인지 확인한다"</i> 고 정한 부분이라 여기서 채운다. 사진 첨부가 생기면서 더 중요해졌다 —
     * 붙인 사진의 인식 결과가 <b>내 체크리스트에 자동 등록</b>되기 때문이다.
     *
     * <p>없는 것과 남의 것을 구분하지 않고 모두 {@code 404} 로 답한다. 구분하면 남의 여행에 어떤
     * id 가 있는지 세어 볼 수 있다.
     */
    private JsonNode checkOwnership(Trip trip, JsonNode input) {
        List<Long> photoIds = RuleCheckContract.attachedPhotoIds(input);

        if (!photoIds.isEmpty() && trip == null) {
            // 사진을 저장할 곳도, 인식 물품을 등록할 곳도 없다.
            // trip_photos.trip_id 와 checklist_items.trip_id 가 NOT NULL 이다.
            throw ApiException.badRequest(
                    "사진을 붙이려면 여행을 먼저 선택해 주세요.", "tripId");
        }

        if (trip == null) {
            // 여행 없이 묻는 챗봇이다. 가리킬 내 자료가 없으므로 id 도 없어야 한다.
            for (JsonNode item : input.path("items")) {
                if (!item.path("itemId").isNull() || !item.path("detectionId").isNull()) {
                    throw ApiException.badRequest(
                            "여행 없이 묻는 질문에는 itemId·detectionId 를 넣을 수 없습니다.", "input.items");
                }
            }
            return input;
        }

        Set<Long> ownPhotoIds = new LinkedHashSet<>();
        photos.findByTripIdOrderById(trip.getId()).forEach(photo -> ownPhotoIds.add(photo.getId()));
        for (Long photoId : photoIds) {
            if (!ownPhotoIds.contains(photoId)) throw ApiException.notFound("사진", photoId);
        }

        Set<Long> ownItemIds = new LinkedHashSet<>();
        items.findByTripIdOrderById(trip.getId()).forEach(item -> ownItemIds.add(item.getId()));
        Set<Long> ownDetectionIds = new LinkedHashSet<>();
        if (!ownPhotoIds.isEmpty()) {
            detections.findByPhotoIdInOrderById(ownPhotoIds)
                    .forEach(detection -> ownDetectionIds.add(detection.getId()));
        }

        for (JsonNode item : input.path("items")) {
            JsonNode itemId = item.path("itemId");
            if (!itemId.isNull() && !ownItemIds.contains(itemId.asLong())) {
                throw ApiException.notFound("체크리스트 항목", itemId.asLong());
            }
            JsonNode detectionId = item.path("detectionId");
            if (!detectionId.isNull() && !ownDetectionIds.contains(detectionId.asLong())) {
                throw ApiException.notFound("인식 물품", detectionId.asLong());
            }
        }
        return input;
    }
}
