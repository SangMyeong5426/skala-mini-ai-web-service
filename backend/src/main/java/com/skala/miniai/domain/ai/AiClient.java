package com.skala.miniai.domain.ai;

import tools.jackson.databind.JsonNode;
import com.skala.miniai.common.Codes;

/**
 * AI 확장 지점. <b>여기가 나중에 실제 LLM·비전 모델로 바뀌는 자리</b>다.
 *
 * <p>인터페이스를 두는 이유는 {@link MockAiClient} 를 {@code RealAiClient} 로 갈아 끼울 때
 * 호출하는 쪽(서비스·컨트롤러·DB·화면)이 하나도 바뀌지 않게 하기 위해서다 (AI-Ready 원칙 1).
 *
 * <p>입출력 모양은 {@code docs/07-ai-ready.md} 의 JSON Schema 가 정본이다.
 */
public interface AiClient {

    /** 07 의 input 을 받아 output 을 돌려준다. 실패하면 예외를 던진다 — 작업은 FAILED 가 된다. */
    JsonNode run(Codes.JobType jobType, JsonNode input);

    /** {@code ai_jobs.model_name} 에 남길 이름. 코드에 쓰지 않고 설정에서 읽는다. */
    String modelName();
}
