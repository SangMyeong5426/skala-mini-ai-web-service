-- skala-mini-ai-web-service 스키마
--
-- docs/05-erd.md 의 ERD와 짝이다. 한쪽만 고치지 않는다.
-- Supabase / Neon 의 SQL Editor에 붙여넣어 실행한다.
-- 로컬에서 시험하려면 database/README.md 의 "개발용 로컬 DB".

-- 개발 중 재실행을 위해 기존 테이블을 지운다.
-- 운영 DB가 아니므로 데이터 손실을 감수한다. 의존 역순으로 지운다.
DROP TABLE IF EXISTS item_rule_checks   CASCADE;
DROP TABLE IF EXISTS item_detections    CASCADE;
DROP TABLE IF EXISTS ai_jobs            CASCADE;
DROP TABLE IF EXISTS detected_objects   CASCADE;
DROP TABLE IF EXISTS trip_photos        CASCADE;
DROP TABLE IF EXISTS checklist_items    CASCADE;
DROP TABLE IF EXISTS trips              CASCADE;
DROP TABLE IF EXISTS transport_rules    CASCADE;
DROP TABLE IF EXISTS item_weights       CASCADE;
DROP TABLE IF EXISTS users              CASCADE;


-- ── 사용자 ────────────────────────────────────────────────
CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    nickname    VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);


-- ── 품목별 무게 마스터 (UC-10 예상 무게 산정) ─────────────
-- 카테고리 평균이 아니라 최소·대표·최대 범위를 둔다.
-- 명세 F-10: "결과를 실측값처럼 표현하지 않는다"
CREATE TABLE item_weights (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keyword     VARCHAR(100) NOT NULL UNIQUE,
    category    VARCHAR(20)  NOT NULL,
    min_g       INTEGER      NOT NULL,
    typical_g   INTEGER      NOT NULL,
    max_g       INTEGER      NOT NULL,

    -- 책·금속·배터리·액체는 이미지 추정 오차가 크다.
    -- 명세 F-10: 모델명·용량·실측값을 우선 요청하고 없으면 계산에서 뺀다.
    is_dense    BOOLEAN      NOT NULL DEFAULT FALSE,
    note        TEXT,

    CONSTRAINT item_weights_range_check CHECK (min_g <= typical_g AND typical_g <= max_g)
);


-- ── 반입 규정 마스터 (UC-07 항공 반입 규정 확인) ──────────
-- 최종 판정은 이 테이블을 보는 규칙 엔진이 한다. AI가 아니다.
CREATE TABLE transport_rules (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transport      VARCHAR(20)  NOT NULL,
    keyword        VARCHAR(100) NOT NULL,
    verdict        VARCHAR(20)  NOT NULL,
    condition_note TEXT,
    description    TEXT         NOT NULL,

    -- 명세 9절 "규정 최신성": 출처와 확인 날짜를 저장·표시한다.
    source_url     VARCHAR(255) NOT NULL,
    checked_at     DATE         NOT NULL,

    CONSTRAINT transport_rules_transport_check
        CHECK (transport IN ('FLIGHT', 'TRAIN', 'BUS', 'CAR')),
    CONSTRAINT transport_rules_verdict_check
        CHECK (verdict IN ('CABIN_OK', 'CHECKED_OK', 'CHECKED_FORBIDDEN',
                           'RESTRICTED', 'NEED_MORE_INFO', 'ASK_AIRLINE'))
);

CREATE INDEX idx_transport_rules_lookup ON transport_rules (transport, keyword);


-- ── 여행 (UC-02) ──────────────────────────────────────────
CREATE TABLE trips (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 출발지·도착지는 이동수단과 무관하게 필수다.
    -- docs/03-wireframe.md S-02 · docs/02-use-case.md UC-02 기본 흐름 1단계.
    -- departure_airport 는 항공 전용이라 기차·버스·자차 여행의 출발지를 담지 못한다.
    origin            VARCHAR(100) NOT NULL,
    destination       VARCHAR(100) NOT NULL,
    country_code      CHAR(2),
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    purpose           VARCHAR(20)  NOT NULL,
    transport         VARCHAR(20)  NOT NULL,

    -- 항공 이용 시에만 채운다. 비면 일반 기준만 적용되고 정확도가 낮아진다.
    airline           VARCHAR(50),
    departure_airport CHAR(3),
    arrival_airport   CHAR(3),

    -- 무게 산정의 시작값. 명세 F-10 산정식의 "빈 가방 무게 범위".
    bag_type          VARCHAR(20),
    bag_empty_g       INTEGER,
    weight_limit_g    INTEGER,

    note              TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT trips_date_check      CHECK (start_date <= end_date),
    CONSTRAINT trips_purpose_check   CHECK (purpose   IN ('TOUR','BUSINESS','REST','STUDY')),
    CONSTRAINT trips_transport_check CHECK (transport IN ('FLIGHT','TRAIN','BUS','CAR')),
    CONSTRAINT trips_status_check    CHECK (status    IN ('DRAFT','CONFIRMED','DONE')),
    CONSTRAINT trips_bag_type_check  CHECK (bag_type IS NULL OR bag_type IN ('CARRY_ON','MEDIUM','LARGE'))
);

