package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.ai.BagCheckPrompt;
import com.skala.miniai.domain.ai.MockAiClient;
import com.skala.miniai.domain.ai.OpenAiChatApi;
import com.skala.miniai.domain.ai.OpenAiClient;
import com.skala.miniai.domain.ai.PackingListPrompt;
import com.skala.miniai.domain.ai.RuleCheckPrompt;
import com.skala.miniai.domain.ai.VisionImageLoader;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.master.SeedRules;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.TripRepository;

/**
 * <b>모델이 규정 판정을 흔들 수 있는 두 경로</b>를 막았는지 본다. 리뷰에서 재현된 결함이다.
 *
 * <p>규칙 엔진이 판정을 낸다고 해서 안전한 것이 아니다. 엔진에 <b>무엇을 넘기느냐</b>를 모델이
 * 정하면, 판정 자체를 모델이 정하는 것과 같아진다.
 *
 * <ul>
 *   <li>물품과 규정 키워드의 <b>대응</b>을 모델이 흔들 수 있었다 (순서 바꾸기)
 *   <li>판정을 가르는 <b>속성 값</b>을 모델이 지어낼 수 있었다 (mAh → Wh 환산)
 * </ul>
 *
 * <p>네트워크는 타지 않는다. {@link OpenAiChatApi} 를 갈아 끼워 1차·2차 응답만 흉내 내고,
 * 규칙 엔진과 규정표는 실제 것을 쓴다.
 */
class OpenAiRuleCheckTest {

    private final Json json = new Json(JsonMapper.builder().build());

    private OpenAiChatApi api;
    private OpenAiClient client;

    @BeforeEach
    void setUp() {
        api = mock(OpenAiChatApi.class);
        client = new OpenAiClient(mock(MockAiClient.class), api, new BagCheckPrompt(json),
                new PackingListPrompt(json), new RuleCheckPrompt(json), SeedRules.engine(),
                mock(VisionImageLoader.class), mock(TripPhotoRepository.class),
                mock(TripRepository.class), mock(ChecklistItemRepository.class),
                Optional.empty(), json, 20);
    }

    /** 1차 구조화 응답과 2차 설명 응답을 차례로 돌려준다. */
    private void givenModelReturns(String structured, String explained) {
        given(api.complete(any(), any(), any(), any(), any()))
                .willReturn(json.read(structured), json.read(explained));
    }

    private static final String TWO_REASONS = """
            {"results":[{"reason":"이유 1"},{"reason":"이유 2"}],
             "answer":"답변입니다. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
             "followUpQuestion":null}
            """;

    private JsonNode byName(JsonNode results, String name) {
        for (JsonNode r : results) {
            if (name.equals(r.path("name").asText(""))) return r;
        }
        throw new AssertionError("이름이 " + name + " 인 결과가 없다: " + results);
    }

    /**
     * 모델이 물품 순서를 뒤집어도 <b>각 물품에 자기 규정이</b> 붙어야 한다.
     *
     * <p>예전에는 {@code structured[i]} 를 {@code items[i]} 에 그대로 붙이고 식별값만 입력 값으로
     * 덮어썼다. 그래서 200Wh 배터리에 가위 규정이 붙어 <b>전면 금지가 위탁 가능으로</b> 바뀌었고,
     * 식별값은 맞으니 계약 검증도 통과했다.
     */
    @Test
    void 모델이_순서를_바꿔도_물품마다_자기_규정이_붙는다() {
        JsonNode input = json.read("""
                {"transport":"FLIGHT","airline":null,"question":"이거 둘 기내 되나요?","items":[
                  {"itemId":1,"detectionId":null,"name":"보조배터리","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":200,"batteryMah":null,"bladeCm":null}},
                  {"itemId":2,"detectionId":null,"name":"가위","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":null,"bladeCm":7}}]}
                """);

        // 모델이 역순으로 답한다.
        givenModelReturns("""
                {"results":[
                  {"itemId":2,"detectionId":null,"name":"가위","qty":1,"ruleKeyword":"가위",
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":null,"bladeCm":7}},
                  {"itemId":1,"detectionId":null,"name":"보조배터리","qty":1,"ruleKeyword":"보조배터리",
                   "attributes":{"capacityMl":null,"batteryWh":200,"batteryMah":null,"bladeCm":null}}]}
                """, TWO_REASONS);

        JsonNode results = client.run(Codes.JobType.RULE_CHECK, 7L, input).path("results");

        // 계약대로 results 는 입력 순서다.
        assertThat(results.path(0).path("name").asText()).isEqualTo("보조배터리");
        assertThat(results.path(1).path("name").asText()).isEqualTo("가위");

        assertThat(byName(results, "보조배터리").path("ruleKeyword").asText()).isEqualTo("보조배터리");
        assertThat(byName(results, "보조배터리").path("verdict").asText())
                .as("200Wh 는 전면 금지다. 가위 규정이 붙으면 위탁 가능으로 뒤집힌다")
                .isEqualTo("CHECKED_FORBIDDEN");
        assertThat(byName(results, "가위").path("verdict").asText()).isEqualTo("CHECKED_OK");
    }

