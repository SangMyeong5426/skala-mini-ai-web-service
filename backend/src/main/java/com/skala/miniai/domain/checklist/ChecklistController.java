package com.skala.miniai.domain.checklist;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 06 엔드포인트 6~9.
 *
 * <p>{@code POST} 가 <b>두 가지 코드</b>를 낸다 — 새로 만들면 {@code 201 + Location},
 * 이미 채택했거나 같은 이름의 항목에 연결만 하면 {@code 200} 이다. 06 의 재시도 규약이다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/items")
public class ChecklistController {

    private final ChecklistService service;

    public ChecklistController(ChecklistService service) {
        this.service = service;
    }

    @GetMapping
    public ChecklistDtos.ListResponse list(@PathVariable Long tripId) {
        return service.list(tripId);
    }

    @PostMapping
    public ResponseEntity<ChecklistDtos.Item> add(@PathVariable Long tripId,
                                                  @Valid @RequestBody ChecklistDtos.CreateRequest req) {
        ChecklistService.Added added = service.add(tripId, req);
        if (!added.created()) {
            return ResponseEntity.ok(added.item());
        }
        return ResponseEntity
                .created(URI.create("/api/trips/" + tripId + "/items/" + added.item().itemId()))
                .body(added.item());
    }

    @PatchMapping("/{itemId}")
    public ChecklistDtos.Item update(@PathVariable Long tripId, @PathVariable Long itemId,
                                     @RequestBody ChecklistDtos.UpdateRequest req) {
        return service.update(tripId, itemId, req);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId, @PathVariable Long itemId) {
        service.delete(tripId, itemId);
        return ResponseEntity.noContent().build();
    }
}
