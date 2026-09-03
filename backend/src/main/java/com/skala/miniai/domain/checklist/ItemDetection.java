package com.skala.miniai.domain.checklist;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * ★ N:M 조인 1 — 체크리스트 항목 ↔ 인식 물품.
 *
 * <p><b>연결 자체가 정보를 갖는다</b> — 신뢰도와 사용자 승인 여부. 그래서 조인 테이블에
 * 속성이 있다. 단순 매핑이었다면 FK 두 개로 끝났을 것이다 (docs/05-erd.md).
 *
 * <p>{@code NUMERIC(4,3)} 은 {@code BigDecimal} 이어야 한다. {@code double} 로 두면
 * {@code found [numeric], but expecting [float(53)]} 로 기동이 막힌다.
 */
@Entity
@Table(name = "item_detections")
@IdClass(ItemDetectionId.class)
public class ItemDetection {

    @Id
    @Column(name = "checklist_item_id")
    private Long checklistItemId;

    @Id
    @Column(name = "detected_object_id")
    private Long detectedObjectId;

    @Column(name = "match_confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal matchConfidence;

    /** AI 가 제안한 연결과 <b>사람이 승인한</b> 연결을 구분한다. photoStatus 계산의 기준이다. */
    @Column(name = "confirmed_by_user", nullable = false)
    private boolean confirmedByUser;

    @Column(name = "matched_at", nullable = false)
    private OffsetDateTime matchedAt;

    protected ItemDetection() { }

    public ItemDetection(Long checklistItemId, Long detectedObjectId,
                         BigDecimal matchConfidence, boolean confirmedByUser) {
        this.checklistItemId = checklistItemId;
        this.detectedObjectId = detectedObjectId;
        this.matchConfidence = matchConfidence;
        this.confirmedByUser = confirmedByUser;
    }

    @PrePersist
    void onCreate() {
        if (matchedAt == null) matchedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getChecklistItemId() { return checklistItemId; }
    public Long getDetectedObjectId() { return detectedObjectId; }
    public BigDecimal getMatchConfidence() { return matchConfidence; }
    public boolean isConfirmedByUser() { return confirmedByUser; }

    public void setConfirmedByUser(boolean v) { this.confirmedByUser = v; }
    public void setMatchConfidence(BigDecimal v) { this.matchConfidence = v; }
}
