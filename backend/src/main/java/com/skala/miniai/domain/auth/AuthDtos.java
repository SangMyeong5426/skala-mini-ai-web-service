package com.skala.miniai.domain.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입·로그인·세션 (UC-01).
 *
 * <p><b>비밀번호는 어떤 응답에도 담기지 않는다.</b> 요청 record 에만 있고 그 값은
 * 해시로 바뀐 뒤 버려진다. {@code toString} 이 로그에 찍히지 않도록 응답에는 넣지 않는다.
 */
public final class AuthDtos {

    private AuthDtos() { }

    /** 06: 가입은 <b>4개 필드만</b> 받는다. 비밀번호 확인·추가 프로필·이메일 인증은 없다. */
    public record SignUpRequest(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 50, message = "닉네임은 2~50자입니다.") String nickname,

            @NotBlank(message = "아이디는 필수입니다.")
            @Size(max = 60, message = "아이디는 영문·숫자·밑줄 4~30자입니다.")
            @Pattern(regexp = "\\s*[A-Za-z0-9_]{4,30}\\s*",
                     message = "아이디는 영문·숫자·밑줄 4~30자입니다.") String loginId,

            // 최소 길이만 검증한다. 최대는 BCrypt 의 72바이트 한계라 서비스에서 바이트로 센다.
            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(min = 8, message = "비밀번호는 8자 이상입니다.") String password,

            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 아닙니다.")
            @Size(max = 255) String email) { }

    /** 06: 로그인은 <b>2개 필드</b>. 닉네임·이메일로 로그인하지 않는다. */
    public record LoginRequest(
            @NotBlank(message = "아이디는 필수입니다.")
            @Size(max = 60, message = "아이디는 영문·숫자·밑줄 4~30자입니다.")
            @Pattern(regexp = "\\s*[A-Za-z0-9_]{4,30}\\s*",
                     message = "아이디는 영문·숫자·밑줄 4~30자입니다.") String loginId,
            @NotBlank(message = "비밀번호는 필수입니다.") String password) { }

    public record Me(Long userId, String loginId, String nickname, String email) { }

    public record UserResponse(Me user) { }

    /**
     * 06: {@code csrfToken} 은 <b>항상 문자열</b>이다. 로그인 전에도 값이 있어야
     * 가입 요청이 CSRF 검증을 통과한다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SessionResponse(boolean authenticated, Me user, String csrfToken) { }
}
