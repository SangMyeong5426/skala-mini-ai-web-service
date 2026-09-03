# 데이터 모델링 (ERD)

> 발표 3번 섹션: DB 데이터 모델링(ERD)
>
> 채점 기준: **"ERD 테이블 관계(1:N, N:M) 및 정규화 타당성"**
> Peer Review: **"데이터 모델링(ERD) 관계 및 정규화가 적절한가?"**

## ERD 다이어그램

![ERD](images/05-erd.png)

- 원본: [`images/05-erd.puml`](images/05-erd.puml) (PlantUML)
- 벡터: [`images/05-erd.svg`](images/05-erd.svg) — 발표 슬라이드용
- **로그인 포함 목표 ERD:** 아래 DSL·`.puml`·PNG·SVG에 `users.login_id`를 추가 설계한다.
  현재 `schema.sql`·DB에 적용된 것으로 보지 않는다. 인증 구현 시 SQL·시드·엔티티를 함께 반영해야 한다
- **테이블은 12개다.** `trip_itineraries`(여행 일정·캘린더)와 `item_placements`(3D 가방 정리)는
  `login_id`와 달리 **`schema.sql`에 실제로 적용돼 있다.** 무엇이 적용됐고 무엇이 목표인지
  섞이지 않게 둘을 나눠 적는다
- **PNG·SVG 는 2026-09-03 에 아래 DSL 기준으로 재렌더했다.** DSL·`.puml`·`schema.sql` 이
  `users.login_id` 하나만 빼고 일치한다. 정본은 아래 DSL 이다
- dbdiagram.io 링크: TBD — 아래 DSL을 붙여 넣으면 즉시 생성된다

> **2026-09-03 로그인 개정:** 체크리스트 저장 규약은 유지하며 회원의 로그인 아이디를 추가한다.
> 원본·PNG·SVG에도 후보 JSON·채택 책임·사진 자동 등록 트랜잭션과 조회 계산값을 반영했다.
> SQL의 기존 시드는 개정 전 상태이므로 새 자동 등록 흐름을 검증한 데이터로 보지 않는다.
>
> **2026-09-03 일정·정리 추가:** `trip_itineraries`·`item_placements` 두 표를 더했다.
> 기존 10개 테이블과 관계는 그대로다. `users` 에는 `login_id`·`password_hash` 가 늘었다. **팀 DB 에는 `schema.sql` 전체 재실행이 아니라
> [`database/migrations/`](../database/migrations/) 의 파일로 두 표만 더한다** —
> 전체 재실행은 맨 앞에서 모든 테이블을 DROP 해 실데이터를 지운다.

> 다이어그램은 **PlantUML로 그렸다.** Use-Case·User Flow·아키텍처와 같은 도구라
> `.puml` 원본이 저장소에 남고 버전 관리가 된다. dbdiagram.io는 DSL을 붙여 넣어
> 교차 확인하는 용도로 쓴다.

## 스키마 정의 (dbdiagram.io DSL)

