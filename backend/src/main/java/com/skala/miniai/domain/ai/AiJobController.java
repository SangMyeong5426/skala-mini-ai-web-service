package com.skala.miniai.domain.ai;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 06 엔드포인트 17~18 — AI 확장 지점.
 *
 * <p><b>엔드포인트는 둘뿐이다.</b> AI 작업이 늘어도 {@code jobType} 값만 늘고 경로는 그대로다
 * (ADR 0003).
 *
 * <p>접수는 {@code 202}, 조회는 {@code 200} 이다. 아직 {@code PENDING} 이어도 조회 자체는
 * 성공했으므로 {@code 202} 가 아니다 — 본문의 {@code status} 로 구분한다.
 */
@RestController
@RequestMapping("/api/ai-jobs")
public class AiJobController {

    private final AiJobService service;

    public AiJobController(AiJobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AiJobDtos.Accepted> create(@Valid @RequestBody AiJobDtos.CreateRequest req) {
        AiJobDtos.Accepted accepted = service.create(req);
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/ai-jobs/" + accepted.jobId()))
                .body(accepted);
    }

    @GetMapping("/{jobId}")
    public AiJobDtos.Status status(@PathVariable Long jobId) {
        return service.status(jobId);
    }
}
