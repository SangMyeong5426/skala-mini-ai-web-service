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

> **2026-09-03 개정 반영:** [Notion 기능 정의 개정안](https://app.notion.com/p/3d0c2ab24ce881d9b06cc065c47b1eb7)의
> 사진 우선·별도 추천 원칙에 최신 사용자 결정인 사진 인식 즉시 자동 등록을 적용했다. 아래 스키마는 개정 계약이며
> 실제 Mock·화면 반영 완료 여부는 [문서 지도](README.md#개정안-반영-상태)에서 별도로 관리한다.

## AI 확장 지점

네 곳이다. **엔드포인트는 하나**고 `jobType` 값만 다르다
([ADR 0003](adr/0003-ai-job-endpoint.md)).

| ID | `jobType` | 하는 일 | Use-Case | 화면 | 지금 | 나중 |
| --- | --- | --- | --- | --- | --- | --- |
| AI-01 | `BAG_CHECK` | 사진에서 물품 후보·수량·신뢰도를 뽑는다 | UC-04 | `S-04` | Mock 고정 인식 결과 | 비전 모델 |
| AI-02 | `PACKING_LIST` | 현재 내 목록에 없는 **추가 후보와 이유만** 추천한다 | UC-05 | `S-05` | Mock 고정 목록 | LLM |
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
  `보조배터리 · 20000mAh · Wh 미상`으로 구조화하는 일은 표현이 매번 다르다. **판정은 AI가 하지 않는다.**

반대로 **AI를 두지 않은 곳**도 같은 기준으로 정했다. 반입 판정(`transport_rules` 를 보는
규칙 엔진), 사진 자동 등록·추천 채택·준비 상태 비교, 무게 합산과 `verdict`(산식). 규칙으로
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

### 로그인과 AI 작업의 경계

**AI 작업 4종 모두 로그인 필수**다. 인증은 AI가 아닌 BE의 세션·소유권 검사로 처리한다.
기존 AI 입력·출력 JSON Schema 8개에는 인증 필드를 추가하지 않는다.

- POST 접수 전에 세션을 확인하고 `ai_jobs.user_id`를 서버에서 채운다. 임의 userId는 받지 않는다.
- tripId·photoIds·itemIds·추천 jobId가 본인 자료이며 서로 같은 여행인지 확인한다.
- `RULE_CHECK` 챗봇은 여행 없이 가능하지만 로그인은 필요하다. `trip_id=null`이어도 사용자 FK는 필수다.
- GET 상태 조회·재접속도 작업 소유권을 확인한다. 다른 사용자의 작업은 `404`, 미인증은 `401`이다.
- 로그인·가입의 아이디·닉네임·이메일·비밀번호·해시·세션 쿠키·CSRF 토큰을 AI 입력·프롬프트에 넣지 않는다.
- 세션이 만료되면 FE는 폴링을 멈추고 S-00으로 간다. 서버 작업은 원래 소유자에게 남으며
  재로그인 후 조회한다. 만료가 기존 작업을 `FAILED`로 만들지는 않는다.

인증은 [06](06-api-spec.md#회원가입로그인-계약-uc-01), 아래는 로그인한 사용자의 업무 입력이다.

`POST /api/ai-jobs` 의 **봉투**(`jobId` · `status` · `pollAfterMs` · 폴링 규약)는
[`06-api-spec.md`](06-api-spec.md)가 정한다. 이 문서는 **알맹이** — `input` 과 `output` 의
내부 구조 — 만 다룬다.

알맹이에는 세 가지 약속이 있다. 4종 전부 이 약속대로 설계했다.

| | 무엇 | 어디에 남나 |
| --- | --- | --- |
| **`input`** | FE가 **그 시점 화면에 이미 갖고 있는 사실**만 보낸다. FE가 모르는 값을 요구하지 않는다 | 검증 후 `ai_jobs.input_payload`에 저장. PACKING_LIST의 `alreadyPacked`는 서버의 현재 PREPARED 목록으로 보정한 값을 저장 |
| **서버 보강** | 현재 내 목록과 마스터 데이터(날씨, `item_weights`, `transport_rules`)를 읽어 프롬프트의 `{{server:…}}`에 넣는다 | 추천 output에 `weatherSource`·`weatherAsOf`를 남긴다. 날씨 원문 보관은 TBD |
| **`output`** | `ai_jobs.output_payload` 에 저장돼 FE가 받는 **최종 형태**. 모델이 만드는 필드와 **서버가 규칙으로 채우는 필드**를 "누가 채우나" 표로 구분한다 | 검증은 합쳐진 최종 객체에 건다 |

### 작업이 끝나면 서버가 쓰는 곳

`COMPLETED` 시점에 서버가 어느 테이블에 무엇을 쓰고, 도메인 API 가 어디서 읽는지.
Mock 구현자가 이것 없이는 `S-04`·`S-05` 를 잇지 못한다. **Mock 도 똑같이 쓴다.**

| `jobType` | `COMPLETED` 때 서버가 하는 일 · 도메인 API 가 읽는 곳 |
| --- | --- |
| `BAG_CHECK` | 성공한 인식 물품을 detected_objects에 저장하고 내 목록 생성·연결·PREPARED 등록까지 한 트랜잭션으로 완료한다. 그 후에만 COMPLETED를 반환한다. 승인 요청은 없다. 동일 작업 재처리·폴링은 중복 등록하지 않으며 사후 수정·삭제를 보존한다(06). |
| `PACKING_LIST` | 후보를 output_payload에만 저장한다. 현재 내 목록과 중복되는 후보를 제거하고 `source`·`acceptedItemId=null`·날씨 출처와 시점을 서버가 채운다. **checklist_items에는 쓰지 않는다.** S-05는 GET /api/ai-jobs/{jobId}로 후보를 읽고, 사용자의 채택 POST 때만 내 목록에 미완료로 등록한다(06). |
| `WEIGHT_ESTIMATE` | output_payload에만 저장한다. inspection.weight는 **현재 입력과 일치하는** 가장 최근 완료 결과를 투영한다(excluded 제외, contributions 위 3개). 준비 상태·이름·수량·가방 정보·제외 목록이 달라지면 null로 반환하고 재계산한다. S-07도 현재 상태에 맞는 작업 ID의 결과만 표시한다(06). |
| `RULE_CHECK` | results[] 중 itemId 가 있는 것을 item_rule_checks 에 (checklist_item_id, rule_id, verdict, missing_info) 로 넣는다 — 같은 (item, rule) 이 있으면 덮어쓴다. ruleId 가 null 인 결과(ASK_AIRLINE)는 output_payload 에만 남는다. inspection 의 customs 는 item_rule_checks 를 **항목별로 모아 가장 엄격한 verdict 하나**를 보여준다 (NEED_MORE_INFO 가 있으면 그것 — 시드의 보조배터리가 그 예다). 챗봇(tripId null)은 아무 테이블에도 쓰지 않는다. |

BAG_CHECK 완료 처리가 `detected_objects`·`item_detections`와 내 목록 완료 등록을 한 번에 처리한다.
이 단계는 `PACKING_LIST` 실행과 무관하다. 새 항목은 `PHOTO / PREPARED`, 추천 채택의 새
항목은 `AI` 또는 `RULE / UNCHECKED`다. 추천 채택이 기존 항목과 연결되면 출처와 사용자가
정한 값을 보존한다. 등록 후 선택적 이름·수량 수정은 06의 사후 수정 규약을 따른다. 후보의
`acceptedItemId`는 서버가 채택·삭제 시에만 갱신하며 원래 후보 내용은 유지한다.

### 템플릿 표기

- {{x}} = input 필드
- {{server:x}} = 서버 보강
- {{x | "대체"}} = x 가 null 이면 단위까지 통째로 대체 문자열
- {{list as "포맷" | "대체"}} = 항목마다 포맷 한 줄(포맷 안 식별자는 항목 필드명, 중첩 {{server:…}} 허용), list 가 비면 대체 한 줄
- {{list.length}} = 개수. 서버 보강 수치의 단위는 템플릿에 적는다. rainChance 는 0~100 정수 %.

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
- **서버가 채우는 필드도 스키마에 있다.** 모델에게는 그 필드를 뺀 **파생 스키마**를 준다 —
  코드가 output 스키마에서 서버 필드(각 jobType 의 `serverFields`)를 `required` 에서 빼서 만든다.
  모델 응답은 파생 스키마로, 서버가 채운 최종 객체는 원본으로 검증한다. 모델이 서버 필드를
  내면 버리고 서버 값으로 덮어쓴다. 손으로 스키마를 두 벌 관리하지 않는다.
- **빈 문자열은 없다.** 값이 없으면 `null`. FE 입력창이 비어도 `null` 로 보낸다. `minLength: 1` 이 막는다.

---

## AI-01 `BAG_CHECK` — 사진 속 물품 인식

**UC-04 · `S-04`.** 사진에서 물품을 인식하고 **사용자 승인 없이 내 체크리스트에 챙김 완료로 자동 등록한다.** LOW·속성 부족은 등록 차단 조건이 아니다.
`output.detections[]` 한 항목이 `detected_objects` 한 행이다 — `missingInfo` · `labelText` 도
`missing_info` · `label_text` 컬럼에 그대로 들어간다. `S-04` 「확인 필요」 묶음은 `missingInfo ≠ null` 또는 `LOW` 다.

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
      "maxItems": 100,
      "description": "detected_objects 한 행과 1:1 (missingInfo · labelText 는 missing_info · label_text 컬럼). 사진이 다르면 항목도 다르다. output에 detectionId는 없다. 서버는 인식 행과 내 목록·연결 저장을 마친 후 COMPLETED로 반환한다. S-04는 도메인 GET으로 자동 등록 결과를 조회하며 PATCH는 선택적 사후 수정에만 쓴다.",
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
            "maxLength": 100,
            "pattern": "\\S"
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
            "description": "서버가 confidence 로 채운다: ≥0.80 HIGH · ≥0.50 MEDIUM · 그 외 LOW. 모델이 낸 값이 있어도 덮어쓴다"
          },
          "missingInfo": {
            "type": [
              "string",
              "null"
            ],
            "minLength": 1,
            "maxLength": 100,
            "description": "보이지 않아 못 정한 속성. 예: 용량(ml) · 배터리 정격(Wh) · 날 길이(cm). 수치 확정은 사용자가 S-04·S-08 에서 한다"
          },
          "labelText": {
            "type": [
              "string",
              "null"
            ],
            "minLength": 1,
            "maxLength": 200,
            "description": "라벨·포장에서 읽힌 글자 원문(OCR). 브랜드·용량 표기가 여기 온다. 서버는 파싱하지 않는다"
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
| **서버 (규칙)** | `detections[].confidenceLevel (경계값 0.80 / 0.50 — 모델이 낸 값은 덮어쓴다)` · `failedPhotoIds (모델 호출이 실패한 사진 — 모델이 낸 값은 버린다)` |

**서버가 저장 전에 검증한다** — 하나라도 어긋나면 `FAILED` 로 돌리고 기본 문구를 보여준다.

- input.photoIds ⊆ 그 tripId 의 trip_photos.id — 아니면 400
- detections[].photoId ∈ input.photoIds — 아닌 항목은 버린다 (작업 전체를 FAILED 로 만들지 않는다)
- failedPhotoIds ⊆ input.photoIds, 그리고 detections[].photoId ∉ failedPhotoIds
- 같은 photoId 에 같은 name 이 두 번이면 qty 를 합쳐 하나로

`confidenceLevel` 을 모델이 아니라 서버가 채우는 이유는 [`05-erd.md`](05-erd.md)의
*"confidence_level 을 따로 두는 근거"* 와 같다 — 경계값이 바뀌어도 사용자가 그때 보고
확인한 당시의 표시는 그대로여야 한다. 경계값은 코드가 아니라 설정에 둔다.

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
1. 사진에 보이는 것만 적는다. 보이지 않는 속성 — 용량(ml)·배터리 정격(Wh)·날 길이(cm) — 은 추정하지 않는다. 그 속성이 반입 판정에 필요한 물품(액체·배터리·날붙이)이면 missingInfo 에 "용량(ml)" 처럼 무엇이 필요한지 적는다. 라벨이 읽히면 labelText 에 글자를 원문 그대로 옮긴다 — 그래도 missingInfo 는 적는다. 수치 확정은 사용자가 한다.
2. 같은 종류가 한 사진에 여러 개 보이면 한 항목으로 묶고 qty 로 센다(1~99 정수, 셀 수 없으면 1). 사진이 다르면 항목도 다르다 — photoId 마다 따로 낸다. 사진 한 장에 10개, 전체 100개를 넘기지 않는다. 넘으면 confidence 높은 순으로 자른다.
3. 무엇인지 확신이 없으면 이름을 지어내지 말고 보이는 대로 적는다(예: "검정 파우치"). confidence 를 낮춘다.
4. name 은 한국어 일반명사로 1~100자. 공백만인 이름은 안 된다. 브랜드명은 name 이 아니라 labelText 에 둔다.
5. confidence 는 0~1 사이 소수 셋째 자리까지의 숫자(따옴표 없이).
6. 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 빈 문자열은 쓰지 않는다. photoId 는 입력에 있는 값만 쓴다. confidenceLevel 과 failedPhotoIds 는 서버가 계산해 덮어쓰므로 비워 두어도 된다.
```

### User Prompt 템플릿

```text
여행: {{server:trip.destination}} {{server:trip.startDate}}~{{server:trip.endDate}} · 이동수단 {{server:trip.transport}}
사진 {{photoIds.length}}장. photoId 와 가방 종류:
{{server:photos as "- photoId={{id}} ({{bagKind | \"종류 미상\"}})"}}

[사진 {{photoIds.length}}장 첨부]

위 사진에서 물품을 찾아 JSON 으로 답하라.
```

---

## AI-02 `PACKING_LIST` — 별도 추가 준비물 추천

**UC-05 · `S-05`.** 실제 완료 물품(`alreadyPacked`)과 서버가 읽은 현재 내 목록을 고려해
추가 후보만 반환한다. 사진 자동 등록 물품은 이미 내 목록에 등록돼 있어야 한다. 추천 생성
성공·실패와 무관하게 내 목록은 유지한다.

`COMPLETED` 시 후보는 `ai_jobs.output_payload`에만 저장한다. S-05는 위쪽 내 목록과 아래쪽
추천 후보를 별도로 읽는다. 후보를 선택·승인하면 기존 `POST /trips/{tripId}/items`로
`recommendation: {jobId, candidateIndex}`를 보내고 서버가 미완료로 등록한다(06).
동일 후보의 재승인은 서버 필드 `acceptedItemId`로 판별한다. 후보 배열은 완료 후 순서를
바꾸지 않으며, 사용자의 이름·수량 수정은 내 항목에 저장한다.

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
      "maxLength": 100,
      "pattern": "\\S"
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
      "minLength": 1,
      "maxLength": 500
    },
    "alreadyPacked": {
      "type": "array",
      "maxItems": 100,
      "description": "화면의 PREPARED 내 목록 항목. 없으면 빈 배열. 서버가 현재 PREPARED 목록으로 덮어쓴 뒤 저장·실행하며 요청값 차이만으로 실패하지 않음",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100,
            "pattern": "\\S"
          },
          "category": {
            "oneOf": [
              {
                "enum": [
                  "DOCUMENT",
                  "CLOTHING",
                  "ELECTRONIC",
                  "TOILETRY",
                  "MEDICINE",
                  "ETC"
                ]
              },
              {
                "type": "null"
              }
            ],
            "description": "내 목록 항목의 category. 신규 사진 항목의 미분류 기본값은 ETC"
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

`alreadyPacked`는 기존 입력 스키마를 유지해 **빈 배열이어도 보낸다.** 추천의 최종 기준은
서버 값이며, 보정된 동일 입력을 Mock과 실제 LLM에 사용한다. 요청값을 신뢰해 준비 상태를
바꾸거나 값 차이만으로 실패시키지 않는다.

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
      "description": "별도 추천 후보. 완료 시 내 목록에 자동 저장하지 않는다. 완료 후 배열 위치를 고정하고 사용자 채택 시 서버가 acceptedItemId만 갱신한다",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100,
            "pattern": "\\S"
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
          },
          "reason": {
            "type": "string",
            "minLength": 1,
            "maxLength": 200,
            "pattern": "\\S"
          },
          "source": {
            "enum": [
              "AI",
              "RULE"
            ],
            "description": "서버 필드. 모델 후보는 AI, 고정 필수 규칙 후보는 RULE"
          },
          "acceptedItemId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1,
            "description": "서버 필드. 최초 null, 사용자 채택 시 같은 여행의 내 목록 ID. 항목 삭제 시 null로 해제"
          }
        },
        "required": [
          "name",
          "category",
          "qty",
          "priority",
          "reason",
          "source",
          "acceptedItemId"
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
    },
    "weatherAsOf": {
      "type": "string",
      "format": "date",
      "description": "서버가 실제 사용한 예보·계절 자료의 기준일을 넣는다"
    }
  },
  "required": [
    "items",
    "tips",
    "weatherSource",
    "weatherAsOf"
  ],
  "additionalProperties": false
}
```

### 누가 채우나

| 누가 | 무엇을 |
| --- | --- |
| **모델** | 후보의 `name/category/qty/priority/reason` · `tips[]` |
| **서버 (규칙)** | 후보의 `source/acceptedItemId`, `weatherSource/weatherAsOf`. RULE 후보는 서버가 같은 스키마로 보강하고 중복을 제외한다 |

**작업 접수 전:** 입력 형식·여행 소유권을 검증한 뒤 서버가 현재 내 목록을 읽는다.
그중 PREPARED 물품의 이름·분류·수량으로 `alreadyPacked`를 덮어써 `input_payload`에 저장한다.
요청값이 오래됐거나 `[]`여도 값 차이로 `409`를 반환하지 않고 `202`로 접수한다.
형식 위반은 기존대로 `400`이다. 전체 내 목록은 미완료까지 중복 제외에 사용한다.

**작업 실행 후 저장 전:** 아래 규칙을 적용하고, 출력 검증 실패 시 `FAILED`로 처리한다.

- 생성 완료 시 items[].name이 alreadyPacked나 현재 checklist_items에 이미 있으면 제외한다. 미완료·직접 추가 항목도 포함한다
- 후보별 reason은 비어 있지 않아야 한다. source·acceptedItemId·날씨 메타데이터는 모델값을 버리고 서버가 채운다
- 후보 생성은 내 목록·완료율을 변경하지 않는다. 채택 요청 때도 중복·소유 여행을 다시 검증한다(06)
- 스키마 불통과(tips 120자 초과 등)면 재시도 1회, 그래도 실패면 FAILED — 06 의 FAILED 예시 문구로

날씨는 FE가 모른다. 백엔드가 Open-Meteo에서 받아 프롬프트에 넣고, **어느 날씨였는지**를
`weatherSource`와 데이터 기준일 `weatherAsOf`로 남긴다. `SEASONAL`이면 `S-05`가
*"실시간 예보가 아닌 계절 평균 기준입니다"*와 날짜를 표시한다. 조회 실패 시에도 실제로
사용한 계절 자료의 기준일을 표시하며, 실행일로 임의 대체하지 않는다.

### 예시 — 도쿄 3박4일 · 사진 자동 등록 후 (출발 28일 전 → 계절 평균)

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
    },
    {
      "name": "가위",
      "category": "ETC",
      "qty": 1
    },
    {
      "name": "화장품 용기",
      "category": "TOILETRY",
      "qty": 1
    },
    {
      "name": "검정 파우치",
      "category": "ETC",
      "qty": 1
    }
  ]
}
```

```json
{
  "items": [
    {
      "name": "변환 플러그",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "REQUIRED",
      "reason": "여행지에서 충전기를 연결할 어댑터를 확인하세요.",
      "source": "AI",
      "acceptedItemId": null
    },
    {
      "name": "상비약",
      "category": "MEDICINE",
      "qty": 1,
      "priority": "RECOMMENDED",
      "reason": "평소 사용하는 약이 있다면 여행 기간에 맞게 준비하세요.",
      "source": "AI",
      "acceptedItemId": null
    },
    {
      "name": "화장품",
      "category": "TOILETRY",
      "qty": 1,
      "priority": "RECOMMENDED",
      "reason": "숙소 제공 여부에 따라 개인 세면용품을 검토하세요.",
      "source": "AI",
      "acceptedItemId": null
    },
    {
      "name": "우산",
      "category": "ETC",
      "qty": 1,
      "priority": "RECOMMENDED",
      "reason": "여행 중 강수에 대비할 휴대용 우산을 검토하세요.",
      "source": "AI",
      "acceptedItemId": null
    },
    {
      "name": "여권",
      "category": "DOCUMENT",
      "qty": 1,
      "priority": "REQUIRED",
      "reason": "해외 여행 출국 전 여권 준비 여부를 확인하세요.",
      "source": "RULE",
      "acceptedItemId": null
    }
  ],
  "tips": [
    "일본 콘센트는 A타입, 100V입니다.",
    "10월 초 도쿄 계절 평균은 낮 24도, 아침 16도입니다. 실시간 예보가 아닙니다.",
    "디즈니랜드는 하루 2만 보 이상 걷습니다."
  ],
  "weatherSource": "SEASONAL",
  "weatherAsOf": "2026-09-03"
}
```

예시는 사진 자동 등록 물품을 내 목록에 등록한 뒤의 추천 후보다. `acceptedItemId`가 `null`인
후보는 내 목록에 없으며 완료율에 영향을 주지 않는다. 기존 SQL 시드의 AI 행은 자동 채택
흐름의 이전 데이터이므로 이 예시의 실행 결과로 보지 않는다.

고정 필수 규칙도 추천 후보로 보강하며 `source=RULE`로 표시한다. `REQUIRED`는 추천의
중요도이지 자동 채택 권한이 아니다. 필수 항목 자동 등록 예외와 동일 물품의 부족 수량
추천은 **TBD**이며 이번 데모에서는 적용하지 않는다.
미채택 필수 후보는 06의 조회 계산값 `unacceptedRequiredCount`에 포함해 S-05·S-06에서
경고한다. 이는 AI 출력 필드가 아니며 모델이 계산하지 않는다. 예시의 여권은 내 목록에
없을 때 경고 대상이고, 채택하면 미완료 항목으로 관리한다. 완료율 100%가 필수품 완비를 뜻하지 않는다.

### System Prompt

```text
너는 여행 준비물을 추천하는 보조자다. 사용자가 이미 챙긴 것은 다시 추천하지 않는다.