```dbml
// ══════════════════════════════════════════════════════════
//  AI 여행가방 확인 플랫폼 — 데이터 모델
//  출처: Notion「기능 정의」v2 · docs/02-use-case.md UC-01~10
// ══════════════════════════════════════════════════════════

Table users {
  id            bigserial   [pk]
  login_id      varchar(30) [not null, unique, note: '로그인용 아이디, 소문자 정규화. SQL 반영 예정']
  email         varchar(255)[not null, unique]
  password_hash varchar(255)[not null]  // bcrypt 해시. 평문 저장·응답 금지
  nickname      varchar(50) [not null]
  created_at    timestamptz [not null, default: `now()`]
}

// ── 여행 (UC-02 / F-02) ───────────────────────────────────
Table trips {
  id              bigserial   [pk]
  user_id         bigint      [not null, ref: > users.id]   // 1:N

  origin          varchar(100)[not null, note: '출발 도시. 이동수단과 무관하게 필수']
  destination     varchar(100)[not null, note: '도착 도시']
  country_code    char(2)     [note: 'ISO 3166-1 alpha-2']
  start_date      date        [not null]
  end_date        date        [not null]
  purpose         varchar(20) [not null, note: 'TOUR | BUSINESS | REST | STUDY']
  transport       varchar(20) [not null, note: 'FLIGHT | TRAIN | BUS | CAR']

  airline         varchar(50) [note: '항공 이용 시. 없으면 일반 기준만 적용된다']
  departure_airport char(3)   [note: 'IATA. 예: ICN']
  arrival_airport   char(3)

  bag_type        varchar(20) [note: 'CARRY_ON | MEDIUM | LARGE']
  bag_empty_g     integer     [note: '빈 가방 무게(g). F-10 산정식의 시작값']
  weight_limit_g  integer     [note: '항공사 허용 한도(g)']

  note            text        [note: '자유 메모. AI 가 해석한다']
  status          varchar(20) [not null, default: 'DRAFT', note: 'DRAFT | CONFIRMED | DONE']
  created_at      timestamptz [not null, default: `now()`]
}

// ── 체크리스트 항목 (UC-05·06 / F-05·F-06) ────────────────
Table checklist_items {
  id            bigserial   [pk]
  trip_id       bigint      [not null, ref: > trips.id]     // 1:N
  item_weight_id bigint     [ref: > item_weights.id, note: '무게 추정에 쓴다. 없을 수 있다']

  name          varchar(100)[not null]
  category      varchar(20) [not null, note: 'DOCUMENT | CLOTHING | ELECTRONIC | TOILETRY | MEDICINE | ETC']
  qty           integer     [not null, default: 1]
  priority      varchar(20) [not null, note: 'REQUIRED | RECOMMENDED']
  source        varchar(10) [not null, note: 'RULE | PHOTO | AI | USER — 누가 넣었는지']

  check_status  varchar(20) [not null, default: 'UNCHECKED',
                 note: 'UNCHECKED | PREPARED | NEEDS_CHECK | NOT_IN_PHOTO']

  created_at    timestamptz [not null, default: `now()`]

  indexes { (trip_id, category) }
}

// ── 여행 일정 (S-11 캘린더) ────────────────────────────────
// 항공편·숙소·관광을 한 표에 두고 kind 로 구분한다. 화면이 시간순 한 줄로
// 섞어 보여주기 때문이다 — 나누면 조회마다 UNION 이 필요하다.
// 목적지는 trips 에만 있다. 일정마다 다시 적으면 이행 종속이 생긴다.
Table trip_itineraries {
  id         bigserial   [pk]
  trip_id    bigint      [not null, ref: > trips.id]   // 1:N
  kind       varchar(20) [not null]  // FLIGHT | LODGING | ACTIVITY | TRANSPORT | OTHER
  title      varchar(100)[not null]
  place      varchar(100)            // 공항·호텔·장소. 지도 좌표는 두지 않는다
  code       varchar(50)             // 항공편명(KE703) 등
  start_at   timestamptz [not null]
  end_at     timestamptz             // 끝나는 시각을 모르는 일정이 많다(체크인)
  note       text
  created_at timestamptz [not null, default: `now()`]
}

// ── 3D 가방 정리 배치 (S-12) ───────────────────────────────
// 항목 하나가 가방 안 한 자리를 차지한다. 1:1 이라 별도 id 없이
// checklist_item_id 가 그대로 pk 다.
// trip_id 를 두지 않는다 — checklist_items 를 거치면 알 수 있고,
// 넣으면 이행 종속이 생긴다 (아래 정규화 검토 3NF).
Table item_placements {
  checklist_item_id bigint      [pk, ref: - checklist_items.id]  // 1:1
  compartment       varchar(20) [not null]  // MAIN_LEFT | MAIN_RIGHT | FRONT_POCKET | MESH | TOP
  pos_x             numeric(4,3)[not null]  // 0~1 상대값. 픽셀이 아니라 화면 크기가 달라도 같은 자리
  pos_y             numeric(4,3)[not null]
  pos_z             numeric(4,3)[not null, default: 0]  // 깊이이자 쌓임 순서
  rotated           boolean     [not null, default: false]
  updated_at        timestamptz [not null, default: `now()`]
}

// ── 짐 사진 (UC-03 / F-03) ────────────────────────────────
Table trip_photos {
  id            bigserial   [pk]
  trip_id       bigint      [not null, ref: > trips.id]     // 1:N
  file_path     varchar(255)[not null, note: 'UPLOAD_DIR 기준 상대 경로']
  bag_kind      varchar(20) [note: 'CABIN | CHECKED — 기내용/위탁용 구분 업로드']
  uploaded_at   timestamptz [not null, default: `now()`]
}

// ── 사진에서 인식된 물품 (UC-04 / F-04) ───────────────────
Table detected_objects {
  id            bigserial   [pk]
  photo_id      bigint      [not null, ref: > trip_photos.id]  // 1:N

  name          varchar(100)[not null, note: 'AI 가 제시한 후보명']
  qty           integer     [not null, default: 1]
  confidence    numeric(4,3)[not null, note: '0.000 ~ 1.000']
  confidence_level varchar(10)[not null, note: 'HIGH | MEDIUM | LOW — 화면 표시용']
  missing_info  varchar(100) [note: '보이지 않아 못 정한 속성. 예: 용량(ml). BAG_CHECK output.missingInfo']
  label_text    varchar(200) [note: '라벨 OCR 원문. BAG_CHECK output.labelText']
  approved      boolean     [not null, default: false,
                 note: '이전 승인 설계의 호환 컬럼. 자동 등록·집계 조건으로 사용하지 않음']
  created_at    timestamptz [not null, default: `now()`]
}

// ══════════════════════════════════════════════════════════
//  ★ N:M 관계 1 — 체크리스트 항목 ↔ 인식 물품
//  명세 F-06: "동일 물품명 불일치 → 유사한 후보를 제시하고
//              사용자가 연결하도록 한다"
//  후보 여러 개 ↔ 항목 여러 개를 사람이 연결하는 구조다.
//  연결마다 신뢰도와 사후 사용자 확인 여부가 붙으므로 조인 테이블에 속성이 있다.
// ══════════════════════════════════════════════════════════
Table item_detections {
  checklist_item_id  bigint  [ref: > checklist_items.id]
  detected_object_id bigint  [ref: > detected_objects.id]

  match_confidence   numeric(4,3)[not null, note: '이름·카테고리 매칭 점수']
  confirmed_by_user  boolean [not null, default: false, note: '선택적 사후 수정·확인 여부. 자동 등록 연결은 false도 유효']
  matched_at         timestamptz [not null, default: `now()`]

  indexes { (checklist_item_id, detected_object_id) [pk] }
}

// ── 반입 규정 마스터 (UC-07 / F-07) ───────────────────────
Table transport_rules {
  id            bigserial   [pk]
  transport     varchar(20) [not null, note: 'FLIGHT | TRAIN | BUS | CAR']
  keyword       varchar(100)[not null, note: '물품 키워드. 예: 보조배터리']
  verdict       varchar(20) [not null,
                 note: 'CABIN_OK | CHECKED_OK | CHECKED_FORBIDDEN | RESTRICTED | NEED_MORE_INFO | ASK_AIRLINE']
  condition_note text       [note: '판정 조건. 예: 100Wh 이하']
  description   text        [not null, note: '사용자에게 보여줄 근거 문구']
  source_url    varchar(255)[not null, note: '공식 출처. 인천공항·항공정보포털']
  checked_at    date        [not null, note: '규정 확인 날짜. 최신성 표시에 쓴다']

  indexes { (transport, keyword) }
}

// ══════════════════════════════════════════════════════════
//  ★ N:M 관계 2 — 체크리스트 항목 ↔ 반입 규정
//  한 물품이 여러 규정에 걸린다 (예: 화장품 = 액체 + 용량 제한)
//  한 규정이 여러 물품에 적용된다 (예: 액체 100ml 규정)
//  판정 결과와 확인 시각이 연결마다 다르므로 조인 테이블에 속성이 있다.
// ══════════════════════════════════════════════════════════
Table item_rule_checks {
  checklist_item_id bigint  [ref: > checklist_items.id]
  rule_id           bigint  [ref: > transport_rules.id]

  verdict           varchar(20)[not null, note: '이 물품에 대한 최종 판정']
  missing_info      varchar(100)[note: 'NEED_MORE_INFO 일 때 무엇이 부족한지']
  decided_at        timestamptz[not null, default: `now()`]

  indexes { (checklist_item_id, rule_id) [pk] }
}

// ── 품목별 무게 마스터 (UC-10 / F-10) ─────────────────────
Table item_weights {
  id            bigserial   [pk]
  keyword       varchar(100)[not null, unique]
  category      varchar(20) [not null]
  min_g         integer     [not null]
  typical_g     integer     [not null]
  max_g         integer     [not null]
  is_dense      boolean     [not null, default: false,
                 note: '책·금속·배터리·액체. 오차가 커서 실측을 먼저 요청한다']
  note          text
}

// ── AI 작업 (UC-04·05·07·08·10 전부) ──────────────────────
// AI-Ready 원칙 2 (Structured Data).
// 지금은 Mock 이 채우고, 나중에 LLM·비전 모델이 같은 자리를 채운다.
Table ai_jobs {
  id            bigserial   [pk]
  user_id       bigint      [not null, ref: > users.id]     // 1:N
  trip_id       bigint      [ref: > trips.id,
                 note: 'nullable — 챗봇(UC-08)은 여행 없이도 쓸 수 있다']

  status        varchar(20) [not null, default: 'PENDING', note: 'PENDING | COMPLETED | FAILED']
  job_type      varchar(30) [not null,
                 note: 'PACKING_LIST | BAG_CHECK | WEIGHT_ESTIMATE | RULE_CHECK']

  input_payload  jsonb      [not null, note: 'AI 에 넘긴 입력. 스키마는 07-ai-ready.md']
  output_payload jsonb      [note: 'AI 가 돌려준 결과. PENDING 이면 null']

  model_name    varchar(100)[note: 'Mock 이면 "mock", 나중엔 실제 모델명']
  error_message text        [note: 'FAILED 일 때만 채운다']

  created_at    timestamptz [not null, default: `now()`]
  completed_at  timestamptz

  indexes {
    (user_id, created_at)
    (status)
    (trip_id, job_type)
  }
}
```

