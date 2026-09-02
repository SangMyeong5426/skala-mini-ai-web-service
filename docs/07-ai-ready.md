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

| ID | 확장 지점 | 대상 Use-Case | 지금 | 나중 |
| --- | --- | --- | --- | --- |
| AI-01 | TBD | UC-04, UC-05, UC-07(물품명 구조화), UC-08, UC-10 | Mock이 고정 JSON 반환 | LLM이 같은 스키마 JSON 생성 |

### 왜 여기인가

TBD — 이 지점이 서비스 흐름상 타당한 이유. Peer Review 항목이므로 한 문단은 쓴다.

<!-- 좋은 근거의 형태:
     "사용자가 지금 손으로 하고 있는 판단 중 가장 반복적이고 규칙이 흐릿한
      단계라서, 규칙 기반 코드로는 만들기 어렵고 LLM이 잘하는 일이다." -->

### 무엇이 바뀌고 무엇이 안 바뀌는가

| 계층 | AI 결합 시 변경 여부 |
| --- | --- |
| Frontend | **변경 없음** — 같은 엔드포인트, 같은 응답 스키마를 그대로 소비 |
| REST API 규격 | **변경 없음** — `06-api-spec.md`의 계약 그대로 |
| DB 스키마 | **변경 없음** — `ai_jobs.output_payload`(jsonb)에 그대로 저장 |
| Backend 내부 | **여기만 변경** — Mock 응답 생성 → LLM API 호출로 교체 |
| 환경 변수 | `.env`에 API 키·모델명 추가. **코드 변경 없음** |

> 이 표가 발표 2번 섹션의 핵심 슬라이드다.
> **"백엔드 한 곳만 바뀐다"**를 보여주는 것이 목표다.

---

## 입력 JSON Schema

`POST /api/ai-jobs`의 `input` 필드 구조를 고정한다.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "AI Job Input",
  "type": "object",
  "properties": {
    "TBD": {
      "type": "string",
      "description": "TBD",
      "maxLength": 5000
    }
  },
  "required": ["TBD"],
  "additionalProperties": false
}
```

## 출력 JSON Schema

`GET /api/ai-jobs/{id}`의 `output` 필드 구조를 고정한다.
**Mock이 돌려주는 JSON도 반드시 이 스키마를 지켜야 한다.**

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "AI Job Output",
  "type": "object",
  "properties": {
    "TBD": {
      "type": "string",
      "description": "TBD"
    }
  },
  "required": ["TBD"],
  "additionalProperties": false
}
```

### 스키마 설계 시 지킬 것

- **모든 필드를 `required`로 두거나, 아니면 FE가 없을 때를 처리하게 한다.**
  LLM은 필드를 빠뜨릴 수 있다. 어느 쪽이든 정해서 적는다.
- **`additionalProperties: false`** 로 두면 LLM이 멋대로 필드를 추가했을 때
  검증에서 걸러진다.
- **배열에는 최대 길이를 정한다.** LLM이 100개를 돌려주면 화면이 깨진다.
- **자유 서술 필드를 최소화한다.** 화면에 그릴 수 있는 구조로 받는다.

---

## 프롬프트 설계

실행하지는 않지만, **어떤 프롬프트로 위 출력 스키마를 얻을지**는 설계한다.
OpenAI Playground나 ChatGPT 웹 UI에서 코드 없이 검증할 수 있다.

### System Prompt

```text
TBD
```

### User Prompt 템플릿

```text
TBD

--- 입력 ---
{{ TBD }}
```

### 검증 결과

Playground에서 실제로 돌려 보고 결과를 남긴다. **채점 항목의 "타당성"이
여기서 갈린다.** 돌려 보지 않은 프롬프트는 대개 스키마를 지키지 않는다.

| 시도 | 입력 | 스키마 준수 | 비고 |
| --- | --- | --- | --- |
| 1 | TBD | TBD | TBD |

---

## 모델 파라미터

**AI-Ready 원칙 4 (Security & Config Isolation).** 아래 값은 전부 코드가 아니라
`backend/.env`에서 읽는다. 모델을 바꿀 때 코드를 고치지 않기 위해서다.

| 항목 | 환경 변수 | 지금 값 | 비고 |
| --- | --- | --- | --- |
| 제공자 | `AI_PROVIDER` | `mock` | 나중에 `openai` / `anthropic` |
| 모델명 | `AI_MODEL` | `mock` | |
| API 키 | `AI_API_KEY` | (비움) | **절대 커밋하지 않는다** |
| 응답 다양성 | `AI_TEMPERATURE` | `0.2` | 구조화된 JSON 출력이므로 낮게 |
| 최대 토큰 | `AI_MAX_TOKENS` | `2048` | |
| Mock 응답 지연 | `AI_MOCK_DELAY_MS` | `0` | 발표에서 로딩 화면을 보여주려면 `1000`~`2000` |

> `AI_PROVIDER=mock`이면 Mock 응답을, 다른 값이면 실제 API를 호출하도록
> 백엔드를 분기해 두면 **환경 변수 한 줄로 AI를 켜고 끌 수 있다.**
> 발표에서 보여주기 좋은 지점이다.

## 향후 로드맵

> 발표 5번 섹션: 프로젝트 한계점 및 추후 AI 실제 결합 시 로드맵

| 단계 | 할 일 | 예상 난이도 |
| --- | --- | --- |
| 1 | `AI_PROVIDER` 분기와 실제 API 클라이언트 구현 | TBD |
| 2 | 응답 JSON Schema 검증 및 실패 시 재시도 | TBD |
| 3 | 폴링을 큐(Celery/메시지 브로커)로 교체 | TBD |
| 4 | 비용·토큰 사용량 모니터링 | TBD |

## 알려진 한계

TBD — 솔직하게 적는다. 발표 5번 섹션(회고)에서 그대로 쓴다.
