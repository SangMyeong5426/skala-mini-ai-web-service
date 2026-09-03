package com.skala.miniai.domain.ai;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.databind.JsonNode;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.checklist.ItemRuleCheck;
import com.skala.miniai.domain.checklist.ItemRuleCheckRepository;
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.DetectedObjectRepository;

/**
 * 접수된 작업을 <b>요청과 별개로</b> 실행한다.
 *
 * <p>{@code @TransactionalEventListener(AFTER_COMMIT)} 라 접수 트랜잭션이 커밋된 뒤에 돈다.
 * 커밋 전이면 다른 스레드가 아직 없는 행을 찾는다. {@code @Async} 는 가상 스레드 위에서 돈다
 * (Java 21, {@code application.properties}).
 *
 * <p><b>가상 스레드 설정만으로 비동기가 되는 것은 아니다.</b> 상태를 DB 에 두고
 * 폴링으로 읽는 이 구조가 비동기다 (07).
 *
 * <p>07 "작업이 끝나면 서버가 쓰는 곳" 을 <b>Mock 도 똑같이</b> 수행한다 — 그래야 S-04·S-05 가 이어진다.
 */
@Component
public class AiJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AiJobRunner.class);

    private final AiJobRepository jobs;
    private final AiClient aiClient;
    private final DetectedObjectRepository detections;
    private final ChecklistItemRepository items;
    private final ItemRuleCheckRepository ruleChecks;
    private final Json json;
    private final long mockDelayMs;

    public AiJobRunner(AiJobRepository jobs, AiClient aiClient, DetectedObjectRepository detections,
                       ChecklistItemRepository items, ItemRuleCheckRepository ruleChecks, Json json,
                       @Value("${app.ai.mock-delay-ms:0}") long mockDelayMs) {
        this.jobs = jobs;
        this.aiClient = aiClient;
        this.detections = detections;
        this.items = items;
        this.ruleChecks = ruleChecks;
        this.json = json;
        this.mockDelayMs = mockDelayMs;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(AiJobService.JobAccepted event) {
        AiJob job = jobs.findById(event.jobId()).orElse(null);
        if (job == null || job.getStatus() != Codes.JobStatus.PENDING) return;

        try {
            // 발표에서 로딩 화면을 보여주려면 AI_MOCK_DELAY_MS 를 1000~2000 으로 둔다.
            if (mockDelayMs > 0) Thread.sleep(mockDelayMs);

            JsonNode output = aiClient.run(job.getJobType(), json.read(job.getInputPayload()));
            job.complete(json.write(output), aiClient.modelName());
            persistSideEffects(job, output);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.fail("작업이 중단됐습니다. 다시 시도해 주세요.");
        } catch (RuntimeException e) {
            log.warn("AI 작업 {} 실패", job.getId(), e);
            // 사용자에게 내부 오류를 그대로 보여주지 않는다. 내 목록은 그대로 유지된다.
            job.fail("결과를 만들지 못했습니다. 내 체크리스트는 유지됩니다. 다시 시도하거나 직접 추가해 주세요.");
        }
    }

    private void persistSideEffects(AiJob job, JsonNode output) {
        switch (job.getJobType()) {
            case BAG_CHECK -> saveDetections(output);
            case RULE_CHECK -> saveRuleChecks(job, output);
            // 07: 추천과 무게는 output_payload 에만 남는다. 내 목록에 자동으로 넣지 않는다.
            case PACKING_LIST, WEIGHT_ESTIMATE -> { }
        }
    }

    /**
     * 07: 같은 사진의 <b>미승인</b> 행은 지우고 다시 넣는다. 승인된 행은 건드리지 않는다 —
     * 재분석이 사용자가 이미 확인한 결과를 지우면 안 된다.
     */
    private void saveDetections(JsonNode output) {
        // 사진마다 한 번만 지운다. 인식 결과 수만큼 반복하면 같은 삭제를 여러 번 돌린다.
        Set<Long> photoIds = new LinkedHashSet<>();
        output.path("detections").forEach(d -> photoIds.add(d.path("photoId").asLong()));
        if (!photoIds.isEmpty()) {
            List<DetectedObject> stale = detections.findByPhotoIdInOrderById(photoIds).stream()
                    .filter(o -> !o.isApproved())
                    .toList();
            detections.deleteAll(stale);
            detections.flush();
        }

        for (JsonNode d : output.path("detections")) {
            BigDecimal confidence = new BigDecimal(d.path("confidence").asText("0"))
                    .setScale(3, java.math.RoundingMode.HALF_UP);
            detections.save(new DetectedObject(
                    d.path("photoId").asLong(),
                    d.path("name").asText(),
                    d.path("qty").asInt(1),
                    confidence,
                    // 07: confidenceLevel 은 서버가 confidence 로 채운다. 모델 값이 있어도 덮어쓴다.
                    DetectedObject.levelOf(confidence),
                    d.path("missingInfo").isNull() ? null : d.path("missingInfo").asText(null),
                    d.path("labelText").isNull() ? null : d.path("labelText").asText(null)));
        }
    }

    /** 07: {@code itemId} 와 {@code ruleId} 가 모두 있는 결과만 저장한다. {@code ASK_AIRLINE} 은 JSON 에만 남는다. */
    private void saveRuleChecks(AiJob job, JsonNode output) {
        if (job.getTripId() == null) return;   // 챗봇은 아무 테이블에도 쓰지 않는다

        List<Long> ownItemIds = items.findByTripIdOrderById(job.getTripId()).stream()
                .map(ChecklistItem::getId).toList();

        for (JsonNode r : output.path("results")) {
            if (r.path("itemId").isNull() || r.path("ruleId").isNull()) continue;
            long itemId = r.path("itemId").asLong();
            if (!ownItemIds.contains(itemId)) continue;   // 남의 여행 항목으로 새지 않게

            ruleChecks.save(new ItemRuleCheck(
                    itemId,
                    r.path("ruleId").asLong(),
                    Codes.RuleVerdict.valueOf(r.path("verdict").asText()),
                    r.path("missingInfo").isNull() ? null : r.path("missingInfo").asText(null)));
        }
    }
}
