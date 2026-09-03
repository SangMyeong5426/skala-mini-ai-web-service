package com.skala.miniai.domain.photo;

import java.math.BigDecimal;
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
 * 사진에서 인식된 물품 (UC-04).
 *
 * <p>명세 9.2 수용 기준: <b>"사진 분석 결과는 사용자가 승인하기 전 최종 준비 상태에
 * 반영되지 않아야 한다"</b>. 그래서 {@code approved} 가 기본 {@code false} 이고,
 * 검수·무게·규정 계산은 승인된 것만 본다.
 *
 * <p>{@code confidenceLevel} 을 컬럼으로 두는 이유는 경계값이 바뀌어도 <b>사용자가 그때 보고
 * 승인한 표시</b>가 그대로여야 하기 때문이다 (docs/05-erd.md).
 */
@Entity
@Table(name = "detected_objects")
public class DetectedObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer qty;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 10)
    private Codes.ConfidenceLevel confidenceLevel;

    /** 보이지 않아 못 정한 속성. 예: 용량(ml) · 배터리 정격(Wh) · 날 길이(cm). */
    @Column(name = "missing_info", length = 100)
    private String missingInfo;

    /** 라벨·포장에서 읽힌 글자 원문(OCR). 서버는 파싱하지 않는다. */
    @Column(name = "label_text", length = 200)
    private String labelText;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DetectedObject() { }

    public DetectedObject(Long photoId, String name, Integer qty, BigDecimal confidence,
                          Codes.ConfidenceLevel confidenceLevel, String missingInfo, String labelText) {
        this.photoId = photoId;
        this.name = name;
        this.qty = qty;
        this.confidence = confidence;
        this.confidenceLevel = confidenceLevel;
        this.missingInfo = missingInfo;
        this.labelText = labelText;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (qty == null) qty = 1;
    }

    /** 07 규약: 서버가 {@code confidence} 로 채운다. 모델이 낸 값이 있어도 덮어쓴다. */
    public static Codes.ConfidenceLevel levelOf(BigDecimal confidence) {
        if (confidence.compareTo(new BigDecimal("0.80")) >= 0) return Codes.ConfidenceLevel.HIGH;
        if (confidence.compareTo(new BigDecimal("0.50")) >= 0) return Codes.ConfidenceLevel.MEDIUM;
        return Codes.ConfidenceLevel.LOW;
    }

    public Long getId() { return id; }
    public Long getPhotoId() { return photoId; }
    public String getName() { return name; }
    public Integer getQty() { return qty; }
    public BigDecimal getConfidence() { return confidence; }
    public Codes.ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
    public String getMissingInfo() { return missingInfo; }
    public String getLabelText() { return labelText; }
    public boolean isApproved() { return approved; }

    public void setName(String v) { this.name = v; }
    public void setQty(Integer v) { this.qty = v; }
    public void setApproved(boolean v) { this.approved = v; }
}
