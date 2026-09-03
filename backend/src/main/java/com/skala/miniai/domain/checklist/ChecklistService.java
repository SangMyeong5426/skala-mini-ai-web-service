package com.skala.miniai.domain.checklist;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.CurrentUser;
import com.skala.miniai.common.Rates;
import com.skala.miniai.domain.ai.AiJob;
import com.skala.miniai.domain.ai.AiJobRepository;
import com.skala.miniai.domain.ai.RecommendationStore;
import com.skala.miniai.domain.master.ItemWeightRepository;
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.DetectedObjectRepository;
import com.skala.miniai.domain.trip.TripService;

/**
 * 내 체크리스트 (UC-05 · UC-06).
 *
 * <p>개정된 계약(06)의 핵심은 <b>추천 생성과 내 목록 등록이 분리</b>됐다는 것이다.
 * 추천은 {@code ai_jobs.output_payload} 에만 남고, 사용자가 고른 것만 여기 INSERT 된다.
 *
 * <p>같은 여행의 항목 추가·수정·삭제·사진 승인·추천 채택은 <b>여행 단위로 직렬화</b>한다.
 * 동시 클릭에도 같은 후보가 두 항목을 만들지 않아야 하기 때문이다.
 */
@Service
public class ChecklistService {

    private final ChecklistItemRepository items;
    private final ItemDetectionRepository links;
    private final DetectedObjectRepository detections;
    private final ItemWeightRepository weights;
    private final AiJobRepository aiJobs;
    private final RecommendationStore recommendations;
    private final PhotoStatusResolver photoStatus;
    private final TripService tripService;
    private final CurrentUser currentUser;

    public ChecklistService(ChecklistItemRepository items, ItemDetectionRepository links,
                            DetectedObjectRepository detections, ItemWeightRepository weights,
                            AiJobRepository aiJobs, RecommendationStore recommendations,
                            PhotoStatusResolver photoStatus, TripService tripService,
                            CurrentUser currentUser) {
        this.items = items;
        this.links = links;
        this.detections = detections;
        this.weights = weights;
        this.aiJobs = aiJobs;
        this.recommendations = recommendations;
        this.photoStatus = photoStatus;
        this.tripService = tripService;
        this.currentUser = currentUser;
    }

    /** 새로 만들었는지({@code 201}) 기존 항목을 돌려주는지({@code 200}) 컨트롤러가 알아야 한다. */
    public record Added(ChecklistDtos.Item item, boolean created) { }

    // ── 조회 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChecklistDtos.ListResponse list(Long tripId) {
        tripService.mustOwn(tripId);
        List<ChecklistItem> rows = items.findByTripIdOrderById(tripId);
        Map<Long, Codes.PhotoStatus> statuses = photoStatus.resolve(rows.stream().map(ChecklistItem::getId).toList());

        List<ChecklistDtos.Item> dtos = rows.stream()
                .map(i -> toDto(i, statuses.get(i.getId())))
                .toList();

