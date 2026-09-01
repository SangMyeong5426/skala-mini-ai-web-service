# 3일 로드맵 체크리스트

> PM이 관리한다. 매일 아침 한 번, 저녁 한 번 확인한다.

## 1일차 — 서비스 기획 & Architecture 정의

**목표: AI 기능이 들어올 자리를 미리 비워 둔 웹 서비스를 기획한다.**

### 기획

- [ ] AI-Ready Web Service 아이디어 선정 → `01-service-plan.md`
- [ ] 페르소나와 해결할 문제 정의 → `01-service-plan.md`
- [ ] Teaming, R&R 정의 → `00-team.md`
- [ ] Actor 중심 Use-Case 정의 → `02-use-case.md`
- [ ] **AI 확장 지점 정의** → `02-use-case.md`, `07-ai-ready.md`
- [ ] UI/UX 화면 흐름도(Wireframe) 작성 → Figma, `03-wireframe.md`

### 개발환경 세팅

- [x] 저장소 구조 및 협업 설정 (이 저장소)
- [ ] GitHub 저장소 생성 및 push
- [ ] 팀원 전원 Collaborator 초대
- [ ] 전원 clone 후 `./scripts/setup-git-hooks` 실행
- [ ] `.github/CODEOWNERS`에 실제 GitHub ID 반영
- [ ] **백엔드 스택 결정** → [ADR 0001](adr/0001-backend-stack.md)
- [ ] FE 프로젝트 생성 (Vue 3 + Vite)
- [ ] BE 프로젝트 생성 (ADR 0001 확정 후)
- [ ] DB 생성 (Supabase 또는 Neon) 및 접속 정보 팀 공유 (**저장소 밖에서**)

---

## 2일차 — 시스템 설계 및 Scaffolding

**목표: 데이터 모델·API 명세 확정, 핵심 화면과 데이터 흐름 End-to-End 검증.**

- [ ] 데이터 모델링(ERD) → `05-erd.md`, dbdiagram.io
- [ ] 스키마 SQL 작성 및 DB에 실행 → `database/schema.sql`
- [ ] 데모용 시드 데이터 작성 → `database/seed.sql`
- [ ] REST API 명세 작성 → `06-api-spec.md`
- [ ] **Mock API 엔드포인트 포함** → `06-api-spec.md`
- [ ] AI 입출력 JSON Schema 확정 → `07-ai-ready.md`
- [ ] 프롬프트 설계 및 Playground 검증 → `07-ai-ready.md`
- [ ] FE ↔ BE API 연동
- [ ] BE ↔ DB 연결
- [ ] 시스템 아키텍처 다이어그램 완성 → `04-architecture.md`
- [ ] **핵심 화면 1~2개 구현** (Mock API 호출 → 데이터 렌더링)
- [ ] End-to-End 흐름 검증

> 화면을 늘리지 않는다. **1~2개가 끊김 없이 도는 것**이 채점 대상이다.

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
| 1 | 서비스 기획 & Use-Case | 3분 | `01`, `02` |
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
