package com.skala.miniai.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 업로드된 짐 사진을 {@code /uploads/**} 로 내보낸다.
 *
 * <p>사진을 클라우드가 아니라 로컬 디렉터리에 두는 이유는 데모다. 3일차 시연에서
 * 인터넷이 끊겨도 화면이 떠야 한다. 저장 경로는 코드가 아니라 {@code UPLOAD_DIR}
 * 환경 변수에서 읽으므로, 나중에 S3나 Supabase Storage로 옮길 때 이 클래스만 바꾼다.
 *
 * <p><b>한계.</b> 이 디렉터리는 인증 없이 열려 있다. 3일짜리 데모용이라 그대로 두지만,
 * 실제 서비스라면 소유자 확인을 거치는 컨트롤러로 바꿔야 한다. 명세 9절의
 * "사용자 사진은 인증된 사용자만 조회할 수 있어야 한다"가 여기에 해당하고,
 * 발표 5번 섹션(한계와 향후 계획)에 적을 거리다.
 */
@Configuration
public class UploadConfig implements WebMvcConfigurer {

    private final Path uploadDir;

    public UploadConfig(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir.toUri().toString());
    }
}