        Optional<AiJob> latest = latestCompletedRecommendation(tripId);
        return new ChecklistDtos.ListResponse(
                dtos,
                completionRate(rows),
                latest.map(AiJob::getId).orElse(null),
                unacceptedRequiredCount(rows, latest));
    }

    // ── 직접 추가 · 추천 채택 ──────────────────────────────

    @Transactional
    public Added add(Long tripId, ChecklistDtos.CreateRequest req) {
        tripService.mustOwn(tripId);
        String name = RecommendationStore.normalize(req.name());
        if (name.isEmpty()) {
            throw ApiException.badRequest("물품 이름은 공백일 수 없습니다.", "name");
        }
        return req.recommendation() == null
                ? addDirect(tripId, name, req)
                : accept(tripId, name, req);
    }

    /** 직접 추가. 서버가 {@code source=USER}, {@code checkStatus=UNCHECKED} 를 채운다. */
    private Added addDirect(Long tripId, String name, ChecklistDtos.CreateRequest req) {
        ChecklistItem created = persist(tripId, name,
                req.category() == null ? Codes.Category.ETC : req.category(),
                req.qty() == null ? 1 : req.qty(),
                req.priority() == null ? Codes.Priority.RECOMMENDED : req.priority(),
                Codes.ItemSource.USER);
        return new Added(toDto(created, Codes.PhotoStatus.NOT_IN_PHOTO), true);
    }

    /**
     * 추천 후보 채택. 06 의 재시도 규약을 그대로 따른다 —
     * 이미 채택된 후보나 같은 이름의 기존 항목이 있으면 <b>덮어쓰지 않고</b> 그 항목을 {@code 200} 으로 준다.
     */
    private Added accept(Long tripId, String name, ChecklistDtos.CreateRequest req) {
        ChecklistDtos.RecommendationRef ref = req.recommendation();

        AiJob job = aiJobs.findByIdAndUserId(ref.jobId(), currentUser.id())
                .orElseThrow(() -> ApiException.notFound("추천 작업", ref.jobId()));
        if (job.getJobType() != Codes.JobType.PACKING_LIST || !tripId.equals(job.getTripId())) {
            // 다른 여행·다른 종류의 작업은 존재를 알려주지 않는다.
            throw ApiException.notFound("추천 작업", ref.jobId());
        }
        if (job.getStatus() != Codes.JobStatus.COMPLETED) {
            throw ApiException.conflict("JOB_NOT_COMPLETED", "추천이 아직 끝나지 않았습니다. 잠시 후 다시 시도해 주세요.");
        }

        RecommendationStore.Candidate candidate = recommendations.candidateAt(job, ref.candidateIndex());

        // ① 이미 채택된 후보 — 같은 항목을 그대로 돌려준다. 이름·수량·완료 상태를 되돌리지 않는다.
        if (candidate.acceptedItemId() != null) {
            Optional<ChecklistItem> existing = items.findByIdAndTripId(candidate.acceptedItemId(), tripId);
            if (existing.isPresent()) {
                return new Added(toDto(existing.get(), photoStatusOf(existing.get())), false);
            }
            // 항목이 지워졌다면 연결이 낡은 것이다. 해제하고 아래에서 새로 만든다.
            recommendations.linkCandidate(job, ref.candidateIndex(), null);
        }

        // ② 같은 이름의 내 항목이 이미 있으면 그 항목에 연결만 한다. 상태·수량·출처는 유지한다.
        Optional<ChecklistItem> sameName = items.findByTripIdOrderById(tripId).stream()
                .filter(i -> RecommendationStore.normalize(i.getName()).equals(name)
                        || RecommendationStore.normalize(i.getName())
                                .equals(RecommendationStore.normalize(candidate.name())))
                .findFirst();
        if (sameName.isPresent()) {
            recommendations.linkCandidate(job, ref.candidateIndex(), sameName.get().getId());
            return new Added(toDto(sameName.get(), photoStatusOf(sameName.get())), false);
        }

        // ③ 신규 생성. 출처는 후보의 것을 서버가 복사한다 — 클라이언트가 정하지 않는다.
        Codes.ItemSource source = candidate.source() == null ? Codes.ItemSource.AI : candidate.source();
        ChecklistItem created = persist(tripId, name,
                req.category() != null ? req.category()
                        : candidate.category() != null ? candidate.category() : Codes.Category.ETC,
                req.qty() != null ? req.qty() : candidate.qty(),
                req.priority() != null ? req.priority()
                        : candidate.priority() != null ? candidate.priority() : Codes.Priority.RECOMMENDED,
                source);

        recommendations.linkCandidate(job, ref.candidateIndex(), created.getId());
        return new Added(toDto(created, Codes.PhotoStatus.NOT_IN_PHOTO), true);
    }

    // ── 수정 · 삭제 ───────────────────────────────────────

    @Transactional
    public ChecklistDtos.Item update(Long tripId, Long itemId, ChecklistDtos.UpdateRequest req) {
        tripService.mustOwn(tripId);
        ChecklistItem item = items.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> ApiException.notFound("체크리스트 항목", itemId));

        if (req.name() != null) {
            String name = RecommendationStore.normalize(req.name());
            if (name.isEmpty()) throw ApiException.badRequest("물품 이름은 공백일 수 없습니다.", "name");
            item.setName(name);
            item.setItemWeightId(weightIdOf(name));
        }
        if (req.category() != null) item.setCategory(req.category());
        if (req.qty() != null) {
            if (req.qty() < 1 || req.qty() > 99) {
                throw ApiException.badRequest("수량은 1~99 입니다.", "qty");
            }
            item.setQty(req.qty());
        }
        if (req.priority() != null) item.setPriority(req.priority());
        if (req.checkStatus() != null) item.setCheckStatus(req.checkStatus());

        return toDto(item, photoStatusOf(item));
    }

    /**
     * 06: 삭제는 같은 트랜잭션에서 <b>추천 연결 해제</b>와 <b>사진 승인 해제</b>까지 한다.
     * 그래야 S-04 에서 그 인식 결과를 다시 확인할 수 있다.
     */
    @Transactional
    public void delete(Long tripId, Long itemId) {
        tripService.mustOwn(tripId);
        ChecklistItem item = items.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> ApiException.notFound("체크리스트 항목", itemId));

        latestCompletedRecommendation(tripId)
                .ifPresent(job -> recommendations.unlinkItem(job, itemId));

        // 이 항목에만 확정 연결돼 있던 인식 결과는 승인을 푼다.
        // 다른 항목에도 확정 연결이 남아 있으면 그 승인은 유지한다.
        for (ItemDetection link : links.findByChecklistItemIdIn(List.of(itemId))) {
            boolean confirmedElsewhere = links.findByDetectedObjectId(link.getDetectedObjectId()).stream()
                    .anyMatch(other -> !other.getChecklistItemId().equals(itemId) && other.isConfirmedByUser());
            if (!confirmedElsewhere) {
                detections.findById(link.getDetectedObjectId())
                        .ifPresent(d -> d.setApproved(false));
            }
        }
        links.deleteByChecklistItemId(itemId);
        items.delete(item);
    }

    // ── 다른 서비스가 빌려 쓰는 계산 ──────────────────────

    @Transactional(readOnly = true)
    public BigDecimal completionRate(List<ChecklistItem> rows) {
        long prepared = rows.stream().filter(ChecklistItem::isPrepared).count();
        return Rates.completion(prepared, rows.size());
    }

    @Transactional(readOnly = true)
    public Optional<AiJob> latestCompletedRecommendation(Long tripId) {
        return aiJobs.findTopByTripIdAndJobTypeAndStatusOrderByCompletedAtDescIdDesc(
                tripId, Codes.JobType.PACKING_LIST, Codes.JobStatus.COMPLETED);
    }

    /**
     * 06: 가장 최근 완료된 추천에서 <b>필수인데 아직 내 목록에 없는</b> 후보 수.
     *
     * <p>완료된 추천 작업 자체가 없으면 {@code null} 이다 — "확인 전" 과 "0건" 은 다른 상태다.
     * 내 목록 완료율이 1 이어도 이 경고는 유지된다.
     */
    @Transactional(readOnly = true)
    public Integer unacceptedRequiredCount(List<ChecklistItem> rows, Optional<AiJob> latest) {
        if (latest.isEmpty()) return null;

        Set<String> existingNames = new HashSet<>();
        Set<Long> existingIds = new HashSet<>();
        for (ChecklistItem i : rows) {
            existingNames.add(RecommendationStore.normalize(i.getName()));
            existingIds.add(i.getId());
        }

        int count = 0;
        for (RecommendationStore.Candidate c : recommendations.candidatesOf(latest.get())) {
            if (c.priority() != Codes.Priority.REQUIRED) continue;
            boolean accepted = c.acceptedItemId() != null && existingIds.contains(c.acceptedItemId());
            boolean alreadyInList = existingNames.contains(RecommendationStore.normalize(c.name()));
            if (!accepted && !alreadyInList) count++;
        }
        return count;
    }

    /** 사진 승인이 내 목록에 항목을 만들 때 쓴다 (DetectionService). */
    @Transactional
    public ChecklistItem createFromPhoto(Long tripId, String name, Codes.Category category, int qty) {
        ChecklistItem item = persist(tripId, name,
                category == null ? Codes.Category.ETC : category,
                qty, Codes.Priority.RECOMMENDED, Codes.ItemSource.PHOTO);
        // 06: 사진 승인으로 만든 신규 항목은 곧바로 완료다.
        item.setCheckStatus(Codes.CheckStatus.PREPARED);
        return item;
    }

    public ChecklistDtos.Item toDto(ChecklistItem i, Codes.PhotoStatus status) {
        return new ChecklistDtos.Item(
                i.getId(), i.getName(), i.getCategory(), i.getQty(),
                i.getPriority(), i.getSource(), i.getCheckStatus(),
                status == null ? Codes.PhotoStatus.NOT_IN_PHOTO : status);
    }

    private Codes.PhotoStatus photoStatusOf(ChecklistItem item) {
        return photoStatus.resolve(List.of(item.getId())).get(item.getId());
    }

    private ChecklistItem persist(Long tripId, String name, Codes.Category category, int qty,
                                  Codes.Priority priority, Codes.ItemSource source) {
        ChecklistItem item = new ChecklistItem(tripId, name, category, qty, priority, source,
                Codes.CheckStatus.UNCHECKED);
        item.setItemWeightId(weightIdOf(name));
        return items.save(item);
    }

    /** 무게 마스터에 같은 이름이 있으면 연결한다. 없으면 {@code null} — 무게 계산에서 빠진다. */
    private Long weightIdOf(String name) {
        return weights.findByKeyword(name).map(w -> w.getId()).orElse(null);
    }

    /** 승인 취소 등으로 다른 서비스가 인식 결과를 만졌을 때 최신 상태를 다시 읽기 위해. */
    @Transactional(readOnly = true)
    public List<DetectedObject> approvedDetectionsOf(List<Long> photoIds) {
        return detections.findByPhotoIdInOrderById(photoIds).stream()
                .filter(DetectedObject::isApproved)
                .toList();
    }
}
