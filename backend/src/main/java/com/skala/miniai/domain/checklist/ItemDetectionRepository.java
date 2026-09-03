package com.skala.miniai.domain.checklist;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemDetectionRepository extends JpaRepository<ItemDetection, ItemDetectionId> {

    List<ItemDetection> findByChecklistItemIdIn(Collection<Long> checklistItemIds);

    List<ItemDetection> findByDetectedObjectId(Long detectedObjectId);

    List<ItemDetection> findByDetectedObjectIdIn(Collection<Long> detectedObjectIds);

    Optional<ItemDetection> findByChecklistItemIdAndDetectedObjectId(Long checklistItemId, Long detectedObjectId);

    void deleteByDetectedObjectId(Long detectedObjectId);

    void deleteByChecklistItemId(Long checklistItemId);
}
