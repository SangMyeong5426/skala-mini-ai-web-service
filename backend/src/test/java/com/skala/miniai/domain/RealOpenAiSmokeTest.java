package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.config.UploadConfig;
import com.skala.miniai.domain.ai.BagCheckPrompt;
import com.skala.miniai.domain.ai.MockAiClient;
import com.skala.miniai.domain.ai.OpenAiChatApi;
import com.skala.miniai.domain.ai.OpenAiClient;
import com.skala.miniai.domain.ai.PackingListPrompt;
import com.skala.miniai.domain.ai.VisionImageLoader;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.photo.TripPhoto;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripRepository;
import com.skala.miniai.domain.weather.OpenMeteoWeatherClient;

/**
 * <b>실제 모델을 부르는 수동 점검.</b> 요금이 나갈 수 있고 네트워크를 탄다.
 *
 * <p>어디로 나가는지는 {@code backend/.env} 의 {@code AI_BASE_URL} 이 정한다 — OpenAI 일 수도,
 * Gemini 의 OpenAI 호환 엔드포인트일 수도 있다. 이 테스트는 그 구분을 하지 않는다.
 * <b>{@code AI_PROVIDER} 는 보지 않는다</b> — `.env` 가 {@code mock} 이어도 실제로 부른다.
 *
 * <pre>{@code
 * ./gradlew test --tests '*RealOpenAiSmokeTest*' -Dai.smoke=true
 * }</pre>
 *
 * <p>{@code -Dai.smoke=true} 가 없으면 통째로 건너뛴다. 팀원이 {@code ./gradlew build} 를
 * 돌릴 때마다 요금이 나가면 안 된다. {@code backend/.env} 의 {@code AI_API_KEY}·{@code AI_MODEL}
 * 을 이 테스트가 직접 읽는다 — Gradle 의 {@code .env} 주입은 {@code bootRun} 에만 걸리기 때문이다.
 *
 * <p>DB 는 타지 않는다. 저장 경로는 {@code AiJobAndChecklistTest} 가 H2 로 이미 확인한다.
 * 여기서 보는 것은 <b>프롬프트·이미지 파이프라인·응답 검증</b>이 실제 모델에 대해 성립하는지다.
 */
@EnabledIfSystemProperty(named = "ai.smoke", matches = "true")
class RealOpenAiSmokeTest {

    /** 저장소에 들어 있는 데모 사진. 인터넷이 끊겨도 시연이 되도록 커밋해 둔 것이다. */
    private static final Path DEMO_PHOTOS = Path.of("..", "database", "demo-photos");

    private final Json json = new Json(JsonMapper.builder().build());

    private Map<String, String> env;
    private OpenAiChatApi api;

    @BeforeEach
    void setUp() throws Exception {
        env = readDotEnv();
        assertThat(env.get("AI_API_KEY"))
                .as("backend/.env 의 AI_API_KEY 가 비어 있다")
                .isNotBlank();

        api = new OpenAiChatApi(json,
                env.getOrDefault("AI_BASE_URL", "https://api.openai.com/v1"),
                env.get("AI_API_KEY"),
                env.getOrDefault("AI_MODEL", "gpt-4o"),
                Double.parseDouble(env.getOrDefault("AI_TEMPERATURE", "0.2")),
                Integer.parseInt(env.getOrDefault("AI_MAX_TOKENS", "4096")),
                Long.parseLong(env.getOrDefault("AI_TIMEOUT_MS", "60000")));
    }

    @Test
    void 데모_사진에서_물품을_실제로_인식한다() {
        TripPhoto one = photo(1L, "bag-01.jpg");
        TripPhoto two = photo(2L, "bag-02.jpg");

        TripPhotoRepository photos = mock(TripPhotoRepository.class);
        given(photos.findAllById(any())).willReturn(List.of(one, two));

        JsonNode output = client(photos, mock(ChecklistItemRepository.class))
                .run(Codes.JobType.BAG_CHECK, 7L, json.read("{\"photoIds\":[1,2]}"));

        System.out.println("─── BAG_CHECK 실제 응답 ───\n" + json.write(output));

        assertThat(output.path("detections")).isNotEmpty();
        for (JsonNode d : output.path("detections")) {
            assertThat(d.path("photoId").asLong()).isIn(1L, 2L);
            assertThat(d.path("name").asText()).isNotBlank();
            assertThat(d.path("qty").asInt()).isBetween(1, 99);
            assertThat(d.path("confidence").asDouble()).isBetween(0.0, 1.0);
            assertThat(d.path("confidenceLevel").asText()).isIn("HIGH", "MEDIUM", "LOW");
            assertThat(d.has("missingInfo")).isTrue();
            assertThat(d.has("labelText")).isTrue();
        }
        assertThat(output.path("failedPhotoIds")).isEmpty();
    }

