package com.skala.miniai.domain.checklist;

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
 * 내 체크리스트 항목 (UC-05 · UC-06).
 *
 * <p>개정된 계약(06)에서 이 표에 행이 생기는 경로는 <b>셋뿐</b>이다 —
 * 사진 승인, 추천 채택, 직접 추가. <b>추천 생성만으로는 INSERT 하지 않는다.</b>
 * 후보는 {@code ai_jobs.output_payload} 에 남는다 (docs/05-erd.md 저장 규약).
 *
 * <p>{@code checkStatus} 는 실제 챙김 여부만 뜻한다. 사진 상태는 별도로 계산해
 * {@code photoStatus} 로 내보낸다 — 컬럼이 아니다.
 */
@Entity
@Table(name = "checklist_items")
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    /** 무게 추정에 쓴다. 마스터에 없는 물품도 있으므로 nullable. */
    @Column(name = "item_weight_id")
    private Long itemWeightId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Category category;

    @Column(nullable = false)
    private Integer qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Priority priority;

    /** 최초 등록 경로. 이후 승인·채택이 반복돼도 <b>바꾸지 않는다</b> (06). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Codes.ItemSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_status", nullable = false, length = 20)
    private Codes.CheckStatus checkStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ChecklistItem() { }

    public ChecklistItem(Long tripId, String name, Codes.Category category, Integer qty,
                         Codes.Priority priority, Codes.ItemSource source, Codes.CheckStatus checkStatus) {
        this.tripId = tripId;
        this.name = name;
        this.category = category;
        this.qty = qty;
        this.priority = priority;
        this.source = source;
        this.checkStatus = checkStatus;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (checkStatus == null) checkStatus = Codes.CheckStatus.UNCHECKED;
        if (qty == null) qty = 1;
    }

    /** 06: {@code PREPARED} 만 실제 완료다. 나머지는 전부 미완료로 센다. */
    public boolean isPrepared() {
        return checkStatus == Codes.CheckStatus.PREPARED;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getItemWeightId() { return itemWeightId; }
    public String getName() { return name; }
    public Codes.Category getCategory() { return category; }
    public Integer getQty() { return qty; }
    public Codes.Priority getPriority() { return priority; }
    public Codes.ItemSource getSource() { return source; }
    public Codes.CheckStatus getCheckStatus() { return checkStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setItemWeightId(Long v) { this.itemWeightId = v; }
    public void setName(String v) { this.name = v; }
    public void setCategory(Codes.Category v) { this.category = v; }
    public void setQty(Integer v) { this.qty = v; }
    public void setPriority(Codes.Priority v) { this.priority = v; }
    public void setCheckStatus(Codes.CheckStatus v) { this.checkStatus = v; }
}
