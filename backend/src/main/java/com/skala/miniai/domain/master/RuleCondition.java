package com.skala.miniai.domain.master;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * {@code transport_rules.condition_note} 한 줄을 <b>판정 가능한 조건</b>으로 읽는다.
 *
 * <p>07 「규칙 엔진 판정 규칙」 2번이 "확인된 attributes 로 조건을 판별한다" 고만 정했다.
 * 그런데 {@code condition_note} 는 {@code "100Wh 이하"} · {@code "날 길이 6cm 초과"} 같은
 * <b>사람이 읽는 문장</b>이고, 스키마에 최소·최대 컬럼이 없다. 그래서 문장을 읽는 규약을
 * 여기에 둔다. <b>07 에 없던 것을 정한 것이므로 확인이 필요하다</b>(PR 본문에 남겼다).
 *
 * <p>읽는 모양은 {@code <숫자><단위><비교어>} 하나 이상이고, 여럿이면 <b>모두</b> 만족해야 한다.
 *
 * <table>
 *   <caption>단위와 07 속성의 대응</caption>
 *   <tr><th>단위</th><th>속성</th><th>예</th></tr>
 *   <tr><td>{@code Wh}</td><td>{@code batteryWh}</td><td>{@code "100Wh 초과 160Wh 이하"}</td></tr>
 *   <tr><td>{@code ml}</td><td>{@code capacityMl}</td><td>{@code "용기당 100ml 이하, 총 1L 이하"}</td></tr>
 *   <tr><td>{@code cm}</td><td>{@code bladeCm}</td><td>{@code "날 길이 6cm 이하"}</td></tr>
 * </table>
 *
 * <p><b>모르는 것은 통과시키지 않는다.</b> 문장을 못 읽으면 {@link #unreadable(String)} 이 참이고,
 * 그 행으로는 반입 가능 판정을 내리지 않는다. 규정을 잘못 읽어 "가져가도 된다" 고 하는 쪽이
 * "확인이 필요하다" 고 하는 쪽보다 훨씬 나쁘다.
 *
 * <p>{@code "총 1L 이하"} 처럼 <b>가방 전체</b>에 걸리는 조건은 물품 하나로 판정할 수 없어
 * 읽지 않는다. 리터(L)를 단위 목록에서 뺀 이유다 — 07 도 이 합계 조건을 물품별 속성으로 두지 않았다.
 */
public final class RuleCondition {

    /** 예: {@code 100Wh 이하} · {@code 6cm 초과}. 숫자와 단위 사이 공백은 허용한다. */
    private static final Pattern CLAUSE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(wh|ml|cm)\\s*(이하|미만|초과|이상)", Pattern.CASE_INSENSITIVE);

    /** 문장에서 <b>수치 조건처럼 보이는</b> 조각. 읽은 절 수와 비교해 놓친 것을 찾는다. */
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*([A-Za-z가-힣]+)");

    /**
     * 물품 하나로 판정할 수 없어 <b>일부러 읽지 않는</b> 단위.
     *
     * <p>{@code "용기당 100ml 이하, 총 1L 이하"} 의 {@code 1L} 이 그렇다 — 가방 전체 합계라
     * 물품별 속성에 없다. 07 도 이 합계 조건을 속성으로 두지 않았다.
     */
    private static final Set<String> IGNORED_UNITS = Set.of("l", "ℓ", "리터");

    private final List<Clause> clauses;

    private RuleCondition(List<Clause> clauses) {
        this.clauses = clauses;
    }

    /** {@code null} · 빈 문장이면 조건 없는 규정이다 — 속성과 무관하게 적용된다. */
    public static RuleCondition parse(String conditionNote) {
        if (conditionNote == null || conditionNote.isBlank()) return new RuleCondition(List.of());

        List<Clause> found = new ArrayList<>();
        Matcher m = CLAUSE.matcher(conditionNote);
        while (m.find()) {
            found.add(new Clause(
                    Attribute.of(m.group(2)),
                    Comparator.of(m.group(3)),
                    Double.parseDouble(m.group(1))));
        }
        return new RuleCondition(found);
    }

    /** 이 조건이 보는 속성들. 판정하려면 이 값들이 채워져 있어야 한다. */
    public List<Attribute> requiredAttributes() {
        return clauses.stream().map(Clause::attribute).distinct().toList();
    }

    /**
     * 문장에 <b>읽지 못한 수치 조건</b>이 남아 있는가. 그러면 이 행으로 확정 판정을 하지 않는다.
     *
     * <p>예전에는 <b>한 조각도</b> 못 읽었을 때만 참이었다. 절반만 읽으면 나머지가 조용히
     * 사라졌다 — 리뷰가 짚은 구멍이다.
     *
     * <pre>{@code
     * "100Wh 이하, 1인 2개까지"  →  batteryWh <= 100 만 읽고 수량 제한은 사라진다
     *                            →  5개를 들고 가도 CABIN_OK
     * }</pre>
     *
     * <p>규정표는 앞으로 사람이 늘릴 데이터다. <b>잘못 읽는 것과 못 읽는 것을 구분해야</b>
     * 새 규정이 조용히 무시되지 않는다. 그래서 문장의 {@code 숫자+단위} 조각 수와 실제로 읽은
     * 절 수를 견준다. {@link #IGNORED_UNITS} 는 세지 않는다 — 일부러 읽지 않기로 한 것이다.
     */
    public static boolean unreadable(String conditionNote) {
        if (conditionNote == null || conditionNote.isBlank()) return false;

        int parsed = parse(conditionNote).clauses.size();
        // 문장은 있는데 수치 조건을 하나도 못 읽었다 — "배터리 분리 후 휴대" 같은 산문이다.
        // 지킬 조건이 분명히 있는데 값으로 확인할 방법이 없으므로 확정하지 않는다.
        if (parsed == 0) return true;

        int expected = 0;
        Matcher fragments = NUMBER_WITH_UNIT.matcher(conditionNote);
        while (fragments.find()) {
            String unit = fragments.group(2).toLowerCase(Locale.ROOT);
            if (IGNORED_UNITS.stream().anyMatch(unit::startsWith)) continue;
            expected++;
        }
        // 일부만 읽었다 — 나머지가 조용히 사라지지 않게 막는다.
        return expected != parsed;
    }

    /** 07 {@code attributes} 로 이 조건이 성립하는가. 값이 없으면 판정하지 않는다({@code false}). */
    public boolean matches(JsonNode attributes) {
        for (Clause clause : clauses) {
            JsonNode value = attributes == null ? null : attributes.path(clause.attribute().field());
            if (value == null || !value.isNumber()) return false;
            if (!clause.comparator().test(value.asDouble(), clause.bound())) return false;
        }
        return true;
    }

    /** 07 {@code attributes} 의 필드 이름과 사용자에게 보여 줄 이름. */
    public enum Attribute {
        BATTERY_WH("batteryWh", "배터리 정격(Wh)"),
        CAPACITY_ML("capacityMl", "용량(ml)"),
        BLADE_CM("bladeCm", "날 길이(cm)");

        private final String field;
        private final String label;

        Attribute(String field, String label) {
            this.field = field;
            this.label = label;
        }

        public String field() { return field; }

        /** {@code missingInfo} 에 넣는 문구. BAG_CHECK 프롬프트가 쓰는 표현과 같게 맞췄다. */
        public String label() { return label; }

        static Attribute of(String unit) {
            return switch (unit.toLowerCase(Locale.ROOT)) {
                case "wh" -> BATTERY_WH;
                case "ml" -> CAPACITY_ML;
                default -> BLADE_CM;
            };
        }
    }

    private enum Comparator {
        AT_MOST, LESS_THAN, GREATER_THAN, AT_LEAST;

        boolean test(double value, double bound) {
            return switch (this) {
                case AT_MOST -> value <= bound;
                case LESS_THAN -> value < bound;
                case GREATER_THAN -> value > bound;
                case AT_LEAST -> value >= bound;
            };
        }

        static Comparator of(String korean) {
            return switch (korean) {
                case "이하" -> AT_MOST;
                case "미만" -> LESS_THAN;
                case "초과" -> GREATER_THAN;
                default -> AT_LEAST;
            };
        }
    }

    private record Clause(Attribute attribute, Comparator comparator, double bound) { }
}
