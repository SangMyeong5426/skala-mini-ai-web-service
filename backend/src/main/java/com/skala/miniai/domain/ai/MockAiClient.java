package com.skala.miniai.domain.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;

/**
 * Mock AI. <b>실제 LLM 을 호출하지 않는다</b> (AGENTS.md: 3일차 데모까지 AI 는 전부 Mock).
 *
 * <p>기본 fixture는 {@code src/main/resources/mock/&lt;jobType&gt;.json}, 대표 챗봇 질문은
 * {@code RULE_CHECK_*.json} 이다. 모두 {@code docs/07-ai-ready.md}의 output 스키마를 지킨다.
 *
 * <p>07 "Mock이 돌려주는 것" 규약대로 대표 챗봇 질문은 질문별 고정 응답을 고르고,
 * 나머지는 <b>id 만 입력에 맞춘다</b> —
 * {@code BAG_CHECK} 의 {@code photoId}, {@code WEIGHT_ESTIMATE} 의 {@code limitG}·{@code bagEmptyG},
 * {@code RULE_CHECK} 의 {@code itemId}. 새 여행에서도 Mock 이 깨지지 않는다.
 *
 * <p><b>{@code RULE_CHECK_*.json} 의 {@code verdict}·{@code ruleId}·{@code conditionNote}·
 * {@code sourceUrl}·{@code checkedAt} 은 자리만 채운다.</b> 값은
 * {@link com.skala.miniai.domain.master.RuleEngine} 이 {@code transport_rules} 로 덮어쓴다.
 * 지우지는 않는다 — {@code RuleCheckContract} 가 결과의 열세 칸을 <b>전부</b> 요구해서,
 * 빠지면 Mock 경로가 통째로 검증에 막힌다. 픽스처가 실제로 정하는 것은
 * <b>물품과 속성을 어떻게 구조화하느냐</b>까지다.
 */
@Component
public class MockAiClient implements AiClient {

    private final Json json;
    private final String modelName;
    /** 가상 스레드에서 동시에 들어온다. HashMap 의 computeIfAbsent 는 스레드 안전하지 않다. */
    private final Map<String, JsonNode> fixtures = new ConcurrentHashMap<>();

    public MockAiClient(Json json, @Value("${app.ai.model:mock}") String modelName) {
        this.json = json;
        this.modelName = modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public JsonNode run(Codes.JobType jobType, JsonNode input) {
        if (jobType == Codes.JobType.RULE_CHECK && input.path("question").isTextual()) {
            JsonNode output = load(ruleCheckFixture(input.path("question").asText(), input)).deepCopy();
            return alignRuleCheckItems(output, input);
        }

        JsonNode output = load(jobType.name()).deepCopy();
        return switch (jobType) {
            case BAG_CHECK -> alignPhotoIds(output, input);
            case WEIGHT_ESTIMATE -> alignBag(output, input);
            case RULE_CHECK -> alignRuleCheckItems(output, input);
            case PACKING_LIST -> output;
        };
    }

    /** S-09 대표 질문 12개와 그 밖의 안전한 기본 답변. */
    private String ruleCheckFixture(String question, JsonNode input) {
        String normalized = question.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("보조배터리")
                || (hasItem(input, "보조배터리")
                        && (normalized.contains("wh") || normalized.contains("mah")))) {
            if (containsMeasurement(normalized, "200wh")) return "RULE_CHECK_BATTERY_200WH";
            if (containsMeasurement(normalized, "120wh")) return "RULE_CHECK_BATTERY_120WH";
            if (containsMeasurement(normalized, "100wh")) return "RULE_CHECK_BATTERY_100WH";
            if (containsMeasurement(normalized, "20000mah")) return "RULE_CHECK_BATTERY";
            if (normalized.contains("wh") || normalized.contains("mah")) return "RULE_CHECK_UNKNOWN";
            return "RULE_CHECK_BATTERY_UNKNOWN";
        }
        if (normalized.contains("화장품") || (hasItem(input, "화장품") && normalized.contains("ml"))) {
            if (containsMeasurement(normalized, "120ml")) return "RULE_CHECK_LIQUID";
            if (containsMeasurement(normalized, "50ml")) return "RULE_CHECK_LIQUID_50ML";
            if (normalized.contains("ml")) return "RULE_CHECK_UNKNOWN";
            return "RULE_CHECK_LIQUID_UNKNOWN";
        }
        if (normalized.contains("가위") || (hasItem(input, "가위") && normalized.contains("cm"))) {
            if (containsMeasurement(normalized, "7cm")) return "RULE_CHECK_SCISSORS";
            if (containsMeasurement(normalized, "5cm")) return "RULE_CHECK_SCISSORS_5CM";
            if (normalized.contains("cm")) return "RULE_CHECK_UNKNOWN";
            return "RULE_CHECK_SCISSORS_UNKNOWN";
        }
        if (normalized.contains("노트북")) return "RULE_CHECK_LAPTOP";
        return "RULE_CHECK_UNKNOWN";
    }

    private boolean containsMeasurement(String text, String measurement) {
        for (int index = text.indexOf(measurement); index >= 0;
                index = text.indexOf(measurement, index + 1)) {
            if (index == 0 || !Character.isDigit(text.charAt(index - 1))) return true;
        }
        return false;
    }

    private boolean hasItem(JsonNode input, String name) {
        for (JsonNode item : input.path("items")) {
            if (name.equals(RecommendationStore.normalize(item.path("name").asText("")))) return true;
        }
        return false;
    }