규칙
1. alreadyPacked와 현재 내 목록에 있는 물품은 이름이 같거나 명백히 같은 종류면 items에 넣지 않는다("상의"가 있으면 "티셔츠"를 또 내지 않는다). 아직 미완료인 채택 항목도 제외한다. 같은 물품의 부족 수량은 추천하지 않는다.
2. 여행지·기간·목적·이동수단·날씨에 맞는 추가 후보만 낸다. 최대 40개. 후보별 reason에 이 여행에서 검토할 이유를 1~200자 한국어로 쓴다. 사용자를 대신해 후보를 채택하거나 챙김 완료라고 하지 않는다.
3. priority: 준비가 특히 중요한 것은 REQUIRED, 나머지는 RECOMMENDED. 고정 필수 규칙 항목은 서버가 RULE 후보로 보강하므로 중복으로 내지 않는다. 어떤 후보든 내 목록 추가는 사용자가 선택한다.
4. category 는 DOCUMENT · CLOTHING · ELECTRONIC · TOILETRY · MEDICINE · ETC 중 하나만.
5. qty 는 기간에 맞춘 1~99 정수. 정하기 어려우면 1.
6. tips 는 최대 5개, 각 1~120자. 챙길 물건은 tips 가 아니라 items 에 넣는다 — tips 에는 날씨·콘센트·현지 사정 같은 사실만 쓴다. 날씨 근거가 있으면 수치를 그대로 인용한다(예: "낮 24도").
7. 액체·배터리 같은 반입 규정 판정은 하지 않는다. 그건 다른 단계가 한다.
8. 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 빈 문자열은 쓰지 않는다. source·acceptedItemId·weatherSource·weatherAsOf는 서버 필드다. 모델용 파생 스키마에서는 제외한다.
```

### User Prompt 템플릿

```text
목적지 {{destination}} ({{server:trip.countryCode | "국가 미상"}}) · {{startDate}}~{{endDate}} ({{server:nights}}박) · 목적 {{purpose}} · 이동수단 {{transport}}
메모: {{note | "없음"}}
날씨 ({{server:weather.source}}, {{server:weather.asOf}} 기준): {{server:weather.summary}}, {{server:weather.minC}}~{{server:weather.maxC}}°C, 강수확률 {{server:weather.rainChance}}%

