# skala-ai-web-service

> SKALA Full-Stack Engineering — **AI 웹 서비스 설계 Mini-project** (3일)

TBD — 서비스 한 줄 정의. 확정되면 [`docs/01-service-plan.md`](docs/01-service-plan.md)에서 옮겨 온다.

## 이 프로젝트가 무엇인가

**지금 AI 기능을 만드는 프로젝트가 아니다.** 나중에 AI가 들어와도 구조를 다시
설계하지 않아도 되는 웹 서비스, 즉 **AI-Ready Web Service를 설계**하는 것이 목표다.

3일차 발표는 완성된 앱을 보여주는 자리가 아니라, **"우리 팀의 설계가 얼마나
논리적이고 확장성 있는가"를 설득하는 Tech Talk**다. 그래서 이 저장소의 주된
산출물은 코드가 아니라 [`docs/`](docs/)의 설계 문서다.

### AI-Ready 4대 원칙

| 원칙 | 뜻 | 이 저장소에서 |
| --- | --- | --- |
| **Interface First** | BE가 실제 AI를 부르게 바뀌어도 FE는 그대로 | [`docs/06-api-spec.md`](docs/06-api-spec.md) |
| **Structured Data** | AI가 읽기 쉬운 JSON 규격을 DB에 미리 반영 | [`docs/05-erd.md`](docs/05-erd.md) |
| **Asynchronous Pipeline** | AI는 느리다. 비동기 + 상태관리 구조를 미리 | [`docs/06-api-spec.md`](docs/06-api-spec.md) |
| **Security & Config Isolation** | API 키·모델 파라미터를 코드에서 분리 | `backend/.env.example` |

## 저장소 구조

```text
skala-ai-web-service/
├── frontend/               Vue 3 + Vite            ← Frontend Developer
│   ├── .env.example
│   └── README.md
├── backend/                스택 미정 (ADR 0001)     ← Backend Developer
│   ├── .env.example
│   └── README.md
├── database/               PostgreSQL 스키마         ← Data Architect
│   ├── schema.sql              테이블 정의(DDL)
│   ├── seed.sql                데모용 초기 데이터
│   └── README.md
├── docs/                   설계 문서 — 주요 산출물
│   ├── 00-team.md              팀 구성과 R&R
│   ├── 01-service-plan.md      서비스 기획, 페르소나
│   ├── 02-use-case.md          Use-Case, AI 확장 지점
│   ├── 03-wireframe.md         화면 흐름도
│   ├── 04-architecture.md      시스템 아키텍처
│   ├── 05-erd.md               데이터 모델링
│   ├── 06-api-spec.md          REST API 명세 (FE↔BE 계약)
│   ├── 07-ai-ready.md          프롬프트와 입출력 JSON Schema
│   ├── adr/                    기술 결정 기록
│   └── checklist.md            3일 로드맵
├── .github/                Issue·PR 템플릿, PR 규칙 검사
├── .githooks/              main 직접 push 차단
└── scripts/setup-git-hooks clone 후 한 번 실행
```

데이터베이스 서버는 클라우드(Supabase 또는 Neon)에 둔다. 저장소에는 스키마
정의만 두고 데이터 파일은 두지 않는다. `database/`는 백엔드 스택과 무관하므로
ADR 0001 확정 전에도 작업할 수 있다.

## 시작하기

저장소: <https://github.com/SangMyeong5426/skala-mini-ai-web-service>

```bash
git clone https://github.com/SangMyeong5426/skala-mini-ai-web-service.git
cd skala-mini-ai-web-service
./scripts/setup-git-hooks
```

Windows에서 마지막 줄이 실행되지 않으면 Git Bash에서 `sh scripts/setup-git-hooks`.

**`setup-git-hooks`를 실행하지 않으면 `main` 보호가 동작하지 않는다.**
팀원 전원이 clone 직후 한 번 실행한다.

협업 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md)에 있다. **한 번은 읽는다.**

### 실행

각 워크스페이스가 아직 비어 있다. 스캐폴딩 후 아래를 채운다.

| | 실행 | 포트 |
| --- | --- | --- |
| Frontend | `cd frontend && npm run dev` | 5173 |
| Backend | TBD | TBD |

## 진행 상황

[`docs/checklist.md`](docs/checklist.md)에서 관리한다.

| 일차 | 목표 | 상태 |
| --- | --- | --- |
| 1일차 | 서비스 기획 & Architecture 정의 | 진행 중 |
| 2일차 | 시스템 설계 및 Scaffolding | — |
| 3일차 | 설계 검증 및 최종 발표 (15:00) | — |

## 팀

[`docs/00-team.md`](docs/00-team.md) 참조. R&R 분담도 채점 대상이다.

## 결정 대기 중

| 항목 | 문서 | 기한 |
| --- | --- | --- |
| 백엔드 스택 (FastAPI / Spring Boot) | [ADR 0001](docs/adr/0001-backend-stack.md) | 1일차 |
| 서비스 아이디어 | [`docs/01-service-plan.md`](docs/01-service-plan.md) | 1일차 |
| 데이터베이스 (Supabase / Neon) | — | 1일차 |
