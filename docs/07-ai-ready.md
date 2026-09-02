# AI-Ready 설계

> 발표 2번 섹션 (2분): 서비스 내 AI 확장 지점 소개, 프롬프트 설계 내용 및
> 입출력 JSON 스키마 구조
>
> 채점 기준: **"AI 확장 지점 정의 및 프롬프트/JSON 스키마 타당성"**
> Peer Review: **"향후 AI 기능이 들어올 확장 지점이 서비스 흐름상 타당한가?"**,
> **"AI 프롬프트 설계 및 입출력 JSON 스키마가 기존 웹 구조와 호환되는가?"**

**이 프로젝트에서 AI 코드는 작성하지 않는다.** 3일차 데모까지 AI는 Mock이다.
대신 **AI가 들어올 자리를 정확히 어디에, 어떤 규격으로 비워 뒀는지**를 설계한다.
그것이 이 프로젝트의 주제다.

## AI 확장 지점

네 곳이다. **엔드포인트는 하나**고 `jobType` 값만 다르다
([ADR 0003](adr/0003-ai-job-endpoint.md)).

| ID | `jobType` | 하는 일 | Use-Case | 화면 | 지금 | 나중 |
| --- | --- | --- | --- | --- | --- | --- |
| AI-01 | `BAG_CHECK` | 사진에서 물품 후보·수량·신뢰도를 뽑는다 | UC-04 | `S-04` | Mock 고정 인식 결과 | 비전 모델 |
| AI-02 | `PACKING_LIST` | 승인된 물품을 빼고 **부족한 것만** 추천한다 | UC-05 | `S-05` | Mock 고정 목록 | LLM |
| AI-03 | `WEIGHT_ESTIMATE` | 무게를 **범위**로 추정하고 한도와 비교한다 | UC-10 | `S-06` `S-07` | Mock 고정 범위 | 품목 중량 DB + LLM 보정 |
| AI-04 | `RULE_CHECK` | 질문·물품에서 **속성을 구조화**하고 판정을 **설명**한다 | UC-07 · UC-08 | `S-06` `S-08` `S-09` | Mock 고정 판정 | LLM 구조화 + **규칙 엔진** |

### 왜 여기인가

[`01-service-plan.md`](01-service-plan.md)의 기준은 *"AI를 빼도 서비스가 돌아가되,
고통스럽게 돌아간다"* 다. 네 지점은 전부 **사용자가 지금 손으로 하고 있는 판단 중,
경우가 흐릿하고 표현이 매번 달라서 규칙으로 짤 수 없는 것**이다.

- **사진에서 무엇이 있는지 알아보는 일** — 규칙 기반으로는 아예 불가능하다. 이 서비스의
  약속 *"사진만 찍으면 알려준다"* 가 여기서 시작한다. AI가 없으면 사용자가 목록을 손으로 친다.
- **여행 조건에서 빠진 것을 떠올리는 일** — "10월 초 도쿄, 친구와 디즈니랜드" 에서
  변환 플러그·편한 신발을 떠올리는 건 경우의 수가 무한하다. AI가 없으면 고정 체크리스트다.
- **표에 없는 물건의 무게를 가늠하는 일** — 대부분은 `item_weights` 마스터로 되지만,
  마스터에 없는 것은 상식으로 채워야 한다. AI가 없으면 그 물건은 계산에서 빠진다.
- **자연어 질문에서 물품과 속성을 뽑는 일** — *"20000mAh 보조배터리 기내 되나요?"* 를
  `보조배터리 · 74Wh` 로 바꾸는 건 표현이 매번 다르다. **판정은 AI가 하지 않는다.**

반대로 **AI를 두지 않은 곳**도 같은 기준으로 정했다. 반입 판정(`transport_rules` 를 보는
규칙 엔진), 준비 상태 비교(체크리스트 ↔ 승인 물품 조인), 무게 `verdict`(산식). 규칙으로
정확히 풀리는 일에 AI를 두면 틀릴 자리만 늘어난다.

### 무엇이 바뀌고 무엇이 안 바뀌는가

| 계층 | AI 결합 시 변경 여부 |
| --- | --- |
| Frontend | **변경 없음** — 같은 엔드포인트, 같은 응답 스키마를 그대로 소비 |
| REST API 규격 | **변경 없음** — [`06-api-spec.md`](06-api-spec.md)의 계약 그대로 |
| DB 스키마 | **변경 없음** — `ai_jobs.output_payload`(jsonb)에 그대로 저장 |
| Backend 내부 | **여기만 변경** — `MockAiClient` → `RealAiClient`. 인터페이스 `AiClient` 는 그대로 |
| 환경 변수 | `.env`에 API 키·모델명 추가. **코드 변경 없음** |

> 이 표가 발표 2번 섹션의 핵심 슬라이드다.
> **"백엔드 한 곳만 바뀐다"**를 보여주는 것이 목표다.

---

## 봉투와 알맹이

`POST /api/ai-jobs` 의 **봉투**(`jobId` · `status` · `pollAfterMs` · 폴링 규약)는
[`06-api-spec.md`](06-api-spec.md)가 정한다. 이 문서는 **알맹이** — `input` 과 `output` 의
내부 구조 — 만 다룬다.

알맹이에는 세 가지 약속이 있다. 4종 전부 이 약속대로 설계했다.

| | 무엇 | 어디에 남나 |
| --- | --- | --- |
| **`input`** | FE가 **그 시점 화면에 이미 갖고 있는 사실**만 보낸다. FE가 모르는 값을 요구하지 않는다 | `ai_jobs.input_payload` 에 그대로 |
| **서버 보강** | 모델을 부르기 전에 백엔드가 **마스터 데이터**를 덧붙인다 — 날씨(Open-Meteo), `item_weights` 범위, `transport_rules`. 프롬프트 템플릿에 `{{server:…}}` 로 표시 | 프롬프트에만. `output` 에 출처(`weatherSource` 등)를 남긴다 |
| **`output`** | `ai_jobs.output_payload` 에 저장돼 FE가 받는 **최종 형태**. 모델이 만드는 필드와 **서버가 규칙으로 채우는 필드**를 "누가 채우나" 표로 구분한다 | 검증은 합쳐진 최종 객체에 건다 |

### 스키마 설계 시 지킨 것

- **모든 필드가 `required` 다.** 없을 수 있는 값은 `null` 을 허용하되 필드 자체는 반드시 낸다.
  LLM이 필드를 빠뜨리는 것과 값이 없는 것을 구분해야 FE가 분기하지 않는다.
- **`additionalProperties: false`.** LLM이 멋대로 필드를 추가하면 검증에서 걸러진다.
- **배열마다 `maxItems`.** LLM이 100개를 돌려주면 화면이 깨진다.
- **enum 값은 [`database/schema.sql`](../database/schema.sql)의 `CHECK` 제약과 글자까지 같다.**
  `category` 6종 · `priority` 2종 · `confidenceLevel` 3종 · `verdict` 6종 · `transport` · `purpose` · `bagType`.
  아래 기계 검증이 이것을 확인한다.
