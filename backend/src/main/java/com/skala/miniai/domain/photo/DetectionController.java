package com.skala.miniai.domain.photo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 06 엔드포인트 13~14 — 인식 결과·<b>사후 수정</b> (S-04).
 *
 * <p>개정 전에는 14번이 승인 게이트였다. 지금은 인식 물품이 {@code BAG_CHECK} 완료 시
 * 자동 등록되므로, 이 API 는 <b>이미 등록된 것</b>의 이름·수량·연결을 고치는 용도다.
 * 등록용 승인 버튼·요청을 FE 에 두지 않는다 (06).
 */
@RestController
@RequestMapping("/api/trips/{tripId}/detections")
public class DetectionController {

    private final DetectionService service;

    public DetectionController(DetectionService service) {
        this.service = service;
    }

    @GetMapping
    public PhotoDtos.DetectionListResponse list(@PathVariable Long tripId) {
        return service.list(tripId);
    }

    @PatchMapping("/{detectionId}")
    public PhotoDtos.PatchResponse patch(@PathVariable Long tripId, @PathVariable Long detectionId,
                                         @RequestBody PhotoDtos.PatchRequest req) {
        return service.patch(tripId, detectionId, req);
    }
}
