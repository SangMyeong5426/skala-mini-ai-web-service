# 설계 문서

Mini-project의 산출물은 코드가 아니라 **설계 문서**다. 정량 평가 60점 중 대부분이
이 폴더의 내용으로 채점된다. 파일 이름의 번호는 작성 순서이자 발표 순서다.

## 개정안 반영 상태

**로그인 최종 결정 반영:** [기능정의서](functional-specification.md)를 작성하고 모든 서비스 이용 전
로그인하도록 01~07·다이어그램을 개정했다. 가입은 닉네임·아이디·비밀번호·이메일, 로그인은
아이디·비밀번호다. S-00 인증 화면을 더해 총 11개 화면, 인증 4개를 더해 총 22개 JSON API를 설계한다.
AI는 기본값이 Mock이다 — `AI_PROVIDER=openai` 로 바꿨을 때만 `BAG_CHECK`(사진 인식)이 실제 OpenAI 를 부르고, 나머지 3종은 그때도 Mock 이다([07](07-ai-ready.md#모델-파라미터)). 사진 자동 등록은 BAG_CHECK 완료 처리에 포함하고 S-04 PATCH는 선택적 사후 수정으로 바꾼다. 무게 제외 사유 PENDING_APPROVAL은 제거했다. 이전 DTO·Mock·시드는 후속 반영이 필요하다.


**2026-09-03:** 사용자 요청에 따라 [개인 Notion 기능 정의 개정안](https://app.notion.com/p/3d0c2ab24ce881d9b06cc065c47b1eb7)의
F-04~F-06의 사진 우선 흐름을 반영하고, 최신 사용자 결정으로 사진 사전 승인을 제거했다. 기준은 **사진 인식 → 승인 없이 내 목록 자동 완료 등록 → 별도 추천
→ 선택분만 미완료 추가 → 실제 준비 완료 확인**이다.

이는 사용자 요청에 따른 이번 PR의 적용 기준이다. 기존 [팀 개정안](https://app.notion.com/p/3d0a9fc8c93880348410cc06f2eb5e50)도
유지하며, 두 페이지의 동일성·팀 정본 지정·동기화 여부는 **팀 확인 TBD**다.
조회 결과와 기존 수정 기록·원본 오타 작업은 [02의 출처 구분](02-use-case.md#원본-명세의-불일치와-팀-확정-사항)에 남긴다.

| 구분 | 상태 |
| --- | --- |
| 서비스·UC·화면·아키텍처·저장 규약·API·AI 스키마 | 개정 흐름 반영. 완료율은 내 목록 항목 수 기준, 무게는 실제 완료 항목만 포함 |
| 리뷰 반영 | 완료율 응답·표시 반올림 통일, 미채택 필수 후보 경고, S-06 → S-04 재확인 경로. 추천 입력의 실제 완료 목록은 서버 값으로 보정 |
| DB 테이블·컬럼 | **12개 테이블 + `users.login_id`.** 셋 다 `schema.sql`·시드에 **적용 완료**이고 팀 DB 용 마이그레이션 파일이 [`database/migrations/`](../database/migrations/)에 있다. 후보·채택 연결은 기존 JSON 필드 사용 |
| 다이어그램 | 4종 PNG·PUML·SVG를 로그인 포함 목표 설계로 재생성. S-00 진입·인증/소유권·로그인 아이디와 사진 자동 등록·추천 선택 흐름 반영. `05-erd`는 새 표 둘까지 포함해 다시 렌더했다 |
| 실제 기능 구현 | **BE 는 API 30개를 전부 구현했다** — 업무 26개 + 인증 4개. 로그인 필수·서버 세션·CSRF·소유권 검증과 사진 물품 자동 등록이 개정 계약대로 들어갔다. `/uploads/**` 도 소유권을 확인한다. FE는 화면 자리·공통 타입·브라우저 Mock 단계이고 **개정 계약에 맞춘 화면 연결이 다음 작업**이다 |
| 기존 시드 | 이전 합성 목록. 사진 물품 자동 등록·추천 채택 시점이 보장되는 시나리오로 갱신 필요 |

후속 구현에서는 06·07에 맞춰 `frontend/src/types/api.ts`와 S-04~S-06, 자동 등록·추천 채택·무게 Mock을
함께 맞춘다. `main`의 PR #38로 추가된 `frontend/src/api/mock.ts`는 추천 완료 시 자동 등록과
사진 인식 물품의 `extra` 분류 등 이전 계약을 사용하므로 `fixtures.ts`와 함께 갱신해야 한다.
기존 FE의 `SHIP`, 목적 `ETC` 등 문서·SQL과 다른 값과 로그인 경로도 확인 대상이다.
**이번 문서 반영 요청으로 기존 코드를 임의 변경하지 않았다.**
로그인 필수 정책은 확정됐다. 구현 시 `frontend/src/routes.tsx`의 인증 가드·S-00 폼,
`api/client.ts`의 쿠키·CSRF·401 처리, BE 인증·세션·소유권 검증과 공개 `UploadConfig`를 함께 고친다.
현재 로그인 경로·Mock·시드의 존재가 인증 구현을 의미하지 않는다. SQL·시드·JPA에 login_id를
반영하고 가입·계정 간 접근 차단을 검증해야 한다.

기존 “검증 완료” 체크는 당시 버전의 기록이다. 개정 흐름의 E2E 수용 기준과 이미지 갱신 상태는
[`checklist.md`](checklist.md)에서 별도로 추적한다.

## 문서 지도

| 문서 | 내용 | 주 담당 (R&R) | 관련 배점 |
| --- | --- | --- | --- |
| [`functional-specification.md`](functional-specification.md) | 사용자 제공 Notion 본문 + 로그인 필수 최종 결정 | PM·전원 | 서비스 범위·설계 근거 |
| [`00-team.md`](00-team.md) | 팀 구성과 R&R 분담 | PM | 기획 30점 |
| [`01-service-plan.md`](01-service-plan.md) | 서비스 한 줄 정의, 페르소나, 해결할 문제 | Product/UX | 기획 30점 |
| [`02-use-case.md`](02-use-case.md) | Actor 중심 Use-Case, AI 확장 지점 표시 | Product/UX | 기획 30점 |
| [`03-wireframe.md`](03-wireframe.md) | 화면 흐름도와 Figma 링크 | Product/UX | 기획 30점 |
| [`04-architecture.md`](04-architecture.md) | FE-BE-DB 전체 시스템 구조 다이어그램 | DevOps | 기획 30점 |
| [`05-erd.md`](05-erd.md) | 데이터 모델링, 테이블 관계와 정규화 | Data Architect | 설계 30점 |
| [`06-api-spec.md`](06-api-spec.md) | REST API 명세 (Mock 포함) | API Architect | 설계 30점 |
| [`07-ai-ready.md`](07-ai-ready.md) | AI 확장 지점, 프롬프트, 입출력 JSON 스키마 | API Architect | 기획 30점 |
| [`adr/`](adr/) | 팀이 내린 기술 결정 기록 | 전원 | 설계 타당성 |
| [`../database/`](../database/) | 실행 가능한 스키마 SQL (`05-erd.md`와 짝) | Data Architect | 설계 30점 |
| [`images/`](images/) | 다이어그램 이미지 (ERD·아키텍처·와이어프레임) | 각 담당 | 발표 자료 |
| [`checklist.md`](checklist.md) | 3일 로드맵 진행 체크리스트 | PM | — |

## 명세와 코드의 사슬

기준이 되는 문서 하나를 두지 않는다. **명세와 코드가 번갈아 놓인 사슬**을 맞춰 나간다.

```text
docs/02-use-case.md · 03-wireframe.md     사용자가 무엇을 하는가
        ↕
frontend/src                              그 화면을 어떻게 그리는가
        ↕
docs/06-api-spec.md                       FE와 BE가 무엇을 주고받는가   ← 계약
        ↕
backend/                                  그 요청을 어떻게 처리하는가
        ↕
docs/05-erd.md + database/schema.sql      그 데이터를 어떻게 저장하는가
```

**가운데의 `06-api-spec.md`가 가장 중요하다.** 이 계약이 먼저 고정되면 FE와 BE가
서로를 기다리지 않고 동시에 작업할 수 있고, 나중에 백엔드가 Mock 응답 대신 진짜
LLM API를 부르도록 바뀌어도 프런트엔드는 한 줄도 고치지 않는다.
이것이 PDF가 말하는 **Interface First**다.

## 규칙

- 코드를 바꾸면 짝이 되는 문서도 같은 PR에서 바꾼다. PR 템플릿에 확인 항목이 있다.
- 문서와 코드가 어긋나면 **혼자 판단해서 맞추지 말고** 팀에 묻는다. 어느 쪽이
  틀렸는지는 작성자만 안다.
- 아직 정하지 못한 칸은 지우지 말고 `TBD`로 남긴다. 빈칸이 보여야 누가 무엇을
  못 정했는지 드러난다.
