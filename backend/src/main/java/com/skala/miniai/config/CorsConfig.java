package com.skala.miniai.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 프런트엔드는 5173, 백엔드는 8080 에서 뜬다. 포트가 다르므로 CORS 설정이 없으면
 * FE-BE 연동이 브라우저에서 막힌다. 연동 실패의 가장 흔한 원인이다.
 *
 * <p>허용 origin 은 코드에 쓰지 않고 {@code CORS_ALLOWED_ORIGINS} 환경 변수에서 읽는다.
 *
 * <p><b>{@code WebMvcConfigurer} 가 아니라 {@code CorsConfigurationSource} 빈이다.</b>
 * 로그인 필수가 되면서 Spring Security 필터가 MVC 보다 앞에 서는데, MVC 쪽에만 CORS 를
 * 두면 <b>preflight(OPTIONS)가 인증에서 401 로 막힌다.</b> 이 빈을 두면 Security 가
 * 그것을 집어 CorsFilter 로 먼저 처리한다.
 *
 * <p>{@code allowCredentials=true} 는 세션 쿠키를 주고받기 위해서다. 그래서 origin 에
 * 와일드카드를 쓸 수 없고 지정한 값만 허용한다 — 06 의 "지정 origin 과 credentials 만 허용".
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.asList(allowedOrigins.split("\\s*,\\s*"));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 생성 응답의 주소를 React 가 읽을 수 있게 한다.
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/uploads/**", config);
        return source;
    }
}
