package com.skala.miniai.domain.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** 소유권 확인을 <b>조회에 녹인다.</b> 남의 여행이면 애초에 안 나온다. */
    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    /**
     * 쓰기 경로용. 같은 여행에 대한 동시 요청을 <b>여행 행 하나로 직렬화</b>한다.
     *
     * <p>없으면 「선택한 항목 추가」를 빠르게 두 번 눌렀을 때 두 요청이 모두
     * "아직 채택 안 됨"·"같은 이름 없음" 검사를 통과해 같은 후보로 항목을 <b>두 개</b> 만든다.
     * 06 이 "동시 클릭에도 중복 생성을 막는다" 고 못박은 부분이다.
     *
     * <p>조회 경로는 {@link #findByIdAndUserId} 를 그대로 쓴다 — 읽기까지 줄 세우면 느려진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Trip> findWithLockByIdAndUserId(Long id, Long userId);

    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 캘린더용. 기간이 겹치는 여행을 찾는다 — 시작 이전에 끝나지 않고, 끝난 뒤에 시작하지 않는 것. */
    List<Trip> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDate(
            Long userId, LocalDate to, LocalDate from);
}
