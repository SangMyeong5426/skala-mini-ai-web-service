package com.skala.miniai.domain.ai;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.skala.miniai.common.Codes;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.checklist.ItemDetection;
import com.skala.miniai.domain.checklist.ItemDetectionRepository;
import com.skala.miniai.domain.master.ItemWeightRepository;
import com.skala.miniai.domain.photo.DetectedObject;

/**
 * {@code BAG_CHECK} 완료 시 인식 물품을 <b>승인 없이 내 목록에 등록</b>한다 (06 개정).
 *
 * <p>예전에는 사용자가 S-04 에서 승인해야 목록에 들어갔다. 지금은 인식되면 바로
 * {@code PREPARED} 로 등록되고, S-04 는 <b>사후 수정</b> 화면이 된다.
 *
 * <p>06 이 정한 규칙을 그대로 옮겼다.
 * <ul>
 *   <li>신뢰도·속성 부족과 <b>무관하게</b> 등록한다. LOW 도 등록하고, 화면이 확인 필요만 표시한다.
 *   <li>기존 연결 → 같은 이름 항목 → 신규 생성 순으로 <b>재사용</b>한다.
 *   <li>여러 사진에서 같은 물품을 보면 수량을 <b>합산하지 않고 큰 값</b>을 쓴다.
 *   <li>사용자가 고친 이름·수량·준비 상태를 <b>덮어쓰지 않는다.</b> 재분석해도 마찬가지다.
 *   <li>자동 연결은 {@code confirmed_by_user=false} 여도 유효하다.
 * </ul>
 *
 * <p>이 클래스를 {@code AiJobRunner} 에서 분리한 이유는 규칙이 다섯 개나 되고 그중 셋이
 * "덮어쓰지 않는다" 라서다 — 한 곳에 모아 두지 않으면 조건이 흩어진다.
 */
@Component
public class PhotoAutoRegistrar {

    /** 자동 연결의 신뢰도. 인식 신뢰도를 그대로 옮겨 담아 나중에 근거로 쓴다. */
    private static final BigDecimal DEFAULT_MATCH = new BigDecimal("0.500");

    private final ChecklistItemRepository items;
    private final ItemDetectionRepository links;
    private final ItemWeightRepository weights;

    public PhotoAutoRegistrar(ChecklistItemRepository items, ItemDetectionRepository links,
                              ItemWeightRepository weights) {
        this.items = items;
        this.links = links;
        this.weights = weights;
    }

    /**
     * 저장된 인식 결과를 내 목록에 반영한다.
     *
     * @param tripId     대상 여행
     * @param detections 이번 작업이 저장한 인식 결과
     */
    public void register(Long tripId, List<DetectedObject> detections) {
        if (detections.isEmpty()) return;

        // 같은 이름을 가진 내 항목을 한 번에 찾아 둔다. 물품마다 조회하면 N+1 이다.
        Map<String, ChecklistItem> byName = new HashMap<>();
        for (ChecklistItem item : items.findByTripIdOrderById(tripId)) {
            byName.putIfAbsent(normalize(item.getName()), item);
        }

        for (DetectedObject detection : detections) {
            String name = normalize(detection.getName());
            if (name.isEmpty()) continue;   // 06: 이름 없는 인식은 항목을 만들지 않는다

            // ① 이 인식 결과에 이미 연결이 있으면 그것을 쓴다.
            List<ItemDetection> existing = links.findByDetectedObjectId(detection.getId());
            if (!existing.isEmpty()) {
                existing.forEach(link -> reflect(link.getChecklistItemId(), detection));
                continue;
            }

            // ② 같은 이름의 내 항목이 있으면 연결만 한다. 최초 출처는 유지된다.
            ChecklistItem target = byName.get(name);
            if (target == null) {
                // ③ 없으면 만든다. 06 의 신규 기본값 그대로다.
                target = items.save(new ChecklistItem(
                        tripId, detection.getName().trim(), Codes.Category.ETC, detection.getQty(),
                        Codes.Priority.RECOMMENDED, Codes.ItemSource.PHOTO, Codes.CheckStatus.PREPARED));
                target.setItemWeightId(weights.findByKeyword(name).map(w -> w.getId()).orElse(null));
                byName.put(name, target);
            } else {
                // 기존 목록에서 처음 사진으로 확인된 항목도 실제 준비 완료로 반영한다.
                target.setCheckStatus(Codes.CheckStatus.PREPARED);
                reflect(target.getId(), detection);
            }

            links.save(new ItemDetection(target.getId(), detection.getId(), DEFAULT_MATCH, false));
        }
    }

    /**
     * 기존 항목에 이번 인식을 반영한다.
     *
     * <p>수량은 <b>합산하지 않고 큰 값</b>을 쓴다 — 사진 두 장에 같은 셔츠가 찍혔다고
     * 셔츠가 두 벌인 것은 아니다. 준비 상태는 건드리지 않는다. 사용자가 {@code UNCHECKED} 로
     * 되돌렸다면 재분석이 그것을 뒤집으면 안 된다 (06 5번).
     */
    private void reflect(Long itemId, DetectedObject detection) {
        items.findById(itemId).ifPresent(item -> {
            if (detection.getQty() != null && detection.getQty() > item.getQty()) {
                item.setQty(detection.getQty());
            }
        });
    }

    /** 06 이름 비교 규약 — 앞뒤 공백 제거 후 연속 공백을 하나로. */
    private static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }
}
