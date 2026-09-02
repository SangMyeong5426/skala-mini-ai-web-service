package com.skala.miniai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프런트엔드는 5173, 백엔드는 8080에서 뜬다. 포트가 다르므로 CORS 설정이 없으면
 * 2일차 FE-BE 연동이 브라우저에서 막힌다. 연동 실패의 가장 흔한 원인이다.
 *
 * <p>허용 origin 은 코드에 쓰지 않고 {@code CORS_ALLOWED_ORIGINS} 환경 변수에서 읽는다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
