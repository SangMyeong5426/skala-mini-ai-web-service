# skala-mini-ai-web-service

> SKALA Full-Stack Engineering — **AI 웹 서비스 설계 Mini-project** (3일)

**짐을 다 싸고도 뭘 빠뜨렸는지 확신하지 못하는 여행자가, 가방 사진 한 장으로
준비 상태·예상 무게·기내 반입 가능 여부를 한 번에 확인하는 서비스.**

자세한 기획은 [`docs/01-service-plan.md`](docs/01-service-plan.md).

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
├── frontend/               React + TS (Vite)       ← Frontend Developer
│   ├── .env.example
│   └── README.md
├── backend/                Java 21 / Spring Boot 4  ← Backend Developer
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
정의만 두고 데이터 파일은 두지 않는다.

> **DB 프로젝트 생성은 지금 해도 되지만, `schema.sql` 작성·실행은
> 기능 명세·유저플로우·와이어프레임([`docs/01`](docs/01-service-plan.md) ·
> [`02`](docs/02-use-case.md) · [`03`](docs/03-wireframe.md)) 확정 후에 한다.**
> 계정 만들고 연결 문자열을 받는 것은 주제와 무관하지만, 테이블은 화면과
> 유스케이스를 따라가야 하기 때문이다.

## 팀원 온보딩

초대를 받았다면 **1번부터 순서대로** 따라간다. 1~5번은 10분, 6번(도구 설치)은
다운로드 시간에 달렸다.

> 첫 PR을 열기 전에 [`CONTRIBUTING.md`](CONTRIBUTING.md)의 브랜치·제목 규칙을
> 확인한다. **CI가 검사하므로 안 맞으면 PR이 빨간불로 막힌다.**

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
| Data Architect | [`docs/05-erd.md`](docs/05-erd.md) · [`database/`](database/) | `01`~`03` 확정 후 ERD 초안 → `schema.sql` |
| API Architect | [`docs/06`](docs/06-api-spec.md) · [`07`](docs/07-ai-ready.md) | 엔드포인트 목록 초안 |
| Frontend Developer | [`frontend/README.md`](frontend/README.md) · [`docs/03`](docs/03-wireframe.md) | 와이어프레임 작성 → 확정 후 화면·라우팅 |
| Backend Developer | [`backend/README.md`](backend/README.md) · [`ADR 0001`](docs/adr/0001-backend-stack.md) | `./gradlew build` 확인 → `02`·`06` 확정 후 API 구현 |
| DevOps & Integration | [`CONTRIBUTING.md`](CONTRIBUTING.md) · `.github/` | `CODEOWNERS` 채우기 |

### 5. 환경 파일 만들기 (전원)

```bash
cp frontend/.env.example frontend/.env
cp backend/.env.example backend/.env
```

**`.env`는 커밋되지 않는다.** DB 접속 정보와 API 키는 저장소가 아니라
**팀 채널로** 받아서 각자 채운다.

### 6. 개발 도구 설치 (전원)

**역할과 무관하게 5명 전원이 둘 다 설치한다.**

| 도구 | 버전 | 확인 |
| --- | --- | --- |
| Node.js | **20.19+ 또는 22.12+** | `node -v` |
| JDK (Temurin) | **21** | `java -version` |

역할로 나누지 않는 이유는 셋이다. 2일차 FE↔BE 연동은 **양쪽을 동시에 띄워야**
확인이 되고, BE·FE는 [서로 지원하기로](docs/00-team.md) 했으며, 3일차 데모는
누구 PC에서든 돌아가야 한다. 둘 다 합쳐 15분이면 끝난다.

#### JDK 설치

```bash
# macOS
brew install --cask temurin@21

# Windows (PowerShell)
winget install EclipseAdoptium.Temurin.21.JDK
```

