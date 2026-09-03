package com.skala.miniai.domain.weather;

import java.time.LocalDate;

/**
 * 추천 프롬프트에 넣을 날씨 한 덩어리 (07 AI-02 「User Prompt 템플릿」의 {@code {{server:weather.*}}}).
 *
 * @param source      {@code FORECAST} 또는 {@code SEASONAL}. 07 출력의 {@code weatherSource} 가 된다.
 *                    출발일이 {@code app.weather.forecast-max-days} 이내면 예보, 넘으면 계절 앙상블이다.
 * @param asOf        이 자료를 받은 날. 07 출력의 {@code weatherAsOf} 가 된다. Open-Meteo 는 모델
 *                    실행 시각을 돌려주지 않으므로 <b>받은 날</b>이 곧 기준일이다.
 * @param summary     사람이 읽는 한 줄. 프롬프트에만 쓰고 저장하지 않는다.
 * @param minC        여행 기간의 최저기온 최솟값(℃).
 * @param maxC        여행 기간의 최고기온 최댓값(℃).
 * @param rainChance  강수확률 최댓값(%). 계절 앙상블은 이 값을 주지 않아 {@code null} 이다.
 */
public record WeatherSnapshot(String source, LocalDate asOf, String summary,
                              Integer minC, Integer maxC, Integer rainChance) { }
