package com.skala.miniai.domain.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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

    /** S-09 빈 화면에 노출하는 대표 질문 3개와 그 밖의 안전한 기본 답변. */
    private String ruleCheckFixture(String question, JsonNode input) {
        String normalized = question.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("100wh")
                && (normalized.contains("보조배터리") || hasItem(input, "보조배터리"))) {
            return "RULE_CHECK_BATTERY_100WH";
        }
        if (normalized.contains("보조배터리") && normalized.contains("20000mah")) {
            return "RULE_CHECK_BATTERY";
        }
        if (normalized.contains("화장품") && normalized.contains("120ml")) {
            return "RULE_CHECK_LIQUID";
        }
        if (normalized.contains("가위") && normalized.contains("7cm")) {
            return "RULE_CHECK_SCISSORS";
        }
        return "RULE_CHECK_UNKNOWN";
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
        JsonNode unknown = load("RULE_CHECK_UNKNOWN").path("results").get(0);

        ArrayNode results = root.putArray("results");
        for (JsonNode item : items) {
            JsonNode template = resultByName.getOrDefault(
                    RecommendationStore.normalize(item.path("name").asText("")), unknown);
            ObjectNode result = (ObjectNode) template.deepCopy();
            result.set("itemId", item.get("itemId"));
            result.set("detectionId", item.get("detectionId"));
            result.set("name", item.get("name"));
            result.set("qty", item.get("qty"));
            if (template == unknown) result.set("attributes", item.get("attributes"));
            results.add(result);
        }
        return output;
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