설치 프로그램을 직접 받으려면 [adoptium.net](https://adoptium.net)에서 받는다.
설치 후 확인한다.

```bash
java -version   # 21이 나와야 한다
```

#### Node.js 설치

```bash
# macOS
brew install node

# Windows (PowerShell)
winget install OpenJS.NodeJS.LTS
```

직접 받으려면 [nodejs.org](https://nodejs.org). 설치 후 확인한다.

```bash
node -v   # v20.19 이상 또는 v22.12 이상. v21.x 는 안 된다
```

#### JDK를 프로젝트 폴더 안에 넣을 수는 없다

`node_modules`처럼 저장소 안에 두는 방식은 **없다.** 각자 PC에 설치해야 한다.
대신 **버전을 프로젝트가 강제하게** 만들 수는 있고, 그렇게 해 둔다.

`start.spring.io`가 만들어 주는 `build.gradle`에 아래가 이미 들어 있다. **지우지 않는다.**

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

이러면 누가 JDK 17이나 25를 깔았어도 **컴파일은 21 기준으로** 돈다. 팀원 간 버전이
어긋나서 나는 오류가 사라진다.

`settings.gradle`에 아래를 더하면, 맞는 버전이 없을 때 Gradle이 자동으로 받아 온다.

```gradle
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}
```

> **그래도 JDK 설치를 건너뛸 수는 없다.** `./gradlew` 자체가 자바로 돌기 때문에
> Gradle을 띄울 JVM이 먼저 있어야 한다. toolchain은 *컴파일 버전을 맞춰 주는 것*이지
> *설치를 대신해 주는 것*이 아니다.

IntelliJ IDEA를 쓴다면 `File → Project Structure → SDKs → Download JDK`로 IDE가
받아 주게 할 수도 있다. 단 터미널에서 `./gradlew`를 쓰려면 시스템에도 있어야 한다.

### 막혔을 때

| 증상 | 해결 |
| --- | --- |
| `./scripts/setup-git-hooks: Permission denied` | Git Bash에서 `sh scripts/setup-git-hooks` |
| `bad interpreter: /bin/sh^M` | 줄바꿈 문제. 다시 clone하면 `.gitattributes`가 잡아 준다 |
| push할 때 `main 브랜치로 직접 push할 수 없습니다` | **정상 동작이다.** 작업 브랜치를 만들고 PR로 올린다 |
| PR에서 `Invalid branch name` | 브랜치 이름 규칙 위반. 규칙에 맞는 새 브랜치를 만들어 옮긴다 |
| PR에서 `PR title must follow Conventional Commits` | PR 제목을 `type(scope): 요약`으로 고친다. 제목만 고치면 자동 재검사된다 |
| FE에서 API 호출이 CORS 오류 | 백엔드 `.env`의 `CORS_ALLOWED_ORIGINS`에 `http://localhost:5173`이 있는지 확인 |
| 서버가 `.env`가 없다고 뜸 | 5번 단계를 건너뛴 것이다 |
| `./gradlew`가 `JAVA_HOME` 오류 | JDK 미설치 또는 경로 미설정. 6번 단계 |
| Gradle이 "Could not find a Java installation" | toolchain이 요구하는 21이 없다. 6번 단계 |
| 30분 이상 막힘 | **팀 채널에 올린다.** 3일 중 30분은 크다 |

## 로컬 실행

**스캐폴딩은 끝나 있다.** clone하고 6단계(도구 설치)까지 마치면 둘 다 뜬다.

| | 실행 | 포트 | 비고 |
| --- | --- | --- | --- |
| Frontend | `cd frontend && npm install && npm run dev` | 5173 | Vite 8 · React 19 · TS 6 |
| Backend | `cd backend && ./gradlew bootRun` | 8080 | `.env`의 `DATABASE_URL`이 있어야 뜬다 |

`cd backend && ./gradlew build`는 **DB가 없어도 통과한다** (테스트는 인메모리 H2로 돈다).
Swagger UI는 <http://localhost:8080/swagger-ui.html>.

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
| 서비스 아이디어 | [`docs/01-service-plan.md`](docs/01-service-plan.md) | 1일차 |
| 데이터베이스 (Supabase / Neon) | — | 1일차 |

## 확정된 결정

| 항목 | 결정 | 문서 |
| --- | --- | --- |
| 백엔드 스택 | Java 21 / Spring Boot 4.1.1 · 포트 `8080` | [ADR 0001](docs/adr/0001-backend-stack.md) |
| 프런트엔드 스택 | React + TypeScript (Vite) · 포트 `5173` | [ADR 0002](docs/adr/0002-frontend-stack.md) |
| AI 작업 엔드포인트 | `POST /api/ai-jobs` 하나 + `job_type` | [ADR 0003](docs/adr/0003-ai-job-endpoint.md) |
| 사진 저장 | 로컬 디렉터리 (`UPLOAD_DIR`) — 데모가 네트워크에 묶이지 않게 | `backend/.env.example` |
| 날씨 | Open-Meteo · **API 키 불필요** · 16일 예보 + 계절 예보 7개월 | `backend/.env.example` |
| 인증 | 구현하지 않음. 시드 사용자 1명 고정 (채점 항목 아님) | — |

> React 사용 가능 여부는 1일차에 확인했다. 사용해도 된다.