- **필드명은 `camelCase`.** DB 컬럼(`snake_case`)과 경계에서 변환한다. 06과 같은 규칙.
- **자유 서술 필드는 최소로.** `reason` · `tips` · `answer` 만 문장이고, 전부 길이 상한이 있다.
- **서버가 채우는 필드도 스키마에 있다.** 프롬프트는 그 필드를 "내지 말라" 고 하고,
  서버가 채운 뒤 최종 객체를 검증한다. 모델 출력만 따로 검증하지 않는다 — 스키마가 둘이 되면 어긋난다.

---

## AI-01 `BAG_CHECK` — 사진 속 물품 인식

**UC-04 · `S-04`.** 사진에서 물품 후보를 뽑는다. **승인 전에는 아무 데도 반영되지 않는다.**
`output.detections[]` 한 항목이 `detected_objects` 한 행이다.

### 입력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "BAG_CHECK input",
  "type": "object",
  "properties": {
    "photoIds": {
      "type": "array",
      "minItems": 1,
      "maxItems": 20,
      "uniqueItems": true,
      "items": {
        "type": "integer",
        "minimum": 1
      },
      "description": "trip_photos.id. 백엔드가 파일을 읽어 모델에 이미지로 넘긴다"
    }
  },
  "required": [
    "photoIds"
  ],
  "additionalProperties": false
}
```

사진 파일은 `input` 에 없다. 백엔드가 `photoIds` 로 `trip_photos.file_path` 를 읽어
모델에 이미지로 넘긴다. `input_payload` 에는 id만 남는다.

### 출력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "BAG_CHECK output",
  "type": "object",
  "properties": {
    "detections": {
      "type": "array",
      "maxItems": 50,
      "description": "detected_objects 한 행과 1:1. 사진이 다르면 항목도 다르다",
      "items": {
        "type": "object",
        "properties": {
          "photoId": {
            "type": "integer",
            "minimum": 1,
            "description": "입력 photoIds 에 있는 값만"
          },
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "confidence": {
            "type": "number",
            "minimum": 0,
            "maximum": 1,
            "description": "소수 셋째 자리까지. NUMERIC(4,3)"
          },
          "confidenceLevel": {
            "enum": [
              "HIGH",
              "MEDIUM",
              "LOW"
            ],
            "description": "서버가 confidence 로 채운다: ≥0.80 HIGH · ≥0.50 MEDIUM · 그 외 LOW"
          },
          "missingInfo": {
            "type": [
              "string",
              "null"
            ],
            "maxLength": 100,
            "description": "보이지 않아 못 정한 속성. 예: 용량(ml) · 배터리 정격(Wh) · 날 길이(cm)"
          },
          "labelText": {
            "type": [
              "string",
              "null"
            ],
            "maxLength": 200,
            "description": "라벨·포장에서 읽힌 글자 원문(OCR). 브랜드·용량 표기가 여기 온다"
          }
        },
        "required": [
          "photoId",
          "name",
          "qty",
          "confidence",
          "confidenceLevel",
          "missingInfo",
          "labelText"
        ],
        "additionalProperties": false
      }
    },
    "failedPhotoIds": {
      "type": "array",
      "maxItems": 20,
      "uniqueItems": true,
      "items": {
        "type": "integer",
        "minimum": 1
      },
      "description": "분석에 실패한 사진. S-04 는 성공한 것만 보여주고 실패 사진에 재시도 버튼을 단다"
    }
  },
  "required": [
    "detections",
    "failedPhotoIds"
  ],
  "additionalProperties": false
}
```

### 누가 채우나

| 누가 | 무엇을 |
| --- | --- |
| **모델** | `detections[].photoId` · `detections[].name` · `detections[].qty` · `detections[].confidence` · `detections[].missingInfo` · `detections[].labelText` |
| **서버 (규칙)** | `detections[].confidenceLevel (경계값 0.80 / 0.50)` · `failedPhotoIds (모델 호출이 실패한 사진)` |

`confidenceLevel` 을 모델이 아니라 서버가 채우는 이유는 [`05-erd.md`](05-erd.md)의
*"confidence_level 을 따로 두는 근거"* 와 같다 — 경계값이 바뀌어도 사용자가 그때 보고
승인한 표시는 그대로여야 한다. 경계값은 코드가 아니라 설정에 둔다.

### 예시 — 시드 여행 · 사진 2장

[`seed.sql`](../database/seed.sql)의 `detected_objects` 8행과 같다. `S-04` 데모 장면이
이 출력에서 나온다 — 신뢰도 높음 5개, 확인 필요 2개(용량·날 길이), 불분명 1개.

```json
{
  "photoIds": [
    1,
    2
  ]
}
```

```json
{
  "detections": [
    {
      "photoId": 1,
      "name": "충전기",
      "qty": 1,
      "confidence": 0.93,
      "confidenceLevel": "HIGH",
      "missingInfo": null,
      "labelText": null
    },
    {
      "photoId": 1,
      "name": "보조배터리",
      "qty": 1,
      "confidence": 0.88,
      "confidenceLevel": "HIGH",
      "missingInfo": "배터리 정격(Wh)",
      "labelText": null
    },
    {
      "photoId": 1,
      "name": "상의",
      "qty": 4,
      "confidence": 0.81,
      "confidenceLevel": "HIGH",
      "missingInfo": null,
      "labelText": null
    },
    {
      "photoId": 1,
      "name": "하의",
      "qty": 2,
      "confidence": 0.79,
      "confidenceLevel": "MEDIUM",
      "missingInfo": null,
      "labelText": null
    },
    {
      "photoId": 1,
      "name": "속옷",
      "qty": 4,
      "confidence": 0.72,
      "confidenceLevel": "MEDIUM",
      "missingInfo": null,
      "labelText": null
    },
    {
      "photoId": 2,
      "name": "화장품 용기",
      "qty": 1,
      "confidence": 0.64,
      "confidenceLevel": "MEDIUM",
      "missingInfo": "용량(ml)",
      "labelText": null
    },
    {
      "photoId": 2,
      "name": "가위",
      "qty": 1,
      "confidence": 0.91,
      "confidenceLevel": "HIGH",
      "missingInfo": "날 길이(cm)",
      "labelText": null
    },
    {
      "photoId": 2,
      "name": "검정 파우치",
      "qty": 1,
      "confidence": 0.43,
      "confidenceLevel": "LOW",
      "missingInfo": null,
      "labelText": null
    }
  ],
  "failedPhotoIds": []
}
```

### System Prompt

```text
너는 여행 가방 사진에서 물품을 찾아내는 검수 보조자다.

규칙
1. 사진에 보이는 것만 적는다. 보이지 않는 속성 — 용량(ml)·배터리 정격(Wh)·날 길이(cm) — 은 추정하지 않는다. 그 속성이 반입 판정에 필요한 물품이면 missingInfo 에 "용량(ml)" 처럼 무엇이 필요한지 적는다. 라벨이 읽히면 labelText 에 글자를 원문 그대로 옮기고, 그것으로 속성이 확정되면 missingInfo 는 null 이다.
2. 같은 종류가 한 사진에 여러 개 보이면 한 항목으로 묶고 qty 로 센다. 사진이 다르면 항목도 다르다 — photoId 마다 따로 낸다. 합치는 것은 서버가 한다.
3. 무엇인지 확신이 없으면 이름을 지어내지 말고 보이는 대로 적는다(예: "검정 파우치"). confidence 를 낮춘다.
4. name 은 한국어 일반명사로 100자 이내. 브랜드명은 name 이 아니라 labelText 에 둔다.
5. confidence 는 0~1 사이 소수 셋째 자리까지.
6. 출력은 아래 JSON Schema 를 만족하는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. photoId 는 입력에 있는 값만 쓴다. confidenceLevel 과 failedPhotoIds 는 서버가 채우므로 내지 않는다.
```

