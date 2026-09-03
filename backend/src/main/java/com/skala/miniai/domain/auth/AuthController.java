package com.skala.miniai.domain.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skala.miniai.common.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * 06 엔드포인트 19~22 — 회원가입·로그인·세션·로그아웃 (UC-01 · 화면 S-00).
 *
 * <p><b>가입은 세션을 만들지 않는다.</b> 06 이 "가입만으로 인증 세션을 만들지 않으며
 * S-00 로그인 모드로 이동한다" 고 정했다. 자동 로그인은 「하지 않을 것」이다(01).
 *
 * <p>로그인 성공 시 <b>세션 ID 를 교체</b>한다. 로그인 전 익명 세션의 ID 를 그대로 쓰면
 * 세션 고정 공격에 노출된다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthService authService, CurrentUser currentUser,
                          SecurityContextRepository contextRepository) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.UserResponse> signUp(@Valid @RequestBody AuthDtos.SignUpRequest req) {
        AuthDtos.Me me = authService.signUp(req);
        // 06: 회원 상세 공개 API 가 없으므로 Location 을 생략한다.
        return ResponseEntity.status(201)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.UserResponse(me));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.UserResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        AuthDtos.Me me = authService.authenticate(req);

        // 세션 고정 방어 — 기존 익명 세션을 버리고 새로 만든다.
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true);

        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                me.userId(), null, java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.UserResponse(me));
    }

    /**
     * 앱 진입과 로그인·로그아웃 성공 후 호출한다. <b>미인증도 200</b> 이고 상태만 알려준다 —
     * 여기서 401 을 내면 로그인 화면이 CSRF 토큰을 못 받아 가입조차 못 한다.
     */
    @GetMapping("/session")
    public ResponseEntity<AuthDtos.SessionResponse> session(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String token = csrf == null ? "" : csrf.getToken();

        Long userId = currentUser.idOrNull();
        AuthDtos.Me me = userId == null ? null : authService.find(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.SessionResponse(me != null, me, token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
