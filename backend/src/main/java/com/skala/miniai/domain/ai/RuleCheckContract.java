package com.skala.miniai.domain.ai;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    /**
     * 챗봇 사진 첨부(S-09). <b>선택 필드다.</b>
     *
     * <p>07 의 다른 칸처럼 필수로 두면 기존 호출이 전부 깨진다 — 화면 Mock·테스트·06 예시가
     * 네 칸만 보낸다. 사진을 붙이지 않는 질문이 훨씬 흔하므로 없으면 생략할 수 있게 뒀다.
     */
    private static final Set<String> OPTIONAL_INPUT_FIELDS = Set.of("photoIds");

    /** 한 번에 붙일 수 있는 사진 수. 챗봇은 대화 중 한두 장이라 BAG_CHECK(20장)보다 훨씬 작다. */
    public static final int MAX_ATTACHED_PHOTOS = 5;
    private static final Set<String> INPUT_ITEM_FIELDS = Set.of(
            "itemId", "detectionId", "name", "qty", "attributes");
    private static final Set<String> ATTRIBUTE_FIELDS = Set.of(
            "capacityMl", "batteryWh", "batteryMah", "bladeCm");
    private static final Set<String> OUTPUT_FIELDS = Set.of("results", "answer", "followUpQuestion");
    private static final Set<String> RESULT_FIELDS = Set.of(
            "itemId", "detectionId", "name", "qty", "ruleKeyword", "attributes",
            "verdict", "ruleId", "conditionNote", "reason", "missingInfo", "sourceUrl", "checkedAt");

    public JsonNode validateInput(JsonNode input) {
        requireObject(input, "input", INPUT_FIELDS, OPTIONAL_INPUT_FIELDS, false);
        validatePhotoIds(input.get("photoIds"));
        requireEnum(input.get("transport"), "input.transport", Codes.Transport.class, false);
        requireNullableText(input.get("airline"), "input.airline", 50, false);
        requireNullableText(input.get("question"), "input.question", 500, false);

        JsonNode items = input.get("items");
        if (!items.isArray() || items.size() > 50) {
            failInput("items는 최대 50개의 배열이어야 합니다.", "input.items");
        }
        for (int i = 0; i < items.size(); i++) validateInputItem(items.get(i), i);

        if (input.get("question").isNull() && items.isEmpty() && attachedPhotoIds(input).isEmpty()) {
            failInput("question · items · photoIds 중 하나는 필요합니다.", "input");
        }
        return input;
    }

    /** 붙은 사진 id. 없으면 빈 목록이다. */
    public static List<Long> attachedPhotoIds(JsonNode input) {
        JsonNode photoIds = input == null ? null : input.get("photoIds");
        if (photoIds == null || !photoIds.isArray()) return List.of();
        List<Long> ids = new ArrayList<>();
        photoIds.forEach(id -> ids.add(id.asLong()));
        return ids;
    }

    private void validatePhotoIds(JsonNode photoIds) {
        if (photoIds == null || photoIds.isNull()) return;
        if (!photoIds.isArray() || photoIds.size() > MAX_ATTACHED_PHOTOS) {
            failInput("photoIds는 최대 " + MAX_ATTACHED_PHOTOS + "개의 배열이어야 합니다.", "input.photoIds");
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode id : photoIds) {
            if (!id.isIntegralNumber() || id.asLong() < 1) {
                failInput("photoIds의 값은 1 이상의 정수여야 합니다.", "input.photoIds");
            }
            if (!seen.add(id.asLong())) {
                failInput("photoIds에 같은 사진이 두 번 들어 있습니다.", "input.photoIds");
            }
        }
    }

    /** RULE_CHECK 출력 전체를 저장 전에 검증한다. */
    public void validateOutput(JsonNode input, JsonNode output) {
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

        if (input.path("question").isTextual()) {
            requireText(output.get("answer"), "output.answer", 600, true);
            if (needsMoreInfo) requireText(output.get("followUpQuestion"), "output.followUpQuestion", 200, true);
            else if (!output.get("followUpQuestion").isNull()) {
                failOutput("추가 정보가 필요하지 않으면 followUpQuestion은 null이어야 합니다.");
            }
        } else if (!output.get("answer").isNull() || !output.get("followUpQuestion").isNull()) {
            failOutput("물품 목록 RULE_CHECK의 answer와 followUpQuestion은 null이어야 합니다.");
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

    /**
     * {@code results[]} 는 <b>{@code input.items} 를 순서대로 먼저</b> 담는다.
     *
     * <p>사진을 붙였으면 인식된 물품이 그 <b>뒤에</b> 이어 붙는다. 접수 시점에는 아직 인식을
     * 돌리지 않아 {@code input.items} 에 넣을 수 없기 때문이다 — 그래서 개수가 같기를 요구하지
     * 않고, <b>앞부분이 입력과 같은지</b>만 본다.
     */
    private void matchInputItems(JsonNode items, JsonNode results) {
        if (items.isEmpty()) return;
        if (items.size() > results.size()) failOutput("RULE_CHECK items와 results 개수가 다릅니다.");

        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            JsonNode result = results.get(i);
            if (!sameNullableLong(item.get("itemId"), result.get("itemId"))
                    || !sameNullableLong(item.get("detectionId"), result.get("detectionId"))
                    || !item.get("name").asText().equals(result.get("name").asText())
                    || item.get("qty").asInt() != result.get("qty").asInt()) {
                failOutput("RULE_CHECK items와 results 식별값이 다릅니다.");
            }
        }
    }

    private boolean sameNullableLong(JsonNode left, JsonNode right) {
        return left.isNull() ? right.isNull() : !right.isNull() && left.asLong() == right.asLong();
    }

    private void requireObject(JsonNode node, String field, Set<String> fields, boolean output) {
        requireObject(node, field, fields, Set.of(), output);
    }

    /** {@code optional} 은 있어도 되고 없어도 되지만, 그 밖의 필드는 여전히 거부한다. */
    private void requireObject(JsonNode node, String field, Set<String> fields,
                               Set<String> optional, boolean output) {
        if (node == null || !node.isObject()) fail(output, field + "은 객체여야 합니다.", field);
        for (String name : fields) {
            if (!node.has(name)) fail(output, field + "." + name + "은 필수입니다.", field + "." + name);
        }
        if (node.size() > fields.size() + optional.size()) {
            fail(output, field + "에 허용되지 않은 필드가 있습니다.", field);
        }
        node.propertyStream().map(java.util.Map.Entry::getKey)
                .filter(name -> !fields.contains(name) && !optional.contains(name))
                .findFirst()
                .ifPresent(name -> fail(output, field + "에 허용되지 않은 필드가 있습니다: " + name, field));
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
        else failInput(message, field);
    }

    private void failInput(String message, String field) {
        throw ApiException.badRequest(message, field);
    }

    private void failOutput(String message) {
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_OUTPUT_INVALID", message, null);
    }
}
