# REST API 명세

> 발표 3번 섹션: REST API 명세 요약 (Mock API 포함)
>
> 채점 기준: **"Mock API 엔드포인트 구성 완성도 및 RESTful 규격
> (Method, Path, Status Code) 준수 여부"**, **"Mock API를 활용한 실제 데이터
> 바인딩 및 화면 시연"**

**이 문서가 FE와 BE 사이의 계약이다.** 여기가 먼저 고정되면 두 사람이 서로를
기다리지 않고 동시에 작업할 수 있다. 바꿀 때는 반드시 양쪽에 알린다.

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| Base URL (로컬) | `http://localhost:8080/api` — Spring Boot 기본 포트. [ADR 0001](adr/0001-backend-stack.md)에서 확정 |
| 요청·응답 형식 | `application/json; charset=utf-8` |
| 경로 | 소문자 복수형 명사. 동사를 쓰지 않는다 (`/api/summaries` O, `/api/getSummary` X) |
| 시각 형식 | ISO 8601 UTC (`2026-09-02T05:30:00Z`) |
| 인증 | **구현하지 않는다.** 모든 요청은 시드 사용자(`users.id = 1`)로 처리한다. 채점 항목이 아니라 3일 일정에서 비용만 든다 ([`01-service-plan.md`](01-service-plan.md) 범위) |

### Status Code 사용 규칙

**루브릭이 Status Code 준수를 명시적으로 본다.** 아무 데나 200을 쓰지 않는다.

| 코드 | 언제 쓰는가 |
| --- | --- |
| `200 OK` | 조회 성공, 수정 성공 |
| `201 Created` | 생성 성공. `Location` 헤더에 새 리소스 경로를 넣는다 |
| `202 Accepted` | **비동기 작업을 접수했고 아직 끝나지 않았다.** AI 호출이 여기 해당 |
| `204 No Content` | 삭제 성공. 본문 없음 |
| `400 Bad Request` | 요청 형식·값이 잘못됨 |
| `401 Unauthorized` | 인증 안 됨 |
| `403 Forbidden` | 인증됐지만 권한 없음 |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 중복 등 상태 충돌 |
| `500 Internal Server Error` | 서버 오류 |

### 오류 응답 형식

모든 오류는 같은 모양으로 돌려준다. FE가 오류 처리 코드를 한 번만 쓰면 된다.

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "제목은 1자 이상 100자 이하여야 합니다.",
    "field": "title"
  }
}
```

## 엔드포인트 목록

**Status Code 를 함께 적는다.** 루브릭이 `Method, Path, Status Code` 셋을 나란히 본다.

### 여행 (UC-02)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GET` | `/api/trips` | 내 여행 목록 | `200` | — |
| 2 | `POST` | `/api/trips` | 여행 등록 | **`201`** + `Location` | `400` 날짜 역전·필수값 누락 |
| 3 | `GET` | `/api/trips/{tripId}` | 여행 상세 | `200` | `404` |
| 4 | `PATCH` | `/api/trips/{tripId}` | 여행 수정 | `200` | `400` `404` |
| 5 | `DELETE` | `/api/trips/{tripId}` | 여행 삭제 | **`204`** | `404` |

### 체크리스트 (UC-03 · UC-04)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 6 | `GET` | `/api/trips/{tripId}/items` | 체크리스트 조회 | `200` | `404` |
| 7 | `POST` | `/api/trips/{tripId}/items` | 항목 추가 | **`201`** + `Location` | `400` `404` |
| 8 | `PATCH` | `/api/trips/{tripId}/items/{itemId}` | 항목 수정·완료 처리 | `200` | `400` `404` |
| 9 | `DELETE` | `/api/trips/{tripId}/items/{itemId}` | 항목 삭제 | **`204`** | `404` |

### 짐 사진 (UC-05)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 10 | `POST` | `/api/trips/{tripId}/photos` | 사진 업로드 (`multipart/form-data`) | **`201`** + `Location` | `400` 형식·용량 초과<br>`413` 요청 한도 초과 |
| 11 | `GET` | `/api/trips/{tripId}/photos` | 사진 목록 | `200` | `404` |
| 12 | `DELETE` | `/api/trips/{tripId}/photos/{photoId}` | 사진 삭제 | **`204`** | `404` |

