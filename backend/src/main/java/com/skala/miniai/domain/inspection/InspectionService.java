package com.skala.miniai.domain.inspection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.ai.AiInputBuilder;
import com.skala.miniai.domain.ai.AiJob;
import com.skala.miniai.domain.ai.AiJobRepository;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.checklist.ChecklistService;
import com.skala.miniai.domain.checklist.ItemRuleCheck;
import com.skala.miniai.domain.checklist.ItemRuleCheckRepository;
import com.skala.miniai.domain.checklist.PhotoStatusResolver;
import com.skala.miniai.domain.master.TransportRule;
import com.skala.miniai.domain.master.TransportRuleRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripService;

/**
 * 검수 결과 (UC-06 · UC-07 · UC-10).
 *
 * <p>세 가지를 <b>한 응답</b>으로 모은다. 화면이 세 번 요청하면 셋의 시점이 어긋나
 * "완료 6개인데 무게는 5개 기준" 같은 화면이 나온다.
 */
@Service
public class InspectionService {

    /** 명세 9절 책임 범위 고지. 화면에 반드시 넣는다. */
    private static final String NOTICE =
            "사진 분석 결과는 가방 전체를 확인한 것이 아닙니다. 사진에서 확인되지 않은 물건은 직접 확인해 주세요.";

    /**
     * 07: 항목별로 <b>가장 엄격한 판정 하나</b>만 보여준다. 값이 작을수록 엄격하다.
     * {@code NEED_MORE_INFO} 를 위에 두는 이유는 "모르면 단정하지 않는다" 가 이 서비스의 태도이기 때문이다.
     */
    private static final Map<Codes.RuleVerdict, Integer> STRICTNESS = new EnumMap<>(Codes.RuleVerdict.class);
    static {
        STRICTNESS.put(Codes.RuleVerdict.CHECKED_FORBIDDEN, 0);
        STRICTNESS.put(Codes.RuleVerdict.NEED_MORE_INFO, 1);
        STRICTNESS.put(Codes.RuleVerdict.ASK_AIRLINE, 2);
        STRICTNESS.put(Codes.RuleVerdict.RESTRICTED, 3);
        STRICTNESS.put(Codes.RuleVerdict.CHECKED_OK, 4);
        STRICTNESS.put(Codes.RuleVerdict.CABIN_OK, 5);
    }

    private final ChecklistItemRepository items;
    private final ItemRuleCheckRepository ruleChecks;
    private final TransportRuleRepository rules;
    private final AiJobRepository jobs;
    private final AiInputBuilder inputBuilder;
    private final PhotoStatusResolver photoStatus;
    private final ChecklistService checklistService;
    private final TripService tripService;
    private final Json json;

