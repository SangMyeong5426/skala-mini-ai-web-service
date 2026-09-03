package com.skala.miniai.domain.packing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.skala.miniai.common.Codes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 3D 가방 정리에서 물품 하나가 놓인 자리 (S-12).
 *
 * <p>항목 하나가 가방 안 <b>한 자리</b>를 차지하므로 1:1 이다. 그래서 별도 id 없이
 * {@code checklistItemId} 가 그대로 기본키다.
 *
 * <p><b>{@code tripId} 를 두지 않는다.</b> {@code checklist_items} 를 거치면 알 수 있고,
 * 넣으면 이행 종속이 생긴다 (docs/05-erd.md 정규화 검토 3NF).
 *
 * <p>좌표는 픽셀이 아니라 <b>0~1 상대값</b>이다. 화면 크기·기기가 달라도 같은 자리에 놓이고,
 * 가방 모델을 바꿔도 배치가 살아남는다.
 */
@Entity
@Table(name = "item_placements")
public class ItemPlacement {

    /** FK 가 그대로 PK 다. {@code @GeneratedValue} 를 쓰지 않는다 — 값을 서비스가 정한다. */
    @Id
    @Column(name = "checklist_item_id")
    private Long checklistItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Compartment compartment;

    @Column(name = "pos_x", nullable = false, precision = 4, scale = 3)
    private BigDecimal posX;

    @Column(name = "pos_y", nullable = false, precision = 4, scale = 3)
    private BigDecimal posY;

    /** 깊이이자 쌓임 순서. 값이 클수록 위에 얹힌 것이다. */
    @Column(name = "pos_z", nullable = false, precision = 4, scale = 3)
    private BigDecimal posZ;

    /** 눕힘·세움. 3D 뷰에서 같은 물건이라도 방향에 따라 자리를 다르게 먹는다. */
    @Column(nullable = false)
    private boolean rotated;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ItemPlacement() { }

    public ItemPlacement(Long checklistItemId, Codes.Compartment compartment,
                         BigDecimal posX, BigDecimal posY, BigDecimal posZ, boolean rotated) {
        this.checklistItemId = checklistItemId;
        this.compartment = compartment;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotated = rotated;
    }

    @PrePersist
    @PreUpdate
    void onWrite() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getChecklistItemId() { return checklistItemId; }
    public Codes.Compartment getCompartment() { return compartment; }
    public BigDecimal getPosX() { return posX; }
    public BigDecimal getPosY() { return posY; }
    public BigDecimal getPosZ() { return posZ; }
    public boolean isRotated() { return rotated; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