### User Prompt 템플릿

```text
여행: {{server:trip.destination}} {{server:trip.startDate}}~{{server:trip.endDate}} · 이동수단 {{server:trip.transport}}
사진 {{photoIds.length}}장. photoId 와 가방 종류: {{server:photos as "photoId=1 (CABIN), photoId=2 (CABIN)"}}

[사진 {{photoIds.length}}장 첨부]

위 사진에서 물품을 찾아 JSON 으로 답하라.
```

---

## AI-02 `PACKING_LIST` — 부족한 준비물 추천

**UC-05 · `S-05`.** 승인된 물품(`alreadyPacked`)을 빼고 **부족한 것만** 돌려준다.
FE는 `output.items[]` 를 `POST /api/trips/{tripId}/items` 로 `source = AI` 항목으로 만든다.

`input` 은 [`06-api-spec.md`](06-api-spec.md)의 요청 예시와 같고, `output` 은 완료 예시와
같다. 아래 스키마로 06의 두 예시가 그대로 통과한다 (기계 검증 항목).

### 입력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "PACKING_LIST input",
  "type": "object",
  "properties": {
    "destination": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100
    },
    "startDate": {
      "type": "string",
      "format": "date"
    },
    "endDate": {
      "type": "string",
      "format": "date"
    },
    "transport": {
      "enum": [
        "FLIGHT",
        "TRAIN",
        "BUS",
        "CAR"
      ]
    },
    "purpose": {
      "enum": [
        "TOUR",
        "BUSINESS",
        "REST",
        "STUDY"
      ]
    },
    "note": {
      "type": [
        "string",
        "null"
      ],
      "maxLength": 500
    },
    "alreadyPacked": {
      "type": "array",
      "maxItems": 100,
      "description": "사진에서 승인된 물품(detected_objects.approved = true). 없으면 빈 배열. 필드를 생략하지 않는다",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "category": {
            "enum": [
              "DOCUMENT",
              "CLOTHING",
              "ELECTRONIC",
              "TOILETRY",
              "MEDICINE",
              "ETC"
            ]
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          }
        },
        "required": [
          "name",
          "category",
          "qty"
        ],
        "additionalProperties": false
      }
    }
  },
  "required": [
    "destination",
    "startDate",
    "endDate",
    "transport",
    "purpose",
    "note",
    "alreadyPacked"
  ],
  "additionalProperties": false
}
```

`alreadyPacked` 는 **빈 배열이어도 반드시 보낸다.** 06의 규약이다 — 빈 배열과 미전송을
구분하지 않으면 Mock과 실제 LLM의 동작이 갈린다.

### 출력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "PACKING_LIST output",
  "type": "object",
  "properties": {
    "items": {
      "type": "array",
      "maxItems": 40,
      "description": "부족한 것만. FE 가 POST /api/trips/{tripId}/items 로 source=AI 항목을 만든다",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "category": {
            "enum": [
              "DOCUMENT",
              "CLOTHING",
              "ELECTRONIC",
              "TOILETRY",
              "MEDICINE",
              "ETC"
            ]
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "priority": {
            "enum": [
              "REQUIRED",
              "RECOMMENDED"
            ]
          }
        },
        "required": [
          "name",
          "category",
          "qty",
          "priority"
        ],
        "additionalProperties": false
      }
    },
    "tips": {
      "type": "array",
      "maxItems": 5,
      "items": {
        "type": "string",
        "minLength": 1,
        "maxLength": 120
      }
    },
    "weatherSource": {
      "enum": [
        "FORECAST",
        "SEASONAL"
      ],
      "description": "서버가 채운다. 출발일이 16일 이내면 FORECAST, 넘거나 조회 실패면 SEASONAL(계절 평균). S-05 가 SEASONAL 이면 안내 문구를 띄운다"
    }
  },
  "required": [
    "items",
    "tips",
    "weatherSource"
  ],
  "additionalProperties": false
}
```

### 누가 채우나

| 누가 | 무엇을 |
| --- | --- |
| **모델** | `items[]` · `tips[]` |
| **서버 (규칙)** | `weatherSource (어느 날씨를 프롬프트에 넣었는지)` |

날씨는 FE가 모른다. 백엔드가 Open-Meteo에서 받아 프롬프트에 넣고, **어느 날씨였는지**를
`weatherSource` 로 남긴다. `SEASONAL` 이면 `S-05` 가 *"실시간 예보가 아닌 계절 평균
기준입니다"* 를 띄운다.

### 예시 — 시드 여행 · 도쿄 3박4일 · 사진 승인 후

```json
{
  "destination": "도쿄",
  "startDate": "2026-10-01",
  "endDate": "2026-10-04",
  "transport": "FLIGHT",
  "purpose": "TOUR",
  "note": "친구 2명, 디즈니랜드, 사진 많이 찍을 예정",
  "alreadyPacked": [
    {
      "name": "충전기",
      "category": "ELECTRONIC",
      "qty": 1
    },
    {
      "name": "보조배터리",
      "category": "ELECTRONIC",
      "qty": 1
    },
    {
      "name": "상의",
      "category": "CLOTHING",
      "qty": 4
    },
    {
      "name": "하의",
      "category": "CLOTHING",
      "qty": 2
    },
    {
      "name": "속옷",
      "category": "CLOTHING",
      "qty": 4
    }
  ]
}
```

```json
{
  "items": [
    {
      "name": "여권",
      "category": "DOCUMENT",
      "qty": 1,
      "priority": "REQUIRED"
    },
    {
      "name": "변환 플러그",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "REQUIRED"
    },
    {
      "name": "상비약",
      "category": "MEDICINE",
      "qty": 1,
      "priority": "RECOMMENDED"
    },
    {
      "name": "화장품",
      "category": "TOILETRY",
      "qty": 1,
      "priority": "RECOMMENDED"
    },
    {
      "name": "우산",
      "category": "ETC",
      "qty": 1,
      "priority": "RECOMMENDED"
    }
  ],
  "tips": [
    "일본 콘센트는 A타입입니다.",
    "10월 초 도쿄는 낮 24도, 얇은 겉옷을 권합니다.",
    "디즈니랜드는 하루 2만 보 이상 걷습니다. 편한 신발을 챙기세요."
  ],
  "weatherSource": "FORECAST"
}
```

`alreadyPacked` 의 다섯 개는 시드에서 `approved = true` 인 인식 결과다. 출력 다섯 개는
시드 체크리스트의 `source = AI` · `RULE` 항목과 같다 — 이 출력이 그 행들을 만든다.

### System Prompt

