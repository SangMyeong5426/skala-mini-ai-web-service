package com.skala.miniai.common;

import org.springframework.stereotype.Component;

/**
 * 지금 요청을 보낸 사용자.
 *
 * <p><b>인증은 이번 데모에서 구현하지 않는다.</b> 스키마에 {@code users.password_hash}
 * 자리는 두었지만 토큰·세션을 발급하지 않고, 모든 요청을 시드 사용자로 처리한다
 * (docs/06-api-spec.md · docs/01-service-plan.md 범위).
 *
 * <p>여기 한 군데만 고치면 나중에 인증을 붙일 수 있도록 값을 코드에 흩지 않는다.
 */
@Component
public class CurrentUser {

    /** database/seed.sql 의 김지우. */
    private static final long SEED_USER_ID = 1L;

    public long id() {
        return SEED_USER_ID;
    }
}
