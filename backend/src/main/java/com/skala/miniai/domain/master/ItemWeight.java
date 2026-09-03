package com.skala.miniai.domain.master;

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
 * 품목별 무게 마스터. 카테고리 평균이 아니라 <b>최소·대표·최대 범위</b>다.
 *
 * <p>명세 F-10: "결과를 실측값처럼 표현하지 않는다". 그래서 단일 값이 없다.
 */
@Entity
@Table(name = "item_weights")
public class ItemWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Codes.Category category;

    @Column(name = "min_g", nullable = false)
    private Integer minG;

    @Column(name = "typical_g", nullable = false)
    private Integer typicalG;

    @Column(name = "max_g", nullable = false)
    private Integer maxG;

    /** 책·금속·배터리·액체는 이미지 추정 오차가 크다. 계산에서 뺄지 판단하는 데 쓴다. */
    @Column(name = "is_dense", nullable = false)
    private boolean dense;

    @Column(columnDefinition = "text")
    private String note;

    protected ItemWeight() { }

    public Long getId() { return id; }
    public String getKeyword() { return keyword; }
    public Codes.Category getCategory() { return category; }
    public Integer getMinG() { return minG; }
    public Integer getTypicalG() { return typicalG; }
    public Integer getMaxG() { return maxG; }
    public boolean isDense() { return dense; }
    public String getNote() { return note; }
}
