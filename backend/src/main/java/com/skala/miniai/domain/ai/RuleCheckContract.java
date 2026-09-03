package com.skala.miniai.domain.ai;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;

import tools.jackson.databind.JsonNode;

/** {@code docs/07-ai-ready.md}의 RULE_CHECK 입출력 계약을 실행 시점에 확인한다. */
@Component
public class RuleCheckContract {

    private static final Set<String> INPUT_FIELDS = Set.of("transport", "airline", "question", "items");
    private static final Set<String> INPUT_ITEM_FIELDS = Set.of(
            "itemId", "detectionId", "name", "qty", "attributes");
    private static final Set<String> ATTRIBUTE_FIELDS = Set.of(
            "capacityMl", "batteryWh", "batteryMah", "bladeCm");
    private static final Set<String> OUTPUT_FIELDS = Set.of("results", "answer", "followUpQuestion");
    private static final Set<String> RESULT_FIELDS = Set.of(
            "itemId", "detectionId", "name", "qty", "ruleKeyword", "attributes",
            "verdict", "ruleId", "conditionNote", "reason", "missingInfo", "sourceUrl", "checkedAt");

    public JsonNode validateInput(JsonNode input) {
        requireObject(input, "input", INPUT_FIELDS, false);
        requireEnum(input.get("transport"), "input.transport", Codes.Transport.class, false);
        requireNullableText(input.get("airline"), "input.airline", 50, false);
        requireNullableText(input.get("question"), "input.question", 500, false);

        JsonNode items = input.get("items");
        if (!items.isArray() || items.size() > 50) {
            failInput("items는 최대 50개의 배열이어야 합니다.", "input.items");
        }
        for (int i = 0; i < items.size(); i++) validateInputItem(items.get(i), i);

        if (input.get("question").isNull() && items.isEmpty()) {
            failInput("question 또는 items 중 하나는 필요합니다.", "input");
        }
        return input;
    }

    /** 챗봇 질문 출력만 검증한다. 물품 목록 RULE_CHECK는 기존 S-06 흐름을 유지한다. */
    public void validateChatbotOutput(JsonNode input, JsonNode output) {
        if (!input.path("question").isTextual()) return;

        requireObject(output, "output", OUTPUT_FIELDS, true);
        JsonNode results = output.get("results");
        if (!results.isArray() || results.size() > 50) failOutput("results는 최대 50개의 배열이어야 합니다.");

        boolean needsMoreInfo = false;
        for (int i = 0; i < results.size(); i++) {
            JsonNode result = results.get(i);
            validateResult(result, i);
            needsMoreInfo |= "NEED_MORE_INFO".equals(result.get("verdict").asText());
        }
        matchInputItems(input.get("items"), results);

        requireText(output.get("answer"), "output.answer", 600, true);
        if (needsMoreInfo) requireText(output.get("followUpQuestion"), "output.followUpQuestion", 200, true);
        else if (!output.get("followUpQuestion").isNull()) {
            failOutput("추가 정보가 필요하지 않으면 followUpQuestion은 null이어야 합니다.");
        }
    }

    private void validateInputItem(JsonNode item, int index) {
        String field = "input.items[" + index + "]";
        requireObject(item, field, INPUT_ITEM_FIELDS, false);
        requireNullableId(item.get("itemId"), field + ".itemId", false);
        requireNullableId(item.get("detectionId"), field + ".detectionId", false);
        requireText(item.get("name"), field + ".name", 100, false);
        requireInteger(item.get("qty"), field + ".qty", 1, 99, false);
        validateAttributes(item.get("attributes"), field + ".attributes", false);
    }

    private void validateResult(JsonNode result, int index) {
        String field = "output.results[" + index + "]";
        requireObject(result, field, RESULT_FIELDS, true);
        requireNullableId(result.get("itemId"), field + ".itemId", true);
        requireNullableId(result.get("detectionId"), field + ".detectionId", true);
        requireText(result.get("name"), field + ".name", 100, true);
        requireInteger(result.get("qty"), field + ".qty", 1, 99, true);
        requireNullableText(result.get("ruleKeyword"), field + ".ruleKeyword", 100, true);
        validateAttributes(result.get("attributes"), field + ".attributes", true);
        requireEnum(result.get("verdict"), field + ".verdict", Codes.RuleVerdict.class, true);
        requireNullableId(result.get("ruleId"), field + ".ruleId", true);
        requireNullableText(result.get("conditionNote"), field + ".conditionNote", 200, true);
        requireText(result.get("reason"), field + ".reason", 300, true);
        requireNullableText(result.get("missingInfo"), field + ".missingInfo", 100, true);
        requireNullableText(result.get("sourceUrl"), field + ".sourceUrl", 255, true);
        requireNullableDate(result.get("checkedAt"), field + ".checkedAt");

        boolean need = "NEED_MORE_INFO".equals(result.get("verdict").asText());
        if (need != !result.get("missingInfo").isNull()) {
            failOutput("NEED_MORE_INFO와 missingInfo 조합이 맞지 않습니다.");
        }
        if (result.get("sourceUrl").isNull() != result.get("checkedAt").isNull()) {
            failOutput("sourceUrl과 checkedAt은 함께 있어야 합니다.");
        }
        if (!result.get("sourceUrl").isNull()) {
            try {
                if (!URI.create(result.get("sourceUrl").asText()).isAbsolute()) failOutput("sourceUrl은 절대 URI여야 합니다.");
            } catch (IllegalArgumentException e) {
                failOutput("sourceUrl 형식이 올바르지 않습니다.");
            }
        }
    }