```text
너는 여행 준비물을 추천하는 보조자다. 사용자가 이미 챙긴 것은 다시 추천하지 않는다.

규칙
1. alreadyPacked 에 있는 물품 — 이름이 같거나 명백히 같은 종류("상의" 가 있으면 "티셔츠" 를 또 내지 않는다) — 은 items 에 넣지 않는다.
2. 여행지·기간·목적·이동수단·날씨에 맞는 부족분만 낸다. 최대 40개. 빠뜨리면 곤란한 것을 앞에 둔다.
3. priority: 없으면 여행이 성립하지 않거나 현지에서 구하기 어려운 것(여권·항공권·처방약·변환 플러그)은 REQUIRED, 나머지는 RECOMMENDED.
4. category 는 DOCUMENT · CLOTHING · ELECTRONIC · TOILETRY · MEDICINE · ETC 중 하나만.
5. qty 는 기간에 맞춘다. 정하기 어려우면 1.
6. tips 는 최대 5개, 각 120자 이내. 날씨 근거가 있으면 수치를 그대로 인용한다(예: "낮 24도"). 현지 콘센트·전압처럼 목적지에서 정해지는 사실은 적어도 된다.
7. 액체·배터리 같은 반입 규정 판정은 하지 않는다. 그건 다른 단계가 한다.
8. 출력은 아래 JSON Schema 를 만족하는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. weatherSource 는 서버가 채우므로 내지 않는다.
```

### User Prompt 템플릿

```text
목적지 {{destination}} ({{server:trip.countryCode}}) · {{startDate}}~{{endDate}} ({{server:nights}}박) · 목적 {{purpose}} · 이동수단 {{transport}}
메모: {{note | "없음"}}
날씨 ({{server:weather.source}}, {{server:weather.asOf}} 기준): {{server:weather.summary}}, {{server:weather.minC}}~{{server:weather.maxC}}°C, 강수확률 {{server:weather.rainChance}}

이미 챙긴 것 (다시 추천하지 않는다):
{{alreadyPacked as "- 이름 ×수량 (category)" | "- 없음"}}

부족한 준비물만 JSON 으로 답하라.
```

---

## AI-03 `WEIGHT_ESTIMATE` — 예상 무게 범위

**UC-10 · `S-06` ② · `S-07`.** 승인된 물품의 무게를 **범위**로 추정한다. 단일 값을 내지 않는다.

`GET /api/trips/{tripId}/inspection` 의 `weight` 는 이 출력의 **투영**이다 —
`excluded[]` 를 빼고 `contributions[]` 를 위 3개로 자른 것. `S-06` 은 개수만, `S-07` 은
이 작업 결과 전체를 `GET /api/ai-jobs/{id}` 로 받아 이유까지 그린다.

### 입력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "WEIGHT_ESTIMATE input",
  "type": "object",
  "properties": {
    "bagType": {
      "oneOf": [
        {
          "enum": [
            "CARRY_ON",
            "MEDIUM",
            "LARGE"
          ]
        },
        {
          "type": "null"
        }
      ]
    },
    "bagEmptyG": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": 0,
      "maximum": 20000
    },
    "weightLimitG": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": 1,
      "maximum": 100000
    },
    "items": {
      "type": "array",
      "maxItems": 100,
      "description": "계산에 넣는 것 — check_status = PREPARED 인 체크리스트 항목",
      "items": {
        "type": "object",
        "properties": {
          "itemId": {
            "type": "integer",
            "minimum": 1
          },
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "category": {
            "enum": [
              "DOCUMENT",
              "CLOTHING",
              "ELECTRONIC",
              "TOILETRY",
              "MEDICINE",
              "ETC"
            ]
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          }
        },
        "required": [
          "itemId",
          "name",
          "category",
          "qty"
        ],
        "additionalProperties": false
      }
    },
    "excluded": {
      "type": "array",
      "maxItems": 100,
      "description": "계산에서 뺀 것과 이유. 숨기지 않는다",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "reason": {
            "enum": [
              "NOT_IN_PHOTO",
              "PENDING_APPROVAL"
            ]
          }
        },
        "required": [
          "name",
          "reason"
        ],
        "additionalProperties": false
      }
    }
  },
  "required": [
    "bagType",
    "bagEmptyG",
    "weightLimitG",
    "items",
    "excluded"
  ],
  "additionalProperties": false
}
```

계산에 넣는 것은 `check_status = PREPARED` 인 체크리스트 항목이다. 뺀 것은 **이유와 함께**
`excluded` 로 보낸다 — 사진에서 미확인(`NOT_IN_PHOTO`)과 승인 전(`PENDING_APPROVAL`).
*"계산에서 뺀 항목 수를 숨기지 않는다"* (06).

### 출력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "WEIGHT_ESTIMATE output",
  "type": "object",
  "properties": {
    "minG": {
      "type": "integer",
      "minimum": 0
    },
    "typicalG": {
      "type": "integer",
      "minimum": 0
    },
    "maxG": {
      "type": "integer",
      "minimum": 0
    },
    "limitG": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": 1,
      "description": "서버가 input.weightLimitG 를 그대로 옮긴다. S-07 이 이 응답 하나로 그릴 수 있게"
    },
    "verdict": {
      "enum": [
        "ROOM",
        "NEAR",
        "OVER_RISK",
        "UNKNOWN"
      ],
      "description": "서버가 산식으로 채운다. limitG 가 null 이거나 confidence 가 LOW 면 UNKNOWN · maxG > limitG 면 OVER_RISK · typicalG ≥ 0.8×limitG 면 NEAR · 그 외 ROOM"
    },
    "confidence": {
      "enum": [
        "HIGH",
        "MEDIUM",
        "LOW"
      ]
    },
    "confidenceReason": {
      "type": "string",
      "minLength": 1,
      "maxLength": 200
    },
    "excludedCount": {
      "type": "integer",
      "minimum": 0,
      "description": "= excluded.length. S-06 은 개수만 보여준다"
    },
    "excluded": {
      "type": "array",
      "maxItems": 100,
      "description": "S-07 이 이유와 함께 보여준다. 입력의 excluded 에 모델이 NO_WEIGHT_INFO 로 뺀 것을 덧붙인 목록",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "reason": {
            "enum": [
              "NOT_IN_PHOTO",
              "PENDING_APPROVAL",
              "NO_WEIGHT_INFO"
            ]
          }
        },
        "required": [
          "name",
          "reason"
        ],
        "additionalProperties": false
      }
    },
    "contributions": {
      "type": "array",
      "maxItems": 100,
      "description": "items 마다 하나. subtotalG 큰 순서. S-06 은 위 3개만, S-07 은 전부",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "typicalG": {
            "type": "integer",
            "minimum": 0
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "subtotalG": {
            "type": "integer",
            "minimum": 0,
            "description": "= typicalG × qty"
          }
        },
        "required": [
          "name",
          "typicalG",
          "qty",
          "subtotalG"
        ],
        "additionalProperties": false
      }
    }
  },
  "required": [
    "minG",
    "typicalG",
    "maxG",
    "limitG",
    "verdict",
    "confidence",
    "confidenceReason",
    "excludedCount",
    "excluded",
    "contributions"
  ],
  "additionalProperties": false
}
```