이미 챙긴 것 (다시 추천하지 않는다):
{{alreadyPacked as "- {{name}} ×{{qty}} ({{category | \"분류 미상\"}})" | "- 없음"}}

현재 내 목록 (미완료라도 다시 추천하지 않는다):
{{server:currentItems as "- {{name}} ×{{qty}} ({{checkStatus}})" | "- 없음"}}

추가 후보와 각 추천 이유를 JSON으로 답하라. 목록에 자동 등록하지 않는다.
```

---

## AI-03 `WEIGHT_ESTIMATE` — 예상 무게 범위

**UC-10 · `S-06` ② · `S-07`.** 내 목록에서 실제 챙김 완료(`PREPARED`)인 물품만
**범위**로 추정한다. 사진 자동 등록과 직접 완료 확인을 모두 포함한다. 추천 채택만으로는 포함하지 않는다.

`GET /api/trips/{tripId}/inspection` 의 `weight` 는 이 출력의 **투영**이다 —
`excluded[]` 를 빼고 `contributions[]` 를 위 3개로 자른 것. `S-06` 은 개수만, `S-07` 은
이 작업 결과 전체를 `GET /api/ai-jobs/{jobId}` 로 받아 이유까지 그린다.

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
      "description": "계산에 넣는 것 — check_status = PREPARED 인 체크리스트 항목. category 는 무게에 필요 없어 보내지 않는다",
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
            "maxLength": 100,
            "pattern": "\\S"
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
          "qty"
        ],
        "additionalProperties": false
      }
    },
    "excluded": {
      "type": "array",
      "maxItems": 100,
      "description": "내 목록의 실제 미완료 항목은 UNCHECKED. 사진 자동 등록 물품은 items에 포함한다. 미채택 추천은 포함하지 않는다. NOT_IN_PHOTO는 기존 미완료 상태의 제외 사유다",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100,
            "pattern": "\\S"
          },
          "reason": {
            "enum": [
              "NOT_IN_PHOTO",
              "UNCHECKED"
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

계산에 넣는 것은 `check_status=PREPARED`인 내 목록이다. 사진에서 못 찾았더라도 실제
완료 확인이 있으면 포함한다. 내 목록의 미완료 항목만 `excluded`에 `UNCHECKED`로 보낸다.
사진 인식 물품은 사전 승인 없이 items에 포함하며 무게를 모르면 출력에서 NO_WEIGHT_INFO로 제외한다. 미채택 추천은 items·excluded
어느 쪽에도 넣지 않는다. 구 데이터의 NOT_IN_PHOTO는 준비 미완료일 때만 제외 사유로 쓴다.
이미 확인된 미완료 항목(UNCHECKED)은 현재 가방에 없으므로 무게 불확실성으로 세지 않는다.
`category` 는 무게에 필요 없어 보내지 않는다 — `S-06` 이 그 시점에 갖고 있지 않은 값이기도 하다.

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
    "bagEmptyG": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": 0,
      "description": "서버가 input.bagEmptyG 를 그대로 옮긴다. S-07 이 이 응답 하나로 그린다"
    },
    "verdict": {
      "enum": [
        "ROOM",
        "NEAR",
        "OVER_RISK",
        "UNKNOWN"
      ],
      "description": "서버가 산식으로 채운다. 순서대로 — ① limitG 가 null 이면 UNKNOWN ② maxG > limitG 면 OVER_RISK ③ bagEmptyG 가 null 이거나 confidence 가 LOW 면 UNKNOWN ④ typicalG ≥ 0.8 × limitG 면 NEAR ⑤ 그 외 ROOM"
    },
    "confidence": {
      "enum": [
        "HIGH",
        "MEDIUM",
        "LOW"
      ],
      "description": "서버가 불확실한 제외 항목(reason != UNCHECKED) 수로 채운다. contributions가 비었거나 불확실한 제외가 계산 항목보다 많으면 LOW, 하나라도 있으면 MEDIUM, 없으면 HIGH"
    },
    "confidenceReason": {
      "type": "string",
      "minLength": 1,
      "maxLength": 200,
      "description": "모델이 쓴다. 이유를 개수로. 예: 챙김 미완료 1개, 무게 정보 없음 2개"
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
            "maxLength": 100,
            "pattern": "\\S"
          },
          "reason": {
            "enum": [
              "NOT_IN_PHOTO",
              "UNCHECKED",
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
      "description": "계산에 넣은 물품마다 하나. 모델이 항목별 범위를, 서버가 subtotalG 와 정렬(subtotalG 내림차순)을. S-06 은 위 3개만, S-07 은 전부",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100,
            "pattern": "\\S"
          },
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
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "subtotalG": {
            "type": "integer",
            "minimum": 0,
            "description": "= typicalG × qty. 서버가 채운다"
          }
        },
        "required": [
          "name",
          "minG",
          "typicalG",
          "maxG",
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
    "bagEmptyG",
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
| **모델** | `contributions[].name · minG · typicalG · maxG · qty (계산에 넣은 물품마다 하나)` · `excluded[] 뒤에 덧붙이는 NO_WEIGHT_INFO 항목` · `confidenceReason` |
| **서버 (규칙)** | `limitG · bagEmptyG (input 그대로)` · `contributions[].subtotalG = typicalG × qty, subtotalG 내림차순 정렬` · `minG / typicalG / maxG = bagEmptyG(null 이면 0) + Σ(항목 min/typical/max × qty)` · `excludedCount = excluded.length` · `confidence (불확실한 제외 항목 수 규칙 — UNCHECKED 제외, 모델값은 덮어쓴다)` · `verdict (산식 — 모델이 낸 값은 덮어쓴다)` |

**작업 접수 전:** input.items가 해당 여행의 현재 PREPARED 항목 전체와 ID·이름·수량까지
일치하는지 검증한다. 가방 정보·제외 목록도 현재 상태와 대조한다. 불일치하면 `409`로 재조회를 요청한다. 사진 인식 물품 등록은 BAG_CHECK 완료 처리에서 끝낸다. 사용자가 이후 삭제한 항목을 무게 작업이 재등록하거나 누락 오류로 되돌리지 않는다.

**작업 실행 후 저장 전:** 접수한 입력을 기준으로 아래를 검증한다. 실행 중 목록이 바뀌어도
과거 입력의 결과로 보관할 수 있지만 현재 입력과 다르면 inspection과 S-07에 표시하지 않는다.

- 항목마다 minG ≤ typicalG ≤ maxG — 아니면 그 항목을 NO_WEIGHT_INFO 로 excluded 로 옮긴다
- contributions[].name ⊆ input.items[].name, 같은 이름 두 번 금지, qty == input 의 qty — 어긋나면 FAILED
- input.items 중 contributions 에도 excluded 에도 없는 물품은 NO_WEIGHT_INFO 로 excluded 에 넣는다 (모델이 빠뜨린 것)
- input.excluded 는 output.excluded 의 앞부분과 그대로 같아야 한다 — 아니면 FAILED

**산술은 전부 서버가 한다.** 모델은 물품별 범위와 제외 사유만 내고, 합계·`subtotalG`·정렬·`excludedCount`·
`confidence`·`verdict` 는 서버가 계산해 덮어쓴다. LLM 이 곱셈을 틀려도 결과가 틀리지 않는다.
`verdict` 순서: `limitG` 없음 → `UNKNOWN` · `maxG > limitG` → `OVER_RISK` · 빈 가방 무게 없음이나 `LOW` → `UNKNOWN` ·
`typicalG ≥ 0.8 × limitG` → `NEAR` · 그 외 `ROOM`. 초과 가능성을 먼저 본다 — 정보가 부족해도 이미 넘는 건 넘는 거다.
*"결과를 실측값처럼 표현하지 않는다"* (명세 F-10).

### 예시 — 사진 자동 등록 물품 8개 · 채택 후 미완료 1개 (06 inspection.weight와 일치)

```json
{
  "bagType": "CARRY_ON",
  "bagEmptyG": 3200,
  "weightLimitG": 10000,
  "items": [
    {
      "itemId": 2,
      "name": "상의",
      "qty": 4
    },
    {
      "itemId": 3,
      "name": "하의",
      "qty": 2
    },
    {
      "itemId": 4,
      "name": "속옷",
      "qty": 4
    },
    {
      "itemId": 5,
      "name": "충전기",
      "qty": 1
    },
    {
      "itemId": 6,
      "name": "보조배터리",
      "qty": 1
    },
    {
      "itemId": 11,
      "name": "가위",
      "qty": 1
    },
    {
      "itemId": 8,
      "name": "화장품 용기",
      "qty": 1
    },
    {
      "itemId": 9,
      "name": "검정 파우치",
      "qty": 1
    }
  ],
  "excluded": [
    {
      "name": "변환 플러그",
      "reason": "UNCHECKED"
    }
  ]
}
```

```json
{
  "minG": 4610,
  "typicalG": 5480,
  "maxG": 7010,
  "limitG": 10000,
  "bagEmptyG": 3200,
  "verdict": "ROOM",
  "confidence": "MEDIUM",
  "confidenceReason": "자동 등록 8개 중 6개의 무게를 계산했습니다. 미완료 1개와 무게 정보가 없는 2개는 제외했습니다.",
  "excludedCount": 3,
  "excluded": [
    {
      "name": "변환 플러그",
      "reason": "UNCHECKED"
    },
    {
      "name": "화장품 용기",
      "reason": "NO_WEIGHT_INFO"
    },
    {
      "name": "검정 파우치",
      "reason": "NO_WEIGHT_INFO"
    }
  ],
  "contributions": [
    {
      "name": "상의",
      "minG": 120,
      "typicalG": 200,
      "maxG": 350,
      "qty": 4,
      "subtotalG": 800
    },
    {
      "name": "하의",
      "minG": 250,
      "typicalG": 400,
      "maxG": 650,
      "qty": 2,
      "subtotalG": 800
    },
    {
      "name": "보조배터리",
      "minG": 180,
      "typicalG": 280,
      "maxG": 450,
      "qty": 1,
      "subtotalG": 280
    },
    {
      "name": "속옷",
      "minG": 40,
      "typicalG": 60,
      "maxG": 90,
      "qty": 4,
      "subtotalG": 240
    },
    {
      "name": "충전기",
      "minG": 50,
      "typicalG": 90,
      "maxG": 180,
      "qty": 1,
      "subtotalG": 90
    },
    {
      "name": "가위",
      "minG": 40,
      "typicalG": 70,
      "maxG": 120,
      "qty": 1,
      "subtotalG": 70
    }
  ]
}
```

`3200 + (200×4 + 400×2 + 280 + 60×4 + 90 + 70) = 5480`. 가위도 사진 자동 등록 시
내 목록에 등록돼 계산된다. 변환 플러그는 채택했지만 미완료라 합계에 없다. 화장품 용기의 내용량과 검정 파우치의 재질·내용물 무게는 이 예시에서 알 수 없어 NO_WEIGHT_INFO로 제외한다. 두 물품도 목록과 준비 완료율에는 포함된다. 품목별 범위는
`item_weights` 시드값을 사용하되 기존 SQL 시드의 사진 인식 가위 미등록 상태는 보완해야 한다.

### System Prompt

```text
너는 짐 무게를 범위로 추정하는 보조자다. 단일 값을 내지 않는다. 합계는 내지 않는다 — 서버가 더한다.

