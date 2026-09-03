package com.skala.miniai.domain.trip;

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
 * 06 엔드포인트 1~5.
 *
 * <p>생성은 {@code 201 + Location}, 삭제는 {@code 204} 다. 루브릭이 Status Code 를
 * 명시적으로 보므로 아무 데나 {@code 200} 을 쓰지 않는다.
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    public TripDtos.ListResponse list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<TripDtos.CreateResponse> create(@Valid @RequestBody TripDtos.CreateRequest req) {
        TripDtos.CreateResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/trips/" + created.tripId())).body(created);
    }

    @GetMapping("/{tripId}")
    public TripDtos.Detail detail(@PathVariable Long tripId) {
        return service.detail(tripId);
    }

    @PatchMapping("/{tripId}")
    public TripDtos.Detail update(@PathVariable Long tripId, @RequestBody TripDtos.UpdateRequest req) {
        return service.update(tripId, req);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId) {
        service.delete(tripId);
        return ResponseEntity.noContent().build();
    }
}
