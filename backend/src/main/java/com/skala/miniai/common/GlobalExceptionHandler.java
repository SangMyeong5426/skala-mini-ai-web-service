package com.skala.miniai.common;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 06 의 오류 봉투로 바꾼다. 컨트롤러마다 try/catch 를 쓰지 않는다.
 *
 * <p>업로드 한도 초과만 {@code 413} 이다 — 06 의 사진 업로드 표가 그렇게 정했다.
 * 나머지 검증 실패는 {@code 400 VALIDATION_FAILED} 로 모은다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), e.getMessage(), e.getField()));
    }

    /** {@code @Valid} 실패. 첫 번째 위반 필드를 알려 준다 — 06 의 {@code field} 칸이다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException e) {
        var first = e.getBindingResult().getFieldErrors().stream().findFirst();
        String field = first.map(f -> f.getField()).orElse(null);
        String message = first.map(f -> f.getDefaultMessage()).orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", message, field));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "VALIDATION_FAILED", e.getParameterName() + " 은(는) 필수입니다.", e.getParameterName()));
    }

    /** 날짜·enum·경로 ID를 변환하지 못한 요청은 서버 오류가 아니라 입력 오류다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "VALIDATION_FAILED", e.getName() + " 값 형식을 확인해 주세요.", e.getName()));
    }

    /** 본문이 JSON 이 아니거나 enum 값이 틀렸을 때. 원문 메시지는 내부 구조를 드러내므로 감춘다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "VALIDATION_FAILED", "요청 본문을 읽을 수 없습니다. 필드 이름과 값 형식을 확인해 주세요.", null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ErrorResponse.of(
                "PAYLOAD_TOO_LARGE", "요청 크기가 한도를 넘었습니다. 사진 수를 줄여 다시 올려 주세요.", null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuch(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "찾을 수 없습니다.", null));
    }

    /**
     * DB 제약 위반. {@code schema.sql} 의 CHECK·UNIQUE·FK 가 잡아낸 것이라 요청 값 문제다.
     * 원문에는 테이블·제약 이름이 들어 있어 그대로 내보내지 않는다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 제약 위반", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                "CONSTRAINT_VIOLATION", "요청 값이 저장 규칙과 맞지 않습니다. 입력을 확인해 주세요.", null));
    }

    /**
     * <b>최후 폴백.</b> 없으면 예상 못 한 예외가 Spring 기본 형식
     * {@code {"timestamp","status","error","path"}} 으로 나간다 — 06 의 오류 봉투가 아니다.
     *
     * <p>프런트엔드는 {@code body.error.code} 를 읽는데, 하필 문제가 생겼을 때만 그 필드가
     * 없으면 오류 화면조차 못 그린다. 원인은 로그에 남기고 사용자에게는 감춘다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                "INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", null));
    }
}
