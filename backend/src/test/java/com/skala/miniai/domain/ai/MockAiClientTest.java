package com.skala.miniai.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MockAiClientTest {

    private final Json json = new Json(JsonMapper.builder().build());
    private final MockAiClient client = new MockAiClient(json, "mock");
    private final RuleCheckContract contract = new RuleCheckContract();

    @Test
    void elevenRepresentativeChatbotQuestionsReturnTheirRuleResults() {
        assertQuestion("20000mAh 보조배터리 기내 되나요?", "보조배터리", "NEED_MORE_INFO", "batteryMah", 20000);
        assertQuestion("100Wh 보조배터리 기내 반입되나요?", "보조배터리", "CABIN_OK", "batteryWh", 100);
        assertQuestion("120Wh 보조배터리 기내 반입되나요?", "보조배터리", "ASK_AIRLINE", "batteryWh", 120);
        assertQuestion("200Wh 보조배터리 기내 반입되나요?", "보조배터리", "CHECKED_FORBIDDEN", "batteryWh", 200);
        assertQuestion("50ml 화장품 기내 반입되나요?", "화장품", "CABIN_OK", "capacityMl", 50);
        assertQuestion("120ml 화장품 기내 반입되나요?", "화장품", "CHECKED_OK", "capacityMl", 120);
        assertFollowUp("화장품 용량을 모르겠어요", "화장품", "용기 용량은 몇 ml인가요?");
        assertQuestion("날 길이 5cm 가위 기내 반입되나요?", "가위", "CABIN_OK", "bladeCm", 5);
        assertQuestion("날 길이 7cm 가위 기내 반입되나요?", "가위", "CHECKED_OK", "bladeCm", 7);
        assertFollowUp("가위 길이를 모르겠어요", "가위", "가위 날 길이는 몇 cm인가요?");

        JsonNode laptop = run("노트북 기내 반입되나요?");
        assertThat(laptop.path("results").get(0).path("name").asText()).isEqualTo("노트북");
        assertThat(laptop.path("results").get(0).path("verdict").asText()).isEqualTo("CABIN_OK");
    }

    @Test
    void unsupportedQuestionDoesNotInventARule() {
        JsonNode output = run("삼각대 가져가도 되나요?");

        assertThat(output.path("results").get(0).path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(output.path("results").get(0).path("ruleId").isNull()).isTrue();
        assertThat(output.path("answer").asText()).contains("항공사에 확인");

        assertThat(run("150ml 화장품 기내 반입되나요?").path("results").get(0)
                .path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(run("날 길이 15cm 가위 기내 반입되나요?").path("results").get(0)
                .path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(run("1200Wh 보조배터리 기내 반입되나요?").path("results").get(0)
                .path("verdict").asText()).isEqualTo("ASK_AIRLINE");
    }

    @Test
    void batteryFollowUpUsesPreviousStructuredItem() {
        JsonNode input = json.read("""
                {"transport":"FLIGHT","airline":null,"question":"100Wh예요","items":[{
                  "itemId":null,"detectionId":null,"name":"보조배터리","qty":1,
                  "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":20000,"bladeCm":null}
                }]}
                """);

        JsonNode output = client.run(Codes.JobType.RULE_CHECK, contract.validateInput(input));
        contract.validateOutput(input, output);

        assertThat(output.path("results").get(0).path("attributes").path("batteryWh").asInt()).isEqualTo(100);
        assertThat(output.path("results").get(0).path("verdict").asText()).isEqualTo("CABIN_OK");
        assertThat(output.path("followUpQuestion").isNull()).isTrue();
    }

    @Test
    void unsupportedFollowUpKeepsPreviousItemAndAsksAirline() {
        JsonNode input = json.read("""
                {"transport":"FLIGHT","airline":null,"question":"45Wh예요","items":[{
                  "itemId":7,"detectionId":9,"name":"보조배터리","qty":1,
                  "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":20000,"bladeCm":null}
                }]}
                """);

        JsonNode output = client.run(Codes.JobType.RULE_CHECK, contract.validateInput(input));
        contract.validateOutput(input, output);
        JsonNode result = output.path("results").get(0);

        assertThat(result.path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(result.path("itemId").asLong()).isEqualTo(7);
        assertThat(result.path("detectionId").asLong()).isEqualTo(9);
        assertThat(result.path("name").asText()).isEqualTo("보조배터리");
        assertThat(result.path("attributes").path("batteryMah").asInt()).isEqualTo(20000);
    }

    @Test
    void firstQuestionWith100WhBatteryReturnsCabinOk() {
        JsonNode output = run("100Wh 보조배터리 되나요?");

        assertThat(output.path("results").get(0).path("verdict").asText()).isEqualTo("CABIN_OK");
    }

    @Test
    void itemListOutputEchoesInputOrderAndIdentifiers() {
        JsonNode input = json.read("""
                {"transport":"FLIGHT","airline":"대한항공","question":null,"items":[
                  {"itemId":11,"detectionId":17,"name":"가위","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":null,"bladeCm":null}},
                  {"itemId":6,"detectionId":null,"name":"보조배터리","qty":1,
                   "attributes":{"capacityMl":null,"batteryWh":null,"batteryMah":null,"bladeCm":null}}
                ]}
                """);

        JsonNode output = client.run(Codes.JobType.RULE_CHECK, contract.validateInput(input));
        contract.validateOutput(input, output);

        assertThat(output.path("results").get(0).path("itemId").asLong()).isEqualTo(11);
        assertThat(output.path("results").get(0).path("detectionId").asLong()).isEqualTo(17);
        assertThat(output.path("results").get(1).path("itemId").asLong()).isEqualTo(6);
        assertThat(output.path("answer").isNull()).isTrue();
    }

    @Test
    void malformedRuleCheckInputIsRejected() {
        assertThatThrownBy(() -> contract.validateInput(json.read("{\"foo\":\"bar\"}")))
                .isInstanceOf(com.skala.miniai.common.ApiException.class)
                .hasMessageContaining("필수");
    }

    @Test
    void malformedChatbotOutputIsRejected() {
        JsonNode input = contract.validateInput(json.read("""
                {"transport":"FLIGHT","airline":null,"question":"삼각대 되나요?","items":[]}
                """));

        assertThatThrownBy(() -> contract.validateOutput(input, json.read("{}")))
                .isInstanceOf(com.skala.miniai.common.ApiException.class)
                .hasMessageContaining("필수");
    }

    private void assertQuestion(String question, String name, String verdict, String attribute, int value) {
        JsonNode output = run(question);
        JsonNode result = output.path("results").get(0);

        assertThat(result.path("name").asText()).isEqualTo(name);
        assertThat(result.path("verdict").asText()).isEqualTo(verdict);
        assertThat(result.path("attributes").path(attribute).asInt()).isEqualTo(value);
        assertThat(output.path("answer").asText()).isNotBlank();
    }

    private void assertFollowUp(String question, String name, String followUp) {
        JsonNode output = run(question);
        assertThat(output.path("results").get(0).path("name").asText()).isEqualTo(name);
        assertThat(output.path("results").get(0).path("verdict").asText()).isEqualTo("NEED_MORE_INFO");
        assertThat(output.path("followUpQuestion").asText()).isEqualTo(followUp);
    }

    private JsonNode run(String question) {
        JsonNode input = contract.validateInput(json.read("""
                {"transport":"FLIGHT","airline":null,"question":"%s","items":[]}
                """.formatted(question)));
        JsonNode output = client.run(Codes.JobType.RULE_CHECK, input);
        contract.validateOutput(input, output);
        return output;
    }
}