### 인식 결과 · 승인 (UC-06)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 13 | `GET` | `/api/trips/{tripId}/detections` | 인식 결과 목록 | `200` | `404` |
| 14 | `PATCH` | `/api/trips/{tripId}/detections/{detectionId}` | **승인 · 이름·수량 수정** | `200` | `400` `404` |

> **14번이 이 서비스의 핵심 게이트다.** 명세 9.2 수용 기준 —
> *"사진 분석 결과는 사용자가 승인하기 전 최종 준비 상태에 반영되지 않아야 한다."*
> 이 엔드포인트를 거치지 않은 인식 결과는 `detected_objects.approved = false` 로 남고
> 검수·무게·반입 계산에 들어가지 않는다.

### 검수 결과 (UC-07 · UC-08 · UC-09)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 15 | `GET` | `/api/trips/{tripId}/inspection` | **준비 상태 + 예상 무게 + 반입 판정 통합** | `200` | `404` |

> 화면 `S-07` 하나가 세 영역을 함께 그린다. 세 번 호출하지 않고 한 번에 받는다.
> 영역별로 아직 계산되지 않았으면 해당 키가 `null` 이고, 프런트엔드는 그 영역만
> 로딩 상태로 그린다.

### 반입 규정 (UC-09)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 16 | `GET` | `/api/rules?transport=&keyword=` | 규정 조회 | `200` | `400` 필수 파라미터 누락 |

### AI 확장 지점 (UC-03 · 06 · 08 · 09 · 10)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 17 | `POST` | `/api/ai-jobs` | **AI 작업 생성** | **`202`** + `Location` | `400` 잘못된 `jobType` |
| 18 | `GET` | `/api/ai-jobs/{jobId}` | 작업 상태·결과 조회 | `200` | `404` |

**엔드포인트는 둘뿐이다.** AI 작업이 늘어도 `jobType` 값만 늘고 경로는 그대로다.
근거는 [ADR 0003](adr/0003-ai-job-endpoint.md).

### 헷갈리기 쉬운 두 가지

| 상황 | 코드 | 왜 |
| --- | --- | --- |
| `POST /api/ai-jobs` 로 작업 접수 | **`202`** | 접수만 했고 **아직 안 끝났다**. `200` 이 아니다 |
| `GET /api/ai-jobs/{id}` 인데 아직 `PENDING` | **`200`** | **조회 자체는 성공했다.** `202` 가 아니다. 본문의 `status` 로 구분한다 |

## AI 확장 지점 엔드포인트 (Mock)

**AI-Ready 원칙 3 (Asynchronous Pipeline)을 구현한 형태다.** 지금은 Mock이
즉시 고정 JSON을 돌려주지만, 나중에 실제 LLM·비전 모델을 붙여도 이 규격은 그대로다.
LLM 호출은 수 초가 걸리므로 처음부터 비동기 구조로 열어 둔다.

### `jobType` 4종

| `jobType` | Use-Case | 화면 | 지금 | 나중 |
| --- | --- | --- | --- | --- |
| `PACKING_LIST` | UC-03 체크리스트 생성 | `S-04` | Mock 고정 JSON | LLM |
| `BAG_CHECK` | UC-06 사진 물품 인식 | `S-06` | Mock 고정 인식 결과 | 비전 모델 |
| `WEIGHT_ESTIMATE` | UC-08 예상 무게 산정 | `S-07` `S-08` | Mock 고정 범위 | 품목 중량 DB + LLM 보정 |
| `RULE_CHECK` | UC-09 · UC-10 반입 규정 | `S-07` `S-09` `S-10` | Mock 고정 판정 | LLM 구조화 + 규칙 엔진 |

**`input`·`output`의 내부 구조는 [`07-ai-ready.md`](07-ai-ready.md)의 JSON Schema로
고정한다.** 이 문서는 봉투(HTTP 계약)만 정한다.

