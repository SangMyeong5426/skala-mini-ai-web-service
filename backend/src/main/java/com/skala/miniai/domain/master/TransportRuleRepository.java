package com.skala.miniai.domain.master;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skala.miniai.common.Codes;

public interface TransportRuleRepository extends JpaRepository<TransportRule, Long> {

    List<TransportRule> findByTransportOrderById(Codes.Transport transport);

    /** 키워드는 부분 일치로 찾는다. "보조배터리 2개" 같은 이름도 규정에 걸려야 하기 때문이다. */
    List<TransportRule> findByTransportAndKeywordContainingIgnoreCaseOrderById(
            Codes.Transport transport, String keyword);
}
