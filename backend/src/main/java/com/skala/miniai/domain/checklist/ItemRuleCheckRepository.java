package com.skala.miniai.domain.checklist;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRuleCheckRepository extends JpaRepository<ItemRuleCheck, ItemRuleCheckId> {

    List<ItemRuleCheck> findByChecklistItemIdIn(Collection<Long> checklistItemIds);
}