> ⚠️ **아래 `output` 예시는 잠정이다.** `07-ai-ready.md`의 출력 Schema가 아직
> 확정되지 않았다. 확정되면 이 예시를 그쪽에 맞춘다. **그 전까지 프런트엔드는
> `output` 내부 필드를 확정된 계약으로 취급하지 않는다** — 봉투(`jobId`·`status`·
> `pollAfterMs`)만 믿고 쓴다.

### `POST /api/ai-jobs` — AI 작업 생성

작업을 **접수만** 하고 즉시 응답한다. 결과를 기다리지 않는다.

**Request**

```json
{
  "jobType": "PACKING_LIST",
  "tripId": 12,
  "input": {
    "destination": "도쿄",
    "startDate": "2026-10-01",
    "endDate": "2026-10-04",
    "transport": "FLIGHT",
    "purpose": "TOUR",
    "note": "친구 2명, 디즈니랜드, 사진 많이 찍을 예정"
  }
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `jobType` | ✅ | 위 4종 중 하나. 다른 값이면 `400` |
| `tripId` | — | **`RULE_CHECK` 는 여행 없이도 된다.** UC-09(반입 규정 확인)는 여행 등록 전에도 물어볼 수 있다. 나머지는 필수<br>*(챗봇·비회원은 [`01-service-plan.md`](01-service-plan.md)의 "하지 않을 것"이라 근거로 들지 않는다)* |
| `input` | ✅ | `jobType` 별 스키마는 `07-ai-ready.md` |

**Response — `202 Accepted`**

```http
HTTP/1.1 202 Accepted
Location: /api/ai-jobs/1041
```

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "PENDING",
  "createdAt": "2026-09-02T05:30:00Z",
  "pollAfterMs": 500
}
```

> `pollAfterMs` 는 프런트엔드에 **다음 폴링까지 기다릴 시간**을 알려준다.
> Mock 은 `AI_MOCK_DELAY_MS` 설정을 따라 즉시 끝나지만, 실제 AI 를 붙이면
> 이 값만 늘리면 된다. **프런트엔드 코드는 고치지 않는다.**

### `GET /api/ai-jobs/{jobId}` — 상태·결과 조회

프런트엔드는 이 엔드포인트를 **폴링**한다. Mock 이 즉시 응답해도 폴링으로 구현한다.

**처리 중 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "PENDING",
  "output": null,
  "createdAt": "2026-09-02T05:30:00Z",
  "completedAt": null,
  "pollAfterMs": 500
}
```

**완료 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "COMPLETED",
  "output": {
    "items": [
      { "name": "여권", "category": "DOCUMENT", "qty": 1, "priority": "REQUIRED" },
      { "name": "변환 플러그", "category": "ELECTRONIC", "qty": 1, "priority": "REQUIRED" }
    ],
    "tips": ["일본 콘센트는 A타입입니다.", "10월 초 도쿄는 낮 24도, 얇은 겉옷을 권합니다."],
    "weatherSource": "FORECAST"
  },
  "modelName": "mock",
  "createdAt": "2026-09-02T05:30:00Z",
  "completedAt": "2026-09-02T05:30:01Z"
}
```

**실패 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "FAILED",
  "output": null,
  "errorMessage": "추천을 만들지 못했습니다. 기본 체크리스트를 사용하세요.",
  "createdAt": "2026-09-02T05:30:00Z",
  "completedAt": "2026-09-02T05:30:01Z"
}
```

> **`FAILED` 도 `200` 이다.** 조회 자체는 성공했기 때문이다. `500` 을 쓰면
> 프런트엔드가 네트워크 오류와 AI 실패를 구분하지 못한다.
> 명세의 예외 처리 *"AI 생성 실패 시 기본 체크리스트를 제공한다"* 를 하려면
> 이 구분이 필요하다.

### 폴링 규약

```text
POST /api/ai-jobs              → 202 + jobId + pollAfterMs
     ↓ pollAfterMs 만큼 대기
