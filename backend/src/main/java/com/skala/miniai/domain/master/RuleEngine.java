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
     * 엄격도 — {@code CHECKED_FORBIDDEN > NEED_MORE_INFO > ASK_AIRLINE > RESTRICTED > CHECKED_OK > CABIN_OK}.
     * 숫자가 작을수록 엄격하다.
     *
     * <p>07 이 처음 적은 순서는 {@code ASK_AIRLINE} 을 {@code CHECKED_OK} 보다 <b>덜</b> 엄격하게
     * 봤다. 그러면 두 규정이 겹칠 때 <b>"항공사 승인 필요" 가 "위탁 가능" 에 밀린다</b> —
     * 사용자에게 승인 절차가 안 보이고 "부치면 된다" 만 남는다. 판정을 실제보다 확정적으로
     * 보이게 하는 것이라 이 클래스가 막으려던 것과 같은 종류의 문제다.
     *
     * <p>리뷰 결정에 따라 <b>07 의 표를 고치고</b> {@code InspectionService.STRICTNESS} 와 같은
     * 순서를 쓴다. 두 곳이 하는 일은 다르지만(여기는 겹치는 규정 중 하나를 고르고, 그쪽은
     * 물품별 판정을 화면 요약으로 접는다) 이 순서가 두 용도 모두에 안전하다.
     *
     * <p>{@code NEED_MORE_INFO} 는 "아직 못 정했다" 는 뜻이라 전면 금지 바로 다음이다 —
     * 모르는 채로 반입 가능 쪽으로 기울면 안 된다.
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

        List<TransportRule> candidates = candidatesFor(transport, keyword.asText());
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
            //
            // **출처를 붙이지 않는다.** 붙이면 화면에 "항공사에 확인하세요" 옆으로 공식 링크와
            // 확인 날짜가 뜨는데, 그 링크는 그렇게 말하지 않는다 — 조건이 안 맞아서 적용하지
            // 않기로 한 규정이다. 규정을 못 찾은 경우와 같은 모양으로 내보낸다.
            askAirline(result, null);
            return null;
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

    /**
     * 이 키워드에 걸리는 규정들. <b>정확히 같은 키워드를 먼저</b> 보고, 없을 때만 부분 일치로 떨어진다.
     *
     * <p>저장소 메서드가 부분 일치다 — {@code "보조배터리 2개"} 같은 이름도 걸리라고 그렇게 뒀다.
     * 그런데 {@link #keywordsOf} 가 모델에게 정확한 목록을 주므로, 돌아온 값은 보통 목록 안의
     * 값이다. 규정이 늘어 {@code 배터리} 류가 둘 이상이 되면 부분 일치가 서로를 끌어온다.
     * 정확 일치를 먼저 보면 그때도 의도한 규정만 남는다.
     */
    private List<TransportRule> candidatesFor(Codes.Transport transport, String keyword) {
        List<TransportRule> partial = rules
                .findByTransportAndKeywordContainingIgnoreCaseOrderById(transport, keyword);
        List<TransportRule> exact = partial.stream()
                .filter(rule -> rule.getKeyword().equalsIgnoreCase(keyword))
                .toList();
        return exact.isEmpty() ? partial : exact;
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