규칙
1. 물품에 weightRange(minG/typicalG/maxG) 가 주어지면 그 값을 그대로 contributions 에 옮긴다.
2. weightRange 가 없으면 — 옷·종이·플라스틱처럼 가볍고 균일한 물품은 네 상식으로 g 단위 정수 범위(min ≤ typical ≤ max)를 적는다. 배터리·액체·금속·책·전자기기처럼 밀도가 높아 사진으로 무게를 가늠할 수 없는 것은 추정하지 말고 excluded 에 NO_WEIGHT_INFO 로 넣는다.
3. contributions 에는 계산에 넣은 물품마다 정확히 하나. name 과 qty 는 입력 그대로. 빠뜨리지 않는다.
4. excluded 는 입력의 excluded 를 순서 그대로 옮기고, 2번에서 네가 뺀 것을 뒤에 덧붙인다.
5. confidenceReason 은 1~200자 한국어 한 문장. 뺀 이유를 개수로 적는다(예: "챙김 미완료 1개, 무게 정보 없음 2개"). 입력 reason 코드는 이렇게 옮긴다 — NOT_IN_PHOTO → 사진에서 미확인 · UNCHECKED → 챙김 미완료 · NO_WEIGHT_INFO → 무게 정보 없음.
6. 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 무게는 전부 g 단위 정수. subtotalG·minG·typicalG·maxG(합계)·excludedCount·confidence·verdict·limitG·bagEmptyG 는 서버가 계산해 덮어쓰므로 비워 두어도 된다.
```

### User Prompt 템플릿

```text
가방: {{bagType | "종류 미상"}} · 빈 무게 {{bagEmptyG | "미상"}} g · 한도 {{weightLimitG | "미상"}} g