GET  /api/ai-jobs/{jobId}      → 200 status=PENDING    ┐
     ↓ pollAfterMs 만큼 대기                            │ 반복
GET  /api/ai-jobs/{jobId}      → 200 status=COMPLETED  ┘
```

| 항목 | 값 |
| --- | --- |
| 최대 폴링 횟수 | 60회 |
| 초과 시 | 화면에 *"시간이 오래 걸립니다"* 와 재시도 버튼. **작업은 서버에 남는다** |
| 화면 이탈 | 결과는 저장된다. 다시 들어오면 `GET` 한 번으로 받는다 |

## 주요 응답 예시

### `GET /api/trips/{tripId}/inspection` — 검수 결과 (화면 `S-07`)

**이 서비스의 차별점 셋이 한 응답에 있다.**

```json
{
  "tripId": 12,
  "readiness": {
    "prepared":   [{ "itemId": 5, "name": "충전기", "qty": 1 }],
    "needsCheck": [{ "itemId": 8, "name": "화장품", "qty": 1,
                     "candidates": [
                       { "detectionId": 6, "name": "화장품 용기", "matchConfidence": 0.71 },
                       { "detectionId": 8, "name": "검정 파우치", "matchConfidence": 0.31 }
                     ] }],
    "notInPhoto": [{ "itemId": 1, "name": "여권", "priority": "REQUIRED" }],
    "extra":      [{ "detectionId": 7, "name": "가위", "confidence": 0.91,
                     "verdict": "NEED_MORE_INFO", "missingInfo": "날 길이(cm)" }],
    "completionRate": 0.5
  },
  "weight": {
    "minG": 4570, "typicalG": 5410, "maxG": 6890,
    "limitG": 23000,
    "verdict": "ROOM",
    "confidence": "MEDIUM",
    "confidenceReason": "사진에서 미확인 4개, 승인 전 1개",
    "excludedCount": 5,
    "contributions": [
      { "name": "상의", "typicalG": 200, "qty": 4, "subtotalG": 800 },
      { "name": "하의", "typicalG": 400, "qty": 2, "subtotalG": 800 },
      { "name": "보조배터리", "typicalG": 280, "qty": 1, "subtotalG": 280 }
    ]
  },
  "customs": [
    { "itemId": 6, "name": "보조배터리", "verdict": "CABIN_OK",
      "reason": "보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02" },
    { "itemId": 8, "name": "화장품", "verdict": "NEED_MORE_INFO",
      "missingInfo": "용량(ml)",
      "reason": "액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02" }
  ],
  "notice": "사진 분석 결과는 가방 전체를 확인한 것이 아닙니다. 사진에서 확인되지 않은 물건은 직접 확인해 주세요."
}
```

**필드 이름에 설계가 들어 있다.**

| 필드 | 왜 이 이름인가 |
| --- | --- |
| `notInPhoto` | **`missing` 이 아니다.** 사진에서 못 찾았을 뿐 없다는 뜻이 아니다 |
| `weight.minG` `typicalG` `maxG` | **단일 값이 아니라 범위다.** 명세 F-10: *"결과를 실측값처럼 표현하지 않는다"* |
| `weight.excludedCount` | 계산에서 뺀 항목 수를 **숨기지 않는다** |
| `customs[].missingInfo` | 판정을 단정하지 않고 **무엇이 부족한지** 알려준다 |
| `customs[].sourceUrl` `checkedAt` | 명세 9절 *"규정 최신성"* — 출처와 확인 날짜를 항상 함께 |

`weight.verdict`: `ROOM`(여유) · `NEAR`(근접) · `OVER_RISK`(초과 가능성) · `UNKNOWN`(정보 부족)

### `PATCH /api/trips/{tripId}/detections/{detectionId}` — 승인

```json
// Request
{ "approved": true, "name": "선크림", "qty": 1, "matchedItemIds": [8] }
```

```json
// Response 200
{ "detectionId": 6, "approved": true, "name": "선크림", "qty": 1,
  "linkedItems": [{ "itemId": 8, "name": "화장품", "confirmedByUser": true }] }
