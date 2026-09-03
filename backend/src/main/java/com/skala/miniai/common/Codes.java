package com.skala.miniai.common;

/**
 * 도메인 코드 값 모음.
 *
 * <p>전부 {@code database/schema.sql} 의 CHECK 제약과 <b>같은 값</b>이다.
 * 한쪽만 늘리면 기동은 되지만 INSERT 에서 터진다 — 값을 늘릴 때는 SQL·05-erd·06 을 함께 고친다.
 *
 * <p>enum 을 한 파일에 모은 이유는 값이 짧고 서로 함께 읽히기 때문이다.
 * 도메인마다 흩으면 "지금 쓸 수 있는 값이 뭐냐" 를 여러 파일에서 찾아야 한다.
 */
public final class Codes {

    private Codes() { }

    public enum Purpose { TOUR, BUSINESS, REST, STUDY }

    public enum Transport { FLIGHT, TRAIN, BUS, CAR }

    public enum TripStatus { DRAFT, CONFIRMED, DONE }

    public enum BagType { CARRY_ON, MEDIUM, LARGE }

    public enum Category { DOCUMENT, CLOTHING, ELECTRONIC, TOILETRY, MEDICINE, ETC }

    public enum Priority { REQUIRED, RECOMMENDED }

    /** 최초 등록 경로. {@code PHOTO} 는 사진 승인 생성, {@code AI}·{@code RULE} 은 후보 채택, {@code USER} 는 직접 추가. */
    public enum ItemSource { RULE, PHOTO, AI, USER }

    /**
     * 실제 챙김 상태. <b>{@code PREPARED} 만 완료</b>이고 나머지는 미완료다 (06 개정).
     *
     * <p>{@code NEEDS_CHECK}·{@code NOT_IN_PHOTO} 는 개정 전에 사진 상태를 겸하던 값이다.
     * 지금 사진 상태는 {@link PhotoStatus} 로 계산해서 내보내므로 새로 쓰지 않는다.
     * SQL CHECK 제약과 기존 시드 때문에 값 자체는 남겨 둔다.
     */
    public enum CheckStatus { UNCHECKED, PREPARED, NEEDS_CHECK, NOT_IN_PHOTO }

    /**
     * 사진 비교 상태. <b>컬럼이 아니라 조회 시 계산값</b>이다 (05-erd 개정).
     * 실제 완료 상태와 독립적이다 — 사진에서 못 찾았다고 완료를 취소하지 않는다.
     */
    public enum PhotoStatus { CONFIRMED, NEEDS_CHECK, NOT_IN_PHOTO }

    public enum BagKind { CABIN, CHECKED }

    public enum ConfidenceLevel { HIGH, MEDIUM, LOW }

    public enum RuleVerdict { CABIN_OK, CHECKED_OK, CHECKED_FORBIDDEN, RESTRICTED, NEED_MORE_INFO, ASK_AIRLINE }

    public enum JobType { PACKING_LIST, BAG_CHECK, WEIGHT_ESTIMATE, RULE_CHECK }

    public enum JobStatus { PENDING, COMPLETED, FAILED }

    public enum ItineraryKind { FLIGHT, LODGING, ACTIVITY, TRANSPORT, OTHER }

    /** 3D 가방 안 구역. 좌표만으로는 "앞주머니에 넣었다" 를 표현할 수 없다. */
    public enum Compartment { MAIN_LEFT, MAIN_RIGHT, FRONT_POCKET, MESH, TOP }

    /** 06: {@code ROOM}(여유) · {@code NEAR}(근접) · {@code OVER_RISK}(초과 가능성) · {@code UNKNOWN}(정보 부족). */
    public enum WeightVerdict { ROOM, NEAR, OVER_RISK, UNKNOWN }
}