## 테이블 관계

### 회원과 세션 — 로그인 포함 목표 설계

- `users.id`는 기존 내부 PK, `users.login_id`는 새 로그인용 고유 아이디다. 이메일·닉네임과 구분한다.
  아이디 정규화·입력 길이는 06을 따른다. `email`도 소문자 정규화 후 고유값이며 닉네임은 중복 허용한다.
- `password_hash`만 저장하고 비밀번호 원문은 저장하지 않는다. 세션은 서버 메모리의 HttpSession에
  두므로 10개 도메인 테이블에 세션 테이블을 추가하지 않는다.
- `trips.user_id`·`ai_jobs.user_id`는 인증 사용자에서 채운다. 챗봇의 `trip_id=null`은 허용하지만
  `user_id`가 없는 작업은 허용하지 않는다. 항목·사진은 소속 여행을 거쳐 본인 소유권을 검증한다.
- **SQL·시드 후속:** 현재 `database/schema.sql`에는 `login_id`가 없다. 구현 단계에서 컬럼·고유 제약과
  사용자 매핑을 추가하고 기존 회원·시드의 아이디를 정한 뒤 NOT NULL을 적용한다.
  기존 시드 해시로 로그인이 가능하다고 가정하지 않고, 별도 가입으로 검증한다.
  현재 DB를 초기화하는 schema.sql 재실행을 마이그레이션 대신 사용하지 않는다.

