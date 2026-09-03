package com.skala.miniai.domain.photo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.domain.trip.TripService;

/**
 * 짐 사진 업로드·조회·삭제 (UC-03).
 *
 * <p>파일 이름을 <b>사용자가 준 그대로 쓰지 않는다.</b> {@code ../} 가 섞이면 업로드 디렉터리
 * 밖에 쓰게 되고, 같은 이름이 오면 앞 사진을 덮는다. UUID 로 새로 짓고 확장자만 물려받는다.
 */
@Service
public class PhotoService {

    /** 데모에서 다루는 형식만 받는다. 못 여는 파일을 저장해 두면 S-04 에서야 실패한다. */
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    private final TripPhotoRepository photos;
    private final DetectedObjectRepository detections;
    private final TripService tripService;
    private final Path uploadDir;

    public PhotoService(TripPhotoRepository photos, DetectedObjectRepository detections,
                        TripService tripService, @Value("${app.upload.dir}") String uploadDir) {
        this.photos = photos;
        this.detections = detections;
        this.tripService = tripService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public PhotoDtos.PhotoListResponse list(Long tripId) {
        tripService.mustOwn(tripId);
        return new PhotoDtos.PhotoListResponse(
                photos.findByTripIdOrderById(tripId).stream().map(PhotoService::toDto).toList());
    }

    @Transactional
    public PhotoDtos.PhotoListResponse upload(Long tripId, List<MultipartFile> files, Codes.BagKind bagKind) {
        tripService.mustOwnForUpdate(tripId);
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("사진 파일이 없습니다.", "files");
        }

        // 검증을 **전부 먼저** 돌린다. 검사와 쓰기를 번갈아 하면 3장 중 3번째가 틀렸을 때
        // 앞의 2장이 이미 디스크에 쓰인 뒤다. 트랜잭션이 되돌아가도 파일은 남아 고아가 된다.
        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw ApiException.badRequest("빈 파일은 올릴 수 없습니다.", "files");
            }
            String ext = extensionOf(file.getOriginalFilename());
            if (!ALLOWED.contains(ext)) {
                throw ApiException.badRequest(
                        "지원하지 않는 형식입니다: " + ext + " (jpg · png · webp 만 됩니다)", "files");
            }
            // 여행별 폴더에 UUID 이름으로 저장한다. 원본 이름은 경로 조작에 쓰일 수 있어 버린다.
            paths.add("trips/" + tripId + "/" + UUID.randomUUID() + "." + ext);
        }

        List<PhotoDtos.Photo> saved = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            writeFile(files.get(i), paths.get(i));
            saved.add(toDto(photos.save(new TripPhoto(tripId, paths.get(i), bagKind))));
        }
        return new PhotoDtos.PhotoListResponse(saved);
    }

    @Transactional
    public void delete(Long tripId, Long photoId) {
        // 사진을 지우면 인식 결과가 CASCADE 로 함께 지워져 내 목록 집계가 바뀐다.
        tripService.mustOwnForUpdate(tripId);
        TripPhoto photo = photos.findByIdAndTripId(photoId, tripId)
                .orElseThrow(() -> ApiException.notFound("사진", photoId));

        // 인식 결과는 FK 의 CASCADE 로 함께 지워진다. 파일은 남기지 않는다.
        deleteFileQuietly(photo.getFilePath());
        photos.delete(photo);
    }

    @Transactional(readOnly = true)
    public List<DetectedObject> detectionsOf(Long tripId) {
        List<Long> photoIds = photos.findByTripIdOrderById(tripId).stream().map(TripPhoto::getId).toList();
        return photoIds.isEmpty() ? List.of() : detections.findByPhotoIdInOrderById(photoIds);
    }

    static PhotoDtos.Photo toDto(TripPhoto p) {
        return new PhotoDtos.Photo(p.getId(), "/uploads/" + p.getFilePath(), p.getBagKind(), p.getUploadedAt());
    }

    private void writeFile(MultipartFile file, String relative) {
        try {
            Path target = uploadDir.resolve(relative).normalize();
            // resolve 뒤에 한 번 더 확인한다. 상대 경로를 우리가 만들었어도 검사는 남긴다.
            if (!target.startsWith(uploadDir)) {
                throw ApiException.badRequest("저장 경로가 올바르지 않습니다.", "files");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED",
                    "사진을 저장하지 못했습니다. 다시 시도해 주세요.", null);
        }
    }

    private void deleteFileQuietly(String relative) {
        try {
            Path target = uploadDir.resolve(relative).normalize();
            if (target.startsWith(uploadDir)) Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 파일이 없어도 DB 행은 지운다. 시드 사진처럼 저장소가 관리하는 파일도 있다.
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
