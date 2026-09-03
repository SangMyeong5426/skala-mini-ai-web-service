# REST API 명세

> 발표 3번 섹션: REST API 명세 요약 (Mock API 포함)
>
> 채점 기준: **"Mock API 엔드포인트 구성 완성도 및 RESTful 규격
> (Method, Path, Status Code) 준수 여부"**, **"Mock API를 활용한 실제 데이터
> 바인딩 및 화면 시연"**

**이 문서가 FE와 BE 사이의 계약이다.** 여기가 먼저 고정되면 두 사람이 서로를
기다리지 않고 동시에 작업할 수 있다. 바꿀 때는 반드시 양쪽에 알린다.

> **2026-09-03 개정 계약:** [Notion 개정안](https://app.notion.com/p/3d0c2ab24ce881d9b06cc065c47b1eb7)에
> 따라 사진 승인은 완료 등록, 추천 채택은 미완료 등록으로 분리했다. 엔드포인트 18개는
> 유지하고 기존 요청·응답을 확장한다. 현재 코드·시드의 후속 반영 상태는 [문서 지도](README.md#개정안-반영-상태)에 적는다.

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| Base URL (로컬) | `http://localhost:8080/api` — Spring Boot 기본 포트. [ADR 0001](adr/0001-backend-stack.md)에서 확정 |
| 요청·응답 형식 | `application/json; charset=utf-8` |
| 경로 | 소문자 복수형 명사. 동사를 쓰지 않는다 (`/api/summaries` O, `/api/getSummary` X). 예외: `/inspection` 은 자원 목록이 아니라 여행 하나의 **집계 결과**라 단수다 |
| 시각 형식 | ISO 8601 UTC (`2026-09-03T05:30:00Z`) |
| 인증 | **이번 데모에서 구현하지 않는다.** 스키마에는 `users.password_hash` 자리를 두었지만 토큰·세션을 발급하지 않고, 모든 요청은 시드 사용자(`users.id = 1`)로 처리한다. 채점 항목이 아니라 3일 일정에서 비용만 든다 ([`01-service-plan.md`](01-service-plan.md) 범위) |

브라우저 연동 시 `CORS_ALLOWED_ORIGINS`에 지정한 origin만 허용한다.
생성 응답의 `Location`을 React의 `response.headers.get('Location')`으로 읽을 수
있도록 백엔드는 `Access-Control-Expose-Headers: Location`을 설정한다.

### Status Code 사용 규칙

**루브릭이 Status Code 준수를 명시적으로 본다.** 아무 데나 200을 쓰지 않는다.

| 코드 | 언제 쓰는가 |
| --- | --- |
| `200 OK` | 조회 성공, 수정 성공 |
| `201 Created` | 생성 성공. `Location` 헤더에 새 리소스 경로를 넣는다 |
| `202 Accepted` | **비동기 작업을 접수했고 아직 끝나지 않았다.** AI 호출이 여기 해당 |
| `204 No Content` | 삭제 성공. 본문 없음 |
| `400 Bad Request` | 요청 형식·값이 잘못됨 |
| `413 Payload Too Large` | 요청 전체 크기 초과 — 사진 여러 장 (`spring.servlet.multipart.max-request-size`) |
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

### 여행 (UC-02 · UC-09)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GET` | `/api/trips` | 내 여행 목록 | `200` | — |
| 2 | `POST` | `/api/trips` | 여행 등록 | **`201`** + `Location` | `400` 날짜 역전·필수값 누락 |
| 3 | `GET` | `/api/trips/{tripId}` | 여행 상세 | `200` | `404` |
| 4 | `PATCH` | `/api/trips/{tripId}` | 여행 수정 | `200` | `400` `404` |
| 5 | `DELETE` | `/api/trips/{tripId}` | 여행 삭제 | **`204`** | `404` |

### 체크리스트 (UC-05 · UC-06)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 6 | `GET` | `/api/trips/{tripId}/items` | 체크리스트 조회 | `200` | `404` |
| 7 | `POST` | `/api/trips/{tripId}/items` | 직접 추가·추천 채택 | **`201`** + `Location`, 재승인·기존 항목 연결은 `200` | `400` `404` `409` |
| 8 | `PATCH` | `/api/trips/{tripId}/items/{itemId}` | 항목 수정·완료 처리 | `200` | `400` `404` |
| 9 | `DELETE` | `/api/trips/{tripId}/items/{itemId}` | 항목 삭제 | **`204`** | `404` |

### 짐 사진 (UC-03)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 10 | `POST` | `/api/trips/{tripId}/photos` | 사진 업로드 (`multipart/form-data`) | **`201`** + `Location` | `400` 형식·용량 초과<br>`413` 요청 한도 초과 |
| 11 | `GET` | `/api/trips/{tripId}/photos` | 사진 목록 | `200` | `404` |
| 12 | `DELETE` | `/api/trips/{tripId}/photos/{photoId}` | 사진 삭제 | **`204`** | `404` |

### 인식 결과 · 승인 (UC-04)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 13 | `GET` | `/api/trips/{tripId}/detections` | 인식 결과 목록 | `200` | `404` |
| 14 | `PATCH` | `/api/trips/{tripId}/detections/{detectionId}` | **승인·이름·수량 수정·내 목록 완료 등록** | `200` | `400` `404` `409` |

> **14번이 이 서비스의 핵심 게이트다.** 명세 9.2 수용 기준 —
> *"사진 분석 결과는 사용자가 승인하기 전 최종 준비 상태에 반영되지 않아야 한다."*
> 이 엔드포인트를 거치지 않은 인식 결과는 `detected_objects.approved = false` 로 남고
> 검수·무게·반입 계산에 들어가지 않는다.

### 검수 결과 (UC-06 · UC-07 · UC-10)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 15 | `GET` | `/api/trips/{tripId}/inspection` | **준비 상태 + 예상 무게 + 반입 판정 통합** | `200` | `404` |

> 화면 `S-06` 하나가 세 영역을 함께 그린다. 세 번 호출하지 않고 한 번에 받는다.
> 영역별로 아직 계산되지 않았으면 해당 키가 `null` 이고, 프런트엔드는 그 영역만
> 로딩 상태로 그린다.

### 반입 규정 (UC-07)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 16 | `GET` | `/api/rules?transport=&keyword=` | 규정 조회 | `200` | `400` 필수 파라미터 누락 |

### AI 확장 지점 (UC-04 · 05 · 07 · 08 · 10)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 17 | `POST` | `/api/ai-jobs` | **AI 작업 생성** | **`202`** + `Location` | `400` 잘못된 입력, `409` 현재 목록·가방 상태와 입력 불일치 |
| 18 | `GET` | `/api/ai-jobs/{jobId}` | 작업 상태·결과 조회 | `200` | `404` |

**엔드포인트는 둘뿐이다.** AI 작업이 늘어도 `jobType` 값만 늘고 경로는 그대로다.
근거는 [ADR 0003](adr/0003-ai-job-endpoint.md).

### 헷갈리기 쉬운 두 가지

| 상황 | 코드 | 왜 |
| --- | --- | --- |
| `POST /api/ai-jobs` 로 작업 접수 | **`202`** | 접수만 했고 **아직 안 끝났다**. `200` 이 아니다 |
| `GET /api/ai-jobs/{jobId}` 인데 아직 `PENDING` | **`200`** | **조회 자체는 성공했다.** `202` 가 아니다. 본문의 `status` 로 구분한다 |

## AI 확장 지점 엔드포인트 (Mock)

**AI-Ready 원칙 3 (Asynchronous Pipeline)을 구현한 형태다.** 지금은 Mock이
즉시 고정 JSON을 돌려주지만, 나중에 실제 LLM·비전 모델을 붙여도 이 규격은 그대로다.
LLM 호출은 수 초가 걸리므로 처음부터 비동기 구조로 열어 둔다.

### `jobType` 4종

| `jobType` | Use-Case | 화면 | 지금 | 나중 |
| --- | --- | --- | --- | --- |
| `PACKING_LIST` | UC-05 추가 준비물 추천 | `S-05` | Mock 고정 JSON | LLM |
| `BAG_CHECK` | UC-04 사진 물품 인식 | `S-04` | Mock 고정 인식 결과 | 비전 모델 |
| `WEIGHT_ESTIMATE` | UC-10 예상 무게 산정 | `S-06` `S-07` | Mock 고정 범위 | 품목 중량 DB + LLM 보정 |
| `RULE_CHECK` | UC-07 · UC-08 반입 규정 | `S-06` `S-08` `S-09` | Mock 고정 판정 | LLM 구조화 + 규칙 엔진 |

**`input`·`output`의 내부 구조는 [`07-ai-ready.md`](07-ai-ready.md)의 JSON Schema로
고정한다.** 이 문서는 봉투(HTTP 계약)만 정한다.

> **`input`·`output` 의 내부 구조는 [`07-ai-ready.md`](07-ai-ready.md)의 JSON Schema 가 정본이다.**
> 아래 예시는 그 스키마로 검증했다 — 요청 예시의 `input` 과 완료 예시의 `output` 이
> 그대로 통과한다 (07 "기계 검증"). 이 예시를 고치면 07 의 스키마도 같이 고친다.

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
    "note": "친구 2명, 디즈니랜드, 사진 많이 찍을 예정",
    "alreadyPacked": [
      { "name": "충전기", "category": "ELECTRONIC", "qty": 1 },
      { "name": "보조배터리", "category": "ELECTRONIC", "qty": 1 },
      { "name": "상의", "category": "CLOTHING", "qty": 4 }
    ]
  }
}
```

> `alreadyPacked`는 내 목록의 실제 준비 완료(`PREPARED`) 물품이다. 사진 승인과 직접 완료
> 확인을 모두 포함한다. 서버는 해당 여행의 현재 내 목록을 별도로 읽어 미완료 항목도
> 중복 추천에서 제외한다. 미완료 물품을 `alreadyPacked`에 섞지 않는다.
>
> 완료 항목이 없는 경우 빈 배열 `[]` 을 보낸다. 그러면 여행 조건과 현재 내 목록으로
> 현재 내 목록에 없는 후보를 추천한다. **필드를 생략하지 않는다** — 빈 배열과 미전송을
> 구분하지 않으면 Mock 과 실제 LLM 의 동작이 갈린다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `jobType` | ✅ | 위 4종 중 하나. 다른 값이면 `400` |
| `tripId` | — | **`RULE_CHECK` 는 여행 없이도 된다.** 챗봇(UC-08 · 화면 `S-09`)은 여행을 등록하지 않아도 쓸 수 있는 보조 흐름이다. 나머지 `jobType` 은 필수 |
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
  "createdAt": "2026-09-03T05:30:00Z",
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
  "createdAt": "2026-09-03T05:30:00Z",
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
      {
        "name": "변환 플러그",
        "category": "ELECTRONIC",
        "qty": 1,
        "priority": "REQUIRED",
        "reason": "여행지에서 충전기를 연결할 때 필요한 어댑터입니다.",
        "source": "AI",
        "acceptedItemId": null
      },
      {
        "name": "상비약",
        "category": "MEDICINE",
        "qty": 1,
        "priority": "RECOMMENDED",
        "reason": "개인적으로 사용하는 약이 있다면 준비 여부를 확인하세요.",
        "source": "AI",
        "acceptedItemId": null
      }
    ],
    "tips": [
      "일본 콘센트는 A타입, 100V입니다.",
      "10월 초 도쿄 계절 평균은 낮 24도, 아침 16도입니다."
    ],
    "weatherSource": "SEASONAL",
    "weatherAsOf": "2026-09-03"
  },
  "modelName": "mock",
  "createdAt": "2026-09-03T05:30:00Z",
  "completedAt": "2026-09-03T05:30:01Z"
}
```