    public InspectionService(ChecklistItemRepository items, ItemRuleCheckRepository ruleChecks,
                             TransportRuleRepository rules, AiJobRepository jobs,
                             AiInputBuilder inputBuilder, PhotoStatusResolver photoStatus,
                             ChecklistService checklistService, TripService tripService, Json json) {
        this.items = items;
        this.ruleChecks = ruleChecks;
        this.rules = rules;
        this.jobs = jobs;
        this.inputBuilder = inputBuilder;
        this.photoStatus = photoStatus;
        this.checklistService = checklistService;
        this.tripService = tripService;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public InspectionDtos.Response of(Long tripId) {
        Trip trip = tripService.mustOwn(tripId);
        List<ChecklistItem> rows = items.findByTripIdOrderById(tripId);

        return new InspectionDtos.Response(
                tripId,
                readiness(tripId, rows),
                weight(trip),
                customs(rows),
                NOTICE);
    }

    // ── 준비 상태 ─────────────────────────────────────────

    private InspectionDtos.Readiness readiness(Long tripId, List<ChecklistItem> rows) {
        Map<Long, Codes.PhotoStatus> statuses = photoStatus.resolve(rows.stream().map(ChecklistItem::getId).toList());

        List<InspectionDtos.ReadyItem> prepared = new ArrayList<>();
        List<InspectionDtos.ReadyItem> unprepared = new ArrayList<>();
        for (ChecklistItem i : rows) {
            InspectionDtos.ReadyItem dto = new InspectionDtos.ReadyItem(
                    i.getId(), i.getName(), i.getQty(), statuses.get(i.getId()));
            if (i.isPrepared()) prepared.add(dto);
            else unprepared.add(dto);
        }

        return new InspectionDtos.Readiness(
                prepared, unprepared,
                checklistService.completionRate(rows),
                checklistService.unacceptedRequiredCount(rows, checklistService.latestCompletedRecommendation(tripId)));
    }

    // ── 예상 무게 ─────────────────────────────────────────

    /**
     * 06: 가장 최근 완료된 무게 작업 중 <b>현재 입력과 같은 결과만</b> 돌려준다.
     * 다르거나 없으면 {@code null} 이고, 화면이 다시 요청한다.
     *
     * <p>오래된 작업이 뒤늦게 끝나도 현재 결과로 쓰지 않는다 — 사용자가 잘못된 여유를 믿게 된다.
     */
    private InspectionDtos.Weight weight(Trip trip) {
        Optional<AiJob> latest = jobs.findTopByTripIdAndJobTypeAndStatusOrderByCompletedAtDescIdDesc(
                trip.getId(), Codes.JobType.WEIGHT_ESTIMATE, Codes.JobStatus.COMPLETED);
        if (latest.isEmpty()) return null;

        AiJob job = latest.get();
        // 정규화해서 비교한다. 직접 만든 노드와 DB 에서 읽은 노드는 값이 같아도
        // 숫자 노드 타입이 달라 equals 가 false 다 (Json#canonical).
        JsonNode storedInput = json.canonical(json.read(job.getInputPayload()));
        if (storedInput == null || !storedInput.equals(json.canonical(inputBuilder.weightEstimate(trip)))) {
            return null;
        }

        JsonNode out = json.read(job.getOutputPayload());
        if (out == null) return null;

        // 07 output → 06 투영. excluded 는 빼고 contributions 는 위 3개만 보여준다.
        List<InspectionDtos.Contribution> contributions = new ArrayList<>();
        for (JsonNode c : out.path("contributions")) {
            if (contributions.size() == 3) break;
            contributions.add(new InspectionDtos.Contribution(
                    c.path("name").asText(),
                    c.path("typicalG").asInt(),
                    c.path("qty").asInt(),
                    c.path("subtotalG").asInt()));
        }

        return new InspectionDtos.Weight(
                out.path("minG").asInt(),
                out.path("typicalG").asInt(),
                out.path("maxG").asInt(),
                out.path("limitG").isNull() ? null : out.path("limitG").asInt(),
                Codes.WeightVerdict.valueOf(out.path("verdict").asText()),
                Codes.ConfidenceLevel.valueOf(out.path("confidence").asText()),
                out.path("confidenceReason").asText(),
                out.path("excludedCount").asInt(),
                contributions);
    }

    // ── 반입 판정 ─────────────────────────────────────────

    /** 07: {@code item_rule_checks} 를 <b>항목별로 모아 가장 엄격한 판정 하나</b>를 보여준다. */
    private List<InspectionDtos.Customs> customs(List<ChecklistItem> rows) {
        if (rows.isEmpty()) return List.of();

        Map<Long, ChecklistItem> byId = new LinkedHashMap<>();
        rows.forEach(i -> byId.put(i.getId(), i));

        List<ItemRuleCheck> checks = ruleChecks.findByChecklistItemIdIn(byId.keySet());
        if (checks.isEmpty()) return List.of();

        Map<Long, TransportRule> ruleById = new LinkedHashMap<>();
        rules.findAllById(checks.stream().map(ItemRuleCheck::getRuleId).toList())
                .forEach(r -> ruleById.put(r.getId(), r));

        Map<Long, ItemRuleCheck> strictest = new LinkedHashMap<>();
        for (ItemRuleCheck c : checks) {
            strictest.merge(c.getChecklistItemId(), c,
                    (a, b) -> STRICTNESS.get(a.getVerdict()) <= STRICTNESS.get(b.getVerdict()) ? a : b);
        }

        List<InspectionDtos.Customs> out = new ArrayList<>();
        for (ChecklistItem item : rows) {
            ItemRuleCheck c = strictest.get(item.getId());
            if (c == null) continue;
            TransportRule rule = ruleById.get(c.getRuleId());
            out.add(new InspectionDtos.Customs(
                    item.getId(), item.getName(), c.getVerdict(), c.getMissingInfo(),
                    rule == null ? null : rule.getDescription(),
                    rule == null ? null : rule.getSourceUrl(),
                    rule == null ? null : rule.getCheckedAt()));
        }
        return out;
    }
}