    @Test
    void 여행_정보로_추가_준비물을_실제로_추천한다() {
        ChecklistItemRepository items = mock(ChecklistItemRepository.class);
        given(items.findByTripIdOrderById(7L)).willReturn(List.of(
                new ChecklistItem(7L, "우산", Codes.Category.ETC, 1,
                        Codes.Priority.RECOMMENDED, Codes.ItemSource.USER, Codes.CheckStatus.UNCHECKED)));

        JsonNode output = client(mock(TripPhotoRepository.class), items)
                .run(Codes.JobType.PACKING_LIST, 7L, json.read("""
                        {"destination":"도쿄","startDate":"2026-10-03","endDate":"2026-10-06",
                         "transport":"FLIGHT","purpose":"TOUR","note":"친구와 디즈니랜드",
                         "alreadyPacked":[{"name":"충전기","category":"ELECTRONIC","qty":1}]}
                        """));

        System.out.println("─── PACKING_LIST 실제 응답 ───\n" + json.write(output));

        assertThat(output.path("items")).isNotEmpty();
        for (JsonNode c : output.path("items")) {
            assertThat(c.path("name").asText()).isNotBlank();
            assertThat(c.path("reason").asText()).isNotBlank();
            assertThat(c.path("source").asText()).isEqualTo("AI");
            assertThat(c.path("acceptedItemId").isNull()).isTrue();
            // 이미 챙긴 것·내 목록에 있는 것은 서버가 걸러 낸다.
            assertThat(c.path("name").asText()).isNotIn("충전기", "우산");
        }
        assertThat(output.path("weatherSource").asText()).isIn("FORECAST", "SEASONAL");
        // 날씨를 실제로 받았는지까지 본다. asOf 가 null 이면 Open-Meteo 조회가 조용히 실패한 것이다.
        assertThat(output.path("weatherAsOf").isNull())
                .as("Open-Meteo 조회가 실패했다. 로그의 'Open-Meteo' 줄을 본다")
                .isFalse();
    }

    private OpenAiClient client(TripPhotoRepository photos, ChecklistItemRepository items) {
        TripRepository trips = mock(TripRepository.class);
        Trip trip = new Trip(1L, "서울", "도쿄");
        ReflectionTestUtils.setField(trip, "id", 7L);
        ReflectionTestUtils.setField(trip, "countryCode", "JP");
        ReflectionTestUtils.setField(trip, "startDate", LocalDate.of(2026, 10, 3));
        ReflectionTestUtils.setField(trip, "endDate", LocalDate.of(2026, 10, 6));
        ReflectionTestUtils.setField(trip, "transport", Codes.Transport.FLIGHT);
        given(trips.findById(7L)).willReturn(Optional.of(trip));

        UploadConfig upload = mock(UploadConfig.class);
        given(upload.dir()).willReturn(DEMO_PHOTOS.toAbsolutePath().normalize());

        return new OpenAiClient(mock(MockAiClient.class), api, new BagCheckPrompt(json),
                new PackingListPrompt(json), new VisionImageLoader(upload, 1024, 4_194_304),
                photos, trips, items, Optional.of(weatherClient()), json, 20);
    }

    private OpenMeteoWeatherClient weatherClient() {
        return new OpenMeteoWeatherClient(json,
                "https://geocoding-api.open-meteo.com/v1/search",
                "https://api.open-meteo.com/v1/forecast",
                "https://seasonal-api.open-meteo.com/v1/seasonal",
                16, 10_000);
    }

    private static TripPhoto photo(long id, String fileName) {
        TripPhoto photo = new TripPhoto(7L, fileName, Codes.BagKind.CABIN);
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    /** Gradle 의 {@code .env} 주입은 {@code bootRun} 에만 걸린다. 여기서는 직접 읽는다. */
    private static Map<String, String> readDotEnv() throws Exception {
        Map<String, String> values = new HashMap<>();
        Path file = Path.of(".env");
        if (!Files.isReadable(file)) return values;
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) continue;
            values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return values;
    }
}