**실패 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "FAILED",
  "output": null,
  "errorMessage": "추천을 만들지 못했습니다. 내 체크리스트는 유지됩니다. 다시 시도하거나 직접 추가해 주세요.",
  "createdAt": "2026-09-03T05:30:00Z",
  "completedAt": "2026-09-03T05:30:01Z"
}
```

> **`FAILED` 도 `200` 이다.** 조회 자체는 성공했기 때문이다. `500` 을 쓰면
> 프런트엔드가 네트워크 오류와 AI 실패를 구분하지 못한다.
> 추천 실패를 알려도 이미 승인·등록한 내 목록은 유지한다. 실패를 이유로 기본 목록을
> 자동으로 채택하거나 기존 항목을 삭제하지 않는다.

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

각 예시는 해당 동작 시점의 응답이다. 추천 작업의 최초 완료 응답은 채택 전이며,
아래 내 목록 조회·검수 예시는 사용자가 후보를 채택한 후의 상태를 보여준다.

### `GET /api/trips/{tripId}/inspection` — 검수 결과 (화면 `S-06`)

**이 서비스의 차별점 셋이 한 응답에 있다.** 아래는 완료 항목 6개·채택 후 미완료 1개인
상태다. 부분 집계 예시의 `customs`는 보조배터리 한 건만 보여준다. 기존 SQL 시드의
승인 가위 미등록 상태를 그대로 재현하는 예시는 아니다.

```json
{
  "tripId": 12,
  "readiness": {
    "prepared": [
      {
        "itemId": 2,
        "name": "상의",
        "qty": 4,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 3,
        "name": "하의",
        "qty": 2,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 4,
        "name": "속옷",
        "qty": 4,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 5,
        "name": "충전기",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 6,
        "name": "보조배터리",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 11,
        "name": "가위",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      }
    ],
    "unprepared": [
      {
        "itemId": 7,
        "name": "변환 플러그",
        "qty": 1,
        "photoStatus": "NOT_IN_PHOTO"
      }
    ],
    "completionRate": 0.8571428571428571
  },
  "weight": {
    "minG": 4610,
    "typicalG": 5480,
    "maxG": 7010,
    "limitG": 10000,
    "verdict": "ROOM",
    "confidence": "MEDIUM",
    "confidenceReason": "준비 완료 6개를 계산했습니다. 미완료 1개와 미승인 인식 후보 2개는 제외했습니다.",
    "excludedCount": 3,
    "contributions": [
      {
        "name": "상의",
        "typicalG": 200,
        "qty": 4,
        "subtotalG": 800
      },
      {
        "name": "하의",
        "typicalG": 400,
        "qty": 2,
        "subtotalG": 800
      },
      {
        "name": "보조배터리",
        "typicalG": 280,
        "qty": 1,
        "subtotalG": 280
      }
    ]
  },
  "customs": [
    {
      "itemId": 6,
      "name": "보조배터리",
      "verdict": "NEED_MORE_INFO",
      "missingInfo": "배터리 정격(Wh)",
      "reason": "보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라 달라집니다. 라벨의 Wh 를 확인해 주세요.",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    }
  ],
  "notice": "사진 분석 결과는 가방 전체를 확인한 것이 아닙니다. 사진에서 확인되지 않은 물건은 직접 확인해 주세요."
}
```

**필드 이름에 설계가 들어 있다.**

| 필드 | 왜 이 이름인가 |
| --- | --- |
| `photoStatus=NOT_IN_PHOTO` | 사진에서 못 찾았을 뿐 없다는 뜻이 아니다. 실제 완료와 별도다 |
| `weight.minG` `typicalG` `maxG` | **단일 값이 아니라 범위다.** 명세 F-10: *"결과를 실측값처럼 표현하지 않는다"* |
| `weight.excludedCount` | 계산에서 뺀 항목 수를 **숨기지 않는다** |
| `customs[].missingInfo` | 판정을 단정하지 않고 **무엇이 부족한지** 알려준다 |
| `customs[].sourceUrl` `checkedAt` | 명세 9절 *"규정 최신성"* — 출처와 확인 날짜를 항상 함께 |

`weight.verdict`: `ROOM`(여유) · `NEAR`(근접) · `OVER_RISK`(초과 가능성) · `UNKNOWN`(정보 부족)

### `GET /api/trips/{tripId}/detections` — 인식 결과 (화면 `S-04`)

```json
{
  "detections": [
    { "detectionId": 2, "photoId": 1, "name": "보조배터리", "qty": 1,
      "confidence": 0.880, "confidenceLevel": "HIGH", "approved": true,
      "missingInfo": "배터리 정격(Wh)", "labelText": null },
    { "detectionId": 6, "photoId": 2, "name": "화장품 용기", "qty": 1,
      "confidence": 0.640, "confidenceLevel": "MEDIUM", "approved": false,
      "missingInfo": "용량(ml)", "labelText": null },
    { "detectionId": 8, "photoId": 2, "name": "검정 파우치", "qty": 1,
      "confidence": 0.430, "confidenceLevel": "LOW", "approved": false,
      "missingInfo": null, "labelText": null }
  ]
}
```

> `missingInfo` · `labelText` 는 `BAG_CHECK` 출력([`07-ai-ready.md`](07-ai-ready.md))에서 그대로 온다.
> `S-04` 「확인 필요」 묶음 = `missingInfo ≠ null` 또는 `confidenceLevel = LOW`.

### `PATCH /api/trips/{tripId}/detections/{detectionId}` — 승인·완료 등록

승인과 내 목록 반영은 **같은 트랜잭션**이다. 추천 작업을 기다리지 않는다.

```json
// Request — 체크리스트가 비어 있는 여행에서 승인
{ "approved": true, "name": "선크림", "qty": 1, "category": "TOILETRY" }
```

```json
// Response 200 — 새 항목을 만들었더라도 수정한 주 자원은 인식 결과다
{ "detectionId": 6, "approved": true, "name": "선크림", "qty": 1,
  "linkedItems": [{ "itemId": 8, "name": "선크림", "confirmedByUser": true,
                    "source": "PHOTO", "checkStatus": "PREPARED" }] }
