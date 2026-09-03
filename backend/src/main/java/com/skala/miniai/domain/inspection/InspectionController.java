package com.skala.miniai.domain.inspection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 06 엔드포인트 15 — 검수 결과 (S-06).
 *
 * <p>경로가 단수({@code /inspection})인 이유는 자원 <b>목록</b>이 아니라 여행 하나의
 * <b>집계 결과</b>이기 때문이다 (06 의 경로 규약 예외).
 */
@RestController
public class InspectionController {

    private final InspectionService service;

    public InspectionController(InspectionService service) {
        this.service = service;
    }

    @GetMapping("/api/trips/{tripId}/inspection")
    public InspectionDtos.Response inspection(@PathVariable Long tripId) {
        return service.of(tripId);
    }
}
