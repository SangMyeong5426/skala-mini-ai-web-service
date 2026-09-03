package com.skala.miniai.domain.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.photo.TripPhoto;
import com.skala.miniai.domain.trip.Trip;

/**
 * {@code BAG_CHECK} 프롬프트와 <b>모델용 파생 스키마</b>.
 *
 * <p>System Prompt 와 User Prompt 템플릿은 {@code docs/07-ai-ready.md} 「AI-01」 절의 글을
 * <b>한 글자도 바꾸지 않고</b> 옮긴 것이다. 07 이 정본이므로 여기서 문구를 손보지 않는다 —
 * 고칠 일이 생기면 07 을 먼저 고치고 이 파일을 맞춘다.
 *
 * <p>{@link #outputSchema()} 는 07 의 출력 Schema 를 <b>모델에게 줄 형태로 줄인 것</b>이다.
 * 07 이 "실제 모델 검증 시 프롬프트와 모델용 파생 스키마를 확인" 하라고 남겨 둔 자리다.
 * 두 가지가 빠져 있다.
 *
 * <ul>
 *   <li>{@code confidenceLevel} · {@code failedPhotoIds} — 07 이 <b>서버가 채운다</b>고 못박았다.
 *       모델에게 물으면 경계값이 흔들리고, 실패한 사진은 애초에 모델이 보지 못한다.
 *   <li>{@code minimum} · {@code maxItems} · {@code pattern} 같은 값 제약 — OpenAI 의
 *       Structured Outputs 는 {@code strict} 모드에서 이 키워드들을 받지 않는다.
 *       대신 {@link OpenAiClient} 가 받은 뒤에 직접 검사·보정한다.
 * </ul>
 */
@Component
public class BagCheckPrompt {

    /** 07 「AI-01 System Prompt」 그대로. */
    private static final String SYSTEM = """
            너는 여행 가방 사진에서 물품을 찾아내는 검수 보조자다.

            규칙
            1. 사진에 보이는 것만 적는다. 보이지 않는 속성 — 용량(ml)·배터리 정격(Wh)·날 길이(cm) — 은 추정하지 않는다. 그 속성이 반입 판정에 필요한 물품(액체·배터리·날붙이)이면 missingInfo 에 "용량(ml)" 처럼 무엇이 필요한지 적는다. 라벨이 읽히면 labelText 에 글자를 원문 그대로 옮긴다 — 그래도 missingInfo 는 적는다. 수치 확정은 사용자가 한다.
            2. 같은 종류가 한 사진에 여러 개 보이면 한 항목으로 묶고 qty 로 센다(1~99 정수, 셀 수 없으면 1). 사진이 다르면 항목도 다르다 — photoId 마다 따로 낸다. 사진 한 장에 10개, 전체 100개를 넘기지 않는다. 넘으면 confidence 높은 순으로 자른다.
            3. 무엇인지 확신이 없으면 이름을 지어내지 말고 보이는 대로 적는다(예: "검정 파우치"). confidence 를 낮춘다.
            4. name 은 한국어 일반명사로 1~100자. 공백만인 이름은 안 된다. 브랜드명은 name 이 아니라 labelText 에 둔다.
            5. confidence 는 0~1 사이 소수 셋째 자리까지의 숫자(따옴표 없이).
            6. 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 빈 문자열은 쓰지 않는다. photoId 는 입력에 있는 값만 쓴다. confidenceLevel 과 failedPhotoIds 는 서버가 계산해 덮어쓰므로 비워 두어도 된다.
            """;

    private final Json json;

    public BagCheckPrompt(Json json) {
        this.json = json;
    }

    public String system() {
        return SYSTEM;
    }

    /**
     * 07 「AI-01 User Prompt 템플릿」 그대로 채운다.
     *
     * <p>{@code {{server:…}}} 자리는 화면이 아니라 <b>서버가 읽은 값</b>이다 (07 「서버 보강」).
     * 사진은 여기 글이 아니라 별도의 이미지 파트로 붙는다 — 템플릿의 {@code [사진 N장 첨부]} 가 그 자리다.
     *
     * @param photos {@code images} 와 <b>같은 순서·같은 집합</b>이어야 한다. 목록에 없는 photoId 를
     *               모델이 쓰면 {@link OpenAiClient} 가 그 인식 결과를 버린다.
     */
    public String user(Trip trip, List<TripPhoto> photos) {
        StringBuilder sb = new StringBuilder();
        sb.append("여행: ").append(trip.getDestination())
                .append(' ').append(trip.getStartDate()).append('~').append(trip.getEndDate())
                .append(" · 이동수단 ").append(trip.getTransport()).append('\n');
        sb.append("사진 ").append(photos.size()).append("장. photoId 와 가방 종류:\n");
        for (TripPhoto photo : photos) {
            sb.append("- photoId=").append(photo.getId())
                    .append(" (").append(photo.getBagKind() == null ? "종류 미상" : photo.getBagKind()).append(")\n");
        }
        sb.append('\n').append("[사진 ").append(photos.size()).append("장 첨부]").append('\n');
        sb.append('\n').append("위 사진에서 물품을 찾아 JSON 으로 답하라.");
        return sb.toString();
    }

    /** Structured Outputs 의 {@code strict} 모드가 받는 형태. 값 범위 검사는 서버가 따로 한다. */
    public ObjectNode outputSchema() {
        ObjectNode detection = json.newObject();
        detection.put("type", "object");
        ObjectNode props = detection.putObject("properties");
        props.putObject("photoId").put("type", "integer")
                .put("description", "입력에 준 photoId 만 쓴다");
        props.putObject("name").put("type", "string")
                .put("description", "한국어 일반명사 1~100자. 브랜드명은 여기 두지 않는다");
        props.putObject("qty").put("type", "integer")
                .put("description", "1~99. 셀 수 없으면 1");
        props.putObject("confidence").put("type", "number")
                .put("description", "0~1, 소수 셋째 자리까지");
        nullableString(props, "missingInfo", "보이지 않아 못 정한 속성. 없으면 null");
        nullableString(props, "labelText", "라벨에서 읽힌 글자 원문. 없으면 null");

        // strict 모드는 properties 의 키를 전부 required 에 넣기를 요구한다. 하나라도 빠지면
        // OpenAI 가 400 을 낸다 — properties 를 늘리면 이 배열도 함께 늘린다.
        ArrayNode required = detection.putArray("required");
        for (String field : List.of("photoId", "name", "qty", "confidence", "missingInfo", "labelText")) {
            required.add(field);
        }
        detection.put("additionalProperties", false);

        ObjectNode schema = json.newObject();
        schema.put("type", "object");
        ObjectNode root = schema.putObject("properties");
        ObjectNode detections = root.putObject("detections");
        detections.put("type", "array");
        detections.put("description", "사진에서 찾은 물품. 없으면 빈 배열");
        detections.set("items", detection);
        schema.putArray("required").add("detections");
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 07 은 {@code missingInfo}·{@code labelText} 를 {@code ["string","null"]} 로 정했다. */
    private static void nullableString(ObjectNode props, String name, String description) {
        ObjectNode node = props.putObject(name);
        ArrayNode types = node.putArray("type");
        types.add("string");
        types.add("null");
        node.put("description", description);
    }
}