### 내 목록과 추천 후보의 저장 규약

| 데이터 | 저장 위치 | 등록·변경 규칙 |
| --- | --- | --- |
| 내 체크리스트 | `checklist_items` | 사진 자동 등록·추천 채택·직접 추가로만 항목을 만든다. 추천 생성만으로 INSERT하지 않는다 |
| 사진 인식 결과 | `detected_objects` | 성공한 인식 물품은 모두 자동 등록. 기존 `approved` 값으로 등록·무게·규정 대상을 거르지 않는다 |
| 사진 자동 등록 | `detected_objects` + `item_detections` + `checklist_items` + `ai_jobs` | BAG_CHECK의 결과·연결·완료 등록·COMPLETED 상태를 한 트랜잭션으로 저장. 신규 항목은 `PHOTO / PREPARED`, 기존 항목은 출처 유지. 추천 실패와 무관하다 |
| 추천 후보 | `ai_jobs.output_payload.items[]` | `PACKING_LIST` 완료 시 저장. 이름·수량·이유·출처와 서버 필드 `acceptedItemId`를 가진다(07) |
| 추천 채택 연결 | 같은 후보의 `acceptedItemId` | 최초 `null`, 채택 시 내 목록 ID. 후보의 위치는 작업 완료 후 바꾸지 않아 `(jobId, candidateIndex)`로 식별한다 |