```

| 요청 | 처리 |
| --- | --- |
| `approved=true`, 기존 승인 연결 있음 | 같은 항목을 갱신하고 완료 처리. 최초 등록 출처는 유지한다 |
| `approved=true`, 연결 없음 | 이름이 일치하는 내 항목이 있으면 연결하고, 없으면 생성한다. 신규 기본값은 `source=PHOTO`, `checkStatus=PREPARED`, `priority=RECOMMENDED`, `category=ETC`이며 category는 승인 요청으로 보완 가능 |
| `matchedItemIds: [8]` 또는 `[8, 9]` | 같은 여행의 항목인지 검증한 뒤 이 인식 결과의 연결을 배열 전체로 교체한다. 실제 동일 물품이 중복 항목으로 남으면 먼저 병합·수량 확인을 요청한다 |
| `matchedItemIds: []` | 미승인 결과의 후보 연결 해제에 사용한다. 최종 `approved=true`와 함께 연결이 비도록 요청하면 `400` |
| `matchedItemIds` 생략 | 기존 연결 유지. 단, 승인 시 연결이 전혀 없으면 위 생성·연결 규칙을 적용한다 |

이름·수량을 수정한 승인 결과는 연결 항목에도 반영한다. 연결 항목이 여러 개이거나 다른
사진과 수량이 충돌하면 임의로 합산하지 않고 `409`와 재확인 안내를 반환한다.
사진 여러 장에 같은 물품이 보이면 하나의 항목으로 연결하고 사용자가 **최종 수량**을
정한다. 반복 승인은 기존 연결을 사용하므로 항목을 더 만들지 않는다.

사용자가 명시적으로 `approved=false`로 취소하면 해당 연결의 `confirmed_by_user`도
해제한다. 영향을 받는 항목에 다른 승인·확정 연결이 없으면 `UNCHECKED`로 돌려 실제 챙김을
다시 확인하도록 한다. 항목은 삭제하지 않는다. 이 명시적 승인 취소는 사진 재분석에서
물품을 못 찾는 경우와 다르다. 단순 미인식은 기존 완료 상태를 바꾸지 않는다.

`linkedItems`가 배열인 이유는 기존 N:M 모델 때문이다(05). 승인된 물품이 내 목록에 없이
`extra`로만 남는 성공 응답은 허용하지 않는다. 미승인 후보는 확정 준비 상태·무게·규정에
넣지 않으며 S-04에서 계속 확인한다.

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
  "weightLimitG": 10000,
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
  "transport": "FLIGHT", "status": "DRAFT", "createdAt": "2026-09-03T05:30:00Z" }
```

