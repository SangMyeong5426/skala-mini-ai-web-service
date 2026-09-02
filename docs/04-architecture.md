# 시스템 아키텍처

> 발표 3번 섹션 (4분): 전체 시스템 아키텍처 다이어그램
>
> 채점 기준: **"FE-BE-DB 전체 시스템 구조 다이어그램의 명확성"**

## 전체 구조

```text
┌─────────────────┐         ┌──────────────────────┐         ┌───────────────┐
│   Frontend      │  HTTP   │      Backend         │   SQL   │  PostgreSQL   │
│   React + TS    │ ──────▶ │      Spring Boot 3   │ ──────▶ │  Supabase/Neon│
│                 │  JSON   │                      │         │   (Cloud)     │
│  - 라우팅        │ ◀────── │  - REST Controller   │ ◀────── │               │
│  - 화면 컴포넌트  │         │  - 서비스 로직        │         │  - 테이블      │
│  - API 클라이언트 │         │  - Repository        │         │  - 관계        │
└─────────────────┘         └──────────┬───────────┘         └───────────────┘
                                       │
                                       │ ◀─── AI 확장 지점 (지금은 비어 있음)
                                       ▼
                            ┌──────────────────────┐
                            │   Mock AI Service    │   지금: 고정 JSON 반환
                            │   ─────────────────  │
                            │   나중: OpenAI /     │   나중: 같은 스키마로
                            │        Claude API    │        LLM 응답 반환
                            └──────────────────────┘
                                 이 상자 안만 교체된다
```

> **다이어그램의 핵심 메시지는 오른쪽 아래 상자다.** "AI가 들어올 자리를 미리
> 비워 뒀고, 그 상자 안만 교체하면 된다"는 것이 이 프로젝트의 주제다.
> 발표 때 이 한 장으로 설명할 수 있어야 한다.

> 확정된 다이어그램은 `images/04-architecture.png`로 저장하고 여기서
> 참조한다. 내보내는 방법은 [`images/README.md`](images/README.md).

## 기술 스택

| 계층 | 기술 | 선택 근거 |
| --- | --- | --- |
| Frontend | React + TypeScript (Vite) | 팀원 사전 경험이 여기 있어 학습 시간을 아낀다. TS 타입이 `06-api-spec.md`의 응답 규격과 1:1로 대응해 **Interface First를 코드로 증명**한다. [ADR 0002](adr/0002-frontend-stack.md) |
| Backend | Java 21 / Spring Boot 3 | Controller-Service-Repository 계층이 강제돼 5명이 짜도 구조가 흩어지지 않는다. JPA 엔터티가 ERD와 1:1로 대응한다. [ADR 0001](adr/0001-backend-stack.md) |
| Database | PostgreSQL (Supabase 또는 Neon) | 로컬 설치 없이 클라우드에서 즉시 생성, 팀원 전원이 같은 DB 공유 |
| Mock API | TBD (Postman Mock Server 또는 백엔드 내 Mock 응답) | TBD |
| 형상관리 | GitHub (모노레포) | FE·BE·문서를 한 저장소에서 관리, 초대·클론 1회 |

## AI-Ready 설계 적용 지점

PDF가 제시한 4대 원칙을 이 구조가 각각 어디서 만족하는지 적는다.
**발표에서 가장 많이 질문받는 부분이다.**

| 원칙 | 이 프로젝트에서의 적용 | 확인 위치 |
| --- | --- | --- |
| **Interface First** | FE는 Mock API의 JSON 규격만 보고 개발한다. BE가 나중에 실제 LLM을 호출해도 응답 스키마가 같으므로 FE는 수정하지 않는다. | `06-api-spec.md` |
| **Structured Data** | AI 결과를 저장할 테이블에 JSON 컬럼과 메타데이터(모델명, 생성시각, 상태)를 미리 둔다. 변환 레이어가 필요 없다. | `05-erd.md` |
| **Asynchronous Pipeline** | AI 호출 엔드포인트는 즉시 `202 Accepted` + 작업 ID를 반환하고, 상태(`PENDING`/`COMPLETED`/`FAILED`)를 조회하는 엔드포인트를 따로 둔다. | `06-api-spec.md` |
| **Security & Config Isolation** | API 키·모델명·temperature를 코드가 아닌 `.env`에서 읽는다. 저장소에는 `.env.example`만 올린다. | `backend/.env.example` |

## 로컬 실행 구성

| 구성요소 | 포트 | 실행 방법 |
| --- | --- | --- |
| Frontend | 5173 | `cd frontend && npm run dev` |
| Backend | 8080 | `cd backend && ./gradlew bootRun` |
| Database | — | 클라우드. `backend/.env`의 `DATABASE_URL`로 접속 |

> 프런트엔드와 백엔드 포트가 다르므로 **CORS 설정이 필요하다.** 백엔드
> 스캐폴딩 때 개발용 origin(`http://localhost:5173`)을 허용해 둔다.
> 2일차에 FE-BE 연동이 막히는 가장 흔한 원인이다.
