package com.skala.miniai.domain.photo;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.skala.miniai.common.Codes;

/**
 * 06 엔드포인트 10~12 — 짐 사진 (S-03).
 *
 * <p>{@code multipart/form-data} 다. 파트 이름은 {@code files}(복수 가능)와 {@code bagKind}.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/photos")
public class PhotoController {

    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    @GetMapping
    public PhotoDtos.PhotoListResponse list(@PathVariable Long tripId) {
        return service.list(tripId);
    }

    @PostMapping
    public ResponseEntity<PhotoDtos.PhotoListResponse> upload(
            @PathVariable Long tripId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "bagKind", required = false) Codes.BagKind bagKind) {

        PhotoDtos.PhotoListResponse saved = service.upload(tripId, files, bagKind);
        // 여러 장을 한 번에 올릴 수 있어 Location 은 첫 사진을 가리킨다.
        String location = "/api/trips/" + tripId + "/photos/" + saved.photos().get(0).photoId();
        return ResponseEntity.created(java.net.URI.create(location)).body(saved);
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId, @PathVariable Long photoId) {
        service.delete(tripId, photoId);
        return ResponseEntity.noContent().build();
    }
}
