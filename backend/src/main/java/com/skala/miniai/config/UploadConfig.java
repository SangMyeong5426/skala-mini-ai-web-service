package com.skala.miniai.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 업로드 디렉터리 위치. 저장 경로는 코드가 아니라 {@code UPLOAD_DIR} 환경 변수에서 읽는다.
 *
 * <p>사진을 클라우드가 아니라 로컬에 두는 이유는 데모다 — 인터넷이 끊겨도 시연이 되어야 한다.
 *
 * <p><b>정적 리소스 핸들러를 두지 않는다.</b> 예전에는 {@code /uploads/**} 를 그대로 열어
 * 두었지만, 로그인 필수 개정에서 06 이 "경로만 아는 다른 회원에게도 파일을 주지 않는다" 고
 * 못박았다. 지금은 소유권을 확인하는 {@code PhotoFileController} 가 그 경로를 처리한다.
 */
@Configuration
public class UploadConfig {

    private final Path uploadDir;

    public UploadConfig(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Path dir() {
        return uploadDir;
    }
}
