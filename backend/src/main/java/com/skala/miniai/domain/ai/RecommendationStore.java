package com.skala.miniai.domain.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;

/**
 * 추천 후보를 {@code ai_jobs.output_payload} 안에서 읽고 채택 연결만 갱신한다.
 *
 * <p><b>별도 추천 테이블을 만들지 않는다</b>(docs/05-erd.md 저장 규약). 후보는 07 의
 * {@code PACKING_LIST} output 그대로 남고, 서버는 각 후보의 {@code acceptedItemId} 만 건드린다.
 * 원래 추천 내용·순서는 바꾸지 않는다 — 그래야 {@code candidateIndex} 가 계속 유효하다.
 *
 * <p>JSON 구조를 아는 곳을 여기 하나로 묶는다. 체크리스트 서비스가 직접 파싱하면
 * 07 스키마가 바뀔 때 고칠 곳이 흩어진다.
 */
@Component
public class RecommendationStore {

    private final Json json;

    public RecommendationStore(Json json) {
        this.json = json;
    }

    /** 07 {@code PACKING_LIST.output.items[]} 한 칸. */
    public record Candidate(
            int index, String name, Codes.Category category, int qty,
            Codes.Priority priority, String reason, Codes.ItemSource source, Long acceptedItemId) { }

    public List<Candidate> candidatesOf(AiJob job) {
        JsonNode output = json.read(job.getOutputPayload());
        if (output == null || !output.has("items")) return List.of();

        List<Candidate> out = new ArrayList<>();
        JsonNode items = output.get("items");
        for (int i = 0; i < items.size(); i++) {
            JsonNode c = items.get(i);
            out.add(new Candidate(
                    i,
                    c.path("name").asText(null),
                    enumOrNull(Codes.Category.class, c.path("category").asText(null)),
                    c.path("qty").asInt(1),
                    enumOrNull(Codes.Priority.class, c.path("priority").asText(null)),
                    c.path("reason").asText(null),
                    enumOrNull(Codes.ItemSource.class, c.path("source").asText(null)),
                    c.path("acceptedItemId").isNull() || c.path("acceptedItemId").isMissingNode()
                            ? null : c.path("acceptedItemId").asLong()));
        }
        return out;
    }

    public Candidate candidateAt(AiJob job, int candidateIndex) {
        List<Candidate> all = candidatesOf(job);
        if (candidateIndex < 0 || candidateIndex >= all.size()) {
            throw ApiException.badRequest(
                    "추천 후보 위치가 올바르지 않습니다: " + candidateIndex, "recommendation.candidateIndex");
        }
        return all.get(candidateIndex);
    }

    /**
     * 후보의 채택 연결만 갱신한다. {@code itemId} 가 {@code null} 이면 해제다
     * (항목 삭제 시 06 이 요구하는 동작).
     */
    public void linkCandidate(AiJob job, int candidateIndex, Long itemId) {
        JsonNode output = json.read(job.getOutputPayload());
        if (output == null || !output.has("items")) return;

        JsonNode item = output.get("items").get(candidateIndex);
        if (item instanceof ObjectNode node) {
            if (itemId == null) node.putNull("acceptedItemId");
            else node.put("acceptedItemId", itemId);
            job.replaceOutputPayload(json.write(output));
        }
    }

    /** 이 항목을 가리키는 후보의 연결을 모두 해제한다. 항목 삭제와 같은 트랜잭션에서 부른다. */
    public void unlinkItem(AiJob job, Long itemId) {
        JsonNode output = json.read(job.getOutputPayload());
        if (output == null || !output.has("items")) return;

        boolean changed = false;
        for (JsonNode c : output.get("items")) {
            if (c instanceof ObjectNode node
                    && !node.path("acceptedItemId").isNull()
                    && node.path("acceptedItemId").asLong() == itemId) {
                node.putNull("acceptedItemId");
                changed = true;
            }
        }
        if (changed) job.replaceOutputPayload(json.write(output));
    }

    /** 06 의 이름 비교 규약 — 앞뒤 공백 제거 후 연속 공백을 하나로 정리해 비교한다. */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return Optional.ofNullable(value)
                .flatMap(v -> {
                    try { return Optional.of(Enum.valueOf(type, v)); }
                    catch (IllegalArgumentException e) { return Optional.empty(); }
                })
                .orElse(null);
    }
}
