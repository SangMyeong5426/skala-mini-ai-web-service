package com.skala.miniai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 로그인 필수 (docs/06-api-spec.md 「회원가입·로그인 계약」).
 *
 * <p>정한 것과 정하지 않은 것을 분명히 한다.
 * <ul>
 *   <li><b>서버 세션</b>({@code HttpSession})을 쓴다. JWT·Redis·Refresh Token 을 넣지 않는다.
 *       단일 백엔드의 메모리에 두고 재시작하면 다시 로그인한다.
 *   <li><b>401 은 JSON</b> 이다. 서버가 HTML 로그인 페이지로 리다이렉트하면 FE 가
 *       "인증 실패" 와 "서버 오류" 를 구분하지 못한다.
 *   <li><b>CSRF 를 끄지 않는다.</b> 쿠키 세션이라 끄면 남의 사이트에서 요청이 성립한다.
 *       토큰은 {@code GET /api/auth/session} 이 주고 FE 가 {@code X-CSRF-TOKEN} 으로 되돌린다.
 * </ul>
 *
 * <p>열어 두는 경로는 <b>넷뿐</b>이다 — 가입·로그인·세션 조회·Swagger.
 * {@code /uploads/**} 는 열지 않는다. 06 이 "경로만 아는 다른 회원에게도 파일을 주지 않는다"
 * 고 못박아서, 소유권까지 확인하는 컨트롤러가 대신 처리한다({@code PhotoFileController}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean secureCookie;

    public SecurityConfig(@Value("${app.session.secure-cookie:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** {@code AuthController} 가 로그인 성공 시 인증 정보를 세션에 직접 저장하는 데 쓴다. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // FE 가 JS 로 읽을 수 있어야 X-CSRF-TOKEN 으로 되돌릴 수 있다.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // 로컬 HTTP 개발에서는 Secure 를 끈다. 켜면 브라우저가 쿠키를 아예 안 보낸다.
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite("Lax").path("/"));
        // CookieCsrfTokenRepository 의 기본 헤더는 X-XSRF-TOKEN 이다.
        // 06 이 X-CSRF-TOKEN 을 계약으로 못박았으므로 맞춘다 — 안 맞추면 모든 쓰기 요청이 403 이다.
        csrfRepository.setHeaderName("X-CSRF-TOKEN");

        http
            // CorsConfig 의 CorsConfigurationSource 빈을 쓴다. 이게 없으면 preflight 가 401 이다.
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepository)
                    // 토큰을 지연 없이 요청 속성에 올려 둔다. session 조회가 곧바로 읽는다.
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                    // Swagger 는 설계 산출물이라 열어 둔다. 사용자 자료를 담지 않는다.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write(
                                "{\"error\":{\"code\":\"AUTH_REQUIRED\",\"message\":\"로그인이 필요합니다.\"}}");
                    })
                    .accessDeniedHandler((request, response, deniedException) -> {
                        // CSRF 실패가 여기로 온다. FE 는 세션을 다시 조회해 토큰을 새로 받는다.
                        response.setStatus(403);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write(
                                "{\"error\":{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 다시 시도해 주세요.\"}}");
                    }))
            // 로그인·로그아웃은 AuthController 가 직접 다룬다. 기본 폼·기본 로그아웃을 쓰지 않는다.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
