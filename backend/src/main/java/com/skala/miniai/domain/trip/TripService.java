package com.skala.miniai.domain.trip;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.CurrentUser;
import com.skala.miniai.common.Rates;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;

/**
 * 여행 등록·조회·수정·삭제 (UC-02 · UC-09).
 *
 * <p>다른 도메인 서비스가 <b>여행 소유권 확인</b>을 여기서 빌려 쓴다({@link #mustOwn}).
 * 컨트롤러마다 같은 검사를 복사하면 한 군데를 빠뜨렸을 때 남의 여행이 열린다.
 */
@Service
public class TripService {

    private final TripRepository trips;
    private final ChecklistItemRepository items;
    private final CurrentUser currentUser;

    public TripService(TripRepository trips, ChecklistItemRepository items, CurrentUser currentUser) {
        this.trips = trips;
        this.items = items;
        this.currentUser = currentUser;
    }

    /** 이 사용자의 여행이 맞는지 확인하고 돌려준다. 아니면 {@code 404} — 있는지 없는지도 알려주지 않는다. */
    @Transactional(readOnly = true)
    public Trip mustOwn(Long tripId) {
        return trips.findByIdAndUserId(tripId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("여행", tripId));
    }

    /**
     * 쓰기 전에 부른다. 소유권 확인과 함께 <b>여행 행에 락을 건다.</b>
     *
     * <p>같은 여행의 항목 추가·수정·삭제·사진 승인·추천 채택이 이 락 하나로 직렬화된다.
     * 락 대상을 여행으로 잡은 이유는 그 단위로 동시성이 생기기 때문이다 — 한 사용자가
     * 한 화면에서 여러 번 누르는 상황이다.
     */
    @Transactional
    public Trip mustOwnForUpdate(Long tripId) {
        return trips.findWithLockByIdAndUserId(tripId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("여행", tripId));
    }

    @Transactional(readOnly = true)
    public TripDtos.ListResponse list() {
        List<TripDtos.Summary> summaries = trips.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(t -> new TripDtos.Summary(
                        t.getId(), t.getOrigin(), t.getDestination(),
                        t.getStartDate(), t.getEndDate(),
                        t.getTransport(), t.getStatus(), completionOf(t.getId())))
                .toList();
        return new TripDtos.ListResponse(summaries);
    }

    @Transactional(readOnly = true)
    public TripDtos.Detail detail(Long tripId) {
        Trip t = mustOwn(tripId);
        return new TripDtos.Detail(
                t.getId(), t.getOrigin(), t.getDestination(),
                t.getStartDate(), t.getEndDate(),
                t.getTransport(), t.getStatus(), completionOf(t.getId()),
                t.getCountryCode(), t.getPurpose(),
                t.getAirline(), t.getDepartureAirport(), t.getArrivalAirport(),
                t.getBagType(), t.getBagEmptyG(), t.getWeightLimitG(), t.getNote());
    }

    @Transactional
    public TripDtos.CreateResponse create(TripDtos.CreateRequest req) {
        if (req.startDate().isAfter(req.endDate())) {
            throw ApiException.badRequest("귀국일이 출발일보다 빠릅니다.", "endDate");
        }
        Trip t = new Trip(currentUser.id(), req.origin().trim(), req.destination().trim());
        t.setCountryCode(req.countryCode());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setPurpose(req.purpose());
        t.setTransport(req.transport());
        t.setAirline(req.airline());
        t.setDepartureAirport(req.departureAirport());
        t.setArrivalAirport(req.arrivalAirport());
        t.setBagType(req.bagType());
        t.setBagEmptyG(req.bagEmptyG());
        t.setWeightLimitG(req.weightLimitG());
        t.setNote(req.note());
        // 06: 생성 직후는 DRAFT 다. 클라이언트가 status 를 정하지 않는다.
        t.setStatus(Codes.TripStatus.DRAFT);

        Trip saved = trips.save(t);
        return new TripDtos.CreateResponse(
                saved.getId(), saved.getOrigin(), saved.getDestination(),
                saved.getStartDate(), saved.getEndDate(),
                saved.getTransport(), saved.getStatus(), saved.getCreatedAt());
    }

    @Transactional
    public TripDtos.Detail update(Long tripId, TripDtos.UpdateRequest req) {
        Trip t = mustOwnForUpdate(tripId);

        if (req.origin() != null) t.setOrigin(req.origin().trim());
        if (req.destination() != null) t.setDestination(req.destination().trim());
        if (req.countryCode() != null) t.setCountryCode(req.countryCode());
        if (req.startDate() != null) t.setStartDate(req.startDate());
        if (req.endDate() != null) t.setEndDate(req.endDate());
        if (req.purpose() != null) t.setPurpose(req.purpose());
        if (req.transport() != null) t.setTransport(req.transport());
        if (req.airline() != null) t.setAirline(req.airline());
        if (req.departureAirport() != null) t.setDepartureAirport(req.departureAirport());
        if (req.arrivalAirport() != null) t.setArrivalAirport(req.arrivalAirport());
        if (req.bagType() != null) t.setBagType(req.bagType());
        if (req.bagEmptyG() != null) t.setBagEmptyG(req.bagEmptyG());
        if (req.weightLimitG() != null) t.setWeightLimitG(req.weightLimitG());
        if (req.note() != null) t.setNote(req.note());
        if (req.status() != null) t.setStatus(req.status());

        // 두 날짜 중 하나만 바꿔도 역전될 수 있다. 바꾼 뒤에 검사한다.
        if (t.getStartDate().isAfter(t.getEndDate())) {
            throw ApiException.badRequest("귀국일이 출발일보다 빠릅니다.", "endDate");
        }
        return detail(tripId);
    }

    @Transactional
    public void delete(Long tripId) {
        Trip t = mustOwnForUpdate(tripId);
        // 체크리스트·사진·일정·AI 작업은 FK 의 ON DELETE CASCADE 로 함께 지워진다.
        trips.delete(t);
    }

    /** 06 공통 규약. 홈·S-05·S-06 이 같은 식을 쓰도록 여기 하나만 둔다. */
    private java.math.BigDecimal completionOf(Long tripId) {
        long total = items.countByTripId(tripId);
        long prepared = items.countByTripIdAndCheckStatus(tripId, Codes.CheckStatus.PREPARED);
        return Rates.completion(prepared, total);
    }
}
