package com.skala.miniai.domain.ai;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.weather.WeatherSnapshot;

/**
 * {@code PACKING_LIST} 프롬프트와 <b>모델용 파생 스키마</b>.
 *
 * <p>{@link BagCheckPrompt} 와 같은 규칙이다 — System·User Prompt 는 {@code docs/07-ai-ready.md}
 * 「AI-02」 절 원문이고, 고칠 일이 생기면 07 을 먼저 고친다.
 *
 * <p>파생 스키마에서 뺀 것은 07 이 <b>서버 필드</b>라고 못박은 넷이다:
 * {@code source} · {@code acceptedItemId} · {@code weatherSource} · {@code weatherAsOf}.
 * System Prompt 8번이 직접 "모델용 파생 스키마에서는 제외한다" 고 적어 두었다.
 */
@Component
public class PackingListPrompt {

    /** AI-02 출력 계약의 후보 상한. 모델 보정과 서버 RULE 후보 보강이 함께 쓴다. */
    static final int MAX_CANDIDATES = 40;

    /** 07 「AI-02 System Prompt」 그대로. */
    private static final String SYSTEM = """
            너는 여행 준비물을 추천하는 보조자다. 사용자가 이미 챙긴 것은 다시 추천하지 않는다.

            규칙
            1. alreadyPacked와 현재 내 목록에 있는 물품은 이름이 같거나 명백히 같은 종류면 items에 넣지 않는다("상의"가 있으면 "티셔츠"를 또 내지 않는다). 아직 미완료인 채택 항목도 제외한다. 같은 물품의 부족 수량은 추천하지 않는다.
            2. 여행지·기간·목적·이동수단·날씨에 맞는 추가 후보만 낸다. 최대 %d개. 후보별 reason에 이 여행에서 검토할 이유를 1~200자 한국어로 쓴다. 사용자를 대신해 후보를 채택하거나 챙김 완료라고 하지 않는다.
            3. priority: 준비가 특히 중요한 것은 REQUIRED, 나머지는 RECOMMENDED. 고정 필수 규칙 항목은 서버가 RULE 후보로 보강하므로 중복으로 내지 않는다. 어떤 후보든 내 목록 추가는 사용자가 선택한다.
            4. category 는 DOCUMENT · CLOTHING · ELECTRONIC · TOILETRY · MEDICINE · ETC 중 하나만.
            5. qty 는 기간에 맞춘 1~99 정수. 정하기 어려우면 1.
            6. tips 는 최대 5개, 각 1~120자. 챙길 물건은 tips 가 아니라 items 에 넣는다 — tips 에는 날씨·콘센트·현지 사정 같은 사실만 쓴다. 날씨 근거가 있으면 수치를 그대로 인용한다(예: "낮 24도").
            7. 액체·배터리 같은 반입 규정 판정은 하지 않는다. 그건 다른 단계가 한다.
            8. 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 빈 문자열은 쓰지 않는다. source·acceptedItemId·weatherSource·weatherAsOf는 서버 필드다. 모델용 파생 스키마에서는 제외한다.
            """.formatted(MAX_CANDIDATES);

    private final Json json;

    public PackingListPrompt(Json json) {
        this.json = json;
    }

    public String system() {
        return SYSTEM;
    }

