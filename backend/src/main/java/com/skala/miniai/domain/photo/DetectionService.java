package com.skala.miniai.domain.photo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.checklist.ItemDetection;
import com.skala.miniai.domain.checklist.ItemDetectionRepository;
import com.skala.miniai.domain.trip.TripService;

/**
 * 인식 결과 조회와 <b>선택적 사후 수정</b> (UC-04 · 화면 S-04).
 *
 * <p><b>승인 게이트가 아니다.</b> 06 개정에서 인식 물품은 {@code BAG_CHECK} 완료 시
 * 자동으로 내 목록에 {@code PREPARED} 로 등록된다. 이 화면은 그 뒤에 이름·수량·연결을
 * 고치는 곳이고, 고치지 않아도 목록은 유지된다.
 *
 * <p>그래서 이 서비스는 <b>등록하지 않는다.</b> 등록은 {@code PhotoAutoRegistrar} 가 한다.
 */
@Service
public class DetectionService {

    /** 사용자가 직접 고른 연결이라 신뢰도는 1 이다. 자동 연결(0.5)과 구분된다. */
    private static final BigDecimal USER_CONFIRMED = new BigDecimal("1.000");

    private final DetectedObjectRepository detections;
    private final TripPhotoRepository photos;
    private final ItemDetectionRepository links;
    private final ChecklistItemRepository items;
    private final TripService tripService;

    public DetectionService(DetectedObjectRepository detections, TripPhotoRepository photos,
                            ItemDetectionRepository links, ChecklistItemRepository items,
                            TripService tripService) {
        this.detections = detections;
        this.photos = photos;
        this.links = links;
        this.items = items;
        this.tripService = tripService;
    }

    @Transactional(readOnly = true)
    public PhotoDtos.DetectionListResponse list(Long tripId) {
        tripService.mustOwn(tripId);
        List<PhotoDtos.Detection> out = new ArrayList<>();
        for (DetectedObject d : detectionsOf(tripId)) {
            out.add(new PhotoDtos.Detection(
                    d.getId(), d.getPhotoId(), d.getName(), d.getQty(),
                    d.getConfidence(), d.getConfidenceLevel(),
                    d.getMissingInfo(), d.getLabelText(),
                    links.findByDetectedObjectId(d.getId()).stream()
                            .map(l -> new PhotoDtos.DetectionLink(l.getChecklistItemId(), l.isConfirmedByUser()))
                            .toList()));
        }
        return new PhotoDtos.DetectionListResponse(out);
    }

    @Transactional
    public PhotoDtos.PatchResponse patch(Long tripId, Long detectionId, PhotoDtos.PatchRequest req) {
        // 내 목록도 함께 고치므로 같은 락으로 직렬화한다.
        tripService.mustOwnForUpdate(tripId);
        DetectedObject detection = mustBelongToTrip(tripId, detectionId);

        if (req.approved() != null) {
            throw ApiException.badRequest(
                    "approved 는 더 이상 사용하지 않습니다. 사진 물품은 승인 없이 자동 등록됩니다.", "approved");
        }

        boolean editsContent = req.name() != null || req.qty() != null || req.category() != null;

        // ── 연결 교체 — 생략·빈 배열·값이 각각 다른 뜻이다 (06) ──
        if (req.matchedItemIds() != null) {
            replaceLinks(tripId, detection, req.matchedItemIds());
        }

        List<ItemDetection> current = links.findByDetectedObjectId(detection.getId());

        if (editsContent && current.size() > 1) {
            // 한 값으로 여러 항목을 덮어쓰면 어느 쪽이 맞는지 서버가 정하게 된다.
            throw ApiException.conflict("AMBIGUOUS_LINK",
                    "이 인식 결과가 여러 항목에 연결돼 있습니다. 연결을 먼저 정리하거나 항목별로 수정해 주세요.");
        }

        if (req.name() != null) {
            String name = req.name().trim().replaceAll("\\s+", " ");
            if (name.isEmpty()) throw ApiException.badRequest("물품 이름은 공백일 수 없습니다.", "name");
            if (name.length() > 100) throw ApiException.badRequest("물품 이름은 100자 이하입니다.", "name");
            detection.setName(name);
        }
        if (req.qty() != null) {
            if (req.qty() < 1 || req.qty() > 99) throw ApiException.badRequest("수량은 1~99 입니다.", "qty");
            detection.setQty(req.qty());
        }

        for (ItemDetection link : current) {
            // 06: 사후 수정된 연결은 confirmed_by_user = true 로 기록한다.
            // 최초 등록의 조건이 아니라 "사용자가 확인했다" 는 표시다.
            link.setConfirmedByUser(true);
            link.setMatchConfidence(USER_CONFIRMED);

            items.findById(link.getChecklistItemId()).ifPresent(item -> {
                if (req.name() != null) item.setName(detection.getName());
                if (req.qty() != null) item.setQty(detection.getQty());
                if (req.category() != null) item.setCategory(req.category());
                // 준비 상태는 건드리지 않는다. 이미 PREPARED 이고, 사용자가 되돌렸다면 그 뜻을 지킨다.
            });
        }

        return new PhotoDtos.PatchResponse(
                detection.getId(), detection.getName(), detection.getQty(), linkedItemsOf(detection.getId()));
    }

    /**
     * 06: {@code matchedItemIds} 는 <b>증분이 아니라 전체 교체</b>다.
     * 빈 배열은 연결만 푸는 것이고 <b>항목을 지우지 않는다</b> — 삭제는 item DELETE 로 한다.
     */
    private void replaceLinks(Long tripId, DetectedObject detection, List<Long> itemIds) {
        for (Long itemId : itemIds) {
            items.findByIdAndTripId(itemId, tripId)
                    .orElseThrow(() -> ApiException.badRequest(
                            "이 여행의 체크리스트 항목이 아닙니다: " + itemId, "matchedItemIds"));
        }
        links.deleteByDetectedObjectId(detection.getId());
        links.flush();
        for (Long itemId : itemIds) {
            links.save(new ItemDetection(itemId, detection.getId(), USER_CONFIRMED, true));
        }
    }

    @Transactional(readOnly = true)
    public List<DetectedObject> detectionsOf(Long tripId) {
        List<Long> photoIds = photos.findByTripIdOrderById(tripId).stream().map(TripPhoto::getId).toList();
        return photoIds.isEmpty() ? List.of() : detections.findByPhotoIdInOrderById(photoIds);
    }

    private DetectedObject mustBelongToTrip(Long tripId, Long detectionId) {
        return detectionsOf(tripId).stream()
                .filter(d -> d.getId().equals(detectionId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("인식 결과", detectionId));
    }

    private List<PhotoDtos.LinkedItem> linkedItemsOf(Long detectionId) {
        List<PhotoDtos.LinkedItem> out = new ArrayList<>();
        for (ItemDetection link : links.findByDetectedObjectId(detectionId)) {
            items.findById(link.getChecklistItemId()).ifPresent(item -> out.add(new PhotoDtos.LinkedItem(
                    item.getId(), item.getName(), link.isConfirmedByUser(),
                    item.getSource(), item.getCheckStatus())));
        }
        return out;
    }
}
