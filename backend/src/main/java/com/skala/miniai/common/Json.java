package com.skala.miniai.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code jsonb} 컬럼을 다루는 유일한 통로.
 *
 * <p>{@code ai_jobs.input_payload}·{@code output_payload} 는 문자열로 저장한다
 * ({@code @JdbcTypeCode(SqlTypes.JSON)} + {@code String}). 파싱을 도메인마다 흩으면
 * 07 의 스키마가 바뀔 때 고칠 곳을 못 찾는다.
 */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZE_FAILED",
                    "응답을 만들지 못했습니다.", null);
        }
    }

    public JsonNode read(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYLOAD_BROKEN",
                    "저장된 작업 결과를 읽지 못했습니다.", null);
        }
    }

    /**
     * 두 JSON 을 값으로 비교할 수 있게 <b>정규화</b>한다.
     *
     * <p>직접 만든 노드와 파싱한 노드는 값이 같아도 {@code equals} 가 false 일 수 있다 —
     * {@code put("itemId", 1L)} 는 {@code LongNode}, DB·HTTP 에서 파싱한 {@code 1} 은
     * {@code IntNode} 이고 Jackson 은 이 둘을 다르게 본다.
     *
     * <p>실제로 이것 때문에 {@code inspection.weight} 가 계산 직후에도 {@code null} 로 나왔다.
     * 문자열로 썼다가 다시 읽으면 양쪽 다 파싱된 노드가 되어 비교가 성립한다.
     */
    public JsonNode canonical(JsonNode node) {
        return node == null ? null : read(write(node));
    }

    public ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
