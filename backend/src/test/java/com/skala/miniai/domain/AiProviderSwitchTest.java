package com.skala.miniai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.skala.miniai.domain.ai.AiClient;
import com.skala.miniai.domain.ai.MockAiClient;
import com.skala.miniai.domain.ai.OpenAiClient;

/**
 * 07 이 발표 지점으로 꼽은 <b>"환경 변수 한 줄로 AI를 켜고 끈다"</b> 를 실제로 확인한다.
 *
 * <p>{@link AiClient} 를 주입받는 {@code AiJobRunner} 는 어느 구현이 오는지 모른다.
 * 그 선택이 {@code AI_PROVIDER} 하나로 뒤집히는지가 이 설계의 전부라, 눈으로 보지 않고 여기서 막는다.
 *
 * <p>네트워크는 타지 않는다. 키·모델은 형식만 갖춘 값이고, 실제 호출은 작업을 돌릴 때만 나간다.
 */
class AiProviderSwitchTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class 기본값 {

        @Autowired AiClient aiClient;

        /** 기본은 mock 이다. 발표 데모가 네트워크에 묶이지 않아야 한다 (AGENTS.md). */
        @Test
        void AI_PROVIDER_를_주지_않으면_Mock_이_온다() {
            assertThat(aiClient).isInstanceOf(MockAiClient.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "app.ai.provider=openai",
            "app.ai.model=gpt-4o",
            "app.ai.api-key=sk-test-not-a-real-key"
    })
    @ActiveProfiles("test")
    class openai {

        @Autowired AiClient aiClient;

        @Test
        void AI_PROVIDER_openai_면_실제_클라이언트가_Mock_을_밀어낸다() {
            assertThat(aiClient).isInstanceOf(OpenAiClient.class);
            assertThat(aiClient.modelName()).isEqualTo("gpt-4o");
        }
    }
}