계산에 넣을 물품 (내 목록의 실제 챙김 완료 항목만):
{{items as "- {{name}} ×{{qty}} · 범위 {{server:weightRange | \"범위 없음\"}}" | "- 없음"}}
(weightRange 는 item_weights 의 min/typical/max g. 마스터에 없는 물품은 "범위 없음")

계산에서 이미 뺀 것:
{{excluded as "- {{name}} ({{reason}})" | "- 없음"}}

물품별 무게 범위를 JSON 으로 답하라.
```

---

## AI-04 `RULE_CHECK` — 물품 구조화와 판정 설명

**UC-07 · `S-06` ③ · `S-08`** (체크리스트 물품 목록으로) **와 UC-08 · `S-09` 챗봇**
(자연어 질문으로). 호출자가 둘이지만 스키마는 하나다.

**원칙 ④ — 최종 판정은 규칙 엔진이 한다.** 그래서 이 작업만 모델을 두 번 부른다.

```text
input ──▶ [모델 1차] 물품·속성·ruleKeyword 구조화 ──▶ [규칙 엔진] transport_rules 대조, verdict
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
      "minLength": 1,
      "maxLength": 50
    },
    "question": {
      "type": [
        "string",
        "null"
      ],
      "minLength": 1,
      "maxLength": 500,
      "description": "챗봇(S-09) 자연어 질문. 물품 목록으로 부를 때는 null. 후속 턴은 사용자의 답을 여기에, 직전 output.results[]에서 itemId·detectionId·name·qty·attributes만 골라 items에 보낸다"
    },
    "items": {
      "type": "array",
      "maxItems": 50,
      "description": "S-06/S-08 이 보내는 확인 대상. 챗봇 첫 턴은 빈 배열 — 모델이 질문에서 뽑는다. 후속 턴에는 output.results[]에서 입력에 허용된 5개 필드만 골라 보낸다",
      "items": {
        "type": "object",
        "properties": {
          "itemId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1,
            "description": "checklist_items.id. 사진 자동 등록 물품도 등록된 itemId를 사용한다. 챗봇에서 여행 없이 물은 물품만 null 허용"
          },
          "detectionId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1,
            "description": "detected_objects.id. 사진 자동 등록 물품의 근거 ID를 선택적으로 전달한다. 둘 다 null이면 여행 없이 챗봇이 뽑은 물품"
          },
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100,
            "pattern": "\\S"
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "attributes": {
            "type": "object",
            "description": "규칙 엔진이 판단에 쓴 값. 모델이 질문·라벨에서 뽑았거나 입력에서 온 것. 명시된 값만 — 추정하지 않는다",
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
                "minimum": 0,
                "description": "사용자가 확인한 정격 Wh. 없으면 null을 유지하고 추가정보를 요청한다. mAh만으로 전압을 가정해 환산하지 않는다"
              },
              "batteryMah": {
                "type": [
                  "number",
                  "null"
                ],
                "minimum": 0,
                "description": "mAh만 적힌 경우 그대로 보관한다. 판정에 필요한 Wh는 사용자가 별도로 확인한다"
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
              "batteryMah",
              "bladeCm"
            ],
            "additionalProperties": false
          }
        },
        "required": [
          "itemId",
          "detectionId",
          "name",
          "qty",
          "attributes"
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

챗봇 후속 턴은 직전 `results[]`에서 `itemId/detectionId/name/qty/attributes`만 골라
`items[]`에 보내고 사용자 답을 `question`에 넣는다. `verdict`·`reason` 등 출력 전용 필드는
입력의 `additionalProperties: false`에 걸리므로 보내지 않는다. 대화 이력을 저장하지 않고
현재 질의의 구조화 문맥만 이어간다.

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
      "description": "물품마다 하나. input.items 와 같은 순서, 챗봇이면 질문에서 뽑은 순서. 06 의 customs[] 는 이 배열의 투영(itemId·name·verdict·reason·missingInfo·sourceUrl·checkedAt)",
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
          "detectionId": {
            "type": [
              "integer",
              "null"
            ],
            "minimum": 1
          },
          "name": {
            "type": "string",
            "minLength": 1,
            "maxLength": 100,
            "pattern": "\\S"
          },
          "qty": {
            "type": "integer",
            "minimum": 1,
            "maximum": 99
          },
          "ruleKeyword": {
            "type": [
              "string",
              "null"
            ],
            "minLength": 1,
            "maxLength": 100,
            "description": "모델이 물품을 transport_rules.keyword 로 정규화한 값(화장품 → 액체). 서버가 프롬프트에 준 목록에 없으면 null → ASK_AIRLINE"
          },
          "attributes": {
            "type": "object",
            "description": "규칙 엔진이 판단에 쓴 값. 모델이 질문·라벨에서 뽑았거나 입력에서 온 것. 명시된 값만 — 추정하지 않는다",
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
                "minimum": 0,
                "description": "사용자가 확인한 정격 Wh. 없으면 null을 유지하고 추가정보를 요청한다. mAh만으로 전압을 가정해 환산하지 않는다"
              },
              "batteryMah": {
                "type": [
                  "number",
                  "null"
                ],
                "minimum": 0,
                "description": "mAh만 적힌 경우 그대로 보관한다. 판정에 필요한 Wh는 사용자가 별도로 확인한다"
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
              "batteryMah",
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
            "minLength": 1,
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
            "minLength": 1,
            "maxLength": 100,
            "description": "NEED_MORE_INFO 일 때만, 무엇이 부족한지. 예: 용량(ml). 그 외 null"
          },
          "sourceUrl": {
            "type": [
              "string",
              "null"
            ],
            "minLength": 1,
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
          "detectionId",
          "name",
          "qty",
          "ruleKeyword",
          "attributes",
          "verdict",
          "ruleId",
          "conditionNote",
          "reason",
          "missingInfo",
          "sourceUrl",
          "checkedAt"
        ],
        "additionalProperties": false,
        "allOf": [
          {
            "if": {
              "properties": {
                "verdict": {
                  "const": "NEED_MORE_INFO"
                }
              }
            },
            "then": {
              "properties": {
                "missingInfo": {
                  "type": "string"
                }
              }
            },
            "else": {
              "properties": {
                "missingInfo": {
                  "type": "null"
                }
              }
            }
          },
          {
            "if": {
              "properties": {
                "sourceUrl": {
                  "type": "string"
                }
              }
            },
            "then": {
              "properties": {
                "checkedAt": {
                  "type": "string"
                }
              }
            },
            "else": {
              "properties": {
                "checkedAt": {
                  "type": "null"
                }
              }
            }
          }
        ]
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
      "description": "챗봇 호출에서만. NEED_MORE_INFO 가 있으면 부족한 것 하나만 묻는다. 물품 목록 호출은 null"
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
| **모델 · 1차 구조화** | `results[].itemId · detectionId (입력 echo)` · `results[].name · qty` · `results[].ruleKeyword (서버가 준 규정 키워드 목록 중 하나, 없으면 null)` · `results[].attributes (명시된 값만. mAh 만 있으면 batteryMah 에)` |
| **규칙 엔진** | `results[].verdict · ruleId · conditionNote · missingInfo · sourceUrl · checkedAt` · `results[].attributes.batteryWh (미입력 시 null 유지·추가정보 요청)` |
| **모델 · 2차 설명** | `results[].reason` · `answer` · `followUpQuestion` |

**서버가 저장 전에 검증한다** — 하나라도 어긋나면 `FAILED` 로 돌리고 기본 문구를 보여준다.

- results[] 의 itemId·detectionId·name·qty 가 input.items 와 순서까지 같아야 한다 (챗봇은 name 만) — 아니면 FAILED
- question 이 있으면 answer 는 string — 아니면 FAILED. question 이 null 이면 answer·followUpQuestion 은 null 로 덮어쓴다
- sourceUrl ↔ checkedAt 동시, verdict ↔ missingInfo 결합은 스키마(if/then)가 잡는다

**규칙 엔진 판정 규칙**

1. transport 와 모델이 낸 ruleKeyword 로 transport_rules 를 찾는다. ruleKeyword 가 null 이거나 규정이 없으면 ASK_AIRLINE, ruleId·conditionNote·sourceUrl·checkedAt 은 null
2. 같은 키워드에 행이 여럿이면 확인된 attributes로 조건을 판별한다. batteryMah만 있고 batteryWh가 없으면 Wh 조건을 판정하지 않고 NEED_MORE_INFO로 둔다
3. 판별에 필요한 attributes 가 null 이면 NEED_MORE_INFO + missingInfo. ruleId 는 같은 키워드 규정 중 첫 행(id 가 가장 작은 것) — 사용자가 확인할 첫 기준. 어느 조건이든 결론이 같은 경우만 그 verdict 로 확정한다 — 보조배터리는 160Wh 초과가 전면 금지라 Wh 미상이면 CABIN_OK 로 확정할 수 없다
4. 규정이 충돌하면 더 엄격한 쪽: CHECKED_FORBIDDEN > RESTRICTED > CHECKED_OK > ASK_AIRLINE > CABIN_OK. reason 에 항공사 확인을 권한다

`sourceUrl` 과 `checkedAt` 은 **항상 함께** 있거나 함께 `null` 이다 — 명세 9절 *"규정 최신성"*.
서버가 둘을 같이 채우므로 어긋날 수 없다.

### 예시 1 — S-06 · 내 목록의 자동 등록 물품 — 반입 속성 추가 확인

```json
{
  "transport": "FLIGHT",
  "airline": "대한항공",
  "question": null,
  "items": [
    {
      "itemId": 6,
      "detectionId": null,
      "name": "보조배터리",
      "qty": 1,
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "batteryMah": null,
        "bladeCm": null
      }
    },
    {
      "itemId": 11,
      "detectionId": 7,
      "name": "가위",
      "qty": 1,
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "batteryMah": null,
        "bladeCm": null
      }
    }
  ]
}
```

```json
{
  "results": [
    {
      "itemId": 6,
      "detectionId": null,
      "name": "보조배터리",
      "qty": 1,
      "ruleKeyword": "보조배터리",
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "batteryMah": null,
        "bladeCm": null
      },
      "verdict": "NEED_MORE_INFO",
      "ruleId": 1,
      "conditionNote": "100Wh 이하",
      "reason": "보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라 달라집니다. 라벨의 Wh 를 확인해 주세요.",
      "missingInfo": "배터리 정격(Wh)",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    },
    {
      "itemId": 11,
      "detectionId": 7,
      "name": "가위",
      "qty": 1,
      "ruleKeyword": "가위",
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "batteryMah": null,
        "bladeCm": null
      },
      "verdict": "NEED_MORE_INFO",
      "ruleId": 6,
      "conditionNote": "날 길이 6cm 초과",
      "reason": "날 길이를 확인해야 반입 조건을 비교할 수 있습니다. 라벨이나 실측 길이를 확인해 주세요.",
      "missingInfo": "날 길이(cm)",
      "sourceUrl": "https://www.airport.kr/ap_ko/907/subview.do",
      "checkedAt": "2026-09-02"
    }
  ],
  "answer": null,
  "followUpQuestion": null
}
```

가위는 사진 자동 등록으로 내 목록에 생성된 `itemId: 11`과 인식 근거 `detectionId: 7`을 함께
사용한다. 이름·수량·실제 준비 확인과 반입에 필요한 Wh·날 길이 확인은 별개다. 이 예시의
보조배터리·가위는 준비 완료이지만 반입 판정 속성이 부족해 `NEED_MORE_INFO`다.

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
      "detectionId": null,
      "name": "보조배터리",
      "qty": 1,
      "ruleKeyword": "보조배터리",
      "attributes": {
        "capacityMl": null,
        "batteryWh": null,
        "batteryMah": 20000,
        "bladeCm": null
      },
      "verdict": "NEED_MORE_INFO",
      "ruleId": 1,
      "conditionNote": "100Wh 이하",
      "reason": "mAh만으로 정격 Wh를 확정하지 않습니다. 라벨의 Wh를 확인해 주세요.",
      "missingInfo": "배터리 정격(Wh)",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    }
  ],
  "answer": "배터리 정격 Wh가 없어 반입 조건을 아직 판단할 수 없습니다. 라벨을 확인해 주세요. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
  "followUpQuestion": "배터리 라벨에 표시된 정격 Wh는 얼마인가요?"
}
```

`20000mAh`만 있고 Wh가 없다. `batteryMah: 20000`을 보관하되 전압을 가정하지 않는다.
`batteryWh: null`, `NEED_MORE_INFO`로 두고 라벨의 정격 Wh를 확인하는 후속 질문을 한다.
사용자가 확인값을 제공한 뒤 같은 규칙 엔진으로 다시 판단한다.

### System Prompt

```text
너는 항공·교통 수하물 규정 확인을 돕는 보조자다. 반입 여부를 네가 판정하지 않는다 — 판정은 규칙 엔진이 공식 규정표로 한다. 네 일은 두 단계다.

