package com.skala.miniai.domain.master;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Codes;

/**
 * 반입 판정. <b>여기가 정본이고 AI 는 끼지 않는다.</b>
 *
 * <p>07 「AI-04」가 그렇게 정했다 — <i>"반입 여부를 네가 판정하지 않는다. 판정은 규칙 엔진이
 * 공식 규정표로 한다."</i> 모델은 자연어에서 물품과 속성을 뽑고(1차) 결과를 설명할 뿐(2차),
 * {@code verdict} 를 정하지 않는다. 규정은 {@code transport_rules} 에 출처 URL 과 확인 날짜까지
 * 들어 있는 정확한 데이터라, 여기에 AI 를 두면 틀릴 자리만 늘어난다.
 *
 * <p>07 「규칙 엔진 판정 규칙」 1~4 를 그대로 옮겼다. 이 클래스는 {@code AI_PROVIDER} 와 무관하게
 * <b>Mock 이든 실제 모델이든 똑같이</b> 돈다 — 07 이 "Mock 이라는 이유로 서버 필드 채움을
 * 생략하지 않는다" 고 못박았기 때문이다.
 */
@Component
public class RuleEngine {

    /**
     * 07 규칙 4의 엄격도 — {@code CHECKED_FORBIDDEN > RESTRICTED > CHECKED_OK > ASK_AIRLINE > CABIN_OK}.
     * 숫자가 작을수록 엄격하다.
     *
     * <p>{@code NEED_MORE_INFO} 는 07 목록에 없다. "아직 못 정했다" 는 뜻이라 <b>전면 금지 바로
     * 다음</b>에 뒀다 — 모르는 채로 반입 가능 쪽으로 기울면 안 된다.
     *
     * <p><b>{@code InspectionService.STRICTNESS} 와 순서가 다르다.</b> 그쪽은
     * {@code ASK_AIRLINE} 을 {@code RESTRICTED}·{@code CHECKED_OK} 보다 엄격하게 본다.
     * 07 이 정한 순서가 이쪽이라 여기서는 07 을 따랐고, 어느 쪽이 맞는지는 작성자 확인이 필요하다.
     */
    private static final Map<Codes.RuleVerdict, Integer> STRICTNESS = new EnumMap<>(Codes.RuleVerdict.class);

    static {
        STRICTNESS.put(Codes.RuleVerdict.CHECKED_FORBIDDEN, 0);
        STRICTNESS.put(Codes.RuleVerdict.NEED_MORE_INFO, 1);
        STRICTNESS.put(Codes.RuleVerdict.RESTRICTED, 2);
        STRICTNESS.put(Codes.RuleVerdict.CHECKED_OK, 3);
        STRICTNESS.put(Codes.RuleVerdict.ASK_AIRLINE, 4);
        STRICTNESS.put(Codes.RuleVerdict.CABIN_OK, 5);
    }

    private final TransportRuleRepository rules;

    public RuleEngine(TransportRuleRepository rules) {
        this.rules = rules;
    }

    /** 모델에게 프롬프트로 줄 규정 키워드 목록. 모델은 이 안에서만 {@code ruleKeyword} 를 고른다. */
    @Transactional(readOnly = true)
    public List<String> keywordsOf(Codes.Transport transport) {
        Set<String> keywords = new LinkedHashSet<>();
        rules.findByTransportOrderById(transport).forEach(rule -> keywords.add(rule.getKeyword()));
        return List.copyOf(keywords);
    }

