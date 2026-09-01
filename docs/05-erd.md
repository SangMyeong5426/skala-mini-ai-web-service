# 데이터 모델링 (ERD)

> 발표 3번 섹션: DB 데이터 모델링(ERD)
>
> 채점 기준: **"ERD 테이블 관계(1:N, N:M) 및 정규화 타당성"**
> Peer Review: **"데이터 모델링(ERD) 관계 및 정규화가 적절한가?"**

## ERD 다이어그램

- dbdiagram.io 링크: TBD
- 이미지: `images/05-erd.png` (TBD) — 내보내는 방법은 [`images/README.md`](images/README.md)

> dbdiagram.io는 아래 DSL을 붙여 넣으면 다이어그램이 즉시 생성되고
> PNG/PDF로 내보낼 수 있다. 발표 자료에 그대로 넣으면 된다.

## 스키마 정의 (dbdiagram.io DSL)

```dbml
Table users {
  id            bigserial   [pk]
  email         varchar(255)[not null, unique]
  nickname      varchar(50) [not null]
  created_at    timestamptz [not null, default: `now()`]
}

// TBD: 서비스 도메인 테이블

// ── AI 결과 저장 테이블 ───────────────────────────────────
// AI-Ready 원칙 2 (Structured Data)를 만족시키는 형태.
// 지금은 Mock이 채우고, 나중에 LLM이 같은 자리를 채운다.
Table ai_jobs {
  id            bigserial   [pk]
  user_id       bigint      [not null, ref: > users.id]   // 1:N

  status        varchar(20) [not null, note: 'PENDING | COMPLETED | FAILED']
  job_type      varchar(50) [not null, note: '어떤 AI 기능인지']

  input_payload  jsonb      [not null, note: 'AI에 넘긴 입력']
  output_payload jsonb      [note: 'AI가 돌려준 결과. PENDING이면 null']

  // ── 메타데이터: 코드 변경 없이 모델을 바꿔 끼우기 위한 칸 ──
  model_name    varchar(100)[note: 'Mock이면 "mock", 나중엔 실제 모델명']
  error_message text        [note: 'FAILED일 때만 채운다']

  created_at    timestamptz [not null, default: `now()`]
  completed_at  timestamptz
}
```

## 테이블 관계

| 관계 | 유형 | 설명 |
| --- | --- | --- |
| `users` → `ai_jobs` | 1:N | 사용자 한 명이 여러 AI 작업을 만든다 |
| TBD | TBD | TBD |

> **N:M 관계가 하나도 없으면 채점에서 불리하다.** 루브릭이 `1:N, N:M`을 명시한다.
> 억지로 만들 필요는 없지만, 도메인에 자연스러운 N:M(예: 게시글–태그,
> 사용자–즐겨찾기)이 있는지 한 번은 검토하고 그 결과를 아래에 남긴다.

N:M 검토 결과: TBD

## 정규화 검토

| 항목 | 확인 | 비고 |
| --- | --- | --- |
| 1NF — 모든 컬럼이 원자값인가 | TBD | `jsonb` 컬럼은 예외다. 아래 근거 참조 |
| 2NF — 부분 함수 종속이 없는가 | TBD | |
| 3NF — 이행 함수 종속이 없는가 | TBD | |
| 의도적 비정규화 | TBD | 있다면 **이유를 반드시 적는다** |

### `jsonb` 컬럼을 쓰는 근거

`ai_jobs.input_payload`와 `output_payload`를 정규화된 컬럼으로 쪼개지 않은 것은
설계 실수가 아니라 **의도적 선택**이다.

- AI의 출력 형태는 프롬프트에 따라 달라진다. 컬럼으로 고정하면 프롬프트를
  바꿀 때마다 마이그레이션이 필요하다.
- PDF의 **Structured Data** 원칙이 요구하는 것이 정확히 이것이다 —
  "AI가 읽고 이해하기 쉬운 JSON 규격을 사전에 반영하여 바로 DB에 저장하거나
  FE에 전달할 수 있도록 하여 데이터 변환 레이어의 부담을 최소화"
- 대신 **JSON의 내부 구조는 `07-ai-ready.md`에 JSON Schema로 고정**한다.
  스키마 없는 자유 형식 JSON이 아니다.

> 발표 Q&A에서 "왜 정규화하지 않았느냐"는 질문이 나오면 위 세 줄로 답하면 된다.

## 실행 스크립트

확정된 스키마는 [`database/schema.sql`](../database/schema.sql)에 SQL로 옮긴다.
데모용 초기 데이터는 [`database/seed.sql`](../database/seed.sql)에 둔다.

`database/`는 백엔드 스택과 무관하므로 **ADR 0001 확정을 기다리지 않고
바로 시작할 수 있다.** 이 문서의 DSL과 `schema.sql`은 짝이다. 한쪽만 고치지 않는다.