`acceptedItemId`는 모델 판단이 아니라 서버가 관리하는 연결 정보다. 원래 추천 내용은
유지하고 사용자가 수정한 이름·수량·최종 준비 상태는 `checklist_items`에 저장한다.
별도 추천 테이블이나 SQL 컬럼을 추가하지 않는다. JSON 내부 스키마는 07이 정한다.

추천 채택은 해당 여행의 쓰기를 직렬화하는 트랜잭션 안에서 기존 연결 확인 → 내 목록
생성·연결 → `acceptedItemId` 저장까지 처리한다. 같은 후보를 다시 채택하면 같은 항목을
반환하며 이름·수량·완료 여부를 덮어쓰지 않는다. JSON 안의 ID는 DB 외래키가 아니므로
서버가 같은 여행의 항목인지 검증한다. 항목 삭제 시 해당 여행의 후보 연결도 `null`로
해제한다(06). 이름 변경 후에도 재승인을 판별하기 위해 이름만으로 채택 여부를 저장하지 않는다.

`detected_objects.approved`는 이전 설계와 SQL의 호환을 위해 남기되 신규 흐름에서는 사용하지 않는다.
자동 인식 행은 기본 false로 저장한다. `confirmed_by_user`도 선택적 사후 수정 이력일 뿐이며,
두 값이 false여도 내 목록 등록·준비 완료 집계·무게 입력에 포함한다.

### 준비 완료와 사진 확인 상태

- `check_status=PREPARED`는 실제 챙김 완료다. 그 외 기존 상태는 준비 미완료이며, 신규
  추천 채택·직접 추가는 `UNCHECKED`로 시작한다. 기존 enum은 SQL과 같은 값으로 유지한다.
- 사진 비교 상태는 `item_detections`와 `detected_objects`에서 계산해 API의 `photoStatus`로
  반환한다. `CONFIRMED / NEEDS_CHECK / NOT_IN_PHOTO`이며 별도 컬럼을 만들지 않는다.
- 사진에서 못 찾았다는 이유만으로 `PREPARED`를 바꾸지 않는다. 사진 없이 직접 완료한
  물품도 완료율과 무게 계산 대상이다. 인식된 물품을 내 목록 밖의 승인 대기 상태로 남겨 두지 않는다.
- 완료율은 내 목록의 `PREPARED` 행 수 / 전체 행 수다. 빈 목록은 0이며 수량으로 가중하지
  않는다. 사진 비교·미채택 추천은 이 분모를 바꾸지 않는다.
- 여러 사진의 동일 물품은 같은 항목으로 자동 연결하며 수량은 합산하지 않고 큰 관측값을 사용한다.
  사용자의 사후 수정값은 보존하고 필요하면 다시 정정한다(06). 이 확인을 자동 등록 조건으로 두지 않는다.

