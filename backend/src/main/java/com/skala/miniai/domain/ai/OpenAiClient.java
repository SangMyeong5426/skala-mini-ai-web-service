package com.skala.miniai.domain.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.TripPhoto;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripRepository;

/**
 * 실제 OpenAI 를 부르는 {@link AiClient} — 07 로드맵 <b>2·3단계</b>({@code BAG_CHECK} 비전 입력,
 * 스키마 검증 + 재시도 1회).
 *
 * <p>{@code AI_PROVIDER=openai} 일 때만 빈으로 올라오고, 그때 {@link MockAiClient} 를 밀어낸다
 * ({@code @Primary}). <b>기본값은 그대로 {@code mock} 이다</b> — 발표 데모는 네트워크에 묶이면 안 된다
 * (AGENTS.md). 환경 변수 한 줄로 켜고 끄는 것이 07 이 설계해 둔 모습이다.
 *
 * <p><b>{@code BAG_CHECK} 만 실제로 부른다.</b> 나머지 셋은 {@link MockAiClient} 에 넘긴다 —
 * 요청받은 것은 사진 인식이고, 07 로드맵도 텍스트 3종을 별도 단계로 두었다.
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

    private final MockAiClient mock;
    private final OpenAiChatApi api;
    private final BagCheckPrompt prompt;
    private final VisionImageLoader images;
    private final TripPhotoRepository photos;
    private final TripRepository trips;
    private final Json json;
    private final int maxPhotos;

    public OpenAiClient(MockAiClient mock, OpenAiChatApi api, BagCheckPrompt prompt,
                        VisionImageLoader images, TripPhotoRepository photos, TripRepository trips, Json json,
                        @Value("${app.ai.vision.max-photos:20}") int maxPhotos) {
        this.mock = mock;
        this.api = api;
        this.prompt = prompt;
        this.images = images;
        this.photos = photos;
        this.trips = trips;
        this.json = json;
        this.maxPhotos = maxPhotos;
    }

    @Override
    public String modelName() {
        return api.model();
    }

    @Override
    public JsonNode run(Codes.JobType jobType, JsonNode input) {
        if (jobType != Codes.JobType.BAG_CHECK) return mock.run(jobType, input);
        return bagCheck(input);
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

        JsonNode raw = callWithOneRetry(trip, targets, payload);
        return toOutput(raw, payload, failed);
    }

    /** 07 로드맵 2단계 — <b>재시도는 한 번뿐이다.</b> 두 번째도 실패하면 작업을 {@code FAILED} 로 둔다. */
    private JsonNode callWithOneRetry(Trip trip, List<TripPhoto> targets, List<VisionImage> payload) {
        try {
            return call(trip, targets, payload);
        } catch (OpenAiException e) {
            if (!e.isRetryable()) throw e;
            log.warn("BAG_CHECK 첫 호출 실패, 한 번만 다시 겁니다: {}", e.getMessage());
            return call(trip, targets, payload);
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
