package com.skala.miniai.domain.weather;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import com.skala.miniai.common.Json;

/**
 * Open-Meteo 로 여행지 날씨를 읽는다. <b>API 키가 없다</b> — 팀원이 따로 발급받지 않아도 된다.
 *
 * <p>두 단계다. 예보 API 가 도시명을 받지 않아 <b>지오코딩으로 좌표를 먼저</b> 구한다.
 *
 * <p>출발일이 {@code app.weather.forecast-max-days}(16일) 이내면 예보 API, 넘으면 계절 API 다.
 * 07 이 정한 {@code FORECAST} · {@code SEASONAL} 구분이 그대로 여기서 갈린다.
 * 계절 API 는 앙상블이라 {@code temperature_2m_max} 가 <b>멤버 평균</b>이고 강수확률은 주지 않는다.
 * 두 API 의 {@code daily} 모양이 같아서 파싱은 한 곳이다.
 *
 * <p>{@code WEATHER_PROVIDER} 가 {@code openmeteo} 가 아니면 이 빈이 아예 없다. 그때
 * {@code PACKING_LIST} 는 <b>날씨 없이</b> 추천한다 — 데모에서 네트워크를 끊고 싶을 때 쓴다.
 */
@Component
@ConditionalOnProperty(name = "app.weather.provider", havingValue = "openmeteo", matchIfMissing = true)
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);

    private final RestClient http;
    private final Json json;
    private final String geocodingUrl;
    private final String forecastUrl;
    private final String seasonalUrl;
    private final int forecastMaxDays;

    public OpenMeteoWeatherClient(Json json,
                                  @Value("${app.weather.geocoding-url}") String geocodingUrl,
                                  @Value("${app.weather.forecast-url}") String forecastUrl,
                                  @Value("${app.weather.seasonal-url}") String seasonalUrl,
                                  @Value("${app.weather.forecast-max-days:16}") int forecastMaxDays,
                                  @Value("${app.weather.timeout-ms:10000}") long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5_000)));
        // 날씨를 기다리느라 추천이 늦어지면 안 된다. AI 호출보다 훨씬 짧게 잡는다.
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.http = RestClient.builder().requestFactory(factory).build();
        this.json = json;
        this.geocodingUrl = geocodingUrl;
        this.forecastUrl = forecastUrl;
        this.seasonalUrl = seasonalUrl;
        this.forecastMaxDays = forecastMaxDays;
    }

    @Override
    public Optional<WeatherSnapshot> lookup(String destination, LocalDate startDate, LocalDate endDate) {
        try {
            JsonNode place = get(UriComponentsBuilder.fromUriString(geocodingUrl)
                    .queryParam("name", destination)
                    .queryParam("count", 1)
                    .queryParam("language", "ko"));

            JsonNode first = place.path("results").path(0);
            if (first.isMissingNode()) {
                log.info("Open-Meteo 에 '{}' 좌표가 없습니다. 날씨 없이 추천합니다.", destination);
                return Optional.empty();
            }

            boolean nearby = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), startDate) <= forecastMaxDays;
            return Optional.of(read(first, startDate, endDate, nearby));

        } catch (Exception e) {
            // 07: 조회 실패는 추천을 막지 않는다.
            log.warn("Open-Meteo 조회에 실패했습니다. 날씨 없이 추천합니다.", e);
            return Optional.empty();
        }
    }

    private WeatherSnapshot read(JsonNode place, LocalDate startDate, LocalDate endDate, boolean nearby) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(nearby ? forecastUrl : seasonalUrl)
                .queryParam("latitude", place.path("latitude").asDouble())
                .queryParam("longitude", place.path("longitude").asDouble())
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate);
        // 계절 앙상블에는 강수확률이 없다. 넣어 달라고 하면 400 이다.
        uri = nearby
                ? uri.queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "auto")
                : uri.queryParam("daily", "temperature_2m_max,temperature_2m_min");

        JsonNode daily = get(uri).path("daily");

        Integer maxC = extreme(daily.path("temperature_2m_max"), true);
        Integer minC = extreme(daily.path("temperature_2m_min"), false);
        Integer rainChance = nearby ? extreme(daily.path("precipitation_probability_max"), true) : null;

        String source = nearby ? "FORECAST" : "SEASONAL";
        StringBuilder summary = new StringBuilder(nearby ? "단기 예보" : "계절 평균(앙상블)");
        if (minC != null && maxC != null) summary.append(" 최저 ").append(minC).append("도 · 최고 ").append(maxC).append("도");
        if (rainChance != null) summary.append(" · 강수확률 최대 ").append(rainChance).append('%');

        // Open-Meteo 는 모델 실행 시각을 돌려주지 않는다. 받은 날이 곧 기준일이다.
        return new WeatherSnapshot(source, LocalDate.now(ZoneOffset.UTC), summary.toString(),
                minC, maxC, rainChance);
    }

    /** 기간 전체에서 가장 높은(낮은) 값. {@code null} 이 섞여 오므로 걸러 낸다. */
    private static Integer extreme(JsonNode values, boolean highest) {
        Double found = null;
        for (JsonNode value : values) {
            if (value.isNull() || !value.isNumber()) continue;
            double v = value.asDouble();
            if (found == null || (highest ? v > found : v < found)) found = v;
        }
        return found == null ? null : (int) Math.round(found);
    }

    /**
     * <b>{@code encode()} 를 빠뜨리면 안 된다.</b> 목적지가 "도쿄" 처럼 한글이면 그대로 나가서
     * 서버가 못 알아듣고 {@code results} 가 빈 배열로 돌아온다 — 오류가 아니라 <b>조용한 빈 결과</b>라
     * 날씨 없이 추천이 나가고, 로그를 보지 않으면 눈치채기 어렵다. 실제로 그렇게 한 번 새어 나갔다.
     *
     * <p>{@code toUriString()} 을 {@code RestClient.uri(String)} 에 넘기지 않는 이유도 같다.
     * 그쪽은 인자를 URI <b>템플릿</b>으로 보고 한 번 더 손대므로, 완성된 {@link URI} 를 넘긴다.
     */
    private JsonNode get(UriComponentsBuilder uri) {
        URI encoded = uri.build().encode().toUri();
        return json.read(http.get().uri(encoded).retrieve().body(String.class));
    }
}
