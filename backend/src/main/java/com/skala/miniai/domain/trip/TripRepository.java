package com.skala.miniai.domain.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** 소유권 확인을 <b>조회에 녹인다.</b> 남의 여행이면 애초에 안 나온다. */
    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 캘린더용. 기간이 겹치는 여행을 찾는다 — 시작 이전에 끝나지 않고, 끝난 뒤에 시작하지 않는 것. */
    List<Trip> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDate(
            Long userId, LocalDate to, LocalDate from);
}
