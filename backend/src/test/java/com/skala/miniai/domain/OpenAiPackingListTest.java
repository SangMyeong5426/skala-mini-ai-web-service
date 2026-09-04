package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.ai.BagCheckPrompt;
import com.skala.miniai.domain.ai.MockAiClient;
import com.skala.miniai.domain.ai.OpenAiChatApi;
import com.skala.miniai.domain.ai.OpenAiClient;
import com.skala.miniai.domain.ai.PackingListPrompt;
import com.skala.miniai.domain.ai.RuleCheckPrompt;
import com.skala.miniai.domain.ai.VisionImageLoader;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.master.RuleEngine;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripRepository;
import com.skala.miniai.domain.weather.WeatherClient;
import com.skala.miniai.domain.weather.WeatherSnapshot;

/**
 * 추천에서 <b>서버가 책임지는 몫</b>만 본다 (07 AI-02 「누가 채우나」).
 *
 * <p>중복 제거를 프롬프트에만 맡기면 안 된다. 특히 <b>미완료 항목</b>은 `alreadyPacked` 에
 * 없어서, 서버가 여행의 전체 목록을 읽지 않으면 이미 있는 물건을 다시 추천하게 된다.
 *
 * <p>`source`·`acceptedItemId`·날씨 두 칸은 모델에게 묻지도 않는다. 서버가 채우는지 확인한다.
 */
class OpenAiPackingListTest {

    private static final String INPUT = """
            {"destination":"도쿄","startDate":"2026-10-03","endDate":"2026-10-06",
             "transport":"FLIGHT","purpose":"TOUR","note":null,
             "alreadyPacked":[{"name":"충전기","category":"ELECTRONIC","qty":1}]}
            """;

    private final Json json = new Json(JsonMapper.builder().build());

    private OpenAiChatApi api;
    private ChecklistItemRepository items;
    private WeatherClient weather;

    @BeforeEach
    void setUp() {
        api = mock(OpenAiChatApi.class);
        items = mock(ChecklistItemRepository.class);
        weather = mock(WeatherClient.class);
    }

    private OpenAiClient clientWith(WeatherClient weatherClient) {
        TripRepository trips = mock(TripRepository.class);
        Trip trip = new Trip(1L, "서울", "도쿄");
        ReflectionTestUtils.setField(trip, "id", 7L);
        ReflectionTestUtils.setField(trip, "countryCode", "JP");
        given(trips.findById(7L)).willReturn(Optional.of(trip));

        return new OpenAiClient(mock(MockAiClient.class), api, new BagCheckPrompt(json),
                new PackingListPrompt(json), new RuleCheckPrompt(json), mock(RuleEngine.class),
                mock(VisionImageLoader.class),
                mock(TripPhotoRepository.class), trips, items,
                Optional.ofNullable(weatherClient), json, 20);
    }

    private JsonNode run(String modelJson, WeatherClient weatherClient) {
        given(api.complete(any(), any(), any(), any(), any())).willReturn(json.read(modelJson));
        return clientWith(weatherClient).run(Codes.JobType.PACKING_LIST, 7L, json.read(INPUT));
    }

    @Test
    void 이미_챙긴_것과_미완료_항목을_서버가_다시_걸러_낸다() {
        // 우산은 아직 미완료다 — alreadyPacked 에 없으므로 전체 목록을 읽어야만 걸러진다.
        given(items.findByTripIdOrderById(7L)).willReturn(List.of(
                new ChecklistItem(7L, "우산", Codes.Category.ETC, 1,
                        Codes.Priority.RECOMMENDED, Codes.ItemSource.USER, Codes.CheckStatus.UNCHECKED)));

        JsonNode output = run("""
                {"items":[
                  {"name":"충전기","category":"ELECTRONIC","qty":1,"priority":"REQUIRED","reason":"이미 챙겼다"},
                  {"name":"우산","category":"ETC","qty":1,"priority":"RECOMMENDED","reason":"미완료로 이미 있다"},
                  {"name":"변환 플러그","category":"ELECTRONIC","qty":1,"priority":"REQUIRED","reason":"일본은 A타입이다"},
                  {"name":"변환 플러그","category":"ELECTRONIC","qty":1,"priority":"REQUIRED","reason":"모델이 두 번 냈다"}
                ],"tips":[]}
                """, null);

        assertThat(output.path("items")).hasSize(1);
        assertThat(candidate(output, "변환 플러그")).isNotNull();
    }

    @Test
    void 서버_필드는_모델에게_묻지_않고_서버가_채운다() {
        given(items.findByTripIdOrderById(7L)).willReturn(List.of());
        given(weather.lookup(any(), any(), any())).willReturn(Optional.of(new WeatherSnapshot(
                "FORECAST", LocalDate.of(2026, 9, 3), "단기 예보 최저 15도 · 최고 24도", 15, 24, 40)));

        JsonNode output = run("""
                {"items":[{"name":"우산","category":"ETC","qty":1,"priority":"RECOMMENDED","reason":"강수확률이 높다"}],
                 "tips":["도쿄 콘센트는 A타입, 100V입니다."]}
                """, weather);

        JsonNode umbrella = candidate(output, "우산");
        assertThat(umbrella.path("source").asText()).isEqualTo("AI");
        assertThat(umbrella.path("acceptedItemId").isNull()).isTrue();
        assertThat(output.path("weatherSource").asText()).isEqualTo("FORECAST");
        assertThat(output.path("weatherAsOf").asText()).isEqualTo("2026-09-03");
        assertThat(output.path("tips")).hasSize(1);
    }

    @Test
    void 날씨를_못_받으면_계절_평균으로_두고_기준일은_비운다() {
        given(items.findByTripIdOrderById(7L)).willReturn(List.of());

        JsonNode output = run("""
                {"items":[{"name":"우산","category":"ETC","qty":1,"priority":"RECOMMENDED","reason":"비가 올 수 있다"}],
                 "tips":[]}
                """, null);

        assertThat(output.path("weatherSource").asText()).isEqualTo("SEASONAL");
        // 쓰지 않은 자료의 기준일을 실행일로 지어내지 않는다 (07).
        assertThat(output.path("weatherAsOf").isNull()).isTrue();
    }

    @Test
    void 이유가_없는_후보는_버리고_수량은_1_99_로_자른다() {
        given(items.findByTripIdOrderById(7L)).willReturn(List.of());

        JsonNode output = run("""
                {"items":[
                  {"name":"양말","category":"CLOTHING","qty":400,"priority":"RECOMMENDED","reason":"3박이면 넉넉히"},
                  {"name":"모자","category":"CLOTHING","qty":1,"priority":"RECOMMENDED","reason":"   "}
                ],"tips":[]}
                """, null);

        assertThat(output.path("items")).hasSize(1);
        assertThat(candidate(output, "양말").path("qty").asInt()).isEqualTo(99);
        assertThat(candidate(output, "모자")).isNull();
    }

    private JsonNode candidate(JsonNode output, String name) {
        for (JsonNode candidate : output.path("items")) {
            if (name.equals(candidate.path("name").asText())) return candidate;
        }
        return null;
    }
}
