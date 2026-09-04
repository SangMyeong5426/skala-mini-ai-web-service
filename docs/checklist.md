# 3일 로드맵 체크리스트

> PM이 관리한다. 매일 아침 한 번, 저녁 한 번 확인한다.

## 2026-09-03 로그인 필수 결정

- [x] 기능정의서 작성, 가입 4개 필드·로그인 2개 필드와 서비스 전체 인증 설계 반영
- [x] 01~07·Use-Case·User Flow·아키텍처·목표 ERD에 인증·소유권 반영
- [x] 정적 교차 검증 — 화면 11개·API 30개·가입 4개/로그인 2개 필드, PNG·SVG 4쌍, 기존 AI 스키마 8개·예시 12개 확인
- [x] S-00 가입·로그인 폼, FE 세션 가드·로그아웃·CSRF·401 처리 구현
- [x] BE 회원가입·서버 세션·비밀번호 해시·소유권 검사·사진 파일 보호 구현
- [x] users.login_id의 SQL·시드·JPA 반영 및 PostgreSQL 17.6 스키마 검증
- [x] 06 인증 수용 기준 실행 — 미인증 차단·계정 간 접근 차단·만료/로그아웃·CSRF 자동 E2E 통과

## 2026-09-03 체크리스트 흐름 개정

- [x] 사진 사전 승인 제거: BAG_CHECK 완료 시 자동 등록, S-04는 선택적 사후 수정
- [x] 추천은 사진 자동 등록 후 현재 내 목록을 제외해 생성하고 사용자 선택·승인분만 추가
- [x] 문서 정합성 검증 — 인식 8개 모두 목록·추천 입력·무게 입력에 포함, LOW 물품 포함, 완료율 8/9=0.889(89%), 무게 정보 없는 2개는 NO_WEIGHT_INFO로 구분
- [x] 완료 작업 재처리·일부 사진 실패·사용자 수정 보존·낮은 신뢰도 자동 등록 수용 기준 실행

- [x] 개인 Notion을 바탕으로 한 최신 사용자 결정의 사진 자동 등록·추천 채택·실제 완료 구분을 `01`~`07` MD에 반영
- [x] 내 목록·후보 저장 경계, 기존 API의 추천 참조, 반복 승인 방지 규약 작성
- [x] 추천 이유·날씨 시점·채택 연결을 AI 스키마와 API 예시에 반영
- [ ] 팀 개정안·개인 개정안의 관계와 팀 정본 지정, 원본 오타 교정 여부 확인 → 02
- [ ] PM 확인 — 프롬프트 설계 완료 여부와 Playground 검증 일정 (아래 두 항목)
- [x] PNG·PUML·SVG 4종 재생성 — MD와 자동 등록·추천 채택·완료·필수 후보 경고·사진 재확인 경로 대조, ERD 구조 유지
- [x] FE 타입·S-04~S-06와 BE 자동 등록·추천 채택·무게 Mock에 개정 계약 반영
- [x] 기존 시드를 개정 상태로 갱신 — 사진 인식 8개 자동 완료·미채택 여권 1개, 추천 후보 사전 등록 제거
- [x] 07의 사진 자동 등록·추천 승인 수용 기준 실행 — 자동 등록·선택분 추가·재승인·무게 재계산 테스트와 실제 OpenAI HTTP E2E 통과

아래 완료 표시는 기존 작업 시점의 기록이다. 개정 기능의 구현·E2E 완료로 대신하지 않는다.

## 1일차 — 서비스 기획 & Architecture 정의

**목표: AI 기능이 들어올 자리를 미리 비워 둔 웹 서비스를 기획한다.**

### 기획

- [x] AI-Ready Web Service 아이디어 선정 → [`01-service-plan.md`](01-service-plan.md) — 한 줄 정의·선정 근거 표 작성 완료
- [x] 페르소나와 해결할 문제 정의 → [`01-service-plan.md`](01-service-plan.md) — 1차 김지우 · 2차 이준호 · 확장 2명
- [x] Teaming, R&R 정의 → [`00-team.md`](00-team.md) — 7개 역할 전부 채움
- [x] Actor 중심 Use-Case 정의 → [`02-use-case.md`](02-use-case.md) — UC-01~10, Actor 4종
- [x] **AI 확장 지점 정의** → [`02-use-case.md`](02-use-case.md) — 4개 + UC-07 부분 연동
- [x] AI 입출력 스키마 → [`07-ai-ready.md`](07-ai-ready.md) — `jobType` 4종 입·출력 Schema. 개정 예시 검증과 구현 검증을 구분
- [x] User Flow · 화면 목록 작성 → [`03-wireframe.md`](03-wireframe.md) — S-00~10 (인증 1개 + 업무 10개)
- [ ] **Figma 와이어프레임** — 1차 `S-00`~`S-06` 은 정성껏, 나머지는 스케치

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
- [x] DB 프로젝트 생성 — Supabase (PostgreSQL 17.6 · `ap-south-1`). 스키마·시드 적용 완료
- [ ] 접속 정보 팀 공유 (**저장소 밖에서**) — `DATABASE_URL`·`DATABASE_USERNAME` 은 그대로, 비밀번호만 따로. 받는 쪽은 `./scripts/db-password` → `./scripts/check-db`
- [x] 전원 `npm run dev` · `./gradlew build` 한 번씩 돌려 보기 — 2026-09-03 완료

