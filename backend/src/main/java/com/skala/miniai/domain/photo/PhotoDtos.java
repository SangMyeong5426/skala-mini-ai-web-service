package com.skala.miniai.domain.photo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.skala.miniai.common.Codes;

/** 짐 사진과 인식 결과 API 의 응답 (S-03 · S-04). */
public final class PhotoDtos {

    private PhotoDtos() { }

    /** {@code fileUrl} 은 {@code /uploads/} 를 붙인 <b>URL</b> 이다. DB 의 상대 경로가 아니다. */
    public record Photo(Long photoId, String fileUrl, Codes.BagKind bagKind, OffsetDateTime uploadedAt) { }

    public record PhotoListResponse(List<Photo> photos) { }

    public record Detection(
            Long detectionId, Long photoId, String name, Integer qty,
            BigDecimal confidence, Codes.ConfidenceLevel confidenceLevel,
            boolean approved, String missingInfo, String labelText) { }

    public record DetectionListResponse(List<Detection> detections) { }

    /** 승인 요청. {@code matchedItemIds} 생략과 빈 배열은 <b>뜻이 다르다</b> (06). */
    public record ApproveRequest(
            Boolean approved, String name, Integer qty,
            Codes.Category category, List<Long> matchedItemIds) { }

    public record LinkedItem(
            Long itemId, String name, boolean confirmedByUser,
            Codes.ItemSource source, Codes.CheckStatus checkStatus) { }

    /** 수정한 <b>주 자원은 인식 결과</b>다. 항목을 새로 만들었더라도 응답의 주인공은 detection 이다. */
    public record ApproveResponse(
            Long detectionId, boolean approved, String name, Integer qty,
            List<LinkedItem> linkedItems) { }
}