### `GET /api/trips` — 목록 (화면 `S-01`)

```json
{
  "trips": [
    { "tripId": 1, "origin": "서울", "destination": "도쿄",
      "startDate": "2026-10-01", "endDate": "2026-10-04",
      "transport": "FLIGHT", "status": "CONFIRMED", "completionRate": 0.5 },
    { "tripId": 2, "origin": "서울", "destination": "오사카",
      "startDate": "2026-05-02", "endDate": "2026-05-04",
      "transport": "FLIGHT", "status": "DONE", "completionRate": 1.0 },
    { "tripId": 3, "origin": "서울", "destination": "부산",
      "startDate": "2026-03-14", "endDate": "2026-03-15",
      "transport": "TRAIN", "status": "DONE", "completionRate": 1.0 }
  ]
}
```

### `GET /api/trips/{tripId}/items` — 체크리스트 (화면 `S-05`)

```json
{
  "items": [
    {
      "itemId": 5,
      "name": "충전기",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 7,
      "name": "변환 플러그",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "REQUIRED",
      "source": "AI",
      "checkStatus": "UNCHECKED",
      "photoStatus": "NOT_IN_PHOTO"
    }
  ],
  "completionRate": 0.5,
  "recommendationJobId": 1041
}
```

| 필드 | 값 |
| --- | --- |
| `category` | `DOCUMENT` `CLOTHING` `ELECTRONIC` `TOILETRY` `MEDICINE` `ETC` |
| `priority` | `REQUIRED` `RECOMMENDED` |
| `source` | `RULE` `PHOTO` `AI` `USER` — 최초 등록 경로. `PHOTO`는 사진 승인으로 신규 생성, `AI`·`RULE`은 해당 출처의 후보를 사용자가 채택, `USER`는 직접 추가 |
| `checkStatus` | `PREPARED`가 실제 완료, 나머지 `UNCHECKED` `NEEDS_CHECK` `NOT_IN_PHOTO`는 미완료. 신규 채택·직접 추가는 `UNCHECKED` |
| `photoStatus` | `CONFIRMED` `NEEDS_CHECK` `NOT_IN_PHOTO` — 인식 연결에서 계산하는 별도 사진 상태 |