| 관계 | 유형 | 설명 |
| --- | --- | --- |
| `users` → `trips` | 1:N | 사용자 한 명이 여러 여행을 만든다 |
| `users` → `ai_jobs` | 1:N | 사용자 한 명이 여러 AI 작업을 만든다 |
| `trips` → `checklist_items` | 1:N | 여행 하나에 준비물 여러 개 |
| `trips` → `trip_photos` | 1:N | 여행 하나에 짐 사진 여러 장 |
| `trips` → `trip_itineraries` | 1:N | 여행 하나에 일정 여러 개 (항공편·숙소·관광). 캘린더는 이 둘을 날짜로 묶어 만든다 |
| `checklist_items` → `item_placements` | **1:1** | 물품 하나가 가방 안 <b>한 자리</b>를 차지한다. 자리를 안 잡은 물품은 행이 없다 |
| `trips` → `ai_jobs` | 1:N | 여행 하나에 AI 작업 여러 개 (**nullable** — 챗봇(UC-08)은 여행을 등록하지 않아도 쓸 수 있다) |
| `trip_photos` → `detected_objects` | 1:N | 사진 한 장에서 물품 여러 개 인식 |
| `item_weights` → `checklist_items` | 1:N | 무게 마스터 하나가 여러 항목에 쓰인다 |
| **`checklist_items` ↔ `detected_objects`** | **N:M** | `item_detections` 경유. **아래 참조** |
| **`checklist_items` ↔ `transport_rules`** | **N:M** | `item_rule_checks` 경유. **아래 참조** |

### N:M 검토 결과

**두 개를 찾았고, 둘 다 명세에서 그대로 나온다. 억지로 만든 것이 아니다.**

**① `checklist_items` ↔ `detected_objects` (`item_detections`)**

명세 F-06 예외 처리가 이 관계를 요구한다.

> *"동일 물품명 불일치 → 유사한 후보를 제시하고 **사용자가 연결하도록** 한다"*
> *"중복 탐지 → 동일 물건 후보를 **묶어** 사용자가 수량을 확정하도록 한다"*

- 사진 3장에 같은 충전기가 찍히면 **인식 결과 3개가 체크리스트 항목 1개**에 붙는다
- "화장품"이라는 항목 하나에 **여러 후보**(로션·선크림·파운데이션)가 매칭될 수 있다
- 반대로 인식된 "검정 파우치" 하나가 **여러 항목의 후보**가 될 수 있다

`detected_objects.matched_item_id` 같은 단일 FK로는 이 요구를 표현할 수 없다.

**② `checklist_items` ↔ `transport_rules` (`item_rule_checks`)**

한 물품이 여러 규정에 동시에 걸린다.

- 200ml 화장품 = `액체류 100ml 초과` + `총량 1L 제한` 두 규정
- 보조배터리 = `기내 전용` + `100Wh 초과 시 승인 필요` 두 규정

반대로 `액체 100ml` 규정 하나가 화장품·샴푸·선크림 여러 항목에 적용된다.

### 조인 테이블에 속성이 붙는다

**두 조인 테이블 모두 단순 연결이 아니라 속성을 가진다.** 이것이 이 설계의 핵심이다.

| 조인 테이블 | 속성 | 왜 필요한가 |
| --- | --- | --- |
| `item_detections` | `match_confidence`<br>`confirmed_by_user` | **자동 연결과 사용자의 선택적 사후 확인을 구분한다.** false인 자동 연결도 등록·완료율·무게에서 유효하다. 이전 명세의 사진 승인 선행 조건은 최신 사용자 결정으로 폐지했다 |
| `item_rule_checks` | `verdict`<br>`missing_info` | 같은 규정이라도 **물품마다 판정이 다르다.** 100ml 화장품은 통과, 200ml는 위탁 |

**연결 자체가 정보를 갖는 관계**이므로 조인 테이블이 필요하다. 단순 다대다 매핑이었다면
FK 두 개만으로 끝났을 것이다.

## 정규화 검토

| 항목 | 확인 | 비고 |
| --- | --- | --- |
| **1NF** — 모든 컬럼이 원자값인가 | ✅ | 반복 그룹 없음. `jsonb` 두 컬럼은 **의도적 예외**로 아래 근거 참조 |
| **2NF** — 부분 함수 종속이 없는가 | ✅ | 복합 PK를 쓰는 두 조인 테이블(`item_detections`·`item_rule_checks`)의 비키 속성이 PK **전체**에 종속된다. `match_confidence`는 (항목, 인식결과) 쌍에 대해서만 정해지고, 어느 한쪽만으로는 정해지지 않는다 |
| **3NF** — 이행 함수 종속이 없는가 | ✅ | 무게 정보를 `checklist_items`에 직접 넣지 않고 `item_weights`로 분리했다. 넣었다면 `id → keyword → typical_g` 이행 종속이 생긴다.<br>같은 이유로 `item_placements`에 `trip_id`를, `trip_itineraries`에 `destination`을 **넣지 않았다** — 아래 참조 |
| **의도적 비정규화** | 2건 | `ai_jobs`의 `jsonb` 2개 · `detected_objects.confidence_level` |

