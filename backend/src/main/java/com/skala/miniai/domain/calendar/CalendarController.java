package com.skala.miniai.domain.calendar;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 06 엔드포인트 23 — 여행 캘린더 (S-11).
 *
 * <p>{@code from}·{@code to} 는 필수다. 없으면 "언제부터 언제까지" 를 서버가 추측해야 하는데,
 * 달력이 보여주는 달은 화면이 안다.
 */
@RestController
public class CalendarController {

    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    @GetMapping("/api/calendar")
    public CalendarDtos.Response calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.between(from, to);
    }
}