### `POST /api/trips/{tripId}/items` — 직접 추가·추천 채택

```json
// Request — 직접 추가
{ "name": "우산", "category": "ETC", "qty": 1, "priority": "RECOMMENDED" }
```

`201 Created` + `Location: /api/trips/12/items/12`. 추천 참조가 없으면 서버가
`source=USER`, `checkStatus=UNCHECKED`로 채운다. 이미 챙겼다면 이후 PATCH로 완료 처리한다.

```json
// Request — 추천 후보 선택·승인, 내용과 수량은 사용자가 수정 가능
{ "name": "변환 플러그", "category": "ELECTRONIC", "qty": 1,
  "priority": "REQUIRED", "recommendation": { "jobId": 1041, "candidateIndex": 0 } }
```

```json
// Response 201 — 선택하지 않은 다른 추천은 내 목록에 넣지 않는다
{ "itemId": 7, "name": "변환 플러그", "category": "ELECTRONIC", "qty": 1,
  "priority": "REQUIRED", "source": "AI", "checkStatus": "UNCHECKED",
  "photoStatus": "NOT_IN_PHOTO" }
```

- `candidateIndex`는 완료된 추천의 `output.items`에서 **0부터 시작하는 위치**다. 작업 완료 후
  후보 배열 순서·원래 내용은 바꾸지 않는다. 서버는 후보의 `acceptedItemId`만 갱신한다.