### 누가 채우나

| 누가 | 무엇을 |
| --- | --- |
| **모델** | `minG` · `typicalG` · `maxG` · `confidence` · `confidenceReason` · `excludedCount` · `excluded[]` · `contributions[]` |
| **서버 (규칙)** | `limitG (input 그대로)` · `verdict (산식 — 모델이 낸 값이 있어도 서버 값으로 덮어쓴다)` |

**서버가 저장 전에 검증한다** — 하나라도 어긋나면 `FAILED` 로 돌리고 기본 문구를 보여준다.

- minG ≤ typicalG ≤ maxG
- subtotalG == typicalG × qty
- excludedCount == excluded.length
- bagEmptyG + ΣsubtotalG == typicalG

`verdict` 를 서버가 정하는 이유는 산수이기 때문이다. 모델이 낸 값이 있어도 덮어쓴다.
`limitG` 가 없거나 신뢰도가 `LOW` 면 판정하지 않는다(`UNKNOWN`) — *"결과를 실측값처럼
표현하지 않는다"* (명세 F-10).

### 예시 — 시드 여행 · 06-api-spec inspection.weight 와 같은 수치

```json
{
  "bagType": "CARRY_ON",
  "bagEmptyG": 3200,
  "weightLimitG": 10000,
  "items": [
    {
      "itemId": 2,
      "name": "상의",
      "category": "CLOTHING",
      "qty": 4
    },
    {
      "itemId": 3,
      "name": "하의",
      "category": "CLOTHING",
      "qty": 2
    },
    {
      "itemId": 4,
      "name": "속옷",
      "category": "CLOTHING",
      "qty": 4
    },
    {
      "itemId": 5,
      "name": "충전기",
      "category": "ELECTRONIC",
      "qty": 1
    },
    {
      "itemId": 6,
      "name": "보조배터리",
      "category": "ELECTRONIC",
      "qty": 1
    }
  ],
  "excluded": [
    {
      "name": "여권",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "변환 플러그",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "상비약",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "우산",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "화장품",
      "reason": "PENDING_APPROVAL"
    }
  ]
}
```

```json
{
  "minG": 4570,
  "typicalG": 5410,
  "maxG": 6890,
  "limitG": 10000,
  "verdict": "ROOM",
  "confidence": "MEDIUM",
  "confidenceReason": "사진에서 미확인 4개, 승인 전 1개",
  "excludedCount": 5,
  "excluded": [
    {
      "name": "여권",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "변환 플러그",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "상비약",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "우산",
      "reason": "NOT_IN_PHOTO"
    },
    {
      "name": "화장품",
      "reason": "PENDING_APPROVAL"
    }
  ],
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
    },
    {
      "name": "속옷",
      "typicalG": 60,
      "qty": 4,
      "subtotalG": 240
    },
    {
      "name": "충전기",
      "typicalG": 90,
      "qty": 1,
      "subtotalG": 90
    }
  ]
}
```

`3200 + (200×4 + 400×2 + 60×4 + 90 + 280) = 5410`. 최소·최대도 같은 식으로
`item_weights` 시드의 `min_g` · `max_g` 로 계산했다. 06의 `inspection.weight` 와 g 단위까지
같다 (기계 검증 항목).

### System Prompt

```text
너는 짐 무게를 범위로 추정하는 보조자다. 단일 값을 내지 않는다.

규칙
1. 물품에 weightRange(minG/typicalG/maxG) 가 주어지면 그 값을 그대로 쓴다.
2. weightRange 가 없으면 — 옷·종이·플라스틱처럼 가볍고 균일한 물품은 네 상식으로 범위를 추정한다. 배터리·액체·금속·책·전자기기처럼 밀도가 높아 사진으로 무게를 가늠할 수 없는 것은 추정하지 말고 excluded 에 NO_WEIGHT_INFO 로 넣는다.
3. contributions 는 계산에 넣은 물품마다 하나씩. subtotalG = typicalG × qty. subtotalG 큰 순서로 정렬한다.
4. minG / typicalG / maxG 는 각각 bagEmptyG + Σ(물품 범위 × qty) 다. bagEmptyG 가 null 이면 0 으로 본다.
5. excluded 는 입력의 excluded 를 그대로 옮기고, 2번에서 네가 뺀 것을 뒤에 덧붙인다. excludedCount 는 그 개수다.
6. confidence: excluded 가 계산에 넣은 물품보다 많으면 LOW, 하나라도 있으면 MEDIUM, 없으면 HIGH. confidenceReason 에는 이유를 개수로 적는다(예: "사진에서 미확인 4개, 승인 전 1개").
7. verdict 와 limitG 는 서버가 채우므로 내지 않는다.
8. 출력은 아래 JSON Schema 를 만족하는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다.
```

### User Prompt 템플릿

```text
가방: {{bagType | "종류 미상"}} · 빈 무게 {{bagEmptyG | "미상"}}g · 한도 {{weightLimitG | "미상"}}g

계산에 넣을 물품 (승인된 것만):
{{items as "- name ×qty [category] 범위 {{server:weightRange.minG}}/{{server:weightRange.typicalG}}/{{server:weightRange.maxG}}g" | "범위 없음"}}

계산에서 이미 뺀 것:
{{excluded as "- name (reason)" | "- 없음"}}

무게를 범위로 추정해 JSON 으로 답하라.
```

---

## AI-04 `RULE_CHECK` — 물품 구조화와 판정 설명

**UC-07 · `S-06` ③ · `S-08`** (체크리스트 물품 목록으로) **와 UC-08 · `S-09` 챗봇**
(자연어 질문으로). 호출자가 둘이지만 스키마는 하나다.

**원칙 ④ — 최종 판정은 규칙 엔진이 한다.** 그래서 이 작업만 모델을 두 번 부른다.

```text
input ──▶ [모델 1차] 물품·속성 구조화 ──▶ [규칙 엔진] transport_rules 대조, verdict
                                                 │
output ◀── [모델 2차] reason · answer · followUpQuestion ◀──┘
```

모델은 `verdict` 를 **정하지 않는다.** 구조화하고, 엔진 결과를 사람 말로 옮긴다.
`GET /api/trips/{tripId}/inspection` 의 `customs[]` 는 `output.results[]` 의 투영이다.