    /**
     * 07 {@code RULE_CHECK output} 의 {@code results[]} 에 판정을 <b>덮어쓴다.</b>
     *
     * <p>모델이나 Mock 이 {@code verdict} 를 냈더라도 버린다. 07 「누가 채우나」가
     * {@code verdict · ruleId · conditionNote · missingInfo · sourceUrl · checkedAt} 을
     * 규칙 엔진 몫으로 정했다.
     *
     * <p>같은 입력이면 같은 결과라 <b>여러 번 돌려도 안전하다.</b>
     * 실제 모델 경로는 2차 설명 프롬프트를 만들려고 한 번 부르고, 저장 직전에 한 번 더 지난다.
     *
     * @return 결과 하나마다 적용된 규정의 {@code description}. 규정을 못 찾았으면 그 자리는
     *         {@code null} 이다. 07 의 2차 설명 프롬프트가 이 문장을 요구한다 —
     *         출력 스키마에는 없는 값이라 여기서 함께 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<String> applyTo(JsonNode input, JsonNode output) {
        Codes.Transport transport = transportOf(input);
        List<String> descriptions = new ArrayList<>();
        if (transport == null) return descriptions;

        for (JsonNode result : output.path("results")) {
            descriptions.add(result instanceof ObjectNode node ? judge(transport, node) : null);
        }
        return descriptions;
    }

    private static Codes.Transport transportOf(JsonNode input) {
        try {
            return Codes.Transport.valueOf(input.path("transport").asText(""));
        } catch (IllegalArgumentException e) {
            return null;   // RuleCheckContract 가 접수 때 이미 막지만, 저장 경로에서 터지지는 않게 둔다
        }
    }

    /** @return 적용한 규정의 설명. 규정을 못 찾았으면 {@code null}. */
    private String judge(Codes.Transport transport, ObjectNode result) {
        JsonNode keyword = result.path("ruleKeyword");
        if (!keyword.isTextual() || keyword.asText().isBlank()) {
            // 07 규칙 1 — 모델이 규정 키워드를 못 붙였다. 규정을 지어내지 않는다.
            askAirline(result, null);
            return null;
        }

        List<TransportRule> candidates = rules
                .findByTransportAndKeywordContainingIgnoreCaseOrderById(transport, keyword.asText());
        if (candidates.isEmpty()) {
            askAirline(result, null);
            return null;
        }

        // 사용자가 확인할 첫 기준. 07 규칙 3이 "같은 키워드 규정 중 첫 행(id 가 가장 작은 것)" 이라 했다.
        TransportRule first = candidates.get(0);
        JsonNode attributes = result.path("attributes");

        // 조건 문장을 한 줄도 못 읽는 규정이 섞여 있으면 확정 판정을 하지 않는다.
        boolean unreadable = candidates.stream()
                .anyMatch(rule -> RuleCondition.unreadable(rule.getConditionNote()));

        List<RuleCondition.Attribute> missing = missingAttributes(candidates, attributes);

        if (missing.isEmpty() && !unreadable) {
            List<TransportRule> matched = candidates.stream()
                    .filter(rule -> RuleCondition.parse(rule.getConditionNote()).matches(attributes))
                    .toList();
            if (!matched.isEmpty()) {
                // 07 규칙 4 — 규정이 충돌하면 더 엄격한 쪽.
                TransportRule strictest = matched.stream()
                        .min(Comparator.comparingInt(rule -> STRICTNESS.get(rule.getVerdict())))
                        .orElseThrow();
                apply(result, strictest, null);
                return strictest.getDescription();
            }
            // 속성은 다 알지만 어느 조건에도 걸리지 않는다 — 규정표에 빈 구간이 있다는 뜻이다.
            // 지어내지 않고 항공사 확인으로 넘기되, 사용자가 볼 수 있게 출처는 붙인다.
            askAirline(result, first);
            return first.getDescription();
        }

        // 07 규칙 3 — 어느 조건이든 결론이 같은 경우만 그 verdict 로 확정한다.
        Set<Codes.RuleVerdict> verdicts = new LinkedHashSet<>();
        candidates.forEach(rule -> verdicts.add(rule.getVerdict()));
        if (verdicts.size() == 1 && !unreadable) {
            apply(result, first, null);
            return first.getDescription();
        }

        // 보조배터리가 여기다. 160Wh 초과가 전면 금지라 Wh 를 모르면 CABIN_OK 로 확정할 수 없다.
        //
        // verdict 를 명시해 넘긴다. missingInfo 로 판단하게 두면, 조건 문장을 못 읽었는데
        // 빠진 속성은 없는 경우(unreadable) 에 문구가 null 이 되어 확정 판정으로 새어 나간다.
        String missingInfo = missing.isEmpty()
                ? "규정 조건 확인"
                : String.join(" · ", missing.stream().map(RuleCondition.Attribute::label).toList());
        apply(result, first, missingInfo, Codes.RuleVerdict.NEED_MORE_INFO);
        return first.getDescription();
    }

    /** 후보 규정들이 보는 속성 중 아직 값이 없는 것. 07 규칙 2의 "mAh 만 있는 경우" 가 여기 걸린다. */
    private static List<RuleCondition.Attribute> missingAttributes(
            List<TransportRule> candidates, JsonNode attributes) {
        List<RuleCondition.Attribute> missing = new ArrayList<>();
        for (TransportRule rule : candidates) {
            for (RuleCondition.Attribute attribute : RuleCondition.parse(rule.getConditionNote())
                    .requiredAttributes()) {
                JsonNode value = attributes.path(attribute.field());
                if (!value.isNumber() && !missing.contains(attribute)) missing.add(attribute);
            }
        }
        return missing;
    }

    /** 07 규칙 1 — 규정을 모르면 지어내지 않는다. {@code rule} 이 있으면 출처만 붙인다. */
    private static void askAirline(ObjectNode result, TransportRule rule) {
        apply(result, rule, null, Codes.RuleVerdict.ASK_AIRLINE);
    }

    private static void apply(ObjectNode result, TransportRule rule, String missingInfo) {
        apply(result, rule, missingInfo,
                missingInfo == null ? rule.getVerdict() : Codes.RuleVerdict.NEED_MORE_INFO);
    }

    /** {@code sourceUrl} 과 {@code checkedAt} 은 <b>항상 함께</b> 있거나 함께 {@code null} 이다 (07). */
    private static void apply(ObjectNode result, TransportRule rule,
                              String missingInfo, Codes.RuleVerdict verdict) {
        result.put("verdict", verdict.name());
        if (missingInfo == null) result.putNull("missingInfo");
        else result.put("missingInfo", missingInfo);

        if (rule == null) {
            result.putNull("ruleId");
            result.putNull("conditionNote");
            result.putNull("sourceUrl");
            result.putNull("checkedAt");
            return;
        }
        result.put("ruleId", rule.getId());
        if (rule.getConditionNote() == null) result.putNull("conditionNote");
        else result.put("conditionNote", rule.getConditionNote());
        result.put("sourceUrl", rule.getSourceUrl());
        result.put("checkedAt", rule.getCheckedAt().toString());
    }
}