A. 구조화 (1차 호출)
- 질문이나 물품 목록에서 물품 이름과 규정 판단에 필요한 속성을 뽑는다: capacityMl(액체 용량), batteryWh(배터리 정격), batteryMah(mAh 만 적힌 경우), bladeCm(날 길이).
- 명시된 값만 쓴다. 없으면 null. 추정하지 않는다. 환산도 하지 않는다. mAh는 batteryMah에 그대로 옮기고 Wh가 없으면 null로 둔다.
- 물품마다 ruleKeyword 를 정한다: 서버가 준 규정 키워드 목록 중 그 물품이 해당하는 것(화장품 → 액체, 보조배터리 → 보조배터리). 해당하는 것이 없으면 null.
- 물품 목록으로 받았으면 itemId·detectionId·name·qty 를 그대로 되돌려 보낸다. 빠뜨리거나 순서를 바꾸지 않는다. 질문에서 뽑았으면 itemId·detectionId 는 null, qty 는 언급 없으면 1.
- name 은 한국어 일반명사 1~100자.

B. 설명 (2차 호출)
- 규칙 엔진 결과(verdict·conditionNote·description·missingInfo)를 받아, 물품마다 reason 을 한 문장(1~300자)으로 쓴다. 규정 description 의 뜻을 바꾸지 않는다. description 이 없으면(규정 없음) "해당 규정을 찾지 못했습니다. 항공사에 확인하세요." 로 쓴다.
- 챗봇 호출(question 이 있음)이면 answer(1~600자)를 쓴다. NEED_MORE_INFO 인 물품이 있으면 followUpQuestion 에 부족한 것 하나만 묻는다. 한 번에 하나. 물품 목록 호출이면 answer·followUpQuestion 은 null.
- 규정에 없는 말을 지어내지 않는다. "반드시 됩니다" 같은 확정 표현을 쓰지 않는다. answer 는 "최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다" 로 맺는다.

