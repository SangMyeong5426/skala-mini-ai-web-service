package com.skala.miniai.domain.itinerary;

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

/** 06 엔드포인트 19~22 — 여행 일정 (S-11). */
@RestController
@RequestMapping("/api/trips/{tripId}/itineraries")
public class ItineraryController {

    private final ItineraryService service;

    public ItineraryController(ItineraryService service) {
        this.service = service;
    }

    @GetMapping
    public ItineraryDtos.ListResponse list(@PathVariable Long tripId) {
        return service.list(tripId);
    }

    @PostMapping
    public ResponseEntity<ItineraryDtos.Item> create(@PathVariable Long tripId,
                                                     @Valid @RequestBody ItineraryDtos.CreateRequest req) {
        ItineraryDtos.Item created = service.create(tripId, req);
        return ResponseEntity
                .created(URI.create("/api/trips/" + tripId + "/itineraries/" + created.itineraryId()))
                .body(created);
    }

    @PatchMapping("/{itineraryId}")
    public ItineraryDtos.Item update(@PathVariable Long tripId, @PathVariable Long itineraryId,
                                     @RequestBody ItineraryDtos.UpdateRequest req) {
        return service.update(tripId, itineraryId, req);
    }

    @DeleteMapping("/{itineraryId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId, @PathVariable Long itineraryId) {
        service.delete(tripId, itineraryId);
        return ResponseEntity.noContent().build();
    }
}