### 일정·배치에서 편의 컬럼을 뺀 근거

두 새 테이블에서 **넣고 싶었지만 넣지 않은 컬럼**이 하나씩 있다. 조회가 한 번 더
조인해야 하지만, 넣었다면 3NF가 깨진다.

| 안 넣은 것 | 넣었다면 생기는 종속 | 실제로 무엇이 깨지나 |
| --- | --- | --- |
| `item_placements.trip_id` | `checklist_item_id → trip_id` | 항목을 다른 여행으로 옮기는 기능이 생기면 배치의 `trip_id`가 옛 여행을 가리킨다 |
| `trip_itineraries.destination` | `trip_id → destination` | 여행 목적지를 고쳤을 때 일정이 옛 목적지를 그대로 보여준다. 달력과 상세가 어긋난다 |

`item_placements`는 **1:1이라 별도 id도 두지 않았다.** `checklist_item_id`가 그대로
기본키다. 물품 하나가 가방 안 두 자리를 차지할 수 없으므로 대리키가 할 일이 없다.

좌표를 `numeric(4,3)` **0~1 상대값**으로 둔 것도 같은 맥락이다. 픽셀을 저장하면
화면 크기·가방 모델이 바뀔 때마다 값이 무의미해진다.

### `jsonb` 컬럼을 쓰는 근거

`ai_jobs.input_payload`와 `output_payload`를 정규화된 컬럼으로 쪼개지 않은 것은
설계 실수가 아니라 **의도적 선택**이다.

- AI의 출력 형태는 프롬프트에 따라 달라진다. 컬럼으로 고정하면 프롬프트를
  바꿀 때마다 마이그레이션이 필요하다.
- **`job_type`이 4종이고 각각 출력 모양이 다르다.** 컬럼으로 펴면 대부분이 null인
  넓은 테이블이 된다.
- PDF의 **Structured Data** 원칙이 요구하는 것이 정확히 이것이다 —
  "AI가 읽고 이해하기 쉬운 JSON 규격을 사전에 반영하여 바로 DB에 저장하거나
  FE에 전달할 수 있도록 하여 데이터 변환 레이어의 부담을 최소화"
- 대신 **JSON의 내부 구조는 `07-ai-ready.md`에 JSON Schema로 고정**한다.
  스키마 없는 자유 형식 JSON이 아니다.

### `confidence_level`을 따로 두는 근거

`detected_objects.confidence`(숫자)에서 `confidence_level`(HIGH/MEDIUM/LOW)이
계산되므로 엄밀히는 이행 종속이다. **그럼에도 컬럼으로 둔다.**

- 경계값(예: 0.8 / 0.5)이 **운영 중에 바뀔 수 있다.** 그때 과거 판정을 소급해서
  바꾸면 안 된다 — 인식 당시 표시와 사후 수정 근거를 보존해야 하기 때문이다
- 명세 9.1: *"원본 사진, 인식 결과, 사용자 수정, 최종 상태를 **분리 저장**"*

> 발표 Q&A에서 "왜 정규화하지 않았느냐"는 질문이 나오면 위 두 절로 답하면 된다.
> **정규화를 몰라서 안 한 것과 알고 안 한 것은 다르다.**

## 실행 스크립트

확정된 스키마는 [`database/schema.sql`](../database/schema.sql)에 SQL로 옮긴다.
데모용 초기 데이터는 [`database/seed.sql`](../database/seed.sql)에 둔다.

**이 문서의 DSL과 `schema.sql`은 짝이다. 한쪽만 고치지 않는다.**

현재 요청은 로그인 포함 설계 반영이다. `users.login_id`를 **목표 스키마의 미반영 차이**로
명시해 두었으며 SQL·시드·JPA 변경과 DB 적용은 인증 구현 시 함께 수행한다.
