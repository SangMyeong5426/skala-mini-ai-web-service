package com.skala.miniai.domain.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.domain.user.User;
import com.skala.miniai.domain.user.UserRepository;

/**
 * 가입과 자격 확인 (UC-01).
 *
 * <p>세션 생성·폐기는 여기서 하지 않는다. {@code AuthController} 가 HTTP 세션을 다루고
 * 이 서비스는 <b>사용자 확인</b>만 한다 — 그래야 테스트에서 HTTP 없이 검증할 수 있다.
 */
@Service
public class AuthService {

    /** BCrypt 는 72바이트를 넘는 입력을 조용히 자른다. 자르지 않고 거부한다 (06). */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public AuthDtos.Me signUp(AuthDtos.SignUpRequest req) {
        String loginId = normalizeLoginId(req.loginId());
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String nickname = req.nickname().trim();

        if (nickname.isEmpty()) {
            throw ApiException.badRequest("닉네임은 공백일 수 없습니다.", "nickname");
        }
        if (req.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("비밀번호가 너무 깁니다. 72바이트 이하로 입력해 주세요.", "password");
        }
        // 먼저 확인하고 안내한다. 동시 가입은 DB 의 고유 제약이 최종적으로 막는다.
        if (users.existsByLoginId(loginId)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID",
                    "이미 사용 중인 아이디입니다.", "loginId");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL",
                    "이미 가입된 이메일입니다.", "email");
        }

        User saved = users.save(User.signUp(loginId, email, encoder.encode(req.password()), nickname));
        return toMe(saved);
    }

    /**
     * 06: 실패는 <b>존재하지 않는 아이디와 틀린 비밀번호를 구분하지 않는</b> {@code 401} 이다.
     * 구분하면 아이디 존재 여부를 알려주는 셈이 된다.
     */
    @Transactional(readOnly = true)
    public AuthDtos.Me authenticate(AuthDtos.LoginRequest req) {
        return users.findByLoginId(normalizeLoginId(req.loginId()))
                .filter(u -> u.matches(req.password(), encoder))
                .map(AuthService::toMe)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "아이디 또는 비밀번호를 확인해 주세요.", null));
    }

    @Transactional(readOnly = true)
    public AuthDtos.Me find(Long userId) {
        return users.findById(userId).map(AuthService::toMe)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                        "로그인이 필요합니다.", null));
    }

    /** 06: 앞뒤 공백 제거 + 소문자 정규화. 대소문자만 다른 아이디를 같은 것으로 본다. */
    private static String normalizeLoginId(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static AuthDtos.Me toMe(User u) {
        return new AuthDtos.Me(u.getId(), u.getLoginId(), u.getNickname(), u.getEmail());
    }
}
