package com.skala.miniai.domain.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Json;

/**
 * {@code RULE_CHECK} 프롬프트와 <b>모델용 파생 스키마 둘</b>.
 *
 * <p>다른 둘과 달리 <b>모델을 두 번 부른다</b>. 07 「AI-04」가 그렇게 설계했다.
 *
 * <ol>
 *   <li><b>1차 구조화</b> — 자연어에서 물품과 속성을 뽑는다. 판정하지 않는다.
 *   <li>가운데에서 <b>규칙 엔진</b>이 {@code transport_rules} 로 판정한다. 모델이 끼지 않는다.
 *   <li><b>2차 설명</b> — 규칙 엔진이 낸 판정을 사람 문장으로 옮긴다.
 * </ol>
 *
 * <p>한 번에 부르지 않는 이유가 07 System Prompt 첫 줄에 있다 —
 * <i>"반입 여부를 네가 판정하지 않는다."</i> 판정과 설명을 한 호출에 섞으면 모델이 규정을
 * 보지 않고 결론부터 쓰게 되고, 그 결론에 <b>공식 출처 URL 이 함께 붙어</b> 나간다.
 *
 * <p>System·User Prompt 는 07 원문 그대로다. 파생 스키마에서 뺀 것은 07 이
 * "서버가 채워 덮어쓰므로 비워 두어도 된다" 고 적은 값들이다.
 */
@Component
public class RuleCheckPrompt {

    /** 07 「AI-04 System Prompt」 그대로. 두 호출이 같은 것을 쓴다. */
    private static final String SYSTEM = """
            너는 항공·교통 수하물 규정 확인을 돕는 보조자다. 반입 여부를 네가 판정하지 않는다 — 판정은 규칙 엔진이 공식 규정표로 한다. 네 일은 두 단계다.

            A. 구조화 (1차 호출)
            - 질문이나 물품 목록에서 물품 이름과 규정 판단에 필요한 속성을 뽑는다: capacityMl(액체 용량), batteryWh(배터리 정격), batteryMah(mAh 만 적힌 경우), bladeCm(날 길이).
            - 명시된 값만 쓴다. 없으면 null. 추정하지 않는다. 환산도 하지 않는다. mAh는 batteryMah에 그대로 옮기고 Wh가 없으면 null로 둔다.
            - 물품마다 ruleKeyword 를 정한다: 서버가 준 규정 키워드 목록 중 그 물품이 해당하는 것(화장품 → 액체, 보조배터리 → 보조배터리). 해당하는 것이 없으면 null.
            - 물품 목록으로 받았으면 itemId·detectionId·name·qty 를 그대로 되돌려 보낸다. 빠뜨리거나 순서를 바꾸지 않는다. 질문에서 뽑았으면 itemId·detectionId 는 null, qty 는 언급 없으면 1.
            - name 은 한국어 일반명사 1~100자.

            B. 설명 (2차 호출)
            - 규칙 엔진 결과(verdict·conditionNote·description·missingInfo)를 받아, 물품마다 reason 을 한 문장(1~300자)으로 쓴다. 규정 description 의 뜻을 바꾸지 않는다. description 이 없으면(규정 없음) "해당 규정을 찾지 못했습니다. 항공사에 확인하세요." 로 쓴다.
            - 챗봇 호출(question 이 있음)이면 answer(1~600자)를 쓴다. NEED_MORE_INFO 인 물품이 있으면 followUpQuestion 에 부족한 것 하나만 묻는다. 한 번에 하나. 물품 목록 호출이면 answer·followUpQuestion 은 null.
            - 규정에 없는 말을 지어내지 않는다. "반드시 됩니다" 같은 확정 표현을 쓰지 않는다. answer 는 "최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다" 로 맺는다.

            공통
            - 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 빈 문자열은 쓰지 않는다.
            - verdict · ruleId · conditionNote · missingInfo · sourceUrl · checkedAt · 사용자 미확인 attributes.batteryWh 는 서버가 채워 덮어쓰므로 비워 두어도 된다.
            """;

    private final Json json;

    public RuleCheckPrompt(Json json) {
        this.json = json;
    }

    public String system() {
        return SYSTEM;
    }

    /** 07 「1차 · 구조화」 템플릿. {@code ruleKeywords} 는 규칙 엔진이 준다. */
    public String structuring(JsonNode input, List<String> ruleKeywords) {
        StringBuilder sb = new StringBuilder();
        header(sb, input);
        sb.append("규정 키워드 목록: ")
                .append(ruleKeywords.isEmpty() ? "없음" : String.join(" · ", ruleKeywords)).append('\n');
        sb.append("질문: ").append(text(input.path("question"), "(없음 — 아래 물품 목록으로)")).append('\n');

        sb.append("물품:\n");
        JsonNode items = input.path("items");
        if (!items.isArray() || items.isEmpty()) {
            sb.append("- 없음\n");
        } else {
            for (JsonNode item : items) {
                JsonNode a = item.path("attributes");
                sb.append("- ").append(item.path("name").asText()).append(" ×").append(item.path("qty").asInt(1))
                        .append(" (itemId ").append(text(item.path("itemId"), "-"))
                        .append(" · detectionId ").append(text(item.path("detectionId"), "-"))
                        .append(" · ").append(text(a.path("capacityMl"), "-")).append(" ml")
                        .append(" / ").append(text(a.path("batteryWh"), "-")).append(" Wh")
                        .append(" / ").append(text(a.path("batteryMah"), "-")).append(" mAh")
                        .append(" / ").append(text(a.path("bladeCm"), "-")).append(" cm)\n");
            }
        }

        sb.append('\n')
                .append("물품과 속성을 구조화해 JSON 으로 답하라. ")
                .append("results[] 의 itemId·detectionId·name·qty·ruleKeyword·attributes 만 채운다.");
        return sb.toString();
    }

