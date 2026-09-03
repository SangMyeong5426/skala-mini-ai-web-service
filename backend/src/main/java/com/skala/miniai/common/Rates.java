package com.skala.miniai.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 완료율 계산. 06 "완료율·사진 상태·현재 무게의 공통 규약" 을 <b>한 군데</b>에 둔다.
 *
 * <p>홈·S-05·S-06 이 같은 값을 보여야 한다. 화면마다 다시 계산하면 반올림이 갈린다.
 *
 * <ul>
 *   <li>{@code PREPARED 항목 수 / 전체 항목 수}. 빈 목록은 {@code 0}.
 *   <li>qty 로 가중하지 <b>않는다</b>.
 *   <li>소수 <b>셋째 자리 HALF_UP</b> (6/7 → 0.857). 표시용 % 변환은 FE 몫이다.
 * </ul>
 */
public final class Rates {

    private Rates() { }

    public static BigDecimal completion(long prepared, long total) {
        if (total <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(prepared)
                .divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP);
    }
}
