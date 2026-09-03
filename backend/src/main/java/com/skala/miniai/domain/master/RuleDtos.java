package com.skala.miniai.domain.master;

import java.time.LocalDate;
import java.util.List;

import com.skala.miniai.common.Codes;

/** 반입 규정 조회 응답 (S-08). 출처와 확인 날짜를 <b>항상 함께</b> 준다 (명세 9절 규정 최신성). */
public final class RuleDtos {

    private RuleDtos() { }

    public record Rule(
            Long ruleId, Codes.RuleVerdict verdict, String conditionNote,
            String description, String sourceUrl, LocalDate checkedAt) { }

    public record ListResponse(List<Rule> rules) { }
}