    /**
     * 07 「2차 · 설명」 템플릿.
     *
     * @param judged       규칙 엔진이 판정을 채운 {@code results[]}
     * @param descriptions 결과마다 적용된 규정의 설명. 없으면 {@code null} — 07 이 그때
     *                     "규정 없음" 으로 적으라고 했다.
     */
    public String explaining(JsonNode input, JsonNode judged, List<String> descriptions) {
        StringBuilder sb = new StringBuilder();
        header(sb, input);
        sb.append("질문: ").append(text(input.path("question"), "(없음)")).append('\n');

        sb.append("규칙 엔진 결과:\n");
        for (int i = 0; i < judged.size(); i++) {
            JsonNode r = judged.get(i);
            JsonNode a = r.path("attributes");
            String description = i < descriptions.size() && descriptions.get(i) != null
                    ? descriptions.get(i) : "규정 없음";
            sb.append("- ").append(r.path("name").asText())
                    .append(" (").append(text(a.path("capacityMl"), "-")).append(" ml")
                    .append(" / ").append(text(a.path("batteryWh"), "-")).append(" Wh")
                    .append(" / ").append(text(a.path("bladeCm"), "-")).append(" cm): ")
                    .append(r.path("verdict").asText())
                    .append(" / ").append(text(r.path("conditionNote"), "-"))
                    .append(" / ").append(description)
                    .append(" / 부족: ").append(text(r.path("missingInfo"), "-")).append('\n');
        }

        sb.append('\n').append("각 물품의 reason 을, 챗봇 호출이면 answer 와 followUpQuestion 도 채워 JSON 으로 답하라.");
        return sb.toString();
    }

    private static void header(StringBuilder sb, JsonNode input) {
        sb.append("이동수단 ").append(input.path("transport").asText())
                .append(" · 항공사 ").append(text(input.path("airline"), "미상")).append('\n');
    }

    /** 1차 파생 스키마 — 판정 필드가 아예 없다. 모델에게 물어보지 않는 것이 요지다. */
    public ObjectNode structuringSchema() {
        ObjectNode item = json.newObject();
        item.put("type", "object");
        ObjectNode props = item.putObject("properties");
        nullable(props, "itemId", "integer", "물품 목록으로 받았으면 그대로. 질문에서 뽑았으면 null");
        nullable(props, "detectionId", "integer", "물품 목록으로 받았으면 그대로. 질문에서 뽑았으면 null");
        props.putObject("name").put("type", "string").put("description", "한국어 일반명사 1~100자");
        props.putObject("qty").put("type", "integer").put("description", "1~99. 언급 없으면 1");
        nullable(props, "ruleKeyword", "string", "서버가 준 규정 키워드 목록 중 하나. 없으면 null");
        props.set("attributes", attributesSchema());
        required(item, "itemId", "detectionId", "name", "qty", "ruleKeyword", "attributes");

        ObjectNode schema = json.newObject();
        schema.put("type", "object");
        ObjectNode results = schema.putObject("properties").putObject("results");
        results.put("type", "array");
        results.put("description", "물품마다 하나. 물품 목록으로 받았으면 개수와 순서를 그대로 지킨다");
        results.set("items", item);
        required(schema, "results");
        return schema;
    }

    /** 2차 파생 스키마 — 문장만 받는다. 판정은 이미 규칙 엔진이 냈다. */
    public ObjectNode explainingSchema() {
        ObjectNode item = json.newObject();
        item.put("type", "object");
        item.putObject("properties").putObject("reason")
                .put("type", "string").put("description", "한 문장 1~300자. 규정 설명의 뜻을 바꾸지 않는다");
        required(item, "reason");

        ObjectNode schema = json.newObject();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode results = props.putObject("results");
        results.put("type", "array");
        results.put("description", "규칙 엔진 결과와 같은 개수·순서");
        results.set("items", item);
        nullable(props, "answer", "string", "챗봇 호출이면 1~600자. 물품 목록 호출이면 null");
        nullable(props, "followUpQuestion", "string", "부족한 것 하나만. 없으면 null");
        required(schema, "results", "answer", "followUpQuestion");
        return schema;
    }

    private ObjectNode attributesSchema() {
        ObjectNode attributes = json.newObject();
        attributes.put("type", "object");
        ObjectNode props = attributes.putObject("properties");
        nullable(props, "capacityMl", "number", "액체 용량(ml). 명시된 값만");
        nullable(props, "batteryWh", "number", "배터리 정격(Wh). 명시된 값만. mAh 에서 환산하지 않는다");
        nullable(props, "batteryMah", "number", "mAh 만 적힌 경우 그대로");
        nullable(props, "bladeCm", "number", "날 길이(cm). 명시된 값만");
        required(attributes, "capacityMl", "batteryWh", "batteryMah", "bladeCm");
        return attributes;
    }

    private static void nullable(ObjectNode props, String name, String type, String description) {
        ObjectNode node = props.putObject(name);
        ArrayNode types = node.putArray("type");
        types.add(type);
        types.add("null");
        node.put("description", description);
    }

    /** strict 모드는 properties 의 키를 전부 required 에 넣기를 요구한다. */
    private static void required(ObjectNode schema, String... fields) {
        ArrayNode required = schema.putArray("required");
        for (String field : fields) required.add(field);
        schema.put("additionalProperties", false);
    }

    /** 07 템플릿의 {@code | "기본값"} 자리. 값이 없으면 기본 문구를 쓴다. */
    private static String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        String value = node.asText("");
        return value.isBlank() ? fallback : value;
    }
}