### 입력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "RULE_CHECK input",
  "type": "object",
  "properties": {
    "transport": {
      "enum": [
        "FLIGHT",
        "TRAIN",
        "BUS",
        "CAR"
      ],
      "description": "transport_rules 를 고르는 키. 챗봇은 여행이 없으면 FLIGHT 를 보낸다"
    },
    "airline": {
      "type": [
        "string",
        "null"
      ],
      "maxLength": 50
    },
    "question": {
      "type": [
        "string",
        "null"
      ],
      "minLength": 1,
      "maxLength": 500,
      "description": "챗봇(S-09) 자연어 질문. 물품 목록으로 부를 때는 null"
    },
    "items": {
      "type": "array",
      "maxItems": 50,
      "description": "S-06/S-08 이 보내는 확인 대상. 챗봇은 빈 배열 — 모델이 질문에서 뽑는다",
      "items": {
        "type": "object",
        "properties": {
          "itemId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1,
            "description": "체크리스트 항목이면 id, 추가 물품(detection)이면 null"
          },
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "capacityMl": {
            "type": [
              "number",
              "null"
            ],
            "minimum": 0
          },
          "batteryWh": {
            "type": [
              "number",
              "null"
            ],
            "minimum": 0
          },
          "bladeCm": {
            "type": [
              "number",
              "null"
            ],
            "minimum": 0
          }
        },
        "required": [
          "itemId",
          "name",
          "qty",
          "capacityMl",
          "batteryWh",
          "bladeCm"
        ],
        "additionalProperties": false
      }
    }
  },
  "required": [
    "transport",
    "airline",
    "question",
    "items"
  ],
  "additionalProperties": false,
  "anyOf": [
    {
      "properties": {
        "question": {
          "type": "string",
          "minLength": 1
        }
      },
      "required": [
        "question"
      ]
    },
    {
      "properties": {
        "items": {
          "minItems": 1
        }
      }
    }
  ]
}
```

`question` 이 있거나 `items` 가 비어 있지 않아야 한다(`anyOf`). 챗봇은 여행이 없어도
쓸 수 있으므로 봉투의 `tripId` 가 `null` 이고 `transport` 는 `FLIGHT` 를 보낸다
— `ai_jobs.trip_id` 가 nullable 인 이유다.

### 출력 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "RULE_CHECK output",
  "type": "object",
  "properties": {
    "results": {
      "type": "array",
      "maxItems": 50,
      "description": "물품마다 하나. 06 의 customs[] 는 이 배열의 투영(itemId·name·verdict·reason·missingInfo·sourceUrl·checkedAt)",
      "items": {
        "type": "object",
        "properties": {
          "itemId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1
          },
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "attributes": {
            "type": "object",
            "description": "규칙 엔진이 판단에 쓴 값. 모델이 질문·라벨에서 뽑았거나 입력에서 온 것. 없으면 null — 추정하지 않는다",
            "properties": {
              "capacityMl": {
                "type": [
                  "number",
                  "null"
                ],
                "minimum": 0
              },
              "batteryWh": {
                "type": [
                  "number",
                  "null"
                ],
                "minimum": 0
              },
              "bladeCm": {
                "type": [
                  "number",
                  "null"
                ],
                "minimum": 0
              }
            },
            "required": [
              "capacityMl",
              "batteryWh",
              "bladeCm"
            ],
            "additionalProperties": false
          },
          "verdict": {
            "enum": [
              "CABIN_OK",
              "CHECKED_OK",
              "CHECKED_FORBIDDEN",
              "RESTRICTED",
              "NEED_MORE_INFO",
              "ASK_AIRLINE"
            ],
            "description": "규칙 엔진이 채운다. 모델이 정하지 않는다"
          },
          "ruleId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1,
            "description": "transport_rules.id. S-08 이 GET /api/rules 로 원문을 연다. 맞는 규정이 없으면 null"
          },
          "conditionNote": {
            "type": [
              "string",
              "null"
            ],
            "maxLength": 200,
            "description": "규정의 조건 원문. 예: 100Wh 이하"
          },
          "reason": {
            "type": "string",
            "minLength": 1,
            "maxLength": 300,
            "description": "사용자에게 보이는 한 문장. 규정 description 을 바탕으로 모델이 다듬는다"
          },
          "missingInfo": {
            "type": [
              "string",
              "null"
            ],
            "maxLength": 100,
            "description": "NEED_MORE_INFO 일 때 무엇이 부족한지. 예: 용량(ml)"
          },
          "sourceUrl": {
            "type": [
              "string",
              "null"
            ],
            "maxLength": 255,
            "format": "uri"
          },
          "checkedAt": {
            "type": [
              "string",
              "null"
            ],
            "format": "date",
            "description": "sourceUrl 과 항상 함께 있거나 함께 null"
          }
        },
        "required": [
          "itemId",
          "name",
          "qty",
          "attributes",
          "verdict",
          "ruleId",
          "conditionNote",
          "reason",
          "missingInfo",
          "sourceUrl",
          "checkedAt"
        ],
        "additionalProperties": false
      }
    },
    "answer": {
      "type": [
        "string",
        "null"
      ],
      "minLength": 1,
      "maxLength": 600,
      "description": "챗봇 답변. 물품 목록 호출이면 null"
    },
    "followUpQuestion": {
      "type": [
        "string",
        "null"
      ],
      "minLength": 1,
      "maxLength": 200,
      "description": "NEED_MORE_INFO 가 있을 때 부족한 것 하나만 묻는다. 한 번에 하나"
    }
  },
  "required": [
    "results",
    "answer",
    "followUpQuestion"
  ],
  "additionalProperties": false
}
```

### 누가 채우나

| 누가 | 무엇을 |
| --- | --- |
| **모델 · 1차 구조화** | `results[].itemId (입력 echo)` · `results[].name` · `results[].qty` · `results[].attributes (명시된 값만. mAh 만 있으면 Wh = mAh × 3.7 / 1000)` |
| **규칙 엔진** | `results[].verdict` · `results[].ruleId` · `results[].conditionNote` · `results[].missingInfo` · `results[].sourceUrl` · `results[].checkedAt` |
| **모델 · 2차 설명** | `results[].reason` · `answer` · `followUpQuestion` |

**규칙 엔진 판정 규칙**

1. transport 와 name 으로 transport_rules 를 찾는다. 여러 행이면 attributes 로 조건을 판별한다
2. 판별에 필요한 attributes 가 null 이면 NEED_MORE_INFO + missingInfo. ruleId 는 가장 가까운 규정
3. 규정이 없으면 ASK_AIRLINE, sourceUrl·checkedAt 은 null
4. 규정이 충돌하면 더 엄격한 쪽을 고르고 reason 에 항공사 확인을 권한다

`sourceUrl` 과 `checkedAt` 은 **항상 함께** 있거나 함께 `null` 이다 — 명세 9절 *"규정 최신성"*.
서버가 둘을 같이 채우므로 어긋날 수 없다.

### 예시 1 — S-06 · 시드 체크리스트 — 06-api-spec customs[] 와 같은 판정

```json
{
  "transport": "FLIGHT",
  "airline": "대한항공",
  "question": null,
  "items": [
    {
      "itemId": 6,
      "name": "보조배터리",
      "qty": 1,
      "capacityMl": null,
      "batteryWh": null,
      "bladeCm": null
    },
    {
      "itemId": 8,
      "name": "화장품",
      "qty": 1,
      "capacityMl": null,
      "batteryWh": null,
      "bladeCm": null
    },
    {
      "itemId": null,
      "name": "가위",
      "qty": 1,
      "capacityMl": null,
      "batteryWh": null,
      "bladeCm": null
    }
  ]
}
```