    /**
     * 07 「AI-02 User Prompt 템플릿」 그대로 채운다.
     *
     * <p>날씨 줄은 <b>받았을 때만</b> 넣는다. 못 받았으면 그 사실을 적는다 — 빈 자리를 남기면
     * 모델이 스스로 수치를 지어내고, 규칙 6이 "수치를 그대로 인용하라" 고 해서 더 그럴듯해진다.
     *
     * @param trip     국가 코드·목적·메모를 여기서 읽는다. 07 의 {@code {{server:trip.*}}}.
     * @param current  미완료까지 포함한 현재 내 목록. 07 의 {@code {{server:currentItems}}}.
     */
    public String user(Trip trip, JsonNode input, List<ChecklistItem> current, WeatherSnapshot weather) {
        LocalDate start = LocalDate.parse(input.path("startDate").asText());
        LocalDate end = LocalDate.parse(input.path("endDate").asText());
        long nights = ChronoUnit.DAYS.between(start, end);

        StringBuilder sb = new StringBuilder();
        sb.append("목적지 ").append(input.path("destination").asText())
                .append(" (").append(trip.getCountryCode() == null ? "국가 미상" : trip.getCountryCode()).append(")")
                .append(" · ").append(start).append('~').append(end)
                .append(" (").append(nights).append("박)")
                .append(" · 목적 ").append(input.path("purpose").asText())
                .append(" · 이동수단 ").append(input.path("transport").asText()).append('\n');

        JsonNode note = input.path("note");
        sb.append("메모: ").append(note.isNull() || note.asText("").isBlank() ? "없음" : note.asText()).append('\n');

        if (weather == null) {
            // 07 규칙 6의 "날씨 근거가 있으면" 을 없는 쪽으로 분명히 해 둔다.
            sb.append("날씨: 조회하지 못했다. 날씨 수치를 지어내지 말고, tips 에도 기온·강수 수치를 쓰지 않는다.\n");
        } else {
            sb.append("날씨 (").append(weather.source()).append(", ").append(weather.asOf()).append(" 기준): ")
                    .append(weather.summary());
            if (weather.minC() != null && weather.maxC() != null) {
                sb.append(", ").append(weather.minC()).append('~').append(weather.maxC()).append("°C");
            }
            if (weather.rainChance() != null) sb.append(", 강수확률 ").append(weather.rainChance()).append('%');
            sb.append('\n');
        }

        sb.append('\n').append("이미 챙긴 것 (다시 추천하지 않는다):\n");
        appendPacked(sb, input.path("alreadyPacked"));

        sb.append('\n').append("현재 내 목록 (미완료라도 다시 추천하지 않는다):\n");
        if (current.isEmpty()) {
            sb.append("- 없음\n");
        } else {
            for (ChecklistItem item : current) {
                sb.append("- ").append(item.getName()).append(" ×").append(item.getQty())
                        .append(" (").append(item.getCheckStatus()).append(")\n");
            }
        }

        sb.append('\n').append("추가 후보와 각 추천 이유를 JSON으로 답하라. 목록에 자동 등록하지 않는다.");
        return sb.toString();
    }

    private static void appendPacked(StringBuilder sb, JsonNode packed) {
        if (!packed.isArray() || packed.isEmpty()) {
            sb.append("- 없음\n");
            return;
        }
        for (JsonNode item : packed) {
            JsonNode category = item.path("category");
            sb.append("- ").append(item.path("name").asText()).append(" ×").append(item.path("qty").asInt(1))
                    .append(" (").append(category.isNull() ? "분류 미상" : category.asText("분류 미상")).append(")\n");
        }
    }

    /** Structured Outputs 의 {@code strict} 모드가 받는 형태. 개수·길이 검사는 서버가 따로 한다. */
    public ObjectNode outputSchema() {
        ObjectNode candidate = json.newObject();
        candidate.put("type", "object");
        ObjectNode props = candidate.putObject("properties");
        props.putObject("name").put("type", "string")
                .put("description", "한국어 일반명사 1~100자");
        enumOf(props, "category", "DOCUMENT", "CLOTHING", "ELECTRONIC", "TOILETRY", "MEDICINE", "ETC");
        props.putObject("qty").put("type", "integer")
                .put("description", "1~99. 정하기 어려우면 1");
        enumOf(props, "priority", "REQUIRED", "RECOMMENDED");
        props.putObject("reason").put("type", "string")
                .put("description", "이 여행에서 검토할 이유. 1~200자 한국어");

        ArrayNode required = candidate.putArray("required");
        for (String field : List.of("name", "category", "qty", "priority", "reason")) required.add(field);
        candidate.put("additionalProperties", false);

        ObjectNode schema = json.newObject();
        schema.put("type", "object");
        ObjectNode root = schema.putObject("properties");
        ObjectNode items = root.putObject("items");
        items.put("type", "array");
        items.put("description", "추가 준비물 후보. 최대 %d개. 이미 챙긴 것·내 목록에 있는 것은 넣지 않는다"
                .formatted(MAX_CANDIDATES));
        items.set("items", candidate);
        ObjectNode tips = root.putObject("tips");
        tips.put("type", "array");
        tips.put("description", "날씨·콘센트·현지 사정 같은 사실. 최대 5개, 각 1~120자. 챙길 물건은 여기 쓰지 않는다");
        tips.putObject("items").put("type", "string");

        ArrayNode rootRequired = schema.putArray("required");
        rootRequired.add("items");
        rootRequired.add("tips");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static void enumOf(ObjectNode props, String name, String... values) {
        ArrayNode allowed = props.putObject(name).putArray("enum");
        for (String value : values) allowed.add(value);
    }
}
