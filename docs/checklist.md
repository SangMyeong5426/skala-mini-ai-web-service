# 3일 로드맵 체크리스트

> PM이 관리한다. 매일 아침 한 번, 저녁 한 번 확인한다.

## 1일차 — 서비스 기획 & Architecture 정의

**목표: AI 기능이 들어올 자리를 미리 비워 둔 웹 서비스를 기획한다.**

### 기획

- [x] AI-Ready Web Service 아이디어 선정 → [`01-service-plan.md`](01-service-plan.md) — 한 줄 정의·선정 근거 표 작성 완료
- [x] 페르소나와 해결할 문제 정의 → [`01-service-plan.md`](01-service-plan.md) — 1차 김지우 · 2차 이준호 · 확장 2명
- [x] Teaming, R&R 정의 → [`00-team.md`](00-team.md) — 7개 역할 전부 채움
- [x] Actor 중심 Use-Case 정의 → [`02-use-case.md`](02-use-case.md) — UC-01~10, Actor 4종
- [x] **AI 확장 지점 정의** → [`02-use-case.md`](02-use-case.md) — 4개 + UC-07 부분 연동
- [x] AI 입출력 스키마 → [`07-ai-ready.md`](07-ai-ready.md) — `jobType` 4종 입·출력 Schema, 기계 검증 39항목
- [x] User Flow · 화면 목록 작성 → [`03-wireframe.md`](03-wireframe.md) — S-01~10
- [ ] **Figma 와이어프레임** — 데모 대상 2개는 정성껏, 나머지는 스케치

### 개발환경 세팅

> **순서.** 도구 설치와 협업 설정은 위 "기획"과 **병행해도 된다.**
> 스캐폴딩과 DB 프로젝트 생성도 주제와 무관하므로 먼저 해 둔다.
> 다만 **`schema.sql`·화면·엔터티처럼 설계에 의존하는 작업은 `01`·`02`·`03`이
> 나온 뒤에** 시작한다. 화면과 스키마를 모르는 채 만들면 두 번 일한다.

