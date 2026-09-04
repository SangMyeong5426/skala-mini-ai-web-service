package com.skala.miniai.domain.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * Mock AI. <b>실제 LLM 을 호출하지 않는다.</b> 전체 mock 모드와 혼합 모드의 S-09 챗봇·무게에 쓴다.
 *
 * <p>기본 fixture는 {@code src/main/resources/mock/&lt;jobType&gt;.json}, 대표 챗봇 질문은
 * {@code RULE_CHECK_*.json} 이다. 모두 {@code docs/07-ai-ready.md}의 output 스키마를 지킨다.
 *
 * <p>07 "Mock이 돌려주는 것" 규약대로 대표 챗봇 질문은 질문별 고정 응답을 고르고,
 * 나머지는 <b>입력에 맞춘다</b> —
 * {@code BAG_CHECK} 의 {@code photoId}, {@code WEIGHT_ESTIMATE} 의 현재 물품·수량·가방 정보,
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
    /** 가상 스레드에서 동시에 들어온다. HashMap 의 computeIfAbsent 는 스레드 안전하지 않다. */
    private final Map<String, JsonNode> fixtures = new ConcurrentHashMap<>();

    public MockAiClient(Json json) {
        this.json = json;
    }

    /** 제공자 오타가 실제 AI 시연을 조용히 Mock으로 바꾸지 않게 기동 시점에 막는다. */
    @Value("${app.ai.provider:mock}")
    void validateProvider(String provider) {
        if (!"mock".equals(provider) && !"openai".equals(provider)) {
            throw new IllegalStateException(
                    "AI_PROVIDER는 mock 또는 openai만 가능합니다: " + provider);
        }
    }

    @Override
    public String modelName() {
        return "mock";
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
            case WEIGHT_ESTIMATE -> weightEstimate(output, input);
            case RULE_CHECK -> alignRuleCheckItems(output, input);
            case PACKING_LIST -> packingList(output, input);
        };
    }

    /** 실제 AI와 같은 중복 계약: 이미 준비한 이름은 고정 Mock 추천에서도 제외한다. */
    private JsonNode packingList(JsonNode output, JsonNode input) {
        var taken = new HashSet<String>();
        input.path("alreadyPacked").forEach(item -> taken.add(
                RecommendationStore.normalize(item.path("name").asText(""))));

        if (output.path("items") instanceof ArrayNode candidates) {
            List<JsonNode> kept = new ArrayList<>();
            candidates.forEach(candidate -> {
                if (taken.add(RecommendationStore.normalize(candidate.path("name").asText("")))) {
                    kept.add(candidate);
                }
            });
            candidates.removeAll();
            kept.forEach(candidates::add);
        }
        return output;
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

    /** 07: fixture의 품목별 Mock 범위를 현재 입력의 물품·수량에 적용하고 합계는 서버가 계산한다. */
    private JsonNode weightEstimate(JsonNode output, JsonNode input) {
        ObjectNode root = (ObjectNode) output;
        Integer bagEmptyG = input.hasNonNull("bagEmptyG") ? input.get("bagEmptyG").asInt() : null;
        Integer limitG = input.hasNonNull("weightLimitG") ? input.get("weightLimitG").asInt() : null;

        Map<String, JsonNode> known = new HashMap<>();
        output.path("contributions").forEach(value -> known.put(
                RecommendationStore.normalize(value.path("name").asText("")), value));

        List<ObjectNode> calculated = new ArrayList<>();
        List<String> noWeight = new ArrayList<>();
        for (JsonNode item : input.path("items")) {
            String name = item.path("name").asText("");
            int qty = item.path("qty").asInt(1);
            JsonNode base = known.get(RecommendationStore.normalize(name));
            if (base == null) {
                noWeight.add(name);
                continue;
            }
            ObjectNode contribution = (ObjectNode) base.deepCopy();
            contribution.put("name", name);
            contribution.put("qty", qty);
            contribution.put("subtotalG", contribution.path("typicalG").asInt() * qty);
            calculated.add(contribution);
        }
        calculated.sort(Comparator.comparingInt((ObjectNode value) -> value.path("subtotalG").asInt()).reversed());

        ArrayNode contributions = root.putArray("contributions");
        calculated.forEach(contributions::add);
        int minG = bagEmptyG == null ? 0 : bagEmptyG;
        int typicalG = minG;
        int maxG = minG;
        for (ObjectNode value : calculated) {
            int qty = value.path("qty").asInt();
            minG += value.path("minG").asInt() * qty;
            typicalG += value.path("typicalG").asInt() * qty;
            maxG += value.path("maxG").asInt() * qty;
        }

        ArrayNode excluded = root.putArray("excluded");
        input.path("excluded").forEach(value -> excluded.add(value.deepCopy()));
        noWeight.forEach(name -> excluded.addObject().put("name", name).put("reason", "NO_WEIGHT_INFO"));

        String confidence = calculated.isEmpty() || noWeight.size() > calculated.size() ? "LOW"
                : noWeight.isEmpty() ? "HIGH" : "MEDIUM";
        String verdict = limitG == null ? "UNKNOWN"
                : maxG > limitG ? "OVER_RISK"
                : bagEmptyG == null || "LOW".equals(confidence) ? "UNKNOWN"
                : typicalG >= limitG * 0.8 ? "NEAR" : "ROOM";

        root.put("minG", minG);
        root.put("typicalG", typicalG);
        root.put("maxG", maxG);
        if (limitG == null) root.putNull("limitG"); else root.put("limitG", limitG);
        if (bagEmptyG == null) root.putNull("bagEmptyG"); else root.put("bagEmptyG", bagEmptyG);
        root.put("verdict", verdict);
        root.put("confidence", confidence);
        root.put("confidenceReason", "준비 완료 " + calculated.size() + "개 계산 · 미완료 "
                + input.path("excluded").size() + "개 · 무게 정보 없음 " + noWeight.size() + "개");
        root.put("excludedCount", excluded.size());
        return root;
    }

    /** 07: 입력 물품이 있으면 결과를 같은 개수·순서로 만들고 식별값을 그대로 돌려준다. */
    private JsonNode alignRuleCheckItems(JsonNode output, JsonNode input) {
        JsonNode items = input.path("items");
        if (!items.isArray() || items.isEmpty() || !(output instanceof ObjectNode root)) return output;

        Map<String, JsonNode> resultByName = new HashMap<>();
        output.path("results").forEach(result -> resultByName.put(
                RecommendationStore.normalize(result.path("name").asText("")), result));
        JsonNode unknownOutput = load("RULE_CHECK_UNKNOWN");
        JsonNode unknown = unknownOutput.path("results").get(0);
        boolean allUnknown = true;
        boolean needsMoreInfo = false;

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
