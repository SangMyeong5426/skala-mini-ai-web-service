package com.skala.miniai.domain.checklist;

import java.io.Serializable;
import java.util.Objects;

/** {@link ItemRuleCheck} 의 복합 키. {@code equals}·{@code hashCode} 필수 — {@link ItemDetectionId} 참조. */
public class ItemRuleCheckId implements Serializable {

    private Long checklistItemId;
    private Long ruleId;

    public ItemRuleCheckId() { }

    public ItemRuleCheckId(Long checklistItemId, Long ruleId) {
        this.checklistItemId = checklistItemId;
        this.ruleId = ruleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemRuleCheckId other)) return false;
        return Objects.equals(checklistItemId, other.checklistItemId)
                && Objects.equals(ruleId, other.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checklistItemId, ruleId);
    }
}