CREATE INDEX idx_trips_user_created ON trips (user_id, created_at DESC);


-- ── 체크리스트 항목 (UC-05·06) ────────────────────────────
CREATE TABLE checklist_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id        BIGINT       NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

    -- 무게 추정에 쓴다. 마스터에 없는 물품도 있으므로 nullable.
    item_weight_id BIGINT       REFERENCES item_weights(id) ON DELETE SET NULL,

    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(20)  NOT NULL,
    qty            INTEGER      NOT NULL DEFAULT 1,
    priority       VARCHAR(20)  NOT NULL,

    -- 누가 넣었는지. 규칙 기반 / 사진 인식 승인 / AI 추천 / 사용자 직접.
    -- PHOTO 와 AI 를 나누는 이유는 S-05 의 출처 배지 때문이다. 사진에서 확인된 것과
    -- AI 가 덧붙인 부족분은 사용자에게 다르게 보여야 한다. (docs/03-wireframe.md S-05)
    source         VARCHAR(10)  NOT NULL,

    -- 명세의 핵심 원칙: 사진에서 못 찾은 것을 "누락"이라 하지 않는다.
    -- NOT_IN_PHOTO 는 "사진에서 미확인"이지 "없다"가 아니다.
    check_status   VARCHAR(20)  NOT NULL DEFAULT 'UNCHECKED',

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT checklist_items_qty_check      CHECK (qty > 0),
    CONSTRAINT checklist_items_category_check
        CHECK (category IN ('DOCUMENT','CLOTHING','ELECTRONIC','TOILETRY','MEDICINE','ETC')),
    CONSTRAINT checklist_items_priority_check CHECK (priority IN ('REQUIRED','RECOMMENDED')),
    CONSTRAINT checklist_items_source_check   CHECK (source   IN ('RULE','PHOTO','AI','USER')),
    CONSTRAINT checklist_items_status_check
        CHECK (check_status IN ('UNCHECKED','PREPARED','NEEDS_CHECK','NOT_IN_PHOTO'))
);

CREATE INDEX idx_checklist_items_trip ON checklist_items (trip_id, category);


-- ── 짐 사진 (UC-03) ───────────────────────────────────────
CREATE TABLE trip_photos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id     BIGINT       NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

    -- UPLOAD_DIR 기준 상대 경로. 원본 파일은 저장소에 커밋하지 않는다.
    file_path   VARCHAR(255) NOT NULL,
    bag_kind    VARCHAR(20),
    uploaded_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT trip_photos_bag_kind_check
        CHECK (bag_kind IS NULL OR bag_kind IN ('CABIN','CHECKED'))
);

CREATE INDEX idx_trip_photos_trip ON trip_photos (trip_id);


-- ── 사진에서 인식된 물품 (UC-04) ──────────────────────────
CREATE TABLE detected_objects (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    photo_id         BIGINT       NOT NULL REFERENCES trip_photos(id) ON DELETE CASCADE,

    name             VARCHAR(100) NOT NULL,
    qty              INTEGER      NOT NULL DEFAULT 1,
    confidence       NUMERIC(4,3) NOT NULL,

    -- confidence 에서 계산되지만 컬럼으로 둔다. 경계값이 바뀌어도
    -- 사용자가 그때 보고 승인한 표시는 그대로여야 하기 때문이다.
    -- 근거는 docs/05-erd.md "confidence_level 을 따로 두는 근거".
    confidence_level VARCHAR(10)  NOT NULL,

    -- BAG_CHECK output 의 missingInfo / labelText (docs/07-ai-ready.md).
    -- 원칙 ②: 보이지 않는 속성은 추정하지 않고 묻는다 — 무엇을 물을지가 행에 남아야
    -- S-04 를 나갔다 들어와도 "확인 필요" 묶음을 그릴 수 있다.
    missing_info     VARCHAR(100),
    label_text       VARCHAR(200),

    -- 명세 9.2 수용 기준:
    -- "사진 분석 결과는 사용자가 승인하기 전 최종 준비 상태에 반영되지 않아야 한다"
    approved         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT detected_objects_qty_check        CHECK (qty > 0),
    CONSTRAINT detected_objects_confidence_check CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT detected_objects_level_check      CHECK (confidence_level IN ('HIGH','MEDIUM','LOW'))
);

CREATE INDEX idx_detected_objects_photo ON detected_objects (photo_id);


