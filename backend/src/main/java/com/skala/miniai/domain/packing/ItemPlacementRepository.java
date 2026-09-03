package com.skala.miniai.domain.packing;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPlacementRepository extends JpaRepository<ItemPlacement, Long> {

    List<ItemPlacement> findByChecklistItemIdIn(Collection<Long> checklistItemIds);

    /** "정리 초기화" — 그 여행의 배치만 지운다. 체크리스트 항목 자체는 남는다. */
    void deleteByChecklistItemIdIn(Collection<Long> checklistItemIds);
}
