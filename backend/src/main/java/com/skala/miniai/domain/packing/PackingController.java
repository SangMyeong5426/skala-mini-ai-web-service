package com.skala.miniai.domain.packing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 06 엔드포인트 28~30 — 3D 가방 정리 (S-12).
 *
 * <p>{@code PUT} 인 이유는 <b>전체 교체</b>이기 때문이다. 부분 갱신이면 {@code PATCH} 였겠지만,
 * 배치는 "지금 화면의 상태 전부" 를 저장하는 동작이다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/packing-layout")
public class PackingController {

    private final PackingService service;

    public PackingController(PackingService service) {
        this.service = service;
    }

    @GetMapping
    public PackingDtos.Response layout(@PathVariable Long tripId) {
        return service.layout(tripId);
    }

    @PutMapping
    public PackingDtos.Response save(@PathVariable Long tripId,
                                     @Valid @RequestBody PackingDtos.SaveRequest req) {
        return service.save(tripId, req);
    }

    /** 정리 초기화. 배치만 지우므로 {@code 204} 다. */
    @DeleteMapping
    public ResponseEntity<Void> reset(@PathVariable Long tripId) {
        service.reset(tripId);
        return ResponseEntity.noContent().build();
    }
}
