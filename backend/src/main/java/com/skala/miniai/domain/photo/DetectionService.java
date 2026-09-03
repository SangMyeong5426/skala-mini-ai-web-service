package com.skala.miniai.domain.photo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.domain.ai.RecommendationStore;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.checklist.ChecklistService;
import com.skala.miniai.domain.checklist.ItemDetection;
import com.skala.miniai.domain.checklist.ItemDetectionRepository;
import com.skala.miniai.domain.trip.TripService;

/**
 * 인식 결과 조회와 <b>승인</b> (UC-04).
 *
 * <p>명세 9.2: "사진 분석 결과는 사용자가 승인하기 전 최종 준비 상태에 반영되지 않아야 한다".
 * <b>여기가 그 관문</b>이다.
 *
 * <p>개정된 06 에서 승인은 단순한 플래그가 아니라 <b>내 목록 완료 등록</b>까지 한 트랜잭션에서 한다.
 * 승인된 물품이 내 목록 밖에 {@code extra} 로만 남는 성공 응답은 허용하지 않는다.
 */
@Service
public class DetectionService {

    /** 사용자가 직접 고른 연결이므로 신뢰도는 1 이다. AI 가 제안한 후보와 구분된다. */
    private static final BigDecimal USER_CONFIRMED = new BigDecimal("1.000");

    private final DetectedObjectRepository detections;
    private final TripPhotoRepository photos;
    private final ItemDetectionRepository links;
    private final ChecklistItemRepository items;
    private final ChecklistService checklistService;
    private final TripService tripService;

    public DetectionService(DetectedObjectRepository detections, TripPhotoRepository photos,
                            ItemDetectionRepository links, ChecklistItemRepository items,
                            ChecklistService checklistService, TripService tripService) {
        this.detections = detections;
        this.photos = photos;
        this.links = links;
        this.items = items;
        this.checklistService = checklistService;
        this.tripService = tripService;
    }

    @Transactional(readOnly = true)
    public PhotoDtos.DetectionListResponse list(Long tripId) {
        tripService.mustOwn(tripId);
        return new PhotoDtos.DetectionListResponse(
                detectionsOf(tripId).stream().map(DetectionService::toDto).toList());
    }

    @Transactional
    public PhotoDtos.ApproveResponse approve(Long tripId, Long detectionId, PhotoDtos.ApproveRequest req) {
        // 사진 승인도 내 목록을 건드리므로 같은 락으로 직렬화한다.
        tripService.mustOwnForUpdate(tripId);
        DetectedObject detection = mustBelongToTrip(tripId, detectionId);

        if (req.name() != null) {
            String name = RecommendationStore.normalize(req.name());
            if (name.isEmpty()) throw ApiException.badRequest("물품 이름은 공백일 수 없습니다.", "name");
            detection.setName(name);
        }
        if (req.qty() != null) {
            if (req.qty() < 1 || req.qty() > 99) throw ApiException.badRequest("수량은 1~99 입니다.", "qty");
            detection.setQty(req.qty());
        }

        // ── 연결 교체 — matchedItemIds 는 생략·빈 배열·값이 각각 다른 뜻이다 ──
        if (req.matchedItemIds() != null) {
            replaceLinks(tripId, detection, req.matchedItemIds());
        }

        boolean approving = Boolean.TRUE.equals(req.approved());
        boolean cancelling = Boolean.FALSE.equals(req.approved());

        if (approving) {
            approveAndRegister(tripId, detection, req);
        } else if (cancelling) {
            cancelApproval(detection);
        }

        return new PhotoDtos.ApproveResponse(
                detection.getId(), detection.isApproved(), detection.getName(), detection.getQty(),
                linkedItemsOf(detection.getId()));
    }

    // ── 승인 ──────────────────────────────────────────────

    private void approveAndRegister(Long tripId, DetectedObject detection, PhotoDtos.ApproveRequest req) {
        List<ItemDetection> current = links.findByDetectedObjectId(detection.getId());

        if (req.matchedItemIds() != null && req.matchedItemIds().isEmpty()) {
            // 06: 승인하면서 연결을 비우라는 요청은 모순이다. 승인된 물품은 내 목록에 있어야 한다.
            throw ApiException.badRequest(
                    "승인하려면 연결할 내 목록 항목이 필요합니다. matchedItemIds 를 비우지 마세요.",
                    "matchedItemIds");
        }

        if (current.isEmpty()) {
            // 연결이 없다 — 이름이 같은 내 항목이 있으면 연결하고, 없으면 만든다.
            String name = RecommendationStore.normalize(detection.getName());
            Optional<ChecklistItem> sameName = items.findByTripIdOrderById(tripId).stream()
                    .filter(i -> RecommendationStore.normalize(i.getName()).equals(name))
                    .findFirst();

            ChecklistItem target = sameName.orElseGet(
                    () -> checklistService.createFromPhoto(tripId, name, req.category(), detection.getQty()));
            links.save(new ItemDetection(target.getId(), detection.getId(), USER_CONFIRMED, true));
            current = links.findByDetectedObjectId(detection.getId());
        }

        // 이름·수량 수정을 연결 항목에도 반영한다. 여러 항목에 걸쳐 있으면 임의로 합산하지 않는다.
        if ((req.name() != null || req.qty() != null) && current.size() > 1) {
            throw ApiException.conflict("AMBIGUOUS_LINK",
                    "이 인식 결과가 여러 항목에 연결돼 있습니다. 먼저 병합·수량을 확인해 주세요.");
        }

        for (ItemDetection link : current) {
            link.setConfirmedByUser(true);
            link.setMatchConfidence(USER_CONFIRMED);
            items.findById(link.getChecklistItemId()).ifPresent(item -> {
                if (req.name() != null) item.setName(detection.getName());
                if (req.qty() != null) item.setQty(detection.getQty());
                if (req.category() != null) item.setCategory(req.category());
                // 승인은 실제 챙김 확인이다 — 완료로 올린다.
                item.setCheckStatus(Codes.CheckStatus.PREPARED);
            });
        }
        detection.setApproved(true);
    }

    /**
     * 06: 명시적 승인 취소만 여기 온다. 사진 재분석에서 못 찾은 것과는 다르다 —
     * <b>단순 미인식은 기존 완료 상태를 바꾸지 않는다.</b>
     */
    private void cancelApproval(DetectedObject detection) {
        for (ItemDetection link : links.findByDetectedObjectId(detection.getId())) {
            link.setConfirmedByUser(false);
            items.findById(link.getChecklistItemId()).ifPresent(item -> {
                boolean otherConfirmed = links.findByChecklistItemIdIn(List.of(item.getId())).stream()
                        .anyMatch(other -> !other.getDetectedObjectId().equals(detection.getId())
                                && other.isConfirmedByUser());
                // 다른 확정 연결이 없으면 실제 챙김을 다시 확인하게 한다. 항목은 지우지 않는다.
                if (!otherConfirmed) item.setCheckStatus(Codes.CheckStatus.UNCHECKED);
            });
        }
        detection.setApproved(false);
    }

    /** 06: {@code matchedItemIds} 는 <b>증분이 아니라 전체 교체</b>다. */
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

    // ── 조회 보조 ─────────────────────────────────────────

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

    static PhotoDtos.Detection toDto(DetectedObject d) {
        return new PhotoDtos.Detection(
                d.getId(), d.getPhotoId(), d.getName(), d.getQty(),
                d.getConfidence(), d.getConfidenceLevel(), d.isApproved(),
                d.getMissingInfo(), d.getLabelText());
    }
}