    /** 07: 고정 출력의 {@code photoId} 1·2 를 {@code input.photoIds[0]}·{@code [1]} 로 바꾼다. */
    private JsonNode alignPhotoIds(JsonNode output, JsonNode input) {
        JsonNode photoIds = input.path("photoIds");
        if (!photoIds.isArray() || photoIds.isEmpty()) return output;

        for (JsonNode d : output.path("detections")) {
            if (d instanceof ObjectNode node) {
                int original = node.path("photoId").asInt(1);
                int index = Math.min(Math.max(original - 1, 0), photoIds.size() - 1);
                node.put("photoId", photoIds.get(index).asLong());
            }
        }
        return output;
    }

    /** 07: 가방 정보는 입력 값을 그대로 쓴다. 고정 값을 돌려주면 다른 여행에서 말이 안 된다. */
    private JsonNode alignBag(JsonNode output, JsonNode input) {
        if (output instanceof ObjectNode node) {
            if (input.hasNonNull("weightLimitG")) node.put("limitG", input.get("weightLimitG").asInt());
            else node.putNull("limitG");
            if (input.hasNonNull("bagEmptyG")) node.put("bagEmptyG", input.get("bagEmptyG").asInt());
            else node.putNull("bagEmptyG");
        }
        return output;
    }

    /** 07: 입력 물품이 있으면 결과를 같은 개수·순서로 만들고 식별값을 그대로 돌려준다. */
    private JsonNode alignRuleCheckItems(JsonNode output, JsonNode input) {
        JsonNode items = input.path("items");
        if (!items.isArray() || items.isEmpty() || !(output instanceof ObjectNode root)) return output;

        Map<String, JsonNode> resultByName = new HashMap<>();
        output.path("results").forEach(result -> resultByName.put(
                RecommendationStore.normalize(result.path("name").asText("")), result));
        boolean fromPhotos = !RuleCheckContract.attachedPhotoIds(input).isEmpty();
        JsonNode unknownOutput = load("RULE_CHECK_UNKNOWN");
        JsonNode unknown = unknownOutput.path("results").get(0);
        boolean allUnknown = true;
        boolean needsMoreInfo = false;

        ArrayNode results = root.putArray("results");
        for (JsonNode item : items) {
            String itemName = RecommendationStore.normalize(item.path("name").asText(""));
            JsonNode template = resultByName.get(itemName);
            // 이번 턴에 사진을 붙였으면, 인식된 물품은 질문이 그 물품을 말하지 않아도
            // 규정 키워드가 붙어야 한다. detectionId 만으로는 구분되지 않는다 —
            // 사용자가 보내는 후속 항목도 직전 사진의 detectionId 를 들고 온다.
            if (template == null && fromPhotos && item.path("detectionId").isIntegralNumber()) {
                template = structuredByName().get(itemName);
            }
            if (template == null) template = unknown;
            ObjectNode result = (ObjectNode) template.deepCopy();
            result.set("itemId", item.get("itemId"));
            result.set("detectionId", item.get("detectionId"));
            result.set("name", item.get("name"));
            result.set("qty", item.get("qty"));
            if (template == unknown) result.set("attributes", item.get("attributes"));
            else allUnknown = false;
            needsMoreInfo |= "NEED_MORE_INFO".equals(result.path("verdict").asText());
            results.add(result);
        }
        if (input.path("question").isTextual()) {
            if (!needsMoreInfo) root.putNull("followUpQuestion");
            if (allUnknown) root.set("answer", unknownOutput.get("answer"));
        }
        return output;
    }

    /**
     * <b>이름만 알아도 규정 키워드를 붙일 수 있게</b> 하는 대비표.
     *
     * <p>예전에는 질문으로 고른 픽스처 안의 이름만 연결했다. 그래서 사진을 붙이고
     * <i>"이거 기내 되나요?"</i> 라고 물으면 UNKNOWN 픽스처가 뽑혀, {@code BAG_CHECK} 가
     * 보조배터리를 정확히 인식해도 {@code ruleKeyword} 가 {@code null} 로 덮였다.
     * 규칙 엔진이 규정을 못 찾으니 <b>07 에 적은 "사진 → 속성 확인" 흐름이 기본 Mock 에서
     * 동작하지 않았다.</b>
     *
     * <p>쓰는 것은 <b>속성 미상</b> 픽스처들이다. 사진에는 용량도 정격도 보이지 않으므로
     * 그 모양이 맞다 — 규칙 엔진이 그 자리에서 {@code NEED_MORE_INFO} 와 되물을 것을 만든다.
     *
     * <p>임의 자연어를 늘리는 것이 아니다. <b>이번 턴에 사진을 붙였을 때</b>({@code photoIds} 가
     * 있고 그 물품이 {@code detectionId} 를 가질 때) 만 쓴다 — 사용자가 직접 보낸 후속 항목의
     * 대응은 예전 그대로다.
     */
    private Map<String, JsonNode> structuredByName() {
        Map<String, JsonNode> byName = new HashMap<>();
        for (String fixture : List.of("RULE_CHECK_BATTERY_UNKNOWN", "RULE_CHECK_LIQUID_UNKNOWN",
                "RULE_CHECK_SCISSORS_UNKNOWN", "RULE_CHECK_LAPTOP")) {
            for (JsonNode result : load(fixture).path("results")) {
                byName.putIfAbsent(RecommendationStore.normalize(result.path("name").asText("")), result);
            }
        }
        return byName;
    }

    private JsonNode load(String fixtureName) {
        return fixtures.computeIfAbsent(fixtureName, name -> {
            try (InputStream in = new ClassPathResource("mock/" + name + ".json").getInputStream()) {
                return json.mapper().readTree(in);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_MISSING",
                        "Mock 응답 파일이 없습니다: mock/" + name + ".json", null);
            }
        });
    }
}
