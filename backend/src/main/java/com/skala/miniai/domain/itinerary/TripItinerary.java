package com.skala.miniai.domain.itinerary;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.skala.miniai.common.Codes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 여행 일정 한 줄 (S-11 · 캘린더).
 *
 * <p>항공편·숙소·관광을 <b>한 테이블</b>에 두고 {@code kind} 로 구분한다.
 * 화면이 이것들을 시간순 한 줄로 섞어 보여주기 때문이다 — 나누면 조회마다 UNION 이 필요하다.
 *
 * <p><b>목적지는 여기 없다.</b> {@code trips.destination} 에만 있다. 일정마다 다시 적으면
 * 여행을 고쳤을 때 일정이 옛 목적지를 가리킨다 (이행 종속, docs/05-erd.md 정규화 검토).
 */
@Entity
@Table(name = "trip_itineraries")
public class TripItinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.ItineraryKind kind;

    @Column(nullable = false, length = 100)
    private String title;

    /** 공항·호텔·장소 이름. 지도 좌표는 두지 않는다 (범위 밖). */
    @Column(length = 100)
    private String place;

    /** 항공편명(KE703)처럼 종류마다 다른 짧은 식별자. */
    @Column(length = 50)
    private String code;

    /** {@code TIMESTAMPTZ} → {@code OffsetDateTime}. 06 이 ISO 8601 UTC 를 계약으로 못박았다. */
    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    /** 끝나는 시각을 모르는 일정이 많다(체크인 등). nullable 이다. */
    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TripItinerary() { }

    public TripItinerary(Long tripId, Codes.ItineraryKind kind, String title, OffsetDateTime startAt) {
        this.tripId = tripId;
        this.kind = kind;
        this.title = title;
        this.startAt = startAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Codes.ItineraryKind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getPlace() { return place; }
    public String getCode() { return code; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setKind(Codes.ItineraryKind v) { this.kind = v; }
    public void setTitle(String v) { this.title = v; }
    public void setPlace(String v) { this.place = v; }
    public void setCode(String v) { this.code = v; }
    public void setStartAt(OffsetDateTime v) { this.startAt = v; }
    public void setEndAt(OffsetDateTime v) { this.endAt = v; }
    public void setNote(String v) { this.note = v; }
}
