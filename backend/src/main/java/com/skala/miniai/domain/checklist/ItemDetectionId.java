package com.skala.miniai.domain.checklist;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link ItemDetection} 의 복합 키.
 *
 * <p><b>{@code equals}·{@code hashCode} 가 반드시 있어야 한다.</b> 없으면 기동할 때마다
 * {@code HHH000038: Composite id class does not override equals()} 경고가 뜨고,
 * 영속성 컨텍스트가 같은 행을 다른 것으로 본다 (AGENTS.md "복합 기본키 두 개").
 */
public class ItemDetectionId implements Serializable {

    private Long checklistItemId;
    private Long detectedObjectId;

    public ItemDetectionId() { }

    public ItemDetectionId(Long checklistItemId, Long detectedObjectId) {
        this.checklistItemId = checklistItemId;
        this.detectedObjectId = detectedObjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemDetectionId other)) return false;
        return Objects.equals(checklistItemId, other.checklistItemId)
                && Objects.equals(detectedObjectId, other.detectedObjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checklistItemId, detectedObjectId);
    }
}
