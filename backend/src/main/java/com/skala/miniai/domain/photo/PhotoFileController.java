package com.skala.miniai.domain.photo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.CurrentUser;
import com.skala.miniai.domain.trip.TripRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 짐 사진 파일 제공. <b>정적 핸들러를 대신한다.</b>
 *
 * <p>06: "기존 {@code /uploads/**} 경로도 인증·소유권을 확인하는 서버 처리로 바꿔야 한다.
 * 공개 정적 파일 핸들러를 인증 규칙에서 제외하지 않으며, <b>경로만 아는 다른 회원에게도
 * 파일을 주지 않는다.</b>"
 *
 * <p>URL 은 그대로 {@code /uploads/...} 다 — 화면·시드·문서를 고치지 않기 위해서다.
 * 바뀐 것은 이 경로를 누가 처리하느냐뿐이다.
 *
 * <p>파일용 <b>신규 API 를 추가하지 않는다</b>(06). 이것은 기존 경로의 보호이지 새 엔드포인트가 아니다.
 */
@RestController
@RequestMapping("/uploads")
public class PhotoFileController {

    private final TripPhotoRepository photos;
    private final TripRepository trips;
    private final CurrentUser currentUser;
    private final Path uploadDir;

    public PhotoFileController(TripPhotoRepository photos, TripRepository trips, CurrentUser currentUser,
                               @Value("${app.upload.dir}") String uploadDir) {
        this.photos = photos;
        this.trips = trips;
        this.currentUser = currentUser;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> file(HttpServletRequest request) {
        String relative = request.getRequestURI().substring("/uploads/".length());

        // 이 상대 경로를 가진 사진이 내 여행의 것인지 확인한다.
        // 파일 시스템을 먼저 만지지 않는다 — 존재 여부조차 알려주지 않기 위해서다.
        TripPhoto photo = photos.findByFilePath(relative)
                .filter(p -> trips.findByIdAndUserId(p.getTripId(), currentUser.id()).isPresent())
                .orElseThrow(() -> ApiException.notFound("사진", relative));

        Path target = uploadDir.resolve(photo.getFilePath()).normalize();
        if (!target.startsWith(uploadDir) || !Files.isReadable(target)) {
            throw ApiException.notFound("사진", relative);
        }

        return ResponseEntity.ok()
                // 본인 자료라 브라우저·중간 캐시에 남기지 않는다 (06).
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaTypeOf(target))
                .body(new FileSystemResource(target));
    }

    private static MediaType mediaTypeOf(Path path) {
        try {
            String type = Files.probeContentType(path);
            return type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type);
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
