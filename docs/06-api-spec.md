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
| 인증 | TBD |

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

| # | Method | Path | 설명 | 관련 Use-Case | 상태 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GET` | `/api/TBD` | TBD | UC-01 | TBD |
| 2 | `POST` | `/api/ai-jobs` | AI 작업 생성 (Mock) | UC-02 | 설계됨 |
| 3 | `GET` | `/api/ai-jobs/{id}` | AI 작업 상태·결과 조회 (Mock) | UC-02 | 설계됨 |

---

## AI 확장 지점 엔드포인트 (Mock)

**AI-Ready 원칙 3 (Asynchronous Pipeline)을 구현한 형태다.** 지금은 Mock이
즉시 고정 JSON을 돌려주지만, 나중에 실제 LLM을 붙여도 이 규격은 그대로다.
LLM 호출은 수 초가 걸리므로 처음부터 비동기 구조로 열어 둔다.

### `POST /api/ai-jobs` — AI 작업 생성

작업을 **접수만** 하고 즉시 응답한다. 결과를 기다리지 않는다.

**Request**

```json
{
  "jobType": "TBD",
  "input": {
    "TBD": "TBD"
  }
}
```

**Response — `202 Accepted`**

```http
Location: /api/ai-jobs/1
```

```json
{
  "id": 1,
  "status": "PENDING",
  "jobType": "TBD",
  "createdAt": "2026-09-02T05:30:00Z"
}
```

**오류**

| 코드 | 상황 |
| --- | --- |
| `400` | `jobType`이 없거나 `input`이 스키마에 맞지 않음 |

### `GET /api/ai-jobs/{id}` — 상태·결과 조회

FE는 이 엔드포인트를 폴링해서 `status`가 `COMPLETED`가 되면 결과를 그린다.

**Response — `200 OK` (처리 중)**

```json
{
  "id": 1,
  "status": "PENDING",
  "jobType": "TBD",
  "output": null,
  "createdAt": "2026-09-02T05:30:00Z",
  "completedAt": null
}
```

**Response — `200 OK` (완료)**

```json
{
  "id": 1,
  "status": "COMPLETED",
  "jobType": "TBD",
  "output": {
    "TBD": "TBD"
  },
  "modelName": "mock",
  "createdAt": "2026-09-02T05:30:00Z",
  "completedAt": "2026-09-02T05:30:02Z"
}
```

> `output`의 정확한 구조는 [`07-ai-ready.md`](07-ai-ready.md)의 JSON Schema로
> 고정한다. **Mock이 돌려주는 JSON과 나중에 LLM이 돌려줄 JSON이 같은 스키마여야
> 한다.** 이것을 지키지 못하면 AI-Ready 설계가 무너진다.

**Response — `200 OK` (실패)**

```json
{
  "id": 1,
  "status": "FAILED",
  "output": null,
  "errorMessage": "TBD",
  "completedAt": "2026-09-02T05:30:02Z"
}
```

**오류**

| 코드 | 상황 |
| --- | --- |
| `404` | 해당 `id`의 작업이 없음 |

> **데모 팁:** Mock 단계에서는 `POST` 직후 바로 `COMPLETED`로 만들어도 된다.
> 다만 FE는 반드시 폴링 코드를 넣어 둔다. 그래야 나중에 진짜 AI를 붙였을 때
> FE를 고치지 않는다. 발표에서 로딩 상태를 잠깐 보여주고 싶으면 Mock에
> 1~2초 지연을 넣는다.

---

## Mock 서버 운영 방식

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| Postman Mock Server | 백엔드 없이도 FE가 바로 개발 가능. 1일차부터 쓸 수 있다 | 저장소에 코드가 남지 않아 발표 때 보여줄 것이 적다 |
| 백엔드 안에 Mock 응답 구현 | 데모가 로컬에서 완결. 실제 DB 저장까지 시연 가능 | 백엔드가 뜰 때까지 FE가 기다려야 한다 |

권장: **1일차에 Postman Mock으로 FE를 먼저 굴리고, 2일차에 백엔드 Mock으로 옮긴다.**
Base URL만 `.env`에서 바꾸면 되므로 FE 코드는 그대로다.

- Postman Collection 링크: TBD
- Postman Mock URL: TBD

## OpenAPI 명세

TBD — Swagger Editor(`editor-next.swagger.io`)로 작성 시 `docs/openapi.yaml`에 둔다.

> 백엔드에 `springdoc-openapi-starter-webmvc-ui` 의존성을 넣으면 Swagger UI가
> `/swagger-ui.html`에 자동 생성되고 OpenAPI 문서도 `/v3/api-docs`로 나온다.
> **그러면 이 파일을 손으로 쓰지 않아도 된다.** ADR 0001 참조.