공통
- 출력은 아래 JSON Schema 를 따르는 JSON 객체 하나뿐이다. 설명·마크다운·코드펜스를 붙이지 않는다. 스키마의 필드는 전부 낸다 — 값이 없으면 null 로 낸다. 빈 문자열은 쓰지 않는다.
- verdict · ruleId · conditionNote · missingInfo · sourceUrl · checkedAt · 사용자 미확인 attributes.batteryWh 는 서버가 채워 덮어쓰므로 비워 두어도 된다.
```

### User Prompt 템플릿

두 번 부르므로 템플릿도 둘이다.

```text
[1차 · 구조화]
이동수단 {{transport}} · 항공사 {{airline | "미상"}}
규정 키워드 목록: {{server:ruleKeywords as "{{keyword}}" | "없음"}}
질문: {{question | "(없음 — 아래 물품 목록으로)"}}
물품:
{{items as "- {{name}} ×{{qty}} (itemId {{itemId | \"-\"}} · detectionId {{detectionId | \"-\"}} · {{attributes.capacityMl | \"-\"}} ml / {{attributes.batteryWh | \"-\"}} Wh / {{attributes.batteryMah | \"-\"}} mAh / {{attributes.bladeCm | \"-\"}} cm)" | "- 없음"}}

물품과 속성을 구조화해 JSON 으로 답하라. results[] 의 itemId·detectionId·name·qty·ruleKeyword·attributes 만 채운다.

[2차 · 설명]
이동수단 {{transport}} · 항공사 {{airline | "미상"}}
질문: {{question | "(없음)"}}
규칙 엔진 결과:
{{server:engineResults as "- {{name}} ({{attributes.capacityMl | \"-\"}} ml / {{attributes.batteryWh | \"-\"}} Wh / {{attributes.bladeCm | \"-\"}} cm): {{verdict}} / {{conditionNote | \"-\"}} / {{description | \"규정 없음\"}} / 부족: {{missingInfo | \"-\"}}"}}

