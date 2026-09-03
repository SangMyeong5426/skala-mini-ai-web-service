package com.skala.miniai.domain.master;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.miniai.common.Codes;

/**
 * 06 엔드포인트 16 — 반입 규정 조회 (UC-07 · S-08).
 *
 * <p><b>{@code transport} 는 필수다.</b> 없으면 {@code 400} — 이동수단을 모르면 어떤 규정을
 * 보여줄지 정할 수 없고, 항공 규정을 기차 여행자에게 보여주면 틀린 안내가 된다.
 *
 * <p>판정은 AI 가 아니라 이 표를 보는 규칙 엔진이 한다.
 */
@RestController
public class RuleController {

    private final TransportRuleRepository rules;

    public RuleController(TransportRuleRepository rules) {
        this.rules = rules;
    }

    @GetMapping("/api/rules")
    public RuleDtos.ListResponse list(@RequestParam Codes.Transport transport,
                                      @RequestParam(required = false) String keyword) {

        List<TransportRule> found = (keyword == null || keyword.isBlank())
                ? rules.findByTransportOrderById(transport)
                : rules.findByTransportAndKeywordContainingIgnoreCaseOrderById(transport, keyword.trim());

        return new RuleDtos.ListResponse(found.stream()
                .map(r -> new RuleDtos.Rule(r.getId(), r.getVerdict(), r.getConditionNote(),
                        r.getDescription(), r.getSourceUrl(), r.getCheckedAt()))
                .toList());
    }
}
