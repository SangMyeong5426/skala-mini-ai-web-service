# skala-mini-ai-web-service

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
skala-mini-ai-web-service/
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
├── CONTRIBUTING.md         협업 규칙 — 사람이 읽는다
├── AGENTS.md               에이전트 작업 규칙 — Codex·Cursor 등이 읽는다
├── CLAUDE.md               AGENTS.md와 같은 내용. Claude Code가 읽는다
├── .github/                Issue·PR 템플릿, PR 규칙 검사
├── .githooks/              main 직접 push 차단
└── scripts/setup-git-hooks clone 후 한 번 실행
```

데이터베이스 서버는 클라우드(Supabase 또는 Neon)에 둔다. 저장소에는 스키마
정의만 두고 데이터 파일은 두지 않는다. `database/`는 백엔드 스택과 무관하므로
ADR 0001 확정 전에도 작업할 수 있다.

## 팀원 온보딩

초대를 받았다면 **1번부터 순서대로** 따라간다. 전부 15분이면 끝난다.

### 1. 저장소 받고 훅 켜기

```bash
git clone https://github.com/SangMyeong5426/skala-mini-ai-web-service.git
cd skala-mini-ai-web-service
./scripts/setup-git-hooks
```

Windows에서 마지막 줄이 실행되지 않으면 **Git Bash**를 열고
`sh scripts/setup-git-hooks`를 실행한다. PowerShell·CMD에서는 동작하지 않는다.

성공하면 아래 두 줄이 출력된다.

```text
Git hooks 활성화: .githooks
커밋 템플릿 활성화: .gitmessage
```

**이 단계를 건너뛰면 `main` 보호가 동작하지 않는다.** 실수로 `main`에 직접
push하면 되돌리기 번거롭다. 전원이 반드시 한 번 실행한다.

### 2. 내 역할 확인

[`docs/00-team.md`](docs/00-team.md)에서 자기 R&R을 확인한다.
**R&R 분담 자체가 채점 항목이다.** 비어 있는 역할이 있으면 팀에 알린다.

### 3. 규칙 읽기 (5분)

[`CONTRIBUTING.md`](CONTRIBUTING.md)를 한 번 읽는다. 브랜치 이름과 PR 제목에
규칙이 있고 **CI가 자동으로 검사한다.** 안 맞으면 PR이 빨간불로 막힌다.

AI 코딩 에이전트를 쓴다면 규칙이 이미 저장소에 들어 있어서 따로 설정할 것이
없다. [`AGENTS.md`](AGENTS.md)를 Codex·Cursor 등이, [`CLAUDE.md`](CLAUDE.md)를
Claude Code가 읽는다. **두 파일은 같은 내용이고, 한쪽을 고치면 다른 쪽도 함께
고쳐야 한다** — 안 맞으면 `Docs Sync` 검사가 실패한다.

### 4. 내 역할 문서 읽기

| 역할 | 먼저 읽을 것 | 첫 작업 |
| --- | --- | --- |
| PM | [`docs/checklist.md`](docs/checklist.md) | 3일 일정 점검, Issue 생성 |
| Product/UX Designer | [`docs/01`](docs/01-service-plan.md) · [`02`](docs/02-use-case.md) · [`03`](docs/03-wireframe.md) | 아이디어·페르소나 정리 |
| Data Architect | [`docs/05-erd.md`](docs/05-erd.md) · [`database/`](database/) | ERD 초안, `schema.sql` |
| API Architect | [`docs/06`](docs/06-api-spec.md) · [`07`](docs/07-ai-ready.md) | 엔드포인트 목록 초안 |
| Frontend Developer | [`frontend/README.md`](frontend/README.md) · [`docs/03`](docs/03-wireframe.md) | Vue 프로젝트 생성 |
| Backend Developer | [`backend/README.md`](backend/README.md) · [`ADR 0001`](docs/adr/0001-backend-stack.md) | 스택 결정 후 프로젝트 생성 |
| DevOps & Integration | [`CONTRIBUTING.md`](CONTRIBUTING.md) · `.github/` | `CODEOWNERS` 채우기 |

### 5. 연습 PR 한 번 (중요)

**첫날에 각자 PR을 한 번 만들어 본다.** 규칙이 실제로 어떻게 도는지 미리
겪어 보는 것이 목적이다. 2일차에 처음 PR을 열었다가 브랜치 이름 규칙에
막히면 그때는 고칠 시간이 없다.

연습이지만 버리는 작업은 아니다. **위 표의 "첫 작업"에서 자기 담당 문서의
`TBD` 한 칸을 채우는 것**으로 한다. 역할마다 파일이 달라 충돌하지 않는다.

```bash
git switch -c docs/my-first-change
# 자기 담당 문서 수정
git add -A
git commit -m "docs(fe): 담당자 정보 기입"
git push -u origin docs/my-first-change
```

GitHub에서 PR을 만들고 **`PR Policy` 검사가 초록불인지** 확인한 뒤 merge한다.

> 브랜치는 `<type>/<소문자-kebab-case>`, 커밋·PR 제목은 `type(scope): 요약`.
> 자세한 목록은 [`CONTRIBUTING.md`](CONTRIBUTING.md)에 있다.

### 6. 환경 파일 만들기 (Frontend·Backend 담당자만)

```bash
cp frontend/.env.example frontend/.env
cp backend/.env.example backend/.env
```

**`.env`는 커밋되지 않는다.** DB 접속 정보와 API 키는 저장소가 아니라
**팀 채널로** 받아서 각자 채운다.

### 막혔을 때

| 증상 | 해결 |
| --- | --- |
| `./scripts/setup-git-hooks: Permission denied` | Git Bash에서 `sh scripts/setup-git-hooks` |
| `bad interpreter: /bin/sh^M` | 줄바꿈 문제. 다시 clone하면 `.gitattributes`가 잡아 준다 |
| push할 때 `main 브랜치로 직접 push할 수 없습니다` | **정상 동작이다.** 작업 브랜치를 만들고 PR로 올린다 |
| PR에서 `Invalid branch name` | 브랜치 이름 규칙 위반. 규칙에 맞는 새 브랜치를 만들어 옮긴다 |
| PR에서 `PR title must follow Conventional Commits` | PR 제목을 `type(scope): 요약`으로 고친다. 제목만 고치면 자동 재검사된다 |
| FE에서 API 호출이 CORS 오류 | 백엔드 `.env`의 `CORS_ALLOWED_ORIGINS`에 `http://localhost:5173`이 있는지 확인 |
| 서버가 `.env`가 없다고 뜸 | 6번 단계를 건너뛴 것이다 |
| 30분 이상 막힘 | **팀 채널에 올린다.** 3일 중 30분은 크다 |

## 로컬 실행

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
