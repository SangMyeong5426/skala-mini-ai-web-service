package com.skala.miniai.domain.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;

/**
 * 반입 판정이 <b>규정표에서만</b> 나오는지 본다 (07 「규칙 엔진 판정 규칙」 1~4).
 *
 * <p>모델이 낸 {@code verdict} 를 버리는지가 첫 번째다. 07 이 <i>"반입 여부를 네가 판정하지
 * 않는다"</i> 고 못박았는데, 모델 값이 새어 나가면 그 계약이 조용히 깨진다. 화면에는
 * 출처 URL 이 함께 뜨므로 <b>공식 규정이 아닌 판정이 공식처럼 보인다.</b>
 *
 * <p>DB 는 타지 않는다. {@code transport_rules} 는 {@code database/seed.sql} 과 같은 값으로 흉내 낸다.
 */
class RuleEngineTest {

    private static final String AIRPORT = "https://www.airport.kr/ap_ko/905/subview.do";
    private static final LocalDate CHECKED = LocalDate.of(2026, 9, 2);

    private final Json json = new Json(JsonMapper.builder().build());

    private TransportRuleRepository rules;
    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        rules = mock(TransportRuleRepository.class);
        engine = new RuleEngine(rules);
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(any(), any()))
                .willReturn(List.of());
    }

    /** {@code database/seed.sql} 의 보조배터리 3행. 100Wh · 160Wh 로 결론이 갈린다. */
    private void givenBatteryRules() {
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(
                eq(Codes.Transport.FLIGHT), eq("보조배터리")))
                .willReturn(List.of(
                        rule(1L, "보조배터리", Codes.RuleVerdict.CABIN_OK, "100Wh 이하"),
                        rule(2L, "보조배터리", Codes.RuleVerdict.ASK_AIRLINE, "100Wh 초과 160Wh 이하"),
                        rule(3L, "보조배터리", Codes.RuleVerdict.CHECKED_FORBIDDEN, "160Wh 초과")));
    }

    private static TransportRule rule(Long id, String keyword, Codes.RuleVerdict verdict, String note) {
        TransportRule rule = new TransportRule();
        ReflectionTestUtils.setField(rule, "id", id);
        ReflectionTestUtils.setField(rule, "transport", Codes.Transport.FLIGHT);
        ReflectionTestUtils.setField(rule, "keyword", keyword);
        ReflectionTestUtils.setField(rule, "verdict", verdict);
        ReflectionTestUtils.setField(rule, "conditionNote", note);
        ReflectionTestUtils.setField(rule, "description", "설명");
        ReflectionTestUtils.setField(rule, "sourceUrl", AIRPORT);
        ReflectionTestUtils.setField(rule, "checkedAt", CHECKED);
        return rule;
    }

    /** 모델이 냈다고 가정한 출력 한 건. {@code verdict} 는 일부러 낙관적으로 채워 둔다. */
    private JsonNode judge(String ruleKeyword, String attributes) {
        JsonNode input = json.read("{\"transport\":\"FLIGHT\",\"airline\":null,\"question\":\"q\",\"items\":[]}");
        JsonNode output = json.read("""
                {"results":[{"itemId":null,"detectionId":null,"name":"물품","qty":1,
                  "ruleKeyword":%s,"attributes":%s,
                  "verdict":"CABIN_OK","ruleId":999,"conditionNote":"모델이 지어낸 조건",
                  "reason":"r","missingInfo":null,
                  "sourceUrl":"https://model.invented/rule","checkedAt":"2000-01-01"}],
                 "answer":"a","followUpQuestion":null}
                """.formatted(ruleKeyword == null ? "null" : "\"" + ruleKeyword + "\"", attributes));
        engine.applyTo(input, output);
        return output.path("results").path(0);
    }

    private static final String NO_ATTRS =
            "{\"capacityMl\":null,\"batteryWh\":null,\"batteryMah\":null,\"bladeCm\":null}";

    @Test
    void 모델이_낸_판정과_출처를_버리고_규정표로_다시_매긴다() {
        givenBatteryRules();
        JsonNode result = judge("보조배터리", NO_ATTRS.replace("\"batteryWh\":null", "\"batteryWh\":50"));

        assertThat(result.path("verdict").asText()).isEqualTo("CABIN_OK");
        assertThat(result.path("ruleId").asLong()).isEqualTo(1L);          // 999 가 아니다
        assertThat(result.path("sourceUrl").asText()).isEqualTo(AIRPORT);  // model.invented 가 아니다
        assertThat(result.path("checkedAt").asText()).isEqualTo("2026-09-02");
        assertThat(result.path("conditionNote").asText()).isEqualTo("100Wh 이하");
    }

    /** 07 「예시 2」 그대로 — mAh 만 있으면 확정하지 않는다. */
    @Test
    void mAh_만_있으면_Wh_를_되묻는다() {
        givenBatteryRules();
        JsonNode result = judge("보조배터리", NO_ATTRS.replace("\"batteryMah\":null", "\"batteryMah\":20000"));

        assertThat(result.path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(result.path("missingInfo").asText()).isEqualTo("배터리 정격(Wh)");
        // 07 규칙 3 — 사용자가 확인할 첫 기준을 가리킨다.
        assertThat(result.path("ruleId").asLong()).isEqualTo(1L);
        assertThat(result.path("conditionNote").asText()).isEqualTo("100Wh 이하");
    }

    @Test
    void 경계값_100Wh_는_반입_가능이고_170Wh_는_전면_금지다() {
        givenBatteryRules();
        assertThat(judge("보조배터리", NO_ATTRS.replace("\"batteryWh\":null", "\"batteryWh\":100"))
                .path("verdict").asText()).isEqualTo("CABIN_OK");
        assertThat(judge("보조배터리", NO_ATTRS.replace("\"batteryWh\":null", "\"batteryWh\":120"))
                .path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(judge("보조배터리", NO_ATTRS.replace("\"batteryWh\":null", "\"batteryWh\":170"))
                .path("verdict").asText()).isEqualTo("CHECKED_FORBIDDEN");
    }

    @Test
    void 용량이_100ml_를_넘으면_위탁이다() {
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(
                eq(Codes.Transport.FLIGHT), eq("액체")))
                .willReturn(List.of(
                        rule(4L, "액체", Codes.RuleVerdict.CABIN_OK, "용기당 100ml 이하, 총 1L 이하"),
                        rule(5L, "액체", Codes.RuleVerdict.CHECKED_OK, "100ml 초과")));

        assertThat(judge("액체", NO_ATTRS.replace("\"capacityMl\":null", "\"capacityMl\":120"))
                .path("verdict").asText()).isEqualTo("CHECKED_OK");
        assertThat(judge("액체", NO_ATTRS.replace("\"capacityMl\":null", "\"capacityMl\":100"))
                .path("verdict").asText()).isEqualTo("CABIN_OK");
    }

    /** 07 규칙 1 — 규정을 못 찾으면 지어내지 않는다. 출처도 붙이지 않는다. */
    @Test
    void 규정을_못_찾으면_항공사_확인으로_넘기고_출처를_비운다() {
        JsonNode noKeyword = judge(null, NO_ATTRS);
        assertThat(noKeyword.path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(noKeyword.path("ruleId").isNull()).isTrue();
        assertThat(noKeyword.path("conditionNote").isNull()).isTrue();
        // sourceUrl 과 checkedAt 은 항상 함께 있거나 함께 null 이다 (07 · 명세 9절).
        assertThat(noKeyword.path("sourceUrl").isNull()).isTrue();
        assertThat(noKeyword.path("checkedAt").isNull()).isTrue();

        assertThat(judge("삼각대", NO_ATTRS).path("verdict").asText()).isEqualTo("ASK_AIRLINE");
    }

    /** 조건 문장이 없는 규정은 속성과 무관하게 적용된다 (노트북). */
    @Test
    void 조건_없는_규정은_속성_없이도_확정된다() {
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(
                eq(Codes.Transport.FLIGHT), eq("노트북")))
                .willReturn(List.of(rule(8L, "노트북", Codes.RuleVerdict.CABIN_OK, null)));

        JsonNode result = judge("노트북", NO_ATTRS);
        assertThat(result.path("verdict").asText()).isEqualTo("CABIN_OK");
        assertThat(result.path("conditionNote").isNull()).isTrue();
        assertThat(result.path("missingInfo").isNull()).isTrue();
    }

    /**
     * 07 규칙 3 — 어느 조건이든 결론이 같으면 속성을 몰라도 확정한다.
     * 결론이 갈리는 보조배터리와 대비되는 경우다.
     */
    @Test
    void 결론이_하나뿐이면_속성을_몰라도_확정한다() {
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(
                eq(Codes.Transport.FLIGHT), eq("인화물질")))
                .willReturn(List.of(
                        rule(9L, "인화물질", Codes.RuleVerdict.CHECKED_FORBIDDEN, "100ml 이하"),
                        rule(10L, "인화물질", Codes.RuleVerdict.CHECKED_FORBIDDEN, "100ml 초과")));

        JsonNode result = judge("인화물질", NO_ATTRS);
        assertThat(result.path("verdict").asText()).isEqualTo("CHECKED_FORBIDDEN");
        assertThat(result.path("missingInfo").isNull()).isTrue();
    }

    /** 07 규칙 4 — 규정이 충돌하면 더 엄격한 쪽. 조건이 겹치는 표에서만 일어난다. */
    @Test
    void 조건이_겹치면_더_엄격한_쪽을_고른다() {
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(
                eq(Codes.Transport.FLIGHT), eq("칼")))
                .willReturn(List.of(
                        rule(11L, "칼", Codes.RuleVerdict.CHECKED_OK, "날 길이 6cm 초과"),
                        rule(12L, "칼", Codes.RuleVerdict.CHECKED_FORBIDDEN, "날 길이 5cm 초과")));

        assertThat(judge("칼", NO_ATTRS.replace("\"bladeCm\":null", "\"bladeCm\":7"))
                .path("verdict").asText()).isEqualTo("CHECKED_FORBIDDEN");
    }

    /** 문장을 못 읽으면 확정하지 않는다. 규정을 잘못 읽고 "된다" 고 하는 쪽이 훨씬 나쁘다. */
    @Test
    void 읽지_못하는_조건_문장은_확정_판정을_막는다() {
        given(rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(
                eq(Codes.Transport.FLIGHT), eq("드론")))
                .willReturn(List.of(
                        rule(13L, "드론", Codes.RuleVerdict.CABIN_OK, "기내 반입 시 배터리 분리 후 휴대"),
                        rule(14L, "드론", Codes.RuleVerdict.CHECKED_FORBIDDEN, "160Wh 초과")));

        JsonNode result = judge("드론", NO_ATTRS.replace("\"batteryWh\":null", "\"batteryWh\":50"));
        assertThat(result.path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(result.path("missingInfo").asText()).isEqualTo("규정 조건 확인");
    }
}