```json
{
  "results": [
    {
      "itemId": 6,
      "name": "보조배터리",
      "qty": 1,
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "bladeCm": null
      },
      "verdict": "CABIN_OK",
      "ruleId": 1,
      "conditionNote": "100Wh 이하",
      "reason": "보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.",
      "missingInfo": "배터리 정격(Wh)",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    },
    {
      "itemId": 8,
      "name": "화장품",
      "qty": 1,
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "bladeCm": null
      },
      "verdict": "NEED_MORE_INFO",
      "ruleId": 4,
      "conditionNote": "용기당 100ml 이하, 총 1L 이하",
      "reason": "액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.",
      "missingInfo": "용량(ml)",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    },
    {
      "itemId": null,
      "name": "가위",
      "qty": 1,
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "bladeCm": null
      },
      "verdict": "NEED_MORE_INFO",
      "ruleId": 6,
      "conditionNote": "날 길이 6cm 초과",
      "reason": "날 길이 6cm를 넘는 가위는 기내 반입이 제한됩니다. 위탁수하물로 부치세요.",
      "missingInfo": "날 길이(cm)",
      "sourceUrl": "https://www.airportal.go.kr/library/security.do",
      "checkedAt": "2026-09-02"
    }
  ],
  "answer": null,
  "followUpQuestion": null
}
```

`itemId: null` 인 가위는 체크리스트에 없는 **추가 물품**(승인된 인식 결과)이다.
세 판정과 근거 문구는 시드 `transport_rules` 1 · 4 · 6번 행과 06의 `customs[]` · `extra[]` 와 같다.

### 예시 2 — S-09 챗봇 · 여행 없이 질문 (tripId null)

```json
{
  "transport": "FLIGHT",
  "airline": null,
  "question": "20000mAh 보조배터리 기내 되나요?",
  "items": []
}
```

```json
{
  "results": [
    {
      "itemId": null,
      "name": "보조배터리",
      "qty": 1,
      "attributes": {
        "capacityMl": null,
        "batteryWh": 74,
        "bladeCm": null
      },
      "verdict": "CABIN_OK",
      "ruleId": 1,
      "conditionNote": "100Wh 이하",
      "reason": "20000mAh 는 3.7V 기준 약 74Wh 로 100Wh 이하입니다. 보조배터리는 기내 반입만 가능하고 위탁수하물로는 부칠 수 없습니다.",
      "missingInfo": null,
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    }
  ],
  "answer": "네, 기내 반입은 됩니다. 20000mAh 는 3.7V 기준 약 74Wh 로 100Wh 이하라 별도 승인 없이 기내에 가지고 탈 수 있습니다. 다만 위탁수하물에는 넣을 수 없으니 꼭 손가방에 두세요. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
  "followUpQuestion": null
}
```

`20000mAh` 만 있고 Wh가 없다. 모델이 `3.7V` 기준으로 `74Wh` 를 계산해 `attributes.batteryWh`
에 넣고, 엔진이 규정 1번(100Wh 이하)으로 `CABIN_OK` 를 정하고, 모델이 환산 근거를
`reason` 에 적는다. 원칙 ② *"보이지 않는 속성은 추정하지 않는다"* 의 유일한 예외이고,
근거(공항 안내의 환산식)를 문장에 남기는 조건으로 허용했다.

### System Prompt

```text
너는 항공·교통 수하물 규정 확인을 돕는 보조자다. 반입 여부를 네가 판정하지 않는다 — 판정은 규칙 엔진이 공식 규정표로 한다. 네 일은 두 단계다.

A. 구조화 (1차 호출)
- 질문이나 물품 목록에서 물품 이름과 규정 판단에 필요한 속성을 뽑는다: capacityMl(액체 용량), batteryWh(배터리 정격), bladeCm(날 길이).
- 명시된 값만 쓴다. 없으면 null. 추정하지 않는다.
- 예외 하나: 배터리에 mAh 만 적혀 있으면 Wh = mAh × 3.7 / 1000 으로 환산해 batteryWh 에 넣고, 나중에 reason 에 환산 근거를 쓴다.
- 한 질문에 물품이 여럿이면 results 에 각각 넣는다. name 은 한국어 일반명사 100자 이내.

B. 설명 (2차 호출)
- 규칙 엔진 결과(verdict·conditionNote·description·missingInfo)를 받아, 물품마다 reason 을 한 문장(300자 이내)으로 쓴다. 규정 description 의 뜻을 바꾸지 않는다.
- 챗봇 호출(question 이 있음)이면 answer(600자 이내)를 쓴다. 정보가 부족해 NEED_MORE_INFO 인 물품이 있으면 followUpQuestion 에 부족한 것 하나만 묻는다. 한 번에 하나.
- 규정에 없는 말을 지어내지 않는다. "반드시 됩니다" 같은 확정 표현을 쓰지 않는다. answer 는 "최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다" 로 맺는다.

공통
- verdict · ruleId · conditionNote · missingInfo · sourceUrl · checkedAt 는 서버가 채운다. 내지 않는다.
- 출력은 아래 JSON Schema 를 만족하는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다.
```

### User Prompt 템플릿

두 번 부르므로 템플릿도 둘이다.

```text
[1차 · 구조화]
이동수단 {{transport}} · 항공사 {{airline | "미상"}}
질문: {{question | "(없음 — 아래 물품 목록으로)"}}
물품:
{{items as "- name ×qty (capacityMl / batteryWh / bladeCm)" | "- 없음"}}

물품과 속성을 구조화해 JSON 으로 답하라. results[] 의 itemId·name·qty·attributes 만 채운다.

[2차 · 설명]
규칙 엔진 결과:
{{server:engineResults as "- name: verdict / conditionNote / description / missingInfo"}}

각 물품의 reason 을, 챗봇 호출이면 answer 와 followUpQuestion 도 채워 JSON 으로 답하라.
```

---

## Mock이 돌려주는 것

`AI_PROVIDER=mock` 이면 `MockAiClient` 가 **위 예시 `output` 을 그대로** 돌려준다.
`jobType` 마다 하나씩, `backend/src/main/resources/mock/<jobType>.json`.

- `AI_MOCK_DELAY_MS` 만큼 기다렸다가 `COMPLETED` 로 바꾼다. 발표에서 로딩 화면을
  보여주려면 `1000`~`2000`.
- **서버가 채우는 필드도 Mock 이 채운다.** `confidenceLevel` · `verdict` · `weatherSource` ·
  규칙 엔진 필드. 실제 모델로 바꿔도 이 코드는 그대로 돈다.
- **Mock 응답에도 같은 스키마 검증을 건다.** Mock 이 스키마를 어기면 실제 모델도 어긴다.
  이 검증이 FE를 안 고쳐도 되는 근거다.
- Mock 도 `ai_jobs` 에 기록한다. `202` → 폴링 → 상태 전이가 실제와 똑같이 돈다 (06).
- 시드에는 완료된 `ai_jobs` 를 넣지 않는다. 화면에서 직접 만들어야 데모에서
  `POST → 202 → 폴링 → 렌더링` 이 눈에 보인다 ([`seed.sql`](../database/seed.sql) 주석).

---

## 검증

### 기계 검증 — 2026-09-03, 39항목 통과

스키마와 예시를 손으로 대조하지 않았다. `jsonschema`(Draft 2020-12)로 아래를 확인했다.

