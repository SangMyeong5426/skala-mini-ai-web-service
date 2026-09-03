package com.skala.miniai.domain.checklist;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skala.miniai.common.Codes;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findByTripIdOrderById(Long tripId);

    Optional<ChecklistItem> findByIdAndTripId(Long id, Long tripId);

    long countByTripId(Long tripId);

    long countByTripIdAndCheckStatus(Long tripId, Codes.CheckStatus checkStatus);
}