각 물품의 reason 을, 챗봇 호출이면 answer 와 followUpQuestion 도 채워 JSON 으로 답하라.
```

---

## Mock이 돌려주는 것

`AI_PROVIDER=mock`이면 인식·추천·품목 범위·문장 생성은 고정 예시를 재료로 사용한다.
실제 AI 호출은 하지 않는다. **사용자의 선택·수량·완료 상태를 처리하는 서버 로직은 실제로 동작해야 한다.**

- Mock과 실제 AI 모두 출력 스키마 검증·입력 대조·서버 필드 채움을 거친다. Mock이라는
  이유로 중복 검사, 준비 완료 필터, 합산을 생략하지 않는다.
- `BAG_CHECK`는 실제 입력 photoIds에 맞춰 후보를 만든다. 사진 한 장이면 존재하지 않는
  두 번째 photoId를 사용하지 않는다. 성공한 인식 물품은 완료 처리에서 승인 없이 내 목록에 PREPARED로 등록한다. 실패 사진만 재시도한다.
- `PACKING_LIST`는 현재 내 목록과 중복을 제거하고 reason을 포함한 후보만 반환한다.
  source·acceptedItemId·날씨 메타데이터는 서버가 채운다. 내 목록에 자동 INSERT하지 않는다.
- `WEIGHT_ESTIMATE`는 입력의 실제 완료 물품·수량과 품목별 Mock/마스터 범위로 계산한다.
  bagEmptyG·limitG만 바꾼 고정 합계를 반환하지 않는다. 미완료·미채택 추천은 합산하지 않는다.
- `RULE_CHECK`는 같은 여행의 내 목록 항목·확인된 속성을 사용한다. 사진 자동 등록 항목도 포함한다. 출력 ID는 입력과 일치해야
  하며 Wh가 없으면 추가정보를 요청한다. 챗봇은 여행 없이 질문한 별도 경로다.
- `AI_MOCK_DELAY_MS` 후 완료되며 `202 → 폴링 → COMPLETED/FAILED`를 유지한다.
  사진 등록은 BAG_CHECK 완료 처리, 추천 채택은 별도 item POST가 담당한다.
- 기존 SQL 시드는 이전 합성 목록이다. 사진 인식 가위 등록·추천 채택 시점이 반영된 시드와
  빈 여행 시나리오는 **후속 구현·검증 필요**다. 시드만 보고 개정 흐름 구현을 완료 처리하지 않는다.

---

## 검증

### 문서 계약 검증 — 2026-09-03 개정

기존의 “45항목 통과”는 이전 계약의 검증 기록이다. 개정 후 아래 항목을 다시 검증해 통과했다.
이번 검증은 문서 스키마·예시의 정합성 확인이며 실제 앱 E2E 통과를 뜻하지 않는다.

| 항목 | 확인 결과 |
| --- | --- |
| JSON Schema | Draft 2020-12 스키마 8개 검사 및 날짜 형식 포함 AI 예시 10개·06 PACKING_LIST 예시 2개 검증 통과 |
| 후보 분리 | 추천 예시의 acceptedItemId는 null, 후보별 reason·source·날씨 시점 포함 |
| 완료율·필수 경고 | 06의 홈·내 목록·검수 예시는 동일한 9개 내 목록 기준 `0.889`(표시 `89%`). 미채택 여권 1건은 별도 경고이며 완료율·무게에서 제외 |
| 무게 산수 | 준비 완료 8개 중 무게를 아는 6개 합산, 미완료 1개·무게 정보 없는 2개 제외, 최소 4610g · 대표 5480g · 최대 7010g. 06과 일치 |
| 후속 질문 | RULE_CHECK 결과에서 입력 허용 필드만 추린 객체가 입력 스키마를 통과 |
| Wh 미상 | mAh만 있는 예시는 NEED_MORE_INFO, batteryWh=null, 추가 질문 포함 |
| ERD | 체크리스트 개정 당시 10개 테이블·컬럼명 일치. 로그인 개정 목표에는 users.login_id 1개가 추가되며 SQL 반영은 별도 추적(05) |

### 사진 자동 등록·추천 승인 수용 기준 — 구현 후 실행 필요

| 동작 | 기대 결과 |
| --- | --- |
| 빈 여행에서 충전기·상의·보조배터리 사진 자동 등록 | 내 목록 3개 모두 완료, 완료율 3/3 |
| 어댑터·세면도구 추천 생성 | 아래쪽 후보만 변경, 내 목록·완료율·무게 합계 불변 |
| 어댑터만 채택 | 미완료 항목 1개 추가, 완료율 3/4, 현재 가방 무게 합계 불변 |
| 같은 후보 재승인·동시 요청 | 같은 항목 반환, 중복 없음 |
| 어댑터 실제 완료 확인 | 완료율 4/4, 무게 정보가 있으면 재계산에 포함 |
| 사진 자동 등록 물품명·수량 수정 | 내 목록·사진 연결·무게 입력을 같은 확인값으로 갱신 |
| 추천 실패·낮은 신뢰도 인식 물품 | 자동 등록된 내 목록 유지. LOW 물품도 PREPARED로 집계하고 확인 필요 배지만 표시 |
| 같은 BAG_CHECK 완료 재처리·반복 GET | 등록은 한 번, 사용자 사후 수정·삭제를 되돌리지 않음 |
| 일부 사진 실패·목록 저장 실패 | 성공 사진만 자동 등록. 저장 트랜잭션 실패 시 부분 반영·거짓 COMPLETED 없음 |
| 사진에 없는 물품을 직접 완료 확인 | 완료·무게 대상 유지, 사진 미확인 배지는 별도 표시 |
| 내 목록은 모두 완료·여권 필수 후보는 미채택 | 완료율 `1`·표시 `100%`, S-05·S-06의 `미채택 필수 후보 1건` 유지. `확인하기`로 S-05 이동 |
| 추천 작업이 없거나 후보 배열이 비어 있음 | 작업 없음은 `null`·`필수 추천 확인 전`, 완료된 빈 후보는 `0`. 생성 실패를 0건으로 표시하지 않음 |
| 화면의 alreadyPacked가 과거 목록이거나 빈 배열 | 유효한 형식이면 서버 PREPARED 목록으로 보정·저장하고 `202`. 같은 값으로 Mock 실행, 차이만으로 `409`를 내지 않음 |
| S-06 사진 확인 필요 항목을 선택 | S-04에서 이미 자동 등록된 항목을 필요하면 사후 수정하고 S-06 복귀, 상태·무게 다시 조회. 승인 요청 없음 |

### Playground·실제 모델 검증 — 미실행 TBD

`checklist.md`의 기존 **Playground 검증(예상 10분)**을 별도 미완료 작업으로 유지한다.
프롬프트는 작성됐지만 설계 완료 확인과 Playground 실행 시점은 PM 확인 TBD다.
이 문서 개정에서는 실행하지 않았으며, 범위를 줄여 완료로 바꾸지 않는다.
앱의 AI 호출은 계속 Mock이다. 실제 모델 검증 시 프롬프트와 모델용 파생 스키마를 확인하고,
등록·채택·완료율·무게 합산은 서버가 담당하도록 같은 수용 기준을 적용한다.

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
| 최대 토큰 | `AI_MAX_TOKENS` | `4096` | `BAG_CHECK` 100개(≈4.7k 토큰 추정) · `PACKING_LIST` 40개 |
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
| 4 | 서버 작업 실행에 큐(메시지 브로커) 도입 | 중간 | 작업 실행 방식만 변경하고 FE의 상태 조회·폴링 계약은 유지한다 |
| 5 | `transport_rules` 갱신 잡 — 출처 재확인 날짜 자동 갱신 | 중간 | `checkedAt` 이 오래되면 판정 근거가 약해진다 |
| 6 | 비용·토큰 사용량 모니터링 | 낮음 | `ai_jobs` 에 토큰 수 컬럼 추가 |

## 알려진 한계

- **개정 문서와 실제 구현·시드 반영은 별개다.** 06 계약을 FE 타입·Mock·자동 등록 저장 로직에
  반영하고 위 수용 기준을 실행해야 한다([반영 상태](README.md#개정안-반영-상태)).
- **로그인 필수 설계는 확정됐고 인증 구현은 남아 있다.** 세션·가입·로그아웃·소유권·사진 보호와
  users.login_id의 SQL·시드 반영 후 06의 인증 수용 기준을 실행해야 한다.
- **이미지 4종 재생성을 완료했다.** PNG·PUML·SVG를 03의 MD 흐름·화면 상세와 대조했으며, 코드·시드 반영과 E2E 검증은 별도다.
- **자동으로 사진 간 동일 물품을 판별하는 정교한 병합은 TBD다.** 현재는 기존 항목 연결과
  동일 이름 기준 자동 연결·큰 관측 수량과 선택적 사후 수정을 사용한다. 단순 인식 횟수 합산은 하지 않는다.
- **동일 물품의 부족 수량 추천·필수 후보 자동 채택 예외는 TBD다.** 현재는 내 목록 중복 제외,
  모든 후보 사용자 선택을 기본으로 한다.
- **물품·전체 가방 실측값 저장은 데모 제외·TBD다.** 빈 가방 무게 입력은 기존대로 제공한다.
- **날씨 원문 전체는 보관하지 않는다.** 출처·기준일은 output에 남기지만 과거 응답의 완전한
  재현에 필요한 날씨 원문 스냅샷은 실제 연동 시 검토한다.
- **RULE_CHECK의 실제 모델 호출은 두 단계다.** 현재는 Mock이며 지연·비용 검증은 향후 TBD다.
- **챗봇 사진 첨부**의 별도 저장·연결 흐름은 TBD다. 여행 사진의 BAG_CHECK 완료 시 자동 등록 정책과 구분하며, 사진 사전 승인 단계를 다시 추가하지 않는다. 대화 영구 저장은 범위 밖이다.
- **confidenceLevel 경계값 0.80 / 0.50은 초기값이다.** 실제 모델 인식 정확도·사후 수정률을 보고 조정한다.
- **실제 모델 검증은 하지 않았다.** 스키마·예시 검증과 모델 정확도를 혼동하지 않는다.