    /** 모델이 식별값을 안 돌려줘도 이름으로 짝을 찾는다. 그래도 못 찾으면 키워드를 버린다. */
    @Test
    void 모델이_물품을_빠뜨리면_그_물품의_키워드를_버린다() {
        JsonNode input = json.read("""
                {"transport":"FLIGHT","airline":null,"question":"이거 둘 기내 되나요?","items":[
                  {"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":200,"batteryMah":null,"bladeCm":null}},
                  {"itemId":null,"detectionId":null,"name":"가위","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":null,"bladeCm":7}}]}
                """);

        // 모델이 가위 하나만 답했다.
        givenModelReturns("""
                {"results":[
                  {"itemId":null,"detectionId":null,"name":"가위","qty":1,"ruleKeyword":"가위",
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":null,"bladeCm":7}}]}
                """, TWO_REASONS);

        JsonNode results = client.run(Codes.JobType.RULE_CHECK, 7L, input).path("results");

        assertThat(results).hasSize(2);
        assertThat(byName(results, "가위").path("verdict").asText()).isEqualTo("CHECKED_OK");
        // 짝을 못 찾은 물품에 남의 규정을 붙이지 않는다.
        JsonNode battery = byName(results, "보조배터리");
        assertThat(battery.path("ruleKeyword").isNull()).isTrue();
        assertThat(battery.path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(battery.path("sourceUrl").isNull()).isTrue();
    }

    /**
     * 07: mAh 만 있으면 Wh 를 확정하지 않는다. <b>프롬프트로 부탁만 하고 믿지 않는다.</b>
     *
     * <p>모델이 20000mAh 를 74Wh 로 환산해 내면 그 숫자가 공식 규정 판정의 근거가 된다.
     * 되물어야 할 질문이 {@code CABIN_OK} 로 확정돼 나갔던 경로다.
     */
    @Test
    void 사용자가_말하지_않은_Wh_는_버린다() {
        givenModelReturns("""
                {"results":[
                  {"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,"ruleKeyword":"보조배터리",
                   "attributes":{"capacityMl":null,"batteryWh":74,"batteryMah":20000,"bladeCm":null}}]}
                """, """
                {"results":[{"reason":"라벨의 Wh 를 확인해 주세요."}],
                 "answer":"확인이 필요합니다. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
                 "followUpQuestion":"배터리 라벨에 표시된 정격 Wh는 얼마인가요?"}
                """);

        JsonNode output = client.run(Codes.JobType.RULE_CHECK, 7L, json.read("""
                {"transport":"FLIGHT","airline":null,"question":"20000mAh 보조배터리 기내 되나요?","items":[]}
                """));

        JsonNode battery = output.path("results").path(0);
        assertThat(battery.path("attributes").path("batteryWh").isNull())
                .as("질문에 없는 74Wh 를 버려야 한다").isTrue();
        assertThat(battery.path("attributes").path("batteryMah").asInt()).isEqualTo(20000);
        assertThat(battery.path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(battery.path("missingInfo").asText()).isEqualTo("배터리 정격(Wh)");
        assertThat(output.path("followUpQuestion").asText()).isNotBlank();
    }

    /** 되묻기에 사용자가 답한 값은 살아야 한다. 07 「예시 2」의 후속 턴이다. */
    @Test
    void 사용자가_답한_Wh_는_판정에_쓴다() {
        givenModelReturns("""
                {"results":[
                  {"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,"ruleKeyword":"보조배터리",
                   "attributes":{"capacityMl":null,"batteryWh":100,"batteryMah":20000,"bladeCm":null}}]}
                """, """
                {"results":[{"reason":"100Wh 이하라 기내 반입만 가능합니다."}],
                 "answer":"기내로 가져가세요. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
                 "followUpQuestion":null}
                """);

        JsonNode output = client.run(Codes.JobType.RULE_CHECK, 7L, json.read("""
                {"transport":"FLIGHT","airline":null,"question":"100Wh예요","items":[
                  {"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":20000,"bladeCm":null}}]}
                """));

        JsonNode battery = output.path("results").path(0);
        assertThat(battery.path("attributes").path("batteryWh").asInt()).isEqualTo(100);
        assertThat(battery.path("verdict").asText()).isEqualTo("CABIN_OK");
        assertThat(output.path("followUpQuestion").isNull()).isTrue();
    }

    /** 입력에서 이미 확인된 속성은 모델이 뭐라 하든 그대로 쓴다. */
    @Test
    void 입력에_확인된_속성이_모델_값을_이긴다() {
        givenModelReturns("""
                {"results":[
                  {"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,"ruleKeyword":"보조배터리",
                   "attributes":{"capacityMl":null,"batteryWh":50,"batteryMah":null,"bladeCm":null}}]}
                """, """
                {"results":[{"reason":"160Wh 를 넘습니다."}],
                 "answer":"반입할 수 없습니다. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
                 "followUpQuestion":null}
                """);

        JsonNode output = client.run(Codes.JobType.RULE_CHECK, 7L, json.read("""
                {"transport":"FLIGHT","airline":null,"question":"이거 되나요?","items":[
                  {"itemId":null,"detectionId":null,"name":"보조배터리","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":200,"batteryMah":null,"bladeCm":null}}]}
                """));

        JsonNode battery = output.path("results").path(0);
        assertThat(battery.path("attributes").path("batteryWh").asInt()).isEqualTo(200);
        assertThat(battery.path("verdict").asText()).isEqualTo("CHECKED_FORBIDDEN");
    }
}
