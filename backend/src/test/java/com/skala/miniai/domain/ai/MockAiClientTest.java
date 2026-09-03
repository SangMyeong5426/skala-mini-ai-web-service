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
    void representativeChatbotQuestionsReturnTheirRuleResults() {
        assertQuestion("20000mAh 보조배터리 기내 되나요?", "보조배터리", "NEED_MORE_INFO", "batteryMah", 20000);
        assertQuestion("120ml 화장품 기내 반입되나요?", "화장품", "CHECKED_OK", "capacityMl", 120);
        assertQuestion("날 길이 7cm 가위 기내 반입되나요?", "가위", "CHECKED_OK", "bladeCm", 7);
    }

    @Test
    void unsupportedQuestionDoesNotInventARule() {
        JsonNode output = run("삼각대 가져가도 되나요?");

        assertThat(output.path("results").get(0).path("verdict").asText()).isEqualTo("ASK_AIRLINE");
        assertThat(output.path("results").get(0).path("ruleId").isNull()).isTrue();
        assertThat(output.path("answer").asText()).contains("항공사에 확인");
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
        contract.validateChatbotOutput(input, output);

        assertThat(output.path("results").get(0).path("attributes").path("batteryWh").asInt()).isEqualTo(100);
        assertThat(output.path("results").get(0).path("verdict").asText()).isEqualTo("CABIN_OK");
        assertThat(output.path("followUpQuestion").isNull()).isTrue();
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

        assertThatThrownBy(() -> contract.validateChatbotOutput(input, json.read("{}")))
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

    private JsonNode run(String question) {
        JsonNode input = contract.validateInput(json.read("""
                {"transport":"FLIGHT","airline":null,"question":"%s","items":[]}
                """.formatted(question)));
        JsonNode output = client.run(Codes.JobType.RULE_CHECK, input);
        contract.validateChatbotOutput(input, output);
        return output;
    }
}
