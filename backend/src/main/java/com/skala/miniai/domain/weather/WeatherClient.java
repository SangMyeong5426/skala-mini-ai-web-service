package com.skala.miniai.domain.weather;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 여행지·기간의 날씨를 한 번 읽어 온다.
 *
 * <p>{@code AiClient} 와 같은 이유로 인터페이스를 둔다 — 제공자를 바꿔도 부르는 쪽이 안 바뀐다
 * ({@code WEATHER_PROVIDER}).
 *
 * <p><b>실패를 예외로 올리지 않는다.</b> 날씨는 추천을 <b>거들</b> 뿐이라, 못 받았다고 추천
 * 작업을 실패로 만들면 안 된다. 못 받으면 {@link Optional#empty()} 고, 그때는 날씨 없이 추천한다.
 */
public interface WeatherClient {

    Optional<WeatherSnapshot> lookup(String destination, LocalDate startDate, LocalDate endDate);
}