- 서버가 같은 여행·사용자의 `COMPLETED / PACKING_LIST` 작업인지, 위치가 유효한지 확인한다.
  다른 여행의 작업은 `404`, 미완료 작업은 `409`, 잘못된 위치·값은 `400`이다.
- 신규 항목은 후보의 `source`(`AI` 또는 `RULE`)를 서버가 복사하고 `UNCHECKED`로 만든다.
  클라이언트가 임의로 `source`·완료 상태를 지정하지 않는다.
- 후보의 `acceptedItemId`가 이미 있으면 같은 항목을 `200`으로 반환한다. 재시도 본문의
  이름·수량으로 기존 항목을 다시 덮어쓰거나 완료 상태를 되돌리지 않는다.
- 최초 채택이라도 원래 후보명 또는 수정된 이름과 같은 내 항목이 있으면 그 항목에 연결해
  `200`으로 반환한다. 상태·수량·출처를 유지하며 차이는 항목 PATCH로 사용자가 정정한다.
- 이름 비교는 앞뒤 공백 제거·연속 공백 정리 후 일치를 기준으로 한다. 명백한 동의어는
  후보 생성 단계에서 제외하고, 자동으로 동일시하기 어려운 물품은 사용자 연결로 확인한다.
- 같은 여행의 항목 추가·수정·삭제·사진 승인·추천 채택은 여행 단위 트랜잭션으로 직렬화한다.
  항목과 `acceptedItemId`를 함께 저장해 동시 클릭에도 중복 생성을 막는다(05).
