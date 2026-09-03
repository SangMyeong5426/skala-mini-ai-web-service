package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import com.skala.miniai.domain.ai.OpenAiException;
import com.skala.miniai.domain.ai.VisionImage;
import com.skala.miniai.domain.ai.VisionImageLoader;
import com.skala.miniai.domain.photo.TripPhoto;
import com.skala.miniai.domain.photo.TripPhotoRepository;
import com.skala.miniai.domain.trip.Trip;
import com.skala.miniai.domain.trip.TripRepository;

/**
 * 모델이 낸 답을 <b>믿지 않고 다시 검사하는</b> 부분만 본다.
 *
 * <p>Structured Outputs 가 지켜 주는 것은 <b>모양</b>뿐이다. {@code qty=500}, {@code confidence=1.9},
 * 보내지도 않은 {@code photoId} 는 스키마를 통과한다 — 그대로 저장하면
 * {@code detected_objects} 의 {@code NUMERIC(4,3)} 이나 FK 에서 터진다.
 *
 * <p>네트워크는 타지 않는다. {@link OpenAiChatApi} 를 갈아 끼워 응답만 흉내 낸다.
 */
class OpenAiBagCheckTest {

    private final Json json = new Json(JsonMapper.builder().build());

    private OpenAiChatApi api;
    private VisionImageLoader loader;
    private TripPhotoRepository photos;
    private OpenAiClient client;

    @BeforeEach
    void setUp() {
        api = mock(OpenAiChatApi.class);
        loader = mock(VisionImageLoader.class);
        photos = mock(TripPhotoRepository.class);
        TripRepository trips = mock(TripRepository.class);

        Trip trip = new Trip(1L, "서울", "도쿄");
        ReflectionTestUtils.setField(trip, "id", 7L);
        ReflectionTestUtils.setField(trip, "startDate", LocalDate.of(2026, 10, 3));
        ReflectionTestUtils.setField(trip, "endDate", LocalDate.of(2026, 10, 6));
        ReflectionTestUtils.setField(trip, "transport", Codes.Transport.FLIGHT);
        given(trips.findById(7L)).willReturn(Optional.of(trip));

        client = new OpenAiClient(mock(MockAiClient.class), api, new BagCheckPrompt(json),
                loader, photos, trips, json, 20);
    }

    /** 사진 1·2 는 읽히고 3 은 접수 뒤에 지워진 상황을 만든다. */
    private void givenPhotos() {
        TripPhoto one = photo(1L);
        TripPhoto two = photo(2L);
        given(photos.findAllById(any())).willReturn(List.of(one, two));
        given(loader.load(one)).willReturn(Optional.of(new VisionImage(1L, "data:image/jpeg;base64,AAA")));
        given(loader.load(two)).willReturn(Optional.of(new VisionImage(2L, "data:image/jpeg;base64,BBB")));
    }

    private static TripPhoto photo(long id) {
        TripPhoto photo = new TripPhoto(7L, "trips/7/" + id + ".jpg", Codes.BagKind.CABIN);
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    private JsonNode run(String modelJson) {
        givenPhotos();
        given(api.complete(any(), any(), any(), any(), any())).willReturn(json.read(modelJson));
        return client.run(Codes.JobType.BAG_CHECK, json.read("{\"photoIds\":[1,2,3]}"));
    }

    @Test
    void 규격을_벗어난_값을_서버가_보정한다() {
        JsonNode output = run("""
                {"detections":[
                  {"photoId":1,"name":"보조배터리","qty":1,"confidence":0.874,
                   "missingInfo":"배터리 정격(Wh)","labelText":"20000mAh"},
                  {"photoId":2,"name":"물병","qty":500,"confidence":1.9,
                   "missingInfo":"   ","labelText":null}
                ]}
                """);

        JsonNode battery = output.path("detections").path(0);
        assertThat(battery.path("name").asText()).isEqualTo("보조배터리");
        assertThat(battery.path("confidence").decimalValue()).isEqualByComparingTo("0.874");
        // 07: confidenceLevel 은 서버가 confidence 로 채운다. 모델에게 묻지 않는다.
        assertThat(battery.path("confidenceLevel").asText()).isEqualTo("HIGH");
        assertThat(battery.path("labelText").asText()).isEqualTo("20000mAh");

        JsonNode bottle = output.path("detections").path(1);
        assertThat(bottle.path("qty").asInt()).isEqualTo(99);                       // 1~99 로 자른다
        assertThat(bottle.path("confidence").decimalValue()).isEqualByComparingTo("1.000");   // 0~1
        assertThat(bottle.path("missingInfo").isNull()).isTrue();                   // 공백은 빈 문자열이 아니라 null
    }

    @Test
    void 보내지_않은_사진과_이름_없는_항목은_버린다() {
        JsonNode output = run("""
                {"detections":[
                  {"photoId":1,"name":"충전기","qty":1,"confidence":0.9,"missingInfo":null,"labelText":null},
                  {"photoId":99,"name":"여권","qty":1,"confidence":0.9,"missingInfo":null,"labelText":null},
                  {"photoId":2,"name":"   ","qty":1,"confidence":0.9,"missingInfo":null,"labelText":null}
                ]}
                """);

        assertThat(output.path("detections")).hasSize(1);
        // 읽지 못한 사진 3은 조용히 사라지지 않는다 — S-04 가 재시도 버튼을 달 수 있어야 한다.
        assertThat(output.path("failedPhotoIds").path(0).asInt()).isEqualTo(3);
    }

    @Test
    void 사진_한_장에_열_개까지만_남기고_신뢰도_높은_것을_고른다() {
        StringBuilder sb = new StringBuilder("{\"detections\":[");
        for (int i = 0; i < 12; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"photoId\":1,\"name\":\"물건").append(i)
                    .append("\",\"qty\":1,\"confidence\":0.").append(500 + i * 10)
                    .append(",\"missingInfo\":null,\"labelText\":null}");
        }
        JsonNode output = run(sb.append("]}").toString());

        assertThat(output.path("detections")).hasSize(10);
        // 가장 낮은 둘(물건0 · 물건1)이 잘린다.
        assertThat(output.path("detections").path(9).path("name").asText()).isEqualTo("물건2");
    }

    @Test
    void 다시_걸어_볼_실패는_한_번만_재시도한다() {
        givenPhotos();
        given(api.complete(any(), any(), any(), any(), any()))
                .willThrow(new OpenAiException("429", true))
                .willReturn(json.read("{\"detections\":[]}"));

        JsonNode output = client.run(Codes.JobType.BAG_CHECK, json.read("{\"photoIds\":[1,2]}"));

        assertThat(output.path("detections")).isEmpty();
        verify(api, times(2)).complete(any(), any(), any(), any(), any());
    }

    @Test
    void 키가_틀린_실패는_다시_걸지_않는다() {
        givenPhotos();
        given(api.complete(any(), any(), any(), any(), any()))
                .willThrow(new OpenAiException("401", false));

        assertThatThrownBy(() -> client.run(Codes.JobType.BAG_CHECK, json.read("{\"photoIds\":[1,2]}")))
                .isInstanceOf(OpenAiException.class);
        verify(api, times(1)).complete(any(), any(), any(), any(), any());
    }

    @Test
    void 한_장도_읽지_못하면_작업이_실패한다() {
        given(photos.findAllById(any())).willReturn(List.of(photo(1L)));
        given(loader.load(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> client.run(Codes.JobType.BAG_CHECK, json.read("{\"photoIds\":[1]}")))
                .isInstanceOf(OpenAiException.class)
                .hasMessageContaining("분석할 수 있는 사진이 없습니다");
    }
}