```

> `linkedItems` 가 배열인 이유는 **N:M** 이기 때문이다. 인식 결과 하나가 여러
> 체크리스트 항목에 연결될 수 있다. ([`05-erd.md`](05-erd.md))

**연결 수정 규약 — 전체 교체다.**

| 요청 | 결과 |
| --- | --- |
| `matchedItemIds: [8]` | 이 인식 결과의 연결을 **`[8]` 하나로 교체**한다. 기존 연결은 지운다 |
| `matchedItemIds: [8, 9]` | 두 항목에 연결한다 |
| `matchedItemIds: []` | **연결을 모두 해제**한다 |
| 필드 자체를 안 보냄 | 연결을 건드리지 않는다. `approved`·`name`·`qty` 만 바꾼다 |

**추가·삭제가 아니라 배열 전체 교체**로 정한 이유는 화면 때문이다. `S-06`에서
사용자가 후보 목록의 체크박스를 조작하면 **그 시점의 전체 선택 상태**가 넘어온다.
증분으로 보내려면 프런트엔드가 이전 상태를 기억해야 하는데, 폴링으로 갱신되는
화면에서는 그 가정이 깨진다.

```json
// Request — 필드명이 matchedItemId 가 아니라 matchedItemIds 다
{ "approved": true, "name": "선크림", "qty": 1, "matchedItemIds": [8] }
```

## 도메인 API 요청·응답

**필드명이 계약이다.** 여기가 비면 FE와 BE가 같은 엔드포인트를 다른 필드명으로
구현해도 이 문서로 판별할 수 없다.

### `POST /api/trips` — 여행 등록

```json
// Request
{
  "origin": "서울",
  "destination": "도쿄",
  "countryCode": "JP",
  "startDate": "2026-10-01",
  "endDate": "2026-10-04",
  "purpose": "TOUR",
  "transport": "FLIGHT",
  "airline": "대한항공",
  "departureAirport": "ICN",
  "arrivalAirport": "NRT",
  "bagType": "CARRY_ON",
  "bagEmptyG": 3200,
  "weightLimitG": 23000,
  "note": "친구 2명, 디즈니랜드"
}
```

| 필드 | 필수 | 값 |
| --- | --- | --- |
| `origin` `destination` | ✅ | **이동수단과 무관하게 필수** |
| `startDate` `endDate` | ✅ | `startDate <= endDate` 아니면 `400` |
| `purpose` | ✅ | `TOUR` `BUSINESS` `REST` `STUDY` |
| `transport` | ✅ | `FLIGHT` `TRAIN` `BUS` `CAR` |
| `airline` `departureAirport` `arrivalAirport` | — | 비우면 **일반 기준만 적용**되고 정확도가 낮아진다 |
| `bagType` | — | `CARRY_ON` `MEDIUM` `LARGE` |

```http
HTTP/1.1 201 Created
Location: /api/trips/12
```

```json
{ "tripId": 12, "origin": "서울", "destination": "도쿄",
  "startDate": "2026-10-01", "endDate": "2026-10-04",
  "transport": "FLIGHT", "status": "DRAFT", "createdAt": "2026-09-02T05:30:00Z" }
