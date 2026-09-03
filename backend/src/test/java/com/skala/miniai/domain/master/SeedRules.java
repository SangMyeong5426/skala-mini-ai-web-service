package com.skala.miniai.domain.master;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.test.util.ReflectionTestUtils;

import com.skala.miniai.common.Codes;

/**
 * {@code database/seed.sql} 의 FLIGHT 규정을 DB 없이 세운다.
 *
 * <p>{@link TransportRule} 은 생성자가 {@code protected} 라 이 패키지 밖에서 만들 수 없다.
 * 다른 패키지의 테스트가 규칙 엔진을 쓰려면 통로가 하나 있어야 해서 여기 뒀다.
 *
 * <p>값은 {@code database/seed.sql} · {@code src/test/resources/data.sql} 과 <b>같아야 한다.</b>
 * 셋 중 하나를 고치면 나머지도 고친다.
 */
public final class SeedRules {

    private static final String AIRPORT_905 = "https://www.airport.kr/ap_ko/905/subview.do";
    private static final String AIRPORT_907 = "https://www.airport.kr/ap_ko/907/subview.do";
    private static final LocalDate CHECKED = LocalDate.of(2026, 9, 2);

    private SeedRules() { }

    /** 공식 규정표로 판정하는 {@link RuleEngine}. 저장소는 아래 목록에서 키워드로 골라 준다. */
    public static RuleEngine engine() {
        TransportRuleRepository repository = mock(TransportRuleRepository.class);
        List<TransportRule> all = all();
        given(repository.findByTransportOrderById(any())).willReturn(all);
        given(repository.findByTransportAndKeywordContainingIgnoreCaseOrderById(any(), any()))
                .willAnswer(call -> {
                    String keyword = call.getArgument(1, String.class).toLowerCase(Locale.ROOT);
                    return all.stream()
                            .filter(rule -> rule.getKeyword().toLowerCase(Locale.ROOT).contains(keyword))
                            .toList();
                });
        return new RuleEngine(repository);
    }

    public static List<TransportRule> all() {
        return List.of(
                rule(1L, "보조배터리", Codes.RuleVerdict.CABIN_OK, "100Wh 이하",
                        "보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.", AIRPORT_905),
                rule(2L, "보조배터리", Codes.RuleVerdict.ASK_AIRLINE, "100Wh 초과 160Wh 이하",
                        "100Wh를 넘으면 항공사 사전 승인이 필요합니다.", AIRPORT_905),
                rule(3L, "보조배터리", Codes.RuleVerdict.CHECKED_FORBIDDEN, "160Wh 초과",
                        "160Wh를 넘는 보조배터리는 기내·위탁 모두 반입할 수 없습니다.", AIRPORT_905),
                rule(4L, "액체", Codes.RuleVerdict.CABIN_OK, "용기당 100ml 이하, 총 1L 이하",
                        "액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.", AIRPORT_905),
                rule(5L, "액체", Codes.RuleVerdict.CHECKED_OK, "100ml 초과",
                        "100ml를 넘는 액체는 위탁수하물로 부치세요.", AIRPORT_905),
                rule(6L, "가위", Codes.RuleVerdict.CHECKED_OK, "날 길이 6cm 초과",
                        "날 길이 6cm를 넘는 가위는 기내 반입이 제한됩니다. 위탁수하물로 부치세요.", AIRPORT_907),
                rule(7L, "가위", Codes.RuleVerdict.CABIN_OK, "날 길이 6cm 이하",
                        "날 길이 6cm 이하 가위는 기내 반입이 가능합니다.", AIRPORT_907),
                rule(8L, "노트북", Codes.RuleVerdict.CABIN_OK, null,
                        "노트북은 기내 반입 가능합니다. 보안검색 시 가방에서 꺼내 주세요.",
                        "https://www.airportal.go.kr/library/security.do"));
    }

    private static TransportRule rule(Long id, String keyword, Codes.RuleVerdict verdict,
                                      String conditionNote, String description, String sourceUrl) {
        TransportRule rule = new TransportRule();
        ReflectionTestUtils.setField(rule, "id", id);
        ReflectionTestUtils.setField(rule, "transport", Codes.Transport.FLIGHT);
        ReflectionTestUtils.setField(rule, "keyword", keyword);
        ReflectionTestUtils.setField(rule, "verdict", verdict);
        ReflectionTestUtils.setField(rule, "conditionNote", conditionNote);
        ReflectionTestUtils.setField(rule, "description", description);
        ReflectionTestUtils.setField(rule, "sourceUrl", sourceUrl);
        ReflectionTestUtils.setField(rule, "checkedAt", CHECKED);
        return rule;
    }
}
