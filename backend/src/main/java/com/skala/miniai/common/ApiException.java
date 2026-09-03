package com.skala.miniai.common;

import org.springframework.http.HttpStatus;

/**
 * 06-api-spec.md 의 오류 봉투를 그대로 던지기 위한 예외.
 *
 * <p>상태 코드와 {@code code} 를 함께 들고 다닌다. 루브릭이 Status Code 준수를
 * 명시적으로 보기 때문에, 아무 데나 500 이 나가지 않도록 도메인에서 직접 정한다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;

    public ApiException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getField() { return field; }

    public static ApiException notFound(String what, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + "을(를) 찾을 수 없습니다: " + id, null);
    }

    public static ApiException badRequest(String message, String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, field);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, null);
    }
}