- 이름 1~100자(공백만 금지), qty 1~99 정수, category·priority는 위 enum을 검증한다.
- 여러 후보를 선택하면 기존 단건 POST를 후보별로 호출한다. 일부 실패 시 성공한 항목은
  유지하고 실패 후보만 재시도한다. 전체를 다시 보내도 이미 채택된 후보는 `200`이다.
- 화면에서 후보를 정렬·숨겨도 `candidateIndex`는 원래 응답 배열의 위치를 사용한다.
  이후 사진 승인 등으로 같은 물품이 내 목록에 생겼다면 표시 시 현재 내 목록과 대조해
  `추가됨`으로 처리한다. 저장된 후보 배열을 재정렬하거나 삭제하지 않는다.

### `PATCH /api/trips/{tripId}/items/{itemId}` — 수정·실제 완료 처리

```json
// Request — 보낸 필드만 바꾼다. 추천 채택과 별도 동작이다
{ "checkStatus": "PREPARED", "qty": 2 }
```

사진 없이 직접 챙김 완료를 확인한 항목도 완료율·무게에 포함한다. 수량·완료 여부 변경 후
준비율을 다시 계산하고 현재 입력으로 무게 작업을 다시 요청한다. 사진 재분석만으로
`PREPARED`를 취소하지 않는다. `photoStatus`는 조회 전용이며 PATCH로 받지 않는다.

### `DELETE /api/trips/{tripId}/items/{itemId}` — 삭제

`204 No Content`. 같은 트랜잭션에서 그 항목을 참조하는 해당 여행의 추천
`acceptedItemId`도 `null`로 해제한다. 화면은 내 목록·추천을 다시 읽는다.
사진에서 승인한 항목을 삭제하면 연결 인식 결과의 `approved`도 해제해 S-04에서 다시
확인하도록 한다. 하나의 인식 결과가 다른 내 항목에도 확정 연결돼 있다면 그 승인은 유지한다.

### 완료율·사진 상태·현재 무게의 공통 규약

- `completionRate = PREPARED 항목 수 / 내 목록 전체 항목 수`. 빈 목록은 `0`이다.
  항목의 qty로 가중하지 않는다. 홈·내 목록·검수 결과에 같은 식을 사용한다.
- `photoStatus`: 승인된 인식 결과와 사용자 확정 연결이 있으면 `CONFIRMED`, 미승인 연결
  후보만 있으면 `NEEDS_CHECK`, 연결이 없으면 `NOT_IN_PHOTO`. 실제 완료 상태와 독립적이다.
- `readiness.prepared`와 `readiness.unprepared`는 내 목록을 완료 여부로 나눈다. 각 항목에
  `photoStatus`를 표시한다. 미채택 추천·미승인 인식 후보를 내 목록 집계에 넣지 않는다.
- `GET items`는 내 목록과 가장 최근 완료된 추천 작업의 `recommendationJobId`(없으면 `null`)를
  반환한다. 재접속 시 이 ID로 후보를 다시 읽는다. 생성 중인 새 추천은 기존 내 목록을 가리지 않는다.
- `inspection.weight`는 가장 최근 완료된 무게 작업 중 **현재 입력과 같은 결과**만 반환한다.
  현재 입력과 다르거나 결과가 없으면 `null`이다. 완료 여부·이름·수량·가방 정보 및 계산 제외
  목록을 작업 입력과 대조한다. 오래된 작업이 뒤늦게 끝나도 현재 결과로 사용하지 않는다.
- S-06·S-07에서 무게가 `null`이면 현재 입력으로 `WEIGHT_ESTIMATE`를 요청·폴링한다.
  추천 채택만으로는 합계가 늘지 않으며, 실제 완료 확인 후에만 계산 대상에 포함된다.

### `POST /api/trips/{tripId}/photos` — 사진 업로드

`multipart/form-data`. 파트 이름은 `files`(복수 가능), `bagKind`(`CABIN`|`CHECKED`).

```json
// 201 Created
{ "photos": [{ "photoId": 1, "fileUrl": "/uploads/demo/bag-01.jpg",
               "bagKind": "CABIN", "uploadedAt": "2026-09-03T05:31:00Z" }] }
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