    private void validateAttributes(JsonNode attributes, String field, boolean output) {
        requireObject(attributes, field, ATTRIBUTE_FIELDS, output);
        for (String name : ATTRIBUTE_FIELDS) {
            JsonNode value = attributes.get(name);
            if (!value.isNull() && (!value.isNumber() || value.asDouble() < 0)) {
                fail(output, field + "." + name + "은 0 이상의 숫자 또는 null이어야 합니다.", field + "." + name);
            }
        }
    }

    private void matchInputItems(JsonNode items, JsonNode results) {
        if (items.isEmpty()) return;
        if (items.size() != results.size()) failOutput("후속 질문의 items와 results 개수가 다릅니다.");

        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            JsonNode result = results.get(i);
            if (!sameNullableLong(item.get("itemId"), result.get("itemId"))
                    || !sameNullableLong(item.get("detectionId"), result.get("detectionId"))
                    || !item.get("name").asText().equals(result.get("name").asText())
                    || item.get("qty").asInt() != result.get("qty").asInt()) {
                failOutput("후속 질문의 items와 results 식별값이 다릅니다.");
            }
        }
    }

    private boolean sameNullableLong(JsonNode left, JsonNode right) {
        return left.isNull() ? right.isNull() : !right.isNull() && left.asLong() == right.asLong();
    }

    private void requireObject(JsonNode node, String field, Set<String> fields, boolean output) {
        if (node == null || !node.isObject()) fail(output, field + "은 객체여야 합니다.", field);
        for (String name : fields) {
            if (!node.has(name)) fail(output, field + "." + name + "은 필수입니다.", field + "." + name);
        }
        if (node.size() != fields.size()) fail(output, field + "에 허용되지 않은 필드가 있습니다.", field);
    }

    private void requireText(JsonNode node, String field, int max, boolean output) {
        if (node == null || !node.isTextual() || node.asText().isBlank() || node.asText().length() > max) {
            fail(output, field + "은 1~" + max + "자 문자열이어야 합니다.", field);
        }
    }

    private void requireNullableText(JsonNode node, String field, int max, boolean output) {
        if (node == null) fail(output, field + "은 필수입니다.", field);
        if (!node.isNull()) requireText(node, field, max, output);
    }

    private void requireNullableId(JsonNode node, String field, boolean output) {
        if (node == null) fail(output, field + "은 필수입니다.", field);
        if (!node.isNull()) requireInteger(node, field, 1, Long.MAX_VALUE, output);
    }

    private void requireInteger(JsonNode node, String field, long min, long max, boolean output) {
        if (node == null || !node.isIntegralNumber() || node.asLong() < min || node.asLong() > max) {
            fail(output, field + "의 정수 범위가 올바르지 않습니다.", field);
        }
    }

    private <E extends Enum<E>> void requireEnum(
            JsonNode node, String field, Class<E> enumType, boolean output) {
        if (node == null || !node.isTextual()) fail(output, field + "의 코드값이 올바르지 않습니다.", field);
        try {
            Enum.valueOf(enumType, node.asText());
        } catch (IllegalArgumentException e) {
            fail(output, field + "의 코드값이 올바르지 않습니다.", field);
        }
    }

    private void requireNullableDate(JsonNode node, String field) {
        if (node == null) failOutput(field + "은 필수입니다.");
        if (node.isNull()) return;
        if (!node.isTextual()) failOutput(field + "은 날짜 문자열이어야 합니다.");
        try {
            LocalDate.parse(node.asText());
        } catch (RuntimeException e) {
            failOutput(field + "의 날짜 형식이 올바르지 않습니다.");
        }
    }

    private void fail(boolean output, String message, String field) {
        if (output) failOutput(message);
        failInput(message, field);
    }

    private void failInput(String message, String field) {
        throw ApiException.badRequest(message, field);
    }

    private void failOutput(String message) {
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_OUTPUT_INVALID", message, null);
    }
}