```

### `GET /api/trips` — 목록 (화면 `S-02`)

```json
{
  "trips": [
    { "tripId": 12, "origin": "서울", "destination": "도쿄",
      "startDate": "2026-10-01", "endDate": "2026-10-04",
      "transport": "FLIGHT", "status": "CONFIRMED", "completionRate": 0.5 }
  ]
}
```

### `GET /api/trips/{tripId}/items` — 체크리스트 (화면 `S-04`)

```json
{
  "items": [
    { "itemId": 1, "name": "여권", "category": "DOCUMENT", "qty": 1,
      "priority": "REQUIRED", "source": "RULE", "checkStatus": "NOT_IN_PHOTO" },
    { "itemId": 5, "name": "충전기", "category": "ELECTRONIC", "qty": 1,
      "priority": "REQUIRED", "source": "AI", "checkStatus": "PREPARED" }
  ],
  "completionRate": 0.5
}
```

| 필드 | 값 |
| --- | --- |
| `category` | `DOCUMENT` `CLOTHING` `ELECTRONIC` `TOILETRY` `MEDICINE` `ETC` |
| `priority` | `REQUIRED` `RECOMMENDED` |
| `source` | `RULE` `AI` `USER` — 누가 넣었는지 |
| `checkStatus` | `UNCHECKED` `PREPARED` `NEEDS_CHECK` **`NOT_IN_PHOTO`** |

### `POST /api/trips/{tripId}/items` — 항목 추가

```json
// Request
{ "name": "우산", "category": "ETC", "qty": 1, "priority": "RECOMMENDED" }
```

`201 Created` + `Location: /api/trips/12/items/11`. `source` 는 서버가 `USER` 로 채운다.

### `PATCH /api/trips/{tripId}/items/{itemId}` — 수정·완료 처리

```json
// Request — 보낸 필드만 바꾼다
{ "checkStatus": "PREPARED", "qty": 2 }
```

### `POST /api/trips/{tripId}/photos` — 사진 업로드

`multipart/form-data`. 파트 이름은 `files`(복수 가능), `bagKind`(`CABIN`|`CHECKED`).

```json
// 201 Created
{ "photos": [{ "photoId": 1, "fileUrl": "/uploads/demo/bag-01.jpg",
               "bagKind": "CABIN", "uploadedAt": "2026-09-02T05:31:00Z" }] }
```

### `GET /api/rules?transport=FLIGHT&keyword=보조배터리`

```json
{
  "rules": [
    { "ruleId": 1, "verdict": "CABIN_OK", "conditionNote": "100Wh 이하",
      "description": "보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02" }
  ]
}
```

`transport` 는 필수다. 없으면 `400`.

> **필드명은 `camelCase`, DB 컬럼은 `snake_case`다.** 경계에서 변환한다.
> `origin` · `checkStatus` · `bagEmptyG` 처럼 [`05-erd.md`](05-erd.md)의 컬럼과
> 1:1로 대응하므로 어느 쪽을 봐도 같은 것을 가리킨다.

## Mock 서버 운영 방식

**Mock 을 백엔드 안에 둔다.** Postman Mock Server 를 쓰지 않는다.

| | 근거 |
| --- | --- |
| **데모** | `checklist.md` 의 데모 사고 방지 — *"인터넷이 끊겨도 되도록 Mock을 로컬 백엔드에 둔다"* |
| **발표** | `AiClient` 인터페이스에 구현 둘(`MockAiClient` · `RealAiClient`)을 두면 **교체 지점이 코드로 드러난다** |
| **일관성** | Mock 도 같은 DB(`ai_jobs`)에 기록한다. 폴링·상태 전이가 실제와 똑같이 돈다 |

```java
public interface AiClient {
    AiJobResult run(AiJobType type, JsonNode input);
}
```

```properties
# .env 만 바꾸면 교체된다. 코드는 고치지 않는다.
AI_PROVIDER=mock          # mock | openai | anthropic
AI_MOCK_DELAY_MS=0        # 발표 때 1000~2000 으로 두면 로딩 화면을 보여줄 수 있다
```

- Postman Collection 링크: TBD — 팀 외부 공유용으로만 쓴다
- Postman Mock URL: **쓰지 않음**

## OpenAPI 명세

**손으로 쓰지 않는다.** `springdoc-openapi` 가 컨트롤러에서 자동 생성한다.

| | 주소 |
| --- | --- |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI 문서 | <http://localhost:8080/v3/api-docs> |

2일차 산출물인 REST API 명세가 여기서 나온다. 발표 때 **실제로 열어 보여주면**
"Mock API 엔드포인트 구성 완성도"를 말이 아니라 화면으로 증명할 수 있다.

> 컨트롤러를 구현하기 전까지는 `paths` 가 비어 있다. 이 문서가 **먼저 고정되고**
> 구현이 그것을 따라간다. 둘이 어긋나면 이 문서가 기준이다.
