package com.skala.miniai.domain.ai;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skala.miniai.common.Codes;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByIdAndUserId(Long id, Long userId);

    /**
     * 가장 최근 <b>완료된</b> 작업. 06 이 "가장 최근 완료된 추천/무게 작업" 을 여러 곳에서 쓴다.
     * 진행 중인 작업이 기존 결과를 가리지 않도록 {@code status} 를 조건에 넣는다.
     */
    Optional<AiJob> findTopByTripIdAndJobTypeAndStatusOrderByCompletedAtDescIdDesc(
            Long tripId, Codes.JobType jobType, Codes.JobStatus status);
}
