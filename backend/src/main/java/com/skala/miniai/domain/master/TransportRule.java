package com.skala.miniai.domain.master;

import java.time.LocalDate;

import com.skala.miniai.common.Codes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 반입 규정 마스터. <b>최종 판정은 이 표를 보는 규칙 엔진이 한다. AI 가 아니다.</b>
 *
 * <p>명세 9절 "규정 최신성" 때문에 {@code sourceUrl} 과 {@code checkedAt} 이 NOT NULL 이다.
 * 출처 없는 규정은 화면에 띄우지 않는다.
 */
@Entity
@Table(name = "transport_rules")
public class TransportRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Transport transport;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.RuleVerdict verdict;

    @Column(name = "condition_note", columnDefinition = "text")
    private String conditionNote;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "source_url", nullable = false, length = 255)
    private String sourceUrl;

    @Column(name = "checked_at", nullable = false)
    private LocalDate checkedAt;

    protected TransportRule() { }

    public Long getId() { return id; }
    public Codes.Transport getTransport() { return transport; }
    public String getKeyword() { return keyword; }
    public Codes.RuleVerdict getVerdict() { return verdict; }
    public String getConditionNote() { return conditionNote; }
    public String getDescription() { return description; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDate getCheckedAt() { return checkedAt; }
}
