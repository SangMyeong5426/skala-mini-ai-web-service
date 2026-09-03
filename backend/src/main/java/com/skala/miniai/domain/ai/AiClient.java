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

    /**
     * 같은 일을 하되 <b>어느 여행인지</b>도 함께 받는다. {@link AiJobRunner} 는 이쪽을 부른다.
     *
     * <p>07 의 input 에는 {@code tripId} 가 없다. 화면이 보낸 값이 아니라 서버가 아는 값이고,
     * 스키마에 넣으면 {@code additionalProperties: false} 를 어기기 때문이다. 그런데 실제 모델을
     * 부르려면 07 의 {@code {{server:…}}} 자리를 채울 것들 — 국가 코드, 미완료까지 포함한 현재 내
     * 목록 — 을 서버가 읽어야 하고, 그러려면 여행을 알아야 한다.
     *
     * <p><b>기본 구현이 tripId 를 버리고 위 메서드로 넘긴다.</b> {@link MockAiClient} 처럼 여행을
     * 볼 필요가 없는 구현은 아무것도 고치지 않아도 된다.
     */
    default JsonNode run(Codes.JobType jobType, Long tripId, JsonNode input) {
        return run(jobType, input);
    }

    /** {@code ai_jobs.model_name} 에 남길 이름. 코드에 쓰지 않고 설정에서 읽는다. */
    String modelName();
}
