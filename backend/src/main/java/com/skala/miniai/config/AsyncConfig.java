package com.skala.miniai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 작업을 요청 스레드와 분리한다 (AI-Ready 원칙 3).
 *
 * <p>{@code AiJobRunner} 가 {@code @Async} + {@code AFTER_COMMIT} 으로 도는 데 필요하다.
 * 실행기는 따로 만들지 않는다 — {@code spring.threads.virtual.enabled=true} 라
 * Boot 가 가상 스레드 실행기를 기본으로 붙인다 (Java 21).
 *
 * <p><b>가상 스레드가 비동기를 만드는 것은 아니다.</b> 상태를 {@code ai_jobs} 에 두고
 * 폴링으로 읽는 구조가 비동기이고, 이것은 그 구조를 요청과 떼어 놓는 장치일 뿐이다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
