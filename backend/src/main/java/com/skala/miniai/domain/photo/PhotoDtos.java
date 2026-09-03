package com.skala.miniai.domain.photo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.skala.miniai.common.Codes;

/** 짐 사진과 인식 결과 API 의 요청·응답 (S-03 · S-04). */
public final class PhotoDtos {

    private PhotoDtos() { }

    /** {@code fileUrl} 은 {@code /uploads/} 를 붙인 <b>URL</b> 이다. DB 의 상대 경로가 아니다. */
    public record Photo(Long photoId, String fileUrl, Codes.BagKind bagKind, OffsetDateTime uploadedAt) { }

    public record PhotoListResponse(List<Photo> photos) { }

    /** 목록에 붙는 간단한 연결 정보. {@code confirmedByUser=false} 는 <b>아직 사후 수정을 안 했다</b>는 뜻이다. */
    public record DetectionLink(Long itemId, boolean confirmedByUser) { }

    /**
     * 인식 결과. <b>{@code approved} 가 없다.</b>
     *
     * <p>06 개정: 인식 물품은 승인 없이 내 목록에 등록되므로 승인 여부라는 필드가 성립하지 않는다.
     * DB 컬럼은 호환 목적으로만 남는다(05).
     */
    public record Detection(
            Long detectionId, Long photoId, String name, Integer qty,
            BigDecimal confidence, Codes.ConfidenceLevel confidenceLevel,
            String missingInfo, String labelText,
            List<DetectionLink> linkedItems) { }

    public record DetectionListResponse(List<Detection> detections) { }

    /**
     * <b>선택적 사후 수정</b> 요청. 등록용 승인 API 가 아니다.
     *
     * <p>{@code approved} 는 이전 계약의 필드라 값이 오면 {@code 400} 이다 — 조용히 무시하면
     * FE 가 승인 버튼을 그대로 둔 채 동작한다고 믿는다.
     */
    public record PatchRequest(
            Boolean approved, String name, Integer qty,
            Codes.Category category, List<Long> matchedItemIds) { }

    public record LinkedItem(
            Long itemId, String name, boolean confirmedByUser,
            Codes.ItemSource source, Codes.CheckStatus checkStatus) { }

    /** 수정한 <b>주 자원은 인식 결과</b>다. 연결 항목을 함께 고쳤더라도 응답의 주인공은 detection 이다. */
    public record PatchResponse(
            Long detectionId, String name, Integer qty,
            List<LinkedItem> linkedItems) { }
}