---

## 2일차 — 시스템 설계 및 Scaffolding

**목표: 데이터 모델·API 명세 확정, 핵심 화면과 데이터 흐름 End-to-End 검증.**

- [x] 데이터 모델링(ERD) → [`05-erd.md`](05-erd.md) — 10테이블 · **N:M 2개**
- [x] 스키마 SQL 작성 → [`database/schema.sql`](../database/schema.sql) — 로컬 DB에서 실행·제약 검증 완료
- [x] 클라우드 DB(Supabase)에 실행 — 2026-09-03 `./scripts/db-apply`. `source` 에 `PHOTO` 추가와 `detected_objects` 의 `missing_info`·`label_text` 까지 반영 확인
- [x] 데모용 시드 데이터 작성 → [`database/seed.sql`](../database/seed.sql) — 도쿄 3박4일 시나리오
- [x] REST API 명세 작성 → [`06-api-spec.md`](06-api-spec.md) — 22엔드포인트 (기존 업무 18 + 인증 4) · **Status Code 전부 표기**
- [x] **Mock API 엔드포인트 포함** → `POST /api/ai-jobs` + `GET /api/ai-jobs/{jobId}` · `jobType` 4종
- [x] AI 입출력 JSON Schema 확정 → [`07-ai-ready.md`](07-ai-ready.md) — 06 예시·`schema.sql` enum 과 기계 대조
- [ ] 프롬프트 설계 완료 확인 → [`07-ai-ready.md`](07-ai-ready.md) — 작성됨, PM 완료 확인 TBD
- [ ] Playground 검증 → [`07-ai-ready.md`](07-ai-ready.md) — 미실행 TBD. 앱 경로의 OpenAI 스모크·HTTP E2E는 별도로 통과
- [x] FE ↔ BE API 연동 — `VITE_USE_MOCK=false`, 브라우저 세션 로그인·여행 목록·사진 화면 확인
- [x] BE ↔ DB 연결 — H2 전체 API 테스트와 PostgreSQL 17.6 `ddl-auto=validate` 기동 확인
- [x] 시스템 아키텍처 다이어그램 완성 → [`04-architecture.md`](04-architecture.md) — PlantUML 원본 + PNG · SVG
- [x] **화면 구현 1차** — `S-00`~`S-06` 데모 주 경로
- [x] 화면 구현 2차 — `S-07` 무게 상세 · `S-08` 반입 규정 상세
- [x] 화면 구현 3차 — `S-09` 챗봇 · `S-10` 여행 기록 상세
- [x] End-to-End 흐름 검증 — 실제 OpenAI 사진 분석·자동 등록, 챗봇 Mock 분리까지 확인

> 팀 결정으로 **화면 11개 전체(S-00 인증 포함)**를 만든다. 다만 **필수 인증 포함 1차 7개가 끊김 없이 도는 것**이
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

- [x] 데모용 시드 데이터를 미리 넣어 둔다 (빈 화면 시연 방지) — 2026-09-03 Supabase 적용. 여행 1 · 체크리스트 10(`PHOTO` 5 · `AI` 4 · `RULE` 1) · 인식 물품 8 · N:M 8/4
- [x] 인터넷이 끊겨도 되도록 Mock을 **로컬 백엔드**에 둔다
- [x] 실제 연동 시 `frontend/.env`의 `VITE_USE_MOCK=false` 확인
- [x] OpenAI `gpt-4o-mini` 사진·추천·여행 검수 스모크 + H2 사진 업로드 → 자동 등록 → 추천 후보 E2E
- [ ] 발표 PC에서 한 번 실행해 본다 (`npm install`부터)
- [ ] 백업: 데모 화면 녹화본 준비