- [x] 저장소 구조 및 협업 설정 (이 저장소)
- [x] GitHub 저장소 생성 및 push
- [x] 팀원 전원 Collaborator 초대 — 5명 전원 `write` 권한, 수락 완료
- [x] 전원 [README "팀원 온보딩"](../README.md#팀원-온보딩) 1~6단계 완료 — 2026-09-03
- [x] `.github/CODEOWNERS` 주석 해제 및 실제 GitHub ID 반영
- [x] ~~PM·DevOps 백업 지정~~ — **하지 않기로** (2026-09-03). 3일차 피크는 PM 이 그대로 맡는다
- [x] **백엔드 스택 결정** → [ADR 0001](adr/0001-backend-stack.md) · Java 21 / Spring Boot 4.1.1
- [x] **프런트엔드 스택 결정** → [ADR 0002](adr/0002-frontend-stack.md) · React + TypeScript
- [x] **React 사용 가능 여부 교수님께 확인** — 사용 가능
- [x] **JDK 21 설치** — **역할과 무관하게 5명 전원.** 2026-09-02 완료
- [x] **Node.js 20.19+ 또는 22.12+ 설치** — **역할과 무관하게 5명 전원.** 2026-09-03 완료
- [x] FE 프로젝트 생성 (Vite 8 · React 19 · TS 6) — 검증 완료
- [x] BE 프로젝트 생성 (Spring Boot 4.1.1 · Java 21) — `./gradlew build` 통과 확인
- [ ] DB 프로젝트 생성 (Supabase 또는 Neon) 및 접속 정보 팀 공유 (**저장소 밖에서**)
- [x] 전원 `npm run dev` · `./gradlew build` 한 번씩 돌려 보기 — 2026-09-03 완료

---

## 2일차 — 시스템 설계 및 Scaffolding

**목표: 데이터 모델·API 명세 확정, 핵심 화면과 데이터 흐름 End-to-End 검증.**

- [x] 데이터 모델링(ERD) → [`05-erd.md`](05-erd.md) — 10테이블 · **N:M 2개**
- [x] 스키마 SQL 작성 → [`database/schema.sql`](../database/schema.sql) — 로컬 DB에서 실행·제약 검증 완료
- [ ] 클라우드 DB(Supabase)에 실행 — **`schema.sql`·`seed.sql` 이 #27 에서 바뀌었다. 강의장에서 `./scripts/db-apply`**
- [x] 데모용 시드 데이터 작성 → [`database/seed.sql`](../database/seed.sql) — 도쿄 3박4일 시나리오
- [x] REST API 명세 작성 → [`06-api-spec.md`](06-api-spec.md) — 18엔드포인트 · **Status Code 전부 표기**
- [x] **Mock API 엔드포인트 포함** → `POST /api/ai-jobs` + `GET /api/ai-jobs/{id}` · `jobType` 4종
- [x] AI 입출력 JSON Schema 확정 → [`07-ai-ready.md`](07-ai-ready.md) — 06 예시·`schema.sql` enum 과 기계 대조
- [ ] 프롬프트 설계 및 Playground 검증 → [`07-ai-ready.md`](07-ai-ready.md) — 프롬프트는 작성됨, **Playground 실행은 TBD** (10분)
- [ ] FE ↔ BE API 연동
- [ ] BE ↔ DB 연결
- [x] 시스템 아키텍처 다이어그램 완성 → [`04-architecture.md`](04-architecture.md) — PlantUML 원본 + PNG · SVG
- [ ] **화면 구현 1차** — `S-01`~`S-06` 데모 주 경로 (Mock API 호출 → 데이터 렌더링)
- [ ] 화면 구현 2차 — `S-07` 무게 상세 · `S-08` 반입 규정 상세
- [ ] 화면 구현 3차 — `S-09` 챗봇 · `S-10` 여행 기록 상세
- [ ] End-to-End 흐름 검증

> 팀 결정으로 **화면 10개 전체**를 만든다. 다만 **1차 6개가 끊김 없이 도는 것**이
> 먼저다 — 채점은 화면 수가 아니라 *"Mock API를 활용한 실제 데이터 바인딩 및
> 화면 시연"* 을 본다. 시간이 모자라면 3차부터 버린다.

---

## 3일차 — 설계 검증 및 최종 발표

**목표: 15시 발표. 조별 15분 + 질의응답 5분.**

- [ ] 설계 문서 보완 (TBD로 남은 칸 정리)
- [ ] 발표 자료 작성 (Gamma / Canva)
- [ ] Live Demo 리허설 — **한 번은 처음부터 끝까지 돌려 본다**
- [ ] 타 팀 질의 1개 준비 (**필수**)
- [ ] Peer Review 평가서 준비 (조 단위 합의 1부)

### 발표 구성 (총 15분 + Q&A 5분)

| 순서 | 섹션 | 시간 | 근거 문서 |
| --- | --- | --- | --- |
| 1 | 서비스 기획 & Use-Case | 3분 | `01`, `02`, `03` |
| 2 | AI-Ready 설계 포인트 | 2분 | `07` |
| 3 | 시스템 아키텍처 & 설계 | 4분 | `04`, `05`, `06` |
| 4 | Scaffolding & 데모 시연 | 4분 | 저장소, 로컬 데모 |
| 5 | 회고 및 향후 확장 계획 | 2분 | `07` 로드맵, `00-team.md` |
| 6 | Q&A 및 Peer Review | 5분 | — |

> **완성된 앱을 보여주는 자리가 아니다.** "우리 팀의 설계가 얼마나 논리적이고
> 확장성 있는가"를 설득하는 Tech Talk다.

---

## 데모 사고 방지

- [ ] 데모용 시드 데이터를 미리 넣어 둔다 (빈 화면 시연 방지)
- [ ] 인터넷이 끊겨도 되도록 Mock을 **로컬 백엔드**에 둔다
- [ ] 발표 PC에서 한 번 실행해 본다 (`npm install`부터)
- [ ] 백업: 데모 화면 녹화본 준비
