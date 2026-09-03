package com.skala.miniai.domain.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;

/**
 * Mock AI. <b>실제 LLM 을 호출하지 않는다</b> (AGENTS.md: 3일차 데모까지 AI 는 전부 Mock).
 *
 * <p>돌려주는 것은 {@code src/main/resources/mock/&lt;jobType&gt;.json} 이고, 그 내용은
 * {@code docs/07-ai-ready.md} 「예시」 절의 output 을 <b>스크립트로 추출</b>한 것이다.
 * 손으로 옮기면 {@code additionalProperties: false} 스키마를 어기기 쉽다.
 *
 * <p>07 "Mock이 돌려주는 것" 규약대로 <b>id 만 입력에 맞춘다</b> —
 * {@code BAG_CHECK} 의 {@code photoId}, {@code WEIGHT_ESTIMATE} 의 {@code limitG}·{@code bagEmptyG},
 * {@code RULE_CHECK} 의 {@code itemId}. 새 여행에서도 Mock 이 깨지지 않는다.
 */
@Component
public class MockAiClient implements AiClient {

    private final Json json;
    private final String modelName;
    private final Map<Codes.JobType, JsonNode> fixtures = new HashMap<>();

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
        JsonNode output = load(jobType).deepCopy();
        return switch (jobType) {
            case BAG_CHECK -> alignPhotoIds(output, input);
            case WEIGHT_ESTIMATE -> alignBag(output, input);
            case RULE_CHECK -> alignItemIds(output, input);
            case PACKING_LIST -> output;
        };
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

    /** 07: 같은 이름의 입력 항목이 있으면 그 {@code itemId} 를 쓴다. 없으면 {@code null} 로 둔다. */
    private JsonNode alignItemIds(JsonNode output, JsonNode input) {
        Map<String, Long> idByName = new HashMap<>();
        for (JsonNode i : input.path("items")) {
            if (i.hasNonNull("name") && i.hasNonNull("itemId")) {
                idByName.put(RecommendationStore.normalize(i.get("name").asText()), i.get("itemId").asLong());
            }
        }
        for (JsonNode r : output.path("results")) {
            if (r instanceof ObjectNode node) {
                Long id = idByName.get(RecommendationStore.normalize(node.path("name").asText("")));
                if (id != null) node.put("itemId", id);
                else node.putNull("itemId");
            }
        }
        return output;
    }

    private JsonNode load(Codes.JobType jobType) {
        return fixtures.computeIfAbsent(jobType, type -> {
            try (InputStream in = new ClassPathResource("mock/" + type.name() + ".json").getInputStream()) {
                return json.mapper().readTree(in);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_MISSING",
                        "Mock 응답 파일이 없습니다: mock/" + type.name() + ".json", null);
            }
        });
    }
}
