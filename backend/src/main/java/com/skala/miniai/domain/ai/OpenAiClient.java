package com.skala.miniai.domain.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.master.RuleEngine;
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.TripPhoto;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripRepository;
import com.skala.miniai.domain.weather.WeatherClient;
import com.skala.miniai.domain.weather.WeatherSnapshot;

/**
 * 실제 OpenAI 를 부르는 {@link AiClient} — 07 로드맵 <b>2·3단계</b>({@code BAG_CHECK} 비전 입력,
 * 스키마 검증 + 재시도 1회).
 *
 * <p>{@code AI_PROVIDER=openai} 일 때만 빈으로 올라오고, 그때 {@link MockAiClient} 를 밀어낸다
 * ({@code @Primary}). <b>기본값은 그대로 {@code mock} 이다</b> — 발표 데모는 네트워크에 묶이면 안 된다
 * (AGENTS.md). 환경 변수 한 줄로 켜고 끄는 것이 07 이 설계해 둔 모습이다.
 *
 * <p>{@code WEIGHT_ESTIMATE} 만 {@link MockAiClient} 에 넘긴다. 나머지 셋은 실제로 부른다.
 * {@code RULE_CHECK} 는 <b>모델을 두 번</b> 부르고 그 사이에 규칙 엔진이 들어간다 —
 * <b>반입 판정은 AI 가 하지 않는다</b>(07 「AI-04」).
 *
 * <p>호출하는 쪽은 하나도 바뀌지 않는다. {@link AiJobRunner} 는 {@code AiClient} 만 알고,
 * 인식 결과 저장·내 목록 자동 등록·{@code confidenceLevel} 덮어쓰기는 예전 그대로 돈다.
 *
 * <p><b>07 이 서버 몫이라고 정한 것은 모델에게 묻지 않는다.</b>
 * {@code confidenceLevel} 은 {@link DetectedObject#levelOf} 로 채우고,
 * {@code failedPhotoIds} 는 사진을 읽다가 실패한 목록으로 채운다.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    /** 07 출력 스키마의 {@code maxItems}. Structured Outputs 가 강제하지 못하므로 서버가 자른다. */
    private static final int MAX_DETECTIONS = 100;
    /** System Prompt 규칙 2 "사진 한 장에 10개". */
    private static final int MAX_DETECTIONS_PER_PHOTO = 10;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_MISSING_INFO_LENGTH = 100;
    private static final int MAX_LABEL_TEXT_LENGTH = 200;

    /** 07 PACKING_LIST 출력 스키마의 {@code maxItems}. */
    private static final int MAX_CANDIDATES = 40;
    private static final int MAX_TIPS = 5;
    private static final int MAX_TIP_LENGTH = 120;
    private static final int MAX_REASON_LENGTH = 200;

    /** 07 RULE_CHECK 출력 스키마의 한도. */
    private static final int MAX_RULE_RESULTS = 50;
    private static final int MAX_REASON_TEXT_LENGTH = 300;
    private static final int MAX_ANSWER_LENGTH = 600;
    private static final int MAX_FOLLOW_UP_LENGTH = 200;

    private final MockAiClient mock;
    private final OpenAiChatApi api;
    private final BagCheckPrompt prompt;
    private final PackingListPrompt packingPrompt;
    private final RuleCheckPrompt rulePrompt;
    private final RuleEngine ruleEngine;
    private final VisionImageLoader images;
    private final TripPhotoRepository photos;
    private final TripRepository trips;
    private final ChecklistItemRepository items;
    /** {@code WEATHER_PROVIDER} 가 {@code openmeteo} 가 아니면 빈이 없다. 그때는 날씨 없이 추천한다. */
    private final Optional<WeatherClient> weather;
    private final Json json;
    private final int maxPhotos;

    public OpenAiClient(MockAiClient mock, OpenAiChatApi api, BagCheckPrompt prompt,
                        PackingListPrompt packingPrompt, RuleCheckPrompt rulePrompt, RuleEngine ruleEngine,
                        VisionImageLoader images,
                        TripPhotoRepository photos, TripRepository trips, ChecklistItemRepository items,
                        Optional<WeatherClient> weather, Json json,
                        @Value("${app.ai.vision.max-photos:20}") int maxPhotos) {
        this.mock = mock;
        this.api = api;
        this.prompt = prompt;
        this.packingPrompt = packingPrompt;
        this.rulePrompt = rulePrompt;
        this.ruleEngine = ruleEngine;
        this.images = images;
        this.photos = photos;
        this.trips = trips;
        this.items = items;
        this.weather = weather;
        this.json = json;
        this.maxPhotos = maxPhotos;
    }

    @Override
    public String modelName() {
        return api.model();
    }

    @Override
    public JsonNode run(Codes.JobType jobType, JsonNode input) {
        return run(jobType, null, input);
    }

    @Override
    public JsonNode run(Codes.JobType jobType, Long tripId, JsonNode input) {
        return switch (jobType) {
            case BAG_CHECK -> bagCheck(input);
            case PACKING_LIST -> packingList(tripId, input);
            case RULE_CHECK -> ruleCheck(input);
            // 합산은 산식이 정본이다. LLM 보정은 07 로드맵 3-4 로 남겨 뒀다.
            case WEIGHT_ESTIMATE -> mock.run(jobType, input);
        };
    }

    /**
     * 사진 → 물품 인식.
     *
     * <p>실패한 사진은 예외로 만들지 않고 {@code failedPhotoIds} 로 남긴다. 07 이
     * "S-04 는 성공한 것만 보여주고 실패 사진에 재시도 버튼을 단다" 고 정했다.
     * <b>한 장도 못 읽었을 때만</b> 작업 전체가 실패한다.
     */
    private JsonNode bagCheck(JsonNode input) {
        List<Long> requested = new ArrayList<>();
        for (JsonNode id : input.path("photoIds")) requested.add(id.asLong());

        Set<Long> failed = new LinkedHashSet<>();

        // 07 입력 스키마의 maxItems 는 20 이다. 넘는 사진은 실패로 남겨 재시도하게 둔다 —
        // 조용히 버리면 사용자는 그 사진이 분석된 줄 안다.
        if (requested.size() > maxPhotos) {
            failed.addAll(requested.subList(maxPhotos, requested.size()));
            requested = requested.subList(0, maxPhotos);
        }

        Map<Long, TripPhoto> byId = new HashMap<>();
        photos.findAllById(requested).forEach(p -> byId.put(p.getId(), p));

        List<TripPhoto> targets = new ArrayList<>();
        List<VisionImage> payload = new ArrayList<>();
        for (Long photoId : requested) {
            TripPhoto photo = byId.get(photoId);
            if (photo == null) {                       // 접수 뒤에 지워진 사진
                failed.add(photoId);
                continue;
            }
            Optional<VisionImage> image = images.load(photo);
            if (image.isEmpty()) {                     // 파일이 없거나 열지 못한다
                failed.add(photoId);
                continue;
            }
            targets.add(photo);
            payload.add(image.get());
        }

        if (payload.isEmpty()) {
            throw new OpenAiException("분석할 수 있는 사진이 없습니다. 사진 파일을 다시 올려 주세요.", false);
        }

        Trip trip = trips.findById(targets.get(0).getTripId())
                .orElseThrow(() -> new OpenAiException("여행 정보를 찾지 못했습니다.", false));

        JsonNode raw = callWithOneRetry(() -> call(trip, targets, payload), "BAG_CHECK");
        return toOutput(raw, payload, failed);
    }

    /**
     * 07 로드맵 2단계 — <b>재시도는 한 번뿐이다.</b> 두 번째도 실패하면 작업을 {@code FAILED} 로 둔다.
     *
     * <p>세 작업이 같은 규칙을 쓴다. 429·5xx·타임아웃·스키마 위반만 다시 걸고,
     * 401·400 처럼 다시 불러도 같은 실패는 바로 올린다.
     */
    private JsonNode callWithOneRetry(Supplier<JsonNode> call, String label) {
        try {
            return call.get();
        } catch (OpenAiException e) {
            if (!e.isRetryable()) throw e;
            log.warn("{} 첫 호출 실패, 한 번만 다시 겁니다: {}", label, e.getMessage());
            return call.get();
        }
    }

    private JsonNode call(Trip trip, List<TripPhoto> targets, List<VisionImage> payload) {
        JsonNode raw = api.complete(
                prompt.system(),
                prompt.user(trip, targets),
                payload,
                "bag_check_output",
                prompt.outputSchema());

        // strict 스키마를 지켰어도 값까지 맞다는 뜻은 아니다. photoId 가 전부 엉뚱하면
        // 인식 결과를 통째로 버리게 되므로, 그때는 한 번 더 걸어 본다.
        if (!raw.path("detections").isArray()) {
            throw new OpenAiException("응답에 detections 배열이 없습니다.", true);
        }
        return raw;
    }

    /** 모델이 낸 값을 07 출력 스키마에 맞춰 다듬는다. 서버 몫 필드는 여기서 채운다. */
    private JsonNode toOutput(JsonNode raw, List<VisionImage> payload, Set<Long> failed) {
        // 모델이 보지 않은 사진의 photoId 를 냈다면 그 인식 결과는 붙일 곳이 없다.
        Set<Long> allowed = new LinkedHashSet<>();
        payload.forEach(image -> allowed.add(image.photoId()));

        List<Detection> cleaned = new ArrayList<>();
        int rawCount = 0;
        for (JsonNode node : raw.path("detections")) {
            rawCount++;
            Detection detection = clean(node, allowed);
            if (detection != null) cleaned.add(detection);
        }

        if (rawCount > 0 && cleaned.isEmpty()) {
            throw new OpenAiException("모델이 낸 인식 결과가 모두 규격을 벗어났습니다.", true);
        }

        ObjectNode output = json.newObject();
        ArrayNode detections = output.putArray("detections");
        for (Detection detection : trim(cleaned, allowed)) {
            ObjectNode node = detections.addObject();
            node.put("photoId", detection.photoId());
            node.put("name", detection.name());
            node.put("qty", detection.qty());
            node.put("confidence", detection.confidence());
            // 07: 서버가 confidence 로 채운다. 모델 값이 있어도 덮어쓴다.
            node.put("confidenceLevel", DetectedObject.levelOf(detection.confidence()).name());
            if (detection.missingInfo() == null) node.putNull("missingInfo");
            else node.put("missingInfo", detection.missingInfo());
            if (detection.labelText() == null) node.putNull("labelText");
            else node.put("labelText", detection.labelText());
        }

        ArrayNode failedPhotoIds = output.putArray("failedPhotoIds");
        failed.forEach(failedPhotoIds::add);

        log.info("BAG_CHECK 완료: 사진 {}장 분석, 물품 {}개, 실패 {}장",
                payload.size(), detections.size(), failed.size());
        return output;
    }

    /** 한 항목을 검사·보정한다. 살릴 수 없으면 {@code null} 이다. */
    private Detection clean(JsonNode node, Set<Long> allowed) {
        long photoId = node.path("photoId").asLong(0);
        if (!allowed.contains(photoId)) return null;

        String name = text(node.path("name"), MAX_NAME_LENGTH);
        if (name == null) return null;   // 07: 공백만인 이름은 항목이 되지 않는다

        int qty = Math.clamp(node.path("qty").asInt(1), 1, 99);

        // valueOf 를 쓴다. new BigDecimal(double) 은 0.87 을 0.8699999… 로 받아 온다.
        BigDecimal confidence = BigDecimal.valueOf(node.path("confidence").asDouble(0))
                .setScale(3, RoundingMode.HALF_UP);
        if (confidence.compareTo(BigDecimal.ZERO) < 0) confidence = BigDecimal.ZERO.setScale(3);
        if (confidence.compareTo(BigDecimal.ONE) > 0) confidence = BigDecimal.ONE.setScale(3);

        return new Detection(photoId, name, qty, confidence,
                text(node.path("missingInfo"), MAX_MISSING_INFO_LENGTH),
                text(node.path("labelText"), MAX_LABEL_TEXT_LENGTH));
    }

    /**
     * System Prompt 규칙 2 의 개수 제한을 <b>서버가 다시 건다.</b> 프롬프트로 부탁한 것은
     * 지켜지지 않을 수 있고, {@code detected_objects} 행이 그만큼 늘어난다.
     *
     * <p>자를 때는 {@code confidence} 가 높은 것부터 남긴다. 남긴 뒤에는 사진 순서로 되돌려
     * S-04 가 사진별로 묶어 보여주기 좋게 한다.
     */
    private List<Detection> trim(List<Detection> cleaned, Set<Long> allowed) {
        List<Detection> byConfidence = new ArrayList<>(cleaned);
        byConfidence.sort(Comparator.comparing(Detection::confidence).reversed());

        Map<Long, Integer> perPhoto = new LinkedHashMap<>();
        List<Detection> kept = new ArrayList<>();
        for (Detection detection : byConfidence) {
            if (kept.size() >= MAX_DETECTIONS) break;
            int count = perPhoto.getOrDefault(detection.photoId(), 0);
            if (count >= MAX_DETECTIONS_PER_PHOTO) continue;
            perPhoto.put(detection.photoId(), count + 1);
            kept.add(detection);
        }

        List<Long> order = new ArrayList<>(allowed);
        kept.sort(Comparator.comparingInt((Detection d) -> order.indexOf(d.photoId()))
                .thenComparing(Comparator.comparing(Detection::confidence).reversed()));
        return kept;
    }

    // ─── AI-04 RULE_CHECK ────────────────────────────────────────────────────

    /**
     * 자연어 질문·물품 목록 → 반입 판정과 설명. <b>모델을 두 번 부른다.</b>
     *
     * <p>가운데에 규칙 엔진이 들어가는 것이 핵심이다. 한 번에 부르면 모델이 규정을 보지 않고
     * 결론부터 쓰고, 그 결론에 공식 출처 URL 이 함께 붙어 나간다.
     *
     * <p>{@code items} 가 있으면 <b>결과 뼈대를 서버가 만든다.</b> 07 이 "results[] 의 개수·순서와
     * itemId·detectionId·name·qty 가 입력과 같아야 한다" 고 못박았는데, 이걸 모델에게 부탁만 하고
     * 믿으면 어긋난 순간 작업 전체가 실패한다. 모델에게서는 {@code ruleKeyword} 와
     * {@code attributes} 만 받아 채운다.
     */
    private JsonNode ruleCheck(JsonNode input) {
        Codes.Transport transport = Codes.Transport.valueOf(input.path("transport").asText());
        List<String> keywords = ruleEngine.keywordsOf(transport);

        JsonNode structured = callWithOneRetry(
                () -> api.complete(rulePrompt.system(), rulePrompt.structuring(input, keywords),
                        List.of(), "rule_check_structured", rulePrompt.structuringSchema()),
                "RULE_CHECK 1차");

        ObjectNode output = json.newObject();
        ArrayNode results = output.putArray("results");
        buildResults(results, input, structured.path("results"));
        if (results.isEmpty()) {
            throw new OpenAiException("질문에서 물품을 찾지 못했습니다. 물품 이름을 넣어 다시 물어봐 주세요.", false);
        }

        // 판정은 여기서 난다. 모델은 이 값을 만들지도, 바꾸지도 못한다.
        List<String> descriptions = ruleEngine.applyTo(input, output);

        JsonNode explained = callWithOneRetry(
                () -> api.complete(rulePrompt.system(),
                        rulePrompt.explaining(input, results, descriptions),
                        List.of(), "rule_check_explained", rulePrompt.explainingSchema()),
                "RULE_CHECK 2차");

        applyExplanations(output, results, explained, input);
        log.info("RULE_CHECK 완료: 물품 {}개, 추가 질문 {}",
                results.size(), output.path("followUpQuestion").isNull() ? "없음" : "있음");
        return output;
    }

    /** 입력 물품이 있으면 그 개수·순서·식별값을 서버가 확정한다. 없으면 모델이 낸 물품을 쓴다. */
    private void buildResults(ArrayNode results, JsonNode input, JsonNode structured) {
        JsonNode items = input.path("items");
        boolean fromItems = items.isArray() && !items.isEmpty();
        int size = fromItems ? items.size() : Math.min(structured.size(), MAX_RULE_RESULTS);

        for (int i = 0; i < size; i++) {
            JsonNode model = i < structured.size() ? structured.get(i) : json.newObject();
            ObjectNode result = results.addObject();

            if (fromItems) {
                JsonNode item = items.get(i);
                result.set("itemId", item.get("itemId"));
                result.set("detectionId", item.get("detectionId"));
                result.set("name", item.get("name"));
                result.set("qty", item.get("qty"));
            } else {
                result.putNull("itemId");
                result.putNull("detectionId");
                String name = text(model.path("name"), MAX_NAME_LENGTH);
                if (name == null) { results.remove(results.size() - 1); continue; }
                result.put("name", name);
                result.put("qty", Math.clamp(model.path("qty").asInt(1), 1, 99));
            }

            String keyword = text(model.path("ruleKeyword"), MAX_NAME_LENGTH);
            if (keyword == null) result.putNull("ruleKeyword");
            else result.put("ruleKeyword", keyword);
            result.set("attributes", attributesOf(model.path("attributes"),
                    fromItems ? items.get(i).path("attributes") : null));

            // 규칙 엔진이 곧 덮어쓴다. 계약이 요구하는 칸을 먼저 만들어 둔다.
            result.putNull("verdict");
            result.putNull("ruleId");
            result.putNull("conditionNote");
            result.putNull("reason");
            result.putNull("missingInfo");
            result.putNull("sourceUrl");
            result.putNull("checkedAt");
        }
    }

    /**
     * 07: 명시된 값만 쓰고 추정·환산하지 않는다.
     *
     * <p>입력에 이미 확인된 속성이 있으면 <b>그쪽이 이긴다.</b> 사용자가 라벨을 보고 답한 값이라
     * 모델이 질문 문장에서 다시 뽑은 값보다 믿을 만하다.
     */
    private ObjectNode attributesOf(JsonNode model, JsonNode confirmed) {
        ObjectNode attributes = json.newObject();
        for (String field : List.of("capacityMl", "batteryWh", "batteryMah", "bladeCm")) {
            JsonNode fromInput = confirmed == null ? null : confirmed.path(field);
            JsonNode value = fromInput != null && fromInput.isNumber() ? fromInput : model.path(field);
            if (!value.isNumber()) {
                attributes.putNull(field);
            } else {
                // 20000.0 이 아니라 20000 으로 남긴다. 07 예시와 Mock 픽스처가 정수이고,
                // 후속 턴이 이 값을 그대로 items[] 로 되보내므로 모양이 오가며 달라지지 않게 한다.
                double number = value.asDouble();
                if (number == Math.rint(number) && Math.abs(number) < Long.MAX_VALUE) {
                    attributes.put(field, (long) number);
                } else {
                    attributes.put(field, number);
                }
            }
        }
        return attributes;
    }

    /** 2차 결과를 붙인다. {@code answer}·{@code followUpQuestion} 규칙은 07 계약 그대로 서버가 건다. */
    private void applyExplanations(ObjectNode output, ArrayNode results,
                                   JsonNode explained, JsonNode input) {
        JsonNode reasons = explained.path("results");
        boolean needsMoreInfo = false;
        for (int i = 0; i < results.size(); i++) {
            ObjectNode result = (ObjectNode) results.get(i);
            String reason = text(reasons.path(i).path("reason"), MAX_REASON_TEXT_LENGTH);
            // 07: description 이 없으면 규정을 못 찾은 것이다. 그때 쓸 문장까지 정해 뒀다.
            result.put("reason", reason != null ? reason : "해당 규정을 찾지 못했습니다. 항공사에 확인하세요.");
            needsMoreInfo |= Codes.RuleVerdict.NEED_MORE_INFO.name().equals(result.path("verdict").asText());
        }

        // 물품 목록 호출이면 둘 다 null 이어야 한다 — 계약이 그렇게 검사한다.
        if (!input.path("question").isTextual()) {
            output.putNull("answer");
            output.putNull("followUpQuestion");
            return;
        }

        String answer = text(explained.path("answer"), MAX_ANSWER_LENGTH);
        output.put("answer", answer != null ? answer
                : "규정을 확인했습니다. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.");

        String followUp = text(explained.path("followUpQuestion"), MAX_FOLLOW_UP_LENGTH);
        // 되물을 것이 없는데 질문을 남기면 화면이 끝나지 않는다. 그 반대도 계약 위반이다.
        if (!needsMoreInfo) output.putNull("followUpQuestion");
        else output.put("followUpQuestion", followUp != null ? followUp : "확인이 필요한 값을 알려 주세요.");
    }

    // ─── AI-02 PACKING_LIST ──────────────────────────────────────────────────

    /**
     * 여행 정보 → 빠진 준비물 추천.
     *
     * <p>07 「누가 채우나」 그대로다. 모델은 {@code name/category/qty/priority/reason} 과
     * {@code tips[]} 만 낸다. {@code source} · {@code acceptedItemId} · 날씨 두 칸은
     * <b>모델 값을 버리고 서버가 채운다</b> — 애초에 파생 스키마에서 빼서 물어보지도 않는다.
     *
     * <p>중복 제거를 프롬프트에만 맡기지 않고 서버가 한 번 더 한다. 07 이 "items[].name 이
     * alreadyPacked 나 현재 checklist_items 에 이미 있으면 제외한다" 고 정했고, 이건 부탁이 아니라
     * 계약이다. <b>미완료 항목도 제외 대상</b>이라 실제 완료만 담긴 {@code alreadyPacked} 로는 모자라
     * 여행의 전체 목록을 읽는다.
     */
    private JsonNode packingList(Long tripId, JsonNode input) {
        Trip trip = tripId == null ? null : trips.findById(tripId).orElse(null);
        if (trip == null) throw new OpenAiException("여행 정보를 찾지 못했습니다.", false);

        List<ChecklistItem> current = items.findByTripIdOrderById(tripId);

        // 날씨는 거들 뿐이다. 못 받아도 추천은 나간다 (07: 조회 실패면 SEASONAL).
        WeatherSnapshot snapshot = weather
                .flatMap(client -> client.lookup(
                        input.path("destination").asText(),
                        LocalDate.parse(input.path("startDate").asText()),
                        LocalDate.parse(input.path("endDate").asText())))
                .orElse(null);

        JsonNode raw = callWithOneRetry(
                () -> callPacking(trip, input, current, snapshot), "PACKING_LIST");
        return toPackingOutput(raw, input, current, snapshot);
    }

    private JsonNode callPacking(Trip trip, JsonNode input,
                                 List<ChecklistItem> current, WeatherSnapshot snapshot) {
        JsonNode raw = api.complete(
                packingPrompt.system(),
                packingPrompt.user(trip, input, current, snapshot),
                List.of(),                       // 추천은 사진을 보지 않는다
                "packing_list_output",
                packingPrompt.outputSchema());

        if (!raw.path("items").isArray()) {
            throw new OpenAiException("응답에 items 배열이 없습니다.", true);
        }
        return raw;
    }

    private JsonNode toPackingOutput(JsonNode raw, JsonNode input,
                                     List<ChecklistItem> current, WeatherSnapshot snapshot) {
        // 이미 있는 이름을 모은다. 실제 완료(alreadyPacked)와 미완료를 모두 넣는다.
        Set<String> taken = new LinkedHashSet<>();
        for (JsonNode packed : input.path("alreadyPacked")) {
            taken.add(RecommendationStore.normalize(packed.path("name").asText("")));
        }
        current.forEach(item -> taken.add(RecommendationStore.normalize(item.getName())));

        ObjectNode output = json.newObject();
        ArrayNode candidates = output.putArray("items");
        for (JsonNode node : raw.path("items")) {
            if (candidates.size() >= MAX_CANDIDATES) break;

            String name = text(node.path("name"), MAX_NAME_LENGTH);
            String reason = text(node.path("reason"), MAX_REASON_LENGTH);
            // 07: 후보별 reason 은 비어 있지 않아야 한다.
            if (name == null || reason == null) continue;
            // 모델이 같은 이름을 두 번 낼 수도 있다. taken 에 넣으며 걸러 낸다.
            if (!taken.add(RecommendationStore.normalize(name))) continue;

            ObjectNode candidate = candidates.addObject();
            candidate.put("name", name);
            candidate.put("category", enumOrDefault(Codes.Category.class,
                    node.path("category").asText(""), Codes.Category.ETC).name());
            candidate.put("qty", Math.clamp(node.path("qty").asInt(1), 1, 99));
            candidate.put("priority", enumOrDefault(Codes.Priority.class,
                    node.path("priority").asText(""), Codes.Priority.RECOMMENDED).name());
            candidate.put("reason", reason);
            // 07 서버 필드. RULE 후보 보강은 아직 없다 — 모델 후보는 전부 AI 다.
            candidate.put("source", Codes.ItemSource.AI.name());
            candidate.putNull("acceptedItemId");
        }

        ArrayNode tips = output.putArray("tips");
        for (JsonNode tip : raw.path("tips")) {
            if (tips.size() >= MAX_TIPS) break;
            String value = text(tip, MAX_TIP_LENGTH);
            if (value != null) tips.add(value);
        }

        // 07: 날씨 두 칸도 서버가 채운다. 못 받았으면 계절 평균으로 두되 기준일은 없다.
        output.put("weatherSource", snapshot == null ? "SEASONAL" : snapshot.source());
        if (snapshot == null) output.putNull("weatherAsOf");
        else output.put("weatherAsOf", snapshot.asOf().toString());

        log.info("PACKING_LIST 완료: 후보 {}개, tips {}개, 날씨 {}",
                candidates.size(), tips.size(), snapshot == null ? "없음" : snapshot.source());
        return output;
    }

    /** {@code strict} 스키마가 enum 을 강제하지만, 값 검사를 모델 쪽에만 맡기지 않는다. */
    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    /** 07: 빈 문자열은 쓰지 않는다 — 공백뿐이면 {@code null} 이다. 길이는 DB 컬럼 한도로 자른다. */
    private static String text(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual()) return null;
        String value = node.asText().trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record Detection(long photoId, String name, int qty, BigDecimal confidence,
                             String missingInfo, String labelText) { }
}