-- ══════════════════════════════════════════════════════════
--  ★ N:M 관계 1 — 체크리스트 항목 ↔ 인식 물품
--
--  명세 F-06: "동일 물품명 불일치 → 유사한 후보를 제시하고
--              사용자가 연결하도록 한다"
--
--  연결 자체가 정보를 갖는다(신뢰도·승인 여부)므로 조인 테이블에 속성이 있다.
--  단순 매핑이었다면 FK 두 개로 끝났을 것이다.
-- ══════════════════════════════════════════════════════════
CREATE TABLE item_detections (
    checklist_item_id  BIGINT       NOT NULL REFERENCES checklist_items(id)  ON DELETE CASCADE,
    detected_object_id BIGINT       NOT NULL REFERENCES detected_objects(id) ON DELETE CASCADE,

    match_confidence   NUMERIC(4,3) NOT NULL,

    -- AI 가 제안한 연결과 사람이 승인한 연결을 구분한다.
    confirmed_by_user  BOOLEAN      NOT NULL DEFAULT FALSE,
    matched_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (checklist_item_id, detected_object_id),
    CONSTRAINT item_detections_confidence_check
        CHECK (match_confidence >= 0 AND match_confidence <= 1)
);


-- ══════════════════════════════════════════════════════════
--  ★ N:M 관계 2 — 체크리스트 항목 ↔ 반입 규정
--
--  한 물품이 여러 규정에 걸린다 (200ml 화장품 = 액체 + 총량 제한)
--  한 규정이 여러 물품에 적용된다 (액체 100ml 규정)
--  같은 규정이라도 물품마다 판정이 다르므로 조인 테이블에 verdict 가 있다.
-- ══════════════════════════════════════════════════════════
CREATE TABLE item_rule_checks (
    checklist_item_id BIGINT      NOT NULL REFERENCES checklist_items(id) ON DELETE CASCADE,
    rule_id           BIGINT      NOT NULL REFERENCES transport_rules(id) ON DELETE CASCADE,

    verdict           VARCHAR(20) NOT NULL,

    -- NEED_MORE_INFO 일 때 무엇이 부족한지. 예: '용량(ml)', '배터리 Wh'
    -- 명세: "필수정보가 없으면 판정을 보류한다"
    missing_info      VARCHAR(100),
    decided_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (checklist_item_id, rule_id),
    CONSTRAINT item_rule_checks_verdict_check
        CHECK (verdict IN ('CABIN_OK','CHECKED_OK','CHECKED_FORBIDDEN',
                           'RESTRICTED','NEED_MORE_INFO','ASK_AIRLINE'))
);


-- ── AI 작업 ───────────────────────────────────────────────
-- AI-Ready 원칙 2 (Structured Data).
-- 지금은 Mock 이 채우고, 나중에 LLM·비전 모델이 같은 자리를 채운다.
-- 실제 AI 를 붙일 때 이 테이블은 바뀌지 않는다.
CREATE TABLE ai_jobs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- nullable 이다. 챗봇(UC-08)은 여행을 등록하지 않아도 쓸 수 있는 보조 흐름이라
    -- RULE_CHECK 작업이 여행 없이 생길 수 있다.
    -- (UC-07 반입 규정 확인은 사전조건이 여행 정보 등록이므로 근거가 되지 않는다)
    trip_id         BIGINT       REFERENCES trips(id) ON DELETE CASCADE,

    -- AI-Ready 원칙 3 (Asynchronous Pipeline): 상태를 DB 에 둬야
    -- 응답이 느린 AI 를 비동기로 처리할 수 있다.
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    job_type        VARCHAR(30)  NOT NULL,

    -- 입출력을 jsonb 로 두는 근거는 docs/05-erd.md 참조.
    -- 내부 구조는 docs/07-ai-ready.md 의 JSON Schema 로 고정한다.
    input_payload   JSONB        NOT NULL,
    output_payload  JSONB,

    -- 메타데이터: 코드 변경 없이 모델을 바꿔 끼우기 위한 칸
    model_name      VARCHAR(100),
    error_message   TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT ai_jobs_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ai_jobs_type_check
        CHECK (job_type IN ('PACKING_LIST', 'BAG_CHECK', 'WEIGHT_ESTIMATE', 'RULE_CHECK'))
);

-- 사용자별 작업 목록 조회용
CREATE INDEX idx_ai_jobs_user_created ON ai_jobs (user_id, created_at DESC);
-- 상태별 조회용 (처리 중인 작업 찾기)
CREATE INDEX idx_ai_jobs_status ON ai_jobs (status);
-- 여행별·종류별 조회용 (같은 여행의 체크리스트 생성 이력 등)
CREATE INDEX idx_ai_jobs_trip_type ON ai_jobs (trip_id, job_type);
