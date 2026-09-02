# ADR 0003: AI 작업 엔드포인트를 하나로 둔다

- 상태: **Accepted** — 1일차 확정
- 날짜: 2026-09-02
- 관련 Issue: #
- 참여자: 손현아, 최인서, 문성도, 박상명, 박현수

## 배경

기능 명세 초안이 AI 작업을 **작업 종류별로 엔드포인트를 나눠** 제안했다.

```text
POST /api/ai/packing-list     체크리스트 생성
POST /api/ai/bag-check        사진 속 물건 인식
POST /api/ai/rule-chat        반입 규정 질의
GET  /api/jobs/{jobId}        결과 조회
```

그런데 저장소는 이미 **작업 하나로 받는 구조**로 잡혀 있다.
[`docs/06-api-spec.md`](../06-api-spec.md)의 계약과 `database/schema.sql`의
`ai_jobs` 테이블이 그 전제다.

```text
POST /api/ai-jobs             job_type 으로 종류를 구분
GET  /api/ai-jobs/{id}        결과 조회
```

명세와 저장소가 어긋난 상태라 어느 쪽으로 갈지 정해야 한다.

제약:

- AI는 3일차 데모까지 전부 Mock이다.
- 발표 2번 섹션(2분)의 주제가 **"서비스 내 AI 확장 지점"** 이다.
- 아키텍처 다이어그램의 핵심 메시지가 *"이 상자 안만 교체된다"* 이다.

## 검토한 선택지

### A. 작업 종류별로 나눈다 (명세 초안)

| | 내용 |
| --- | --- |
| 장점 | 엔드포인트마다 요청 스키마가 하나로 고정돼 **OpenAPI 문서가 선명하다**<br>Swagger UI에서 각 작업을 따로 시험할 수 있다 |
| 단점 | AI 작업이 늘 때마다 **엔드포인트가 늘어난다** — 확장 지점이 흩어진다<br>프런트엔드가 종류마다 호출 코드를 따로 쓴다<br>`ai_jobs.job_type` 컬럼이 무의미해진다 |

### B. 하나로 두고 `job_type`으로 구분한다 (저장소 현행)

| | 내용 |
| --- | --- |
| 장점 | **"AI가 들어올 자리는 이 엔드포인트 하나"** 라고 말할 수 있다 — 아키텍처 다이어그램의 교체되는 상자와 1:1로 붙는다<br>AI 작업이 늘어도 엔드포인트를 추가하지 않는다 (Interface First)<br>폴링 훅을 프런트엔드에 **한 번만** 만든다<br>`ai_jobs` 테이블 한 곳에 모든 AI 이력이 쌓여 상태 관리가 단순하다 |
| 단점 | 요청 본문이 `job_type`에 따라 달라 OpenAPI 스키마가 느슨해진다 |

## 결정

**B. 하나로 둔다.** 명세 초안의 3분할을 `job_type` 값으로 접는다.

```text
POST /api/ai-jobs   →  202 Accepted + Location: /api/ai-jobs/{id}
GET  /api/ai-jobs/{id}  →  200 (PENDING | COMPLETED | FAILED)
```

| 명세 초안 | `job_type` |
| --- | --- |
| `POST /api/ai/packing-list` | `PACKING_LIST` |
| `POST /api/ai/bag-check` | `BAG_CHECK` |
| `POST /api/ai/rule-chat` | `RULE_CHECK` |
| *(초안에 없음 — UC-10 예상 무게 산정)* | `WEIGHT_ESTIMATE` |

**고른 이유는 채점이다.** 이 프로젝트의 평가는 완성도가 아니라 설계 타당성이고,
발표에서 증명해야 하는 명제는 *"AI가 들어올 자리를 미리 비워 뒀다"* 다.
확장 지점이 **한 곳**일 때 그 명제가 그림 한 장으로 끝난다. 세 곳이면
"확장 지점이 어디냐"는 질문에 세 번 답해야 한다.

### B의 단점을 덮는 방법

느슨해지는 요청 스키마는 문서로 조인다. `job_type`별 입력·출력 JSON Schema를
[`docs/07-ai-ready.md`](../07-ai-ready.md)에 각각 적고, `additionalProperties: false`로
닫는다. **스키마 자체는 종류별로 엄격하고, 엔드포인트만 하나인 형태**다.

## 영향

- [x] `backend/.env.example`·`application.properties`의 AI 설정은 그대로 쓴다
- [x] `docs/06-api-spec.md`의 엔드포인트 목록에 `job_type` 4종을 명시
- [ ] `docs/07-ai-ready.md`에 `job_type`별 입출력 JSON Schema 작성 (API Architect)
- [ ] 기능 명세의 `POST /api/ai/*` 3개와 `GET /api/jobs/{id}`를 이 규격으로 수정
- [x] `ai_jobs`에 `trip_id`(nullable) 추가 — 규정 질의는 여행 없이도 가능하다 (Data Architect)

> `trip_id`를 **nullable**로 두는 이유는 `RULE_CHECK` 때문이다. 반입 규정 질의는
> 여행을 등록하지 않은 상태에서도 할 수 있어야 한다.
