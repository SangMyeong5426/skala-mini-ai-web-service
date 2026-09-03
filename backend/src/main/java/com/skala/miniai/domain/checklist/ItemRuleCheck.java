package com.skala.miniai.domain.checklist;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.skala.miniai.common.Codes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * ★ N:M 조인 2 — 체크리스트 항목 ↔ 반입 규정.
 *
 * <p>한 물품이 여러 규정에 걸리고(200ml 화장품 = 액체 + 총량), 한 규정이 여러 물품에 걸린다.
 * <b>같은 규정이라도 물품마다 판정이 다르므로</b> 조인 테이블에 {@code verdict} 가 있다.
 *
 * <p>{@code missingInfo} 는 판정을 단정하지 않고 무엇이 부족한지 알려주기 위한 칸이다.
 */
@Entity
@Table(name = "item_rule_checks")
@IdClass(ItemRuleCheckId.class)
public class ItemRuleCheck {

    @Id
    @Column(name = "checklist_item_id")
    private Long checklistItemId;

    @Id
    @Column(name = "rule_id")
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.RuleVerdict verdict;

    @Column(name = "missing_info", length = 100)
    private String missingInfo;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    protected ItemRuleCheck() { }

    public ItemRuleCheck(Long checklistItemId, Long ruleId, Codes.RuleVerdict verdict, String missingInfo) {
        this.checklistItemId = checklistItemId;
        this.ruleId = ruleId;
        this.verdict = verdict;
        this.missingInfo = missingInfo;
    }

    @PrePersist
    void onCreate() {
        if (decidedAt == null) decidedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getChecklistItemId() { return checklistItemId; }
    public Long getRuleId() { return ruleId; }
    public Codes.RuleVerdict getVerdict() { return verdict; }
    public String getMissingInfo() { return missingInfo; }
}
