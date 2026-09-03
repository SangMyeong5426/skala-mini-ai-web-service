package com.skala.miniai.domain.photo;

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
 * 짐 사진 (UC-03).
 *
 * <p>{@code filePath} 는 {@code UPLOAD_DIR} 기준 <b>상대 경로</b>다. 절대 경로를 저장하면
 * 개발자 PC 마다 값이 달라져 시드가 못 쓰게 된다. API 는 {@code /uploads/} 를 붙여 URL 로 준다.
 */
@Entity
@Table(name = "trip_photos")
public class TripPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "bag_kind", length = 20)
    private Codes.BagKind bagKind;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    protected TripPhoto() { }

    public TripPhoto(Long tripId, String filePath, Codes.BagKind bagKind) {
        this.tripId = tripId;
        this.filePath = filePath;
        this.bagKind = bagKind;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public String getFilePath() { return filePath; }
    public Codes.BagKind getBagKind() { return bagKind; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
}
