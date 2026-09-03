package com.skala.miniai.domain.packing;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skala.miniai.common.ApiException;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.trip.TripService;

/**
 * 3D 가방 정리 배치 (S-12).
 *
 * <p>저장은 <b>전체 교체</b>다. 드래그마다 요청을 보내면 네트워크 순서가 뒤집혔을 때
 * 물건이 엉뚱한 자리에 남는다. 화면이 현재 배치 전체를 한 번에 보낸다.
 *
 * <p>배치는 <b>그 여행의 체크리스트 항목만</b> 가리킬 수 있다. 다른 여행의 항목 ID 를 보내면
 * {@code 400} 이다 — 조용히 무시하면 화면은 저장됐다고 믿는다.
 */
@Service
public class PackingService {

    private final ItemPlacementRepository placements;
    private final ChecklistItemRepository items;
    private final TripService tripService;

    public PackingService(ItemPlacementRepository placements, ChecklistItemRepository items,
                          TripService tripService) {
        this.placements = placements;
        this.items = items;
        this.tripService = tripService;
    }

    @Transactional(readOnly = true)
    public PackingDtos.Response layout(Long tripId) {
        tripService.mustOwn(tripId);
        List<ChecklistItem> rows = items.findByTripIdOrderById(tripId);
        List<Long> itemIds = rows.stream().map(ChecklistItem::getId).toList();

        Map<Long, ItemPlacement> placed = placements.findByChecklistItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(ItemPlacement::getChecklistItemId, Function.identity()));

        List<PackingDtos.Placement> placedDtos = rows.stream()
                .filter(i -> placed.containsKey(i.getId()))
                .map(i -> toDto(placed.get(i.getId())))
                .toList();

        List<PackingDtos.Unplaced> unplaced = rows.stream()
                .filter(i -> !placed.containsKey(i.getId()))
                .map(i -> new PackingDtos.Unplaced(i.getId(), i.getName(), i.getCategory(), i.getQty()))
                .toList();

        return new PackingDtos.Response(tripId, placedDtos, unplaced);
    }

    @Transactional
    public PackingDtos.Response save(Long tripId, PackingDtos.SaveRequest req) {
        tripService.mustOwn(tripId);
        List<PackingDtos.Placement> incoming = req.placements() == null ? List.of() : req.placements();

        Set<Long> ownIds = items.findByTripIdOrderById(tripId).stream()
                .map(ChecklistItem::getId)
                .collect(Collectors.toSet());

        Set<Long> seen = new HashSet<>();
        for (PackingDtos.Placement p : incoming) {
            if (!ownIds.contains(p.itemId())) {
                throw ApiException.badRequest(
                        "이 여행의 체크리스트 항목이 아닙니다: " + p.itemId(), "placements.itemId");
            }
            if (!seen.add(p.itemId())) {
                // 한 물품은 한 자리다. 중복을 받으면 어느 쪽이 맞는지 서버가 정하게 된다.
                throw ApiException.badRequest(
                        "같은 물품이 두 번 배치됐습니다: " + p.itemId(), "placements.itemId");
            }
        }

        // 전체 교체다. 이번에 안 온 항목은 가방에서 뺀 것이다.
        placements.deleteByChecklistItemIdIn(ownIds);
        placements.flush();

        for (PackingDtos.Placement p : incoming) {
            placements.save(new ItemPlacement(
                    p.itemId(), p.compartment(),
                    scale(p.posX()), scale(p.posY()), scale(p.posZ()),
                    Boolean.TRUE.equals(p.rotated())));
        }
        return layout(tripId);
    }

    /** "정리 초기화" — 배치만 지운다. 체크리스트 항목과 완료 상태는 그대로다. */
    @Transactional
    public void reset(Long tripId) {
        tripService.mustOwn(tripId);
        Set<Long> ownIds = items.findByTripIdOrderById(tripId).stream()
                .map(ChecklistItem::getId)
                .collect(Collectors.toSet());
        if (!ownIds.isEmpty()) placements.deleteByChecklistItemIdIn(ownIds);
    }

    private static PackingDtos.Placement toDto(ItemPlacement p) {
        return new PackingDtos.Placement(
                p.getChecklistItemId(), p.getCompartment(),
                p.getPosX(), p.getPosY(), p.getPosZ(), p.isRotated());
    }

    /** {@code NUMERIC(4,3)} 이라 소수 셋째 자리까지다. 더 긴 값이 오면 반올림해 저장한다. */
    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(3, java.math.RoundingMode.HALF_UP);
    }
}
