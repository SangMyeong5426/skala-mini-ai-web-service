package com.skala.miniai.domain.checklist;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.Codes;
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.DetectedObjectRepository;

/**
 * 항목별 <b>사진 상태</b>를 계산한다. 컬럼이 아니라 조회 시 계산값이다 (05-erd).
 *
 * <p>06 규약 (로그인·자동 등록 개정):
 * <ul>
 *   <li>유효한 연결 중 <b>HIGH/MEDIUM</b> 이거나 <b>사후 확인된</b> 연결이 있으면 {@code CONFIRMED}
 *   <li><b>LOW 연결만</b> 있고 사후 확인되지 않았으면 {@code NEEDS_CHECK}
 *   <li>연결이 없으면 {@code NOT_IN_PHOTO}
 * </ul>
 *
 * <p><b>{@code approved} 를 쓰지 않는다.</b> 사진 물품은 승인 없이 자동 등록되므로
 * 그 컬럼은 등록·집계 조건이 아니다(05). 신뢰도가 낮아도 등록은 되고, 다만 화면이
 * "확인 필요" 를 띄울 근거로 이 상태를 쓴다.
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

        Map<Long, DetectedObject> byId = detections
                .findAllById(all.stream().map(ItemDetection::getDetectedObjectId).toList()).stream()
                .collect(Collectors.toMap(DetectedObject::getId, Function.identity()));

        for (ItemDetection link : all) {
            Long itemId = link.getChecklistItemId();
            DetectedObject detection = byId.get(link.getDetectedObjectId());
            if (detection == null) continue;   // 사진이 지워졌으면 연결도 곧 사라진다

            boolean strong = link.isConfirmedByUser()
                    || detection.getConfidenceLevel() != Codes.ConfidenceLevel.LOW;

            if (strong) {
                result.put(itemId, Codes.PhotoStatus.CONFIRMED);
            } else if (result.get(itemId) != Codes.PhotoStatus.CONFIRMED) {
                // 강한 연결 하나면 CONFIRMED 다. LOW 가 더 있어도 덮어쓰지 않는다.
                result.put(itemId, Codes.PhotoStatus.NEEDS_CHECK);
            }
        }
        return result;
    }
}
