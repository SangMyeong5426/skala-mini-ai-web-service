package com.skala.miniai.domain.ai;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.skala.miniai.common.Codes;
import com.skala.miniai.common.Json;
import com.skala.miniai.domain.checklist.ChecklistItem;
import com.skala.miniai.domain.checklist.ChecklistItemRepository;
import com.skala.miniai.domain.photo.DetectedObject;
import com.skala.miniai.domain.photo.DetectionService;
import com.skala.miniai.domain.trip.Trip;

/**
 * AI 작업 입력을 <b>서버가</b> 만든다.
 *
 * <p>06: "서버가 최종 입력을 결정한다." 화면이 보낸 값이 오래됐을 수 있어서다. 화면은
 * 자기가 아는 상태를 보내고, 서버는 현재 내 목록을 읽어 덮어쓴다.
 *
 * <p>같은 함수를 {@code InspectionService} 도 쓴다 — 저장된 무게 작업의 입력과 <b>지금</b>
 * 입력이 같은지 비교해야 하기 때문이다. 두 곳에서 따로 만들면 미묘하게 달라져
 * 무게가 영영 {@code null} 이 된다.
 */
@Component
public class AiInputBuilder {

    private final ChecklistItemRepository items;
    private final DetectionService detectionService;
    private final Json json;

    public AiInputBuilder(ChecklistItemRepository items, DetectionService detectionService, Json json) {
        this.items = items;
        this.detectionService = detectionService;
        this.json = json;
    }

    /** 07 {@code PACKING_LIST input}. {@code alreadyPacked} 는 <b>실제 완료</b> 항목이다. */
    @Transactional(readOnly = true)
    public ObjectNode packingList(Trip trip) {
        ObjectNode input = json.newObject();
        input.put("destination", trip.getDestination());
        input.put("startDate", trip.getStartDate().toString());
        input.put("endDate", trip.getEndDate().toString());
        input.put("transport", trip.getTransport().name());
        input.put("purpose", trip.getPurpose().name());
        if (trip.getNote() == null) input.putNull("note");
        else input.put("note", trip.getNote());

        ArrayNode packed = input.putArray("alreadyPacked");
        for (ChecklistItem i : items.findByTripIdOrderById(trip.getId())) {
            if (!i.isPrepared()) continue;
            ObjectNode node = packed.addObject();
            node.put("name", i.getName());
            node.put("category", i.getCategory().name());
            node.put("qty", i.getQty());
        }
        return input;
    }

    /**
     * 07 {@code WEIGHT_ESTIMATE input}.
     *
     * <p>계산에 넣는 것은 <b>실제 완료 항목만</b>이다. 미완료 항목과 미승인 인식 후보는
     * {@code excluded} 에 이유와 함께 남긴다 — 무엇을 뺐는지 숨기지 않는다 (명세 F-10).
     */
    @Transactional(readOnly = true)
    public ObjectNode weightEstimate(Trip trip) {
        ObjectNode input = json.newObject();
        if (trip.getBagType() == null) input.putNull("bagType");
        else input.put("bagType", trip.getBagType().name());
        if (trip.getBagEmptyG() == null) input.putNull("bagEmptyG");
        else input.put("bagEmptyG", trip.getBagEmptyG());
        if (trip.getWeightLimitG() == null) input.putNull("weightLimitG");
        else input.put("weightLimitG", trip.getWeightLimitG());

        ArrayNode included = input.putArray("items");
        ArrayNode excluded = input.putArray("excluded");

        for (ChecklistItem i : items.findByTripIdOrderById(trip.getId())) {
            if (i.isPrepared()) {
                ObjectNode node = included.addObject();
                node.put("itemId", i.getId());
                node.put("name", i.getName());
                node.put("qty", i.getQty());
            } else {
                ObjectNode node = excluded.addObject();
                node.put("name", i.getName());
                node.put("reason", Codes.CheckStatus.UNCHECKED.name());
            }
        }

        // 승인 전 인식 후보도 계산에서 빼고 이유를 남긴다.
        for (DetectedObject d : detectionService.detectionsOf(trip.getId())) {
            if (d.isApproved()) continue;
            ObjectNode node = excluded.addObject();
            node.put("name", d.getName());
            node.put("reason", "PENDING_APPROVAL");
        }
        return input;
    }

    /** 07 {@code BAG_CHECK input}. 분석할 사진 목록이 전부다. */
    @Transactional(readOnly = true)
    public ObjectNode bagCheck(List<Long> photoIds) {
        ObjectNode input = json.newObject();
        ArrayNode ids = input.putArray("photoIds");
        photoIds.forEach(ids::add);
        return input;
    }
}
