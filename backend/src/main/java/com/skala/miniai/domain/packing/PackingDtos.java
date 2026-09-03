package com.skala.miniai.domain.packing;

import java.math.BigDecimal;
import java.util.List;

import com.skala.miniai.common.Codes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 3D 가방 정리 배치 (S-12).
 *
 * <p>좌표는 <b>0~1 상대값</b>이다. 픽셀을 주고받으면 화면 크기가 다른 기기에서 자리가 어긋난다.
 */
public final class PackingDtos {

    private PackingDtos() { }

    public record Placement(
            @NotNull(message = "물품 ID는 필수입니다.") Long itemId,
            @NotNull(message = "구역은 필수입니다.") Codes.Compartment compartment,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal posX,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal posY,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal posZ,
            Boolean rotated) { }

    /** 전체 배치를 통째로 교체한다. 드래그 한 번에 요청 하나씩 보내면 순서가 뒤집힌다. */
    public record SaveRequest(List<@NotNull(message = "배치 항목은 null일 수 없습니다.") @Valid Placement> placements) { }

    /** 아직 자리를 못 잡은 물품. 화면 오른쪽 "정리 대기" 목록이다. */
    public record Unplaced(Long itemId, String name, Codes.Category category, Integer qty) { }

    public record Response(
            Long tripId,
            List<Placement> placements,
            List<Unplaced> unplaced) { }
}
