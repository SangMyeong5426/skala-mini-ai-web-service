package com.skala.miniai.domain.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.checklist.ItemRuleCheck;
import com.skala.miniai.domain.checklist.ItemRuleCheckRepository;
import com.skala.miniai.domain.master.RuleEngine;
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
 *
 * <p>{@code BAG_CHECK} 는 인식 결과 저장에서 끝나지 않는다. 06 개정에서 <b>승인 없이
 * 내 목록까지 한 트랜잭션으로</b> 등록하므로 {@link PhotoAutoRegistrar} 가 이어서 돈다.
 * 저장이 실패하면 전체가 롤백되고 작업은 실패로 남는다 — 목록 저장 전에 완료 응답을 내지 않는다.
 */
@Component
public class AiJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AiJobRunner.class);

    private final AiJobRepository jobs;
    private final AiJobService jobService;
    private final AiClient aiClient;
    private final DetectedObjectRepository detections;
    private final ChecklistItemRepository items;
    private final ItemRuleCheckRepository ruleChecks;
    private final PhotoAutoRegistrar autoRegistrar;
    private final RuleCheckContract ruleCheckContract;
    private final RuleEngine ruleEngine;
    private final Json json;
    private final long mockDelayMs;

    public AiJobRunner(AiJobRepository jobs, AiJobService jobService, AiClient aiClient,
                       DetectedObjectRepository detections, ChecklistItemRepository items,
                       ItemRuleCheckRepository ruleChecks, PhotoAutoRegistrar autoRegistrar, Json json,
                       RuleCheckContract ruleCheckContract, RuleEngine ruleEngine,
                       @Value("${app.ai.mock-delay-ms:0}") long mockDelayMs) {
        this.jobs = jobs;
        this.jobService = jobService;
        this.aiClient = aiClient;
        this.detections = detections;
        this.items = items;
        this.ruleChecks = ruleChecks;
        this.autoRegistrar = autoRegistrar;
        this.ruleCheckContract = ruleCheckContract;
        this.ruleEngine = ruleEngine;
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

            JsonNode input = json.read(job.getInputPayload());
            JsonNode output = aiClient.run(job.getJobType(), job.getTripId(), input);
            if (job.getJobType() == Codes.JobType.RULE_CHECK) {
                // 07 「누가 채우나」 — 판정은 모델 몫이 아니다. 모델·Mock 이 낸 verdict·ruleId 는
                // 버리고 transport_rules 로 다시 매긴다. 검증은 그다음이라 계약이 최종값을 본다.
                ruleEngine.applyTo(input, output);
                // 판정이 바뀌었으니 답변 쪽도 그 판정에 맞춘다. 안 하면 계약에서 막힌다.
                alignToEngine(input, output);
                ruleCheckContract.validateOutput(input, output);
            }

            // 순서가 중요하다. **부수 효과를 먼저 쓰고, 성공했을 때만 작업을 완료로 바꾼다.**
            //
            //   ① flush 를 여기서 한다 — 안 하면 INSERT 가 커밋 시점에 나가고,
            //      그때 터지는 제약 위반은 아래 catch 를 못 탄다. 작업이 PENDING 에 갇힌다.
            //   ② job.complete() 를 뒤에 둔다 — 앞에 두면 이 트랜잭션이 ai_jobs 행을
            //      UPDATE 로 잠근 채 markFailed 의 새 트랜잭션이 같은 행을 기다려 **교착**한다.
            persistSideEffects(job, output);
            jobs.flush();

            job.complete(json.write(output), aiClient.modelName());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 실패 표시는 별도 트랜잭션이다. 여기서 job.fail() 을 부르면 이 트랜잭션이
            // 되돌아갈 때 함께 사라져 작업이 PENDING 에 영원히 남는다.
            jobService.markFailed(job.getId(), "작업이 중단됐습니다. 다시 시도해 주세요.");
        } catch (RuntimeException e) {
            log.warn("AI 작업 {} 실패", job.getId(), e);
            // 사용자에게 내부 오류를 그대로 보여주지 않는다. 내 목록은 그대로 유지된다.
            jobService.markFailed(job.getId(),
                    "결과를 만들지 못했습니다. 내 체크리스트는 유지됩니다. 다시 시도하거나 직접 추가해 주세요.");
            // 부분 결과가 커밋되지 않도록 이 트랜잭션은 되돌린다.
            // (RULE_CHECK 에서 valueOf 가 던지기 전에 큐에 들어간 INSERT 가 그 예다)
            throw e;
        }
    }

    private void persistSideEffects(AiJob job, JsonNode output) {
        switch (job.getJobType()) {
            case BAG_CHECK -> autoRegistrar.register(job.getTripId(), saveDetections(output));
            case RULE_CHECK -> saveRuleChecks(job, output);
            // 07: 추천과 무게는 output_payload 에만 남는다. 내 목록에 자동으로 넣지 않는다.
            case PACKING_LIST, WEIGHT_ESTIMATE -> { }
        }
    }

    /**
     * 07: 같은 사진의 <b>미승인</b> 행은 지우고 다시 넣는다. 승인된 행은 건드리지 않는다 —
     * 재분석이 사용자가 이미 확인한 결과를 지우면 안 된다.
     */
    private List<DetectedObject> saveDetections(JsonNode output) {
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

        List<DetectedObject> saved = new ArrayList<>();
        for (JsonNode d : output.path("detections")) {
            BigDecimal confidence = new BigDecimal(d.path("confidence").asText("0"))
                    .setScale(3, java.math.RoundingMode.HALF_UP);
            saved.add(detections.save(new DetectedObject(
                    d.path("photoId").asLong(),
                    d.path("name").asText(),
                    d.path("qty").asInt(1),
                    confidence,
                    // 07: confidenceLevel 은 서버가 confidence 로 채운다. 모델 값이 있어도 덮어쓴다.
                    DetectedObject.levelOf(confidence),
                    d.path("missingInfo").isNull() ? null : d.path("missingInfo").asText(null),
                    d.path("labelText").isNull() ? null : d.path("labelText").asText(null))));
        }
        // 자동 등록이 이 id 들을 써야 하므로 먼저 내보낸다.
        detections.flush();
        return saved;
    }


    /**
     * 규칙 엔진이 판정을 바꾼 뒤, <b>답변 쪽을 그 판정에 맞춘다.</b>
     *
     * <p>없으면 정상 요청이 실패한다. 리뷰에서 재현된 회귀다 — 시드에 FLIGHT 규정만 있어서
     * {@code transport=TRAIN} 배터리 질문이 {@code ASK_AIRLINE} 이 되는데, Mock 픽스처의
     * Wh 되묻기가 그대로 남아 {@code validateOutput} 이
     * <i>"추가 정보가 필요하지 않으면 followUpQuestion은 null이어야 합니다"</i> 로 막았다.
     * 202 로 접수된 작업이 폴링 끝에 {@code FAILED} 로 끝났다.
     *
     * <p>실제 모델 경로는 2차 설명이 이미 판정을 보고 쓰지만, Mock 은 그 단계가 없다.
     * <b>두 경로가 같은 규약을 지나게</b> 여기서 한 번 더 맞춘다 — 07 이 "Mock 이라는 이유로
     * 서버 필드 채움을 생략하지 않는다" 고 한 것과 같은 취지다.
     *
     * <p>{@code reason} 은 규정을 못 찾았을 때만 손댄다. 07 이 그 자리에 쓸 문장까지 정해 뒀다.
     *
     * <p><b>{@code answer} 도 규정을 하나도 못 찾았으면 바꾼다.</b> 그러지 않으면 같은 응답 안에서
     * "규정을 찾지 못했다" 는 결과와 <i>"기내 반입 기준에 해당하며 위탁수하물로 부칠 수 없습니다"</i>
     * 같은 구체적 안내가 충돌한다 — {@code transport=TRAIN} 질문에서 <b>시드를 고치지 않아도</b>
     * 재현되던 것이라, 07 「알려진 한계」의 규정표 드리프트와는 별개다.
     *
     * <p>규정을 <b>찾은</b> 결과의 문장은 건드리지 않는다. Mock 픽스처 문장이 규정표와 어긋날 수
     * 있는 것은 07 「알려진 한계」에 남겨 둔 문제다.
     */
    private void alignToEngine(JsonNode input, JsonNode output) {
        boolean needsMoreInfo = false;
        boolean anyRuleFound = false;
        for (JsonNode result : output.path("results")) {
            if (!(result instanceof ObjectNode node)) continue;
            if (node.path("ruleId").isNull()) {
                node.put("reason", "해당 규정을 찾지 못했습니다. 항공사에 확인하세요.");
            } else {
                anyRuleFound = true;
            }
            needsMoreInfo |= Codes.RuleVerdict.NEED_MORE_INFO.name().equals(node.path("verdict").asText());
        }

        if (!(output instanceof ObjectNode root)) return;
        if (!input.path("question").isTextual()) {
            root.putNull("answer");
            root.putNull("followUpQuestion");
            return;
        }
        if (!anyRuleFound && !output.path("results").isEmpty()) {
            root.put("answer", "해당 이동수단의 반입 규정을 찾지 못했습니다. 항공사에 확인해 주세요. "
                    + "최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.");
        }
        if (!needsMoreInfo) {
            root.putNull("followUpQuestion");
        } else if (!root.path("followUpQuestion").isTextual()
                || root.path("followUpQuestion").asText().isBlank()) {
            root.put("followUpQuestion", "확인이 필요한 값을 알려 주세요.");
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
