package com.skala.miniai.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 06-api-spec.md "오류 응답 형식" — 모든 오류가 같은 모양이다.
 *
 * <pre>{ "error": { "code": "...", "message": "...", "field": "..." } }</pre>
 *
 * <p>FE 가 오류 처리 코드를 한 번만 쓰면 되도록 봉투를 통일한다.
 * {@code field} 는 값이 있을 때만 직렬화한다.
 */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, String field) { }

    public static ErrorResponse of(String code, String message, String field) {
        return new ErrorResponse(new Body(code, message, field));
    }
}
