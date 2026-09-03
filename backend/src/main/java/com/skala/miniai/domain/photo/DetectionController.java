package com.skala.miniai.domain.photo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 06 엔드포인트 13~14 — 인식 결과·승인 (S-04).
 *
 * <p><b>14번이 이 서비스의 핵심 게이트다.</b> 승인 전에는 어떤 결과도 준비 상태·무게·규정에
 * 반영되지 않는다.
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
    public PhotoDtos.ApproveResponse approve(@PathVariable Long tripId, @PathVariable Long detectionId,
                                             @RequestBody PhotoDtos.ApproveRequest req) {
        return service.approve(tripId, detectionId, req);
    }
}
