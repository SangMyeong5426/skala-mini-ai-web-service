package com.skala.miniai.domain.ai;

/**
 * OpenAI 호출이 실패했다. {@link AiJobRunner} 가 잡아 작업을 {@code FAILED} 로 만든다.
 *
 * <p>{@code retryable} 을 들고 다니는 이유는 07 로드맵 2단계 "실패 시 재시도 1회" 때문이다.
 * 다시 부를 가치가 있는 것(429·5xx·타임아웃·스키마 위반)과 아무리 불러도 같은 것
 * (401 키 오류·400 요청 오류)을 {@link OpenAiClient} 가 구분해야 한다.
 *
 * <p>메시지에 <b>API 키를 넣지 않는다.</b> 이 메시지는 로그로 남는다.
 */
public class OpenAiException extends RuntimeException {

    private final boolean retryable;

    public OpenAiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public OpenAiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