| 항목 | 무엇을 |
| --- | --- |
| 메타스키마 | 8개 스키마가 Draft 2020-12 로 유효한가 |
| 예시 ↔ 스키마 | 예시 5쌍의 `input` · `output` 이 자기 스키마를 통과하는가 |
| 06 ↔ 스키마 | 06의 `PACKING_LIST` 요청·완료 예시가 그대로 통과하는가 |
| enum ↔ `schema.sql` | 스키마의 enum 11곳이 `CHECK` 제약과 글자까지 같은가. 무게 `verdict` 는 06 정의와 |
| 무게 산수 | `bagEmptyG + Σ subtotalG == typicalG` · `subtotalG == typicalG × qty` · `min ≤ typical ≤ max` · `excludedCount == excluded.length` · 06 `inspection.weight` 와 수치 일치 |
| 투영 | 06 `inspection.weight` · `customs[]` 의 필드가 스키마 필드의 부분집합인가 |

> 검증 스크립트는 저장소 밖에 있다. BE 트랙이 Mock을 만들 때 같은 검증을 `AiClient`
> 뒤에 넣는다 — 그게 로드맵 2번이다.

### Playground 검증 — TBD

실행하지는 않았다. **채점의 "타당성"이 여기서 갈린다** — 돌려 보지 않은 프롬프트는 대개
스키마를 지키지 않는다. 발표 전에 한 번 돌린다. 10분이면 된다.

절차: OpenAI Playground 또는 Claude Console → System 칸에 System Prompt → User 칸에
템플릿을 예시 `input` 으로 채운 것 → **JSON 출력 모드** → 결과를 위 출력 Schema 에 넣어
검증 → 아래 표에 기록.

| `jobType` | 입력 | 기대 | 결과 |
| --- | --- | --- | --- |
| `BAG_CHECK` | 예시 사진 2장 + 템플릿 | 스키마 통과, `photoId` 가 1·2 만, 보이지 않는 속성은 `missingInfo` | TBD |
| `PACKING_LIST` | 예시 `input` | 스키마 통과, `alreadyPacked` 5개가 `items` 에 없음 | TBD |
| `WEIGHT_ESTIMATE` | 예시 `input` + 시드 범위 | 스키마 통과, `typicalG = 5410`, `contributions` 5개 내림차순 | TBD |
| `RULE_CHECK` 1차 | 예시 2 `question` | `results[0]` 가 보조배터리 · `batteryWh = 74` · 나머지 `null` | TBD |
| `RULE_CHECK` 2차 | 엔진 결과 | `answer` 가 항공사 확인 문장으로 끝남, `followUpQuestion = null` | TBD |

---

## 모델 파라미터

**AI-Ready 원칙 4 (Security & Config Isolation).** 아래 값은 전부 코드가 아니라
[`backend/.env`](../backend/.env.example)에서 읽는다. 모델을 바꿀 때 코드를 고치지 않기 위해서다.

| 항목 | 환경 변수 | 지금 값 | 비고 |
| --- | --- | --- | --- |
| 제공자 | `AI_PROVIDER` | `mock` | 나중에 `openai` / `anthropic` |
| 모델명 | `AI_MODEL` | `mock` | |
| API 키 | `AI_API_KEY` | (비움) | **절대 커밋하지 않는다** |
| 응답 다양성 | `AI_TEMPERATURE` | `0.2` | 구조화된 JSON 출력이므로 낮게 |
| 최대 토큰 | `AI_MAX_TOKENS` | `2048` | `BAG_CHECK` 50개 · `PACKING_LIST` 40개가 들어가는 크기 |
| Mock 응답 지연 | `AI_MOCK_DELAY_MS` | `0` | 발표에서 로딩 화면을 보여주려면 `1000`~`2000` |

> `AI_PROVIDER=mock`이면 Mock 응답을, 다른 값이면 실제 API를 호출하도록
> 백엔드를 분기해 두면 **환경 변수 한 줄로 AI를 켜고 끌 수 있다.**
> 발표에서 보여주기 좋은 지점이다.

## 향후 로드맵

> 발표 5번 섹션: 프로젝트 한계점 및 추후 AI 실제 결합 시 로드맵

| 단계 | 할 일 | 예상 난이도 | 왜 |
| --- | --- | --- | --- |
| 1 | `AI_PROVIDER` 분기와 `RealAiClient` — 텍스트 3종 먼저 | 낮음 | 인터페이스와 스키마가 있다. 프롬프트를 붙이고 JSON 모드로 부르면 된다 |
| 2 | 응답 JSON Schema 검증 + 실패 시 재시도 1회 → `FAILED` | 낮음 | 위 스키마를 그대로 쓴다. Mock 에도 같은 검증을 건다 |
| 3 | `BAG_CHECK` 비전 입력 — 사진을 모델에 넘기는 파이프라인 | 중간 | 이미지 크기·장수 제한, 실패 사진 처리(`failedPhotoIds`) |
| 4 | 폴링을 큐(메시지 브로커)로 교체 | 중간 | 봉투는 그대로. `pollAfterMs` 만 늘린다. FE 변경 없음 |
| 5 | `transport_rules` 갱신 잡 — 출처 재확인 날짜 자동 갱신 | 중간 | `checkedAt` 이 오래되면 판정 근거가 약해진다 |
| 6 | 비용·토큰 사용량 모니터링 | 낮음 | `ai_jobs` 에 토큰 수 컬럼 추가 |

## 알려진 한계

솔직하게 적는다. 발표 5번 섹션(회고)에서 그대로 쓴다.

- **Playground 검증을 하지 않았다.** 프롬프트가 스키마를 지키는지는 돌려 봐야 안다. TBD.
- **체크리스트에 없는 승인 물품(추가 물품)은 무게 계산에 들어가지 않는다.** 시드의 가위가
  그렇다. `WEIGHT_ESTIMATE` 는 `check_status = PREPARED` 인 항목만 받는다. 승인 시 체크리스트
  항목으로 등록하면 포함된다 — 그 흐름을 화면에 넣을지는 TBD.
- **사진 사이의 같은 물품 병합 규칙이 없다.** `BAG_CHECK` 는 사진마다 따로 내고 "합치는 것은
  서버가 한다" 고 했지만, 이름이 같으면 합산인지 별개인지 정하지 않았다. TBD.
- **날씨가 `input_payload` 에 남지 않는다.** 서버가 프롬프트에 넣고 `weatherSource` 만 남긴다.
  같은 작업을 나중에 재현하면 날씨가 달라질 수 있다.
- **`RULE_CHECK` 는 모델을 두 번 부른다.** 지연이 두 배다. Mock 은 한 번이라 데모에서는 안 보인다.
- **챗봇의 사진 첨부**(`S-09` 주요 요소)는 `BAG_CHECK` → `RULE_CHECK` 두 작업이다. 화면에만
  있고 흐름은 설계하지 않았다. 대화 이력도 저장하지 않는다 — 질문 하나에 답 하나.
- **`confidenceLevel` 경계값 0.80 / 0.50 은 근거 없는 초기값이다.** 실제 모델을 붙인 뒤
  승인율을 보고 조정한다. 설정에 두는 이유다.
- **mAh → Wh 환산은 3.7V 를 가정한다.** 리튬이온 공칭 전압이지만 제품마다 다르다. `reason` 에
  가정을 적어 사용자가 라벨로 확인하게 한다.
