package com.skala.miniai.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 지금 요청을 보낸 사용자. <b>세션에서만 읽는다.</b>
 *
 * <p>06 소유권 규약: "요청 userId 를 받지 않는다. {@code ai_jobs.user_id} 는 세션에서 채운다."
 * 클라이언트가 보낸 값을 믿으면 남의 자료를 그 값으로 열 수 있다.
 *
 * <p>인증 주체({@code principal})에 {@code userId} 를 그대로 담는다. 사용자 이름이 아니라
 * 내부 PK 를 담는 이유는 아이디 변경을 허용해도 세션이 깨지지 않게 하기 위해서다.
 */
@Component
public class CurrentUser {

    /** 보호된 경로에서 부른다. 미인증이면 {@code 401} 이다. */
    public long id() {
        Long id = idOrNull();
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.", null);
        }
        return id;
    }

    /** 세션 조회처럼 <b>미인증도 정상</b>인 곳에서 쓴다. */
    public Long idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getPrincipal() instanceof Long userId ? userId : null;
    }
}
