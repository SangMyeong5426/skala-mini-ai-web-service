package com.skala.miniai.domain.checklist;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.Codes;
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.DetectedObjectRepository;

/**
 * 항목별 <b>사진 상태</b>를 계산한다. 컬럼이 아니다 (05-erd 개정).
 *
 * <p>06 규약:
 * <ul>
 *   <li>승인된 인식 결과와 <b>사용자 확정 연결</b>이 있으면 {@code CONFIRMED}
 *   <li>미승인 연결 후보만 있으면 {@code NEEDS_CHECK}
 *   <li>연결이 없으면 {@code NOT_IN_PHOTO}
 * </ul>
 *
 * <p><b>실제 완료 상태와 독립적이다.</b> 사진에서 못 찾았다는 이유로 {@code PREPARED} 를
 * 취소하지 않는다 — 사진 없이 직접 챙긴 물건이 있기 때문이다.
 *
 * <p>항목 하나씩 조회하면 N+1 이 난다. 목록을 한 번에 받아 두 번의 조회로 끝낸다.
 */
@Component
public class PhotoStatusResolver {

    private final ItemDetectionRepository links;
    private final DetectedObjectRepository detections;

    public PhotoStatusResolver(ItemDetectionRepository links, DetectedObjectRepository detections) {
        this.links = links;
        this.detections = detections;
    }

    @Transactional(readOnly = true)
    public Map<Long, Codes.PhotoStatus> resolve(Collection<Long> checklistItemIds) {
        Map<Long, Codes.PhotoStatus> result = new HashMap<>();
        for (Long id : checklistItemIds) {
            result.put(id, Codes.PhotoStatus.NOT_IN_PHOTO);
        }
        if (checklistItemIds.isEmpty()) return result;

        List<ItemDetection> all = links.findByChecklistItemIdIn(checklistItemIds);
        if (all.isEmpty()) return result;

        Set<Long> approvedDetectionIds = new HashSet<>(
                detections.findAllById(all.stream().map(ItemDetection::getDetectedObjectId).toList())
                        .stream()
                        .filter(DetectedObject::isApproved)
                        .map(DetectedObject::getId)
                        .toList());

        for (ItemDetection link : all) {
            Long itemId = link.getChecklistItemId();
            boolean confirmed = link.isConfirmedByUser() && approvedDetectionIds.contains(link.getDetectedObjectId());
            if (confirmed) {
                result.put(itemId, Codes.PhotoStatus.CONFIRMED);
            } else if (result.get(itemId) != Codes.PhotoStatus.CONFIRMED) {
                // 확정 연결 하나면 CONFIRMED 다. 나머지 후보가 있어도 덮어쓰지 않는다.
                result.put(itemId, Codes.PhotoStatus.NEEDS_CHECK);
            }
        }
        return result;
    }
}
