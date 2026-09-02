# 팀 구성 및 R&R

> 채점 기준: **"GitHub 레포지토리 관리 및 R&R 분담의 적절성"** (기획 30점 항목)
>
> 1인이 여러 역할을 겸할 수 있고, 한 역할을 여러 명이 맡아도 된다.
> 다만 **비어 있는 역할이 없어야 한다.** — 7개 역할 모두 채워져 있다.

## 팀 정보

- 조: TBD
- 인원: 5명
- 저장소: <https://github.com/SangMyeong5426/skala-mini-ai-web-service>

## R&R 분담 — 역할 기준

| 역할 | 담당자 | 주요 책임 | 담당 산출물 |
| --- | --- | --- | --- |
| **PM** | 박상명 | 전체 일정·진행 추적, 발표 총괄, 데모 시연 준비 | 최종 발표 슬라이드 |
| **Product/UX Designer** | 문성도, 박현수 | Use-Case 정의, User Flow 설계, 와이어프레임 | `02-use-case.md`, `03-wireframe.md`, Figma |
| **Data Architect** | 최인서, 손현아, 박현수 | DB 데이터 모델링, 테이블 관계(1:N, N:M) 정의 | `05-erd.md`, `database/`, DB 생성 (`01`~`03` 확정 후) |
| **API Architect** | 박상명, 최인서, 손현아 | REST API 규격 작성, Mock 서버 세팅, AI 프롬프트·JSON 규격 정의 | `06-api-spec.md`, `07-ai-ready.md`, Postman |
| **Frontend Developer** | 박상명, 문성도, 박현수 | 레이아웃·라우팅, 핵심 화면 1~2개 (`01`~`03` 확정 후) | `frontend/` |
| **Backend Developer** | 최인서, 손현아, 문성도 | DB 연동, API 일부 실구현 (`02`·`06` 확정 후) | `backend/` |
| **DevOps & Integration** | 박상명 | GitHub 저장소 관리, E2E 연동 테스트·오류 검증 | 저장소 세팅, E2E 테스트 결과 |

> **JDK 21 · Node.js 20.19+ 설치는 역할과 무관하게 5명 전원이 한다.**
> JDK 21은 2026-09-02 전원 완료.
> 도구 설치는 위 표의 역할 분담과 별개다. ([README 6단계](../README.md))
> FE·BE 스캐폴딩은 이미 끝나 있고, 위 표의 구현 작업은 `01`~`03` 확정 후에 시작한다.

## 사람 기준

각자 자기 줄만 보면 된다.

| 이름 | GitHub ID | 맡은 역할 |
| --- | --- | --- |
| 박상명 | `@SangMyeong5426` | PM · DevOps & Integration · API Architect · Frontend Developer |
| 최인서 | `@boboinhaco` | Data Architect · API Architect · Backend Developer |
| 손현아 | `@hyun5555` | Data Architect · API Architect · Backend Developer |
| 문성도 | `@seogdo` | Product/UX Designer · Frontend Developer · Backend Developer |
| 박현수 | `@gustnxoddl51-bot` | Data Architect · Frontend Developer · Product/UX Designer |

> 위 ID는 본인 확인을 마쳤고, [`.github/CODEOWNERS`](../.github/CODEOWNERS)에
> 그대로 반영돼 있다. **역할이 바뀌면 이 표와 `CODEOWNERS`를 같은 PR에서 고친다.**

## 운영 방식

### 백엔드·프런트엔드는 서로 지원한다

**자기 몫이 일찍 끝나면 반대편을 돕는다.** 3일 일정이라 한쪽이 막히면 전체가
멈추기 때문이다. BE 3명(최인서·손현아·문성도), FE 3명(박상명·문성도·박현수)이
겹치도록 짠 것도 같은 이유다.

다만 **[`06-api-spec.md`](06-api-spec.md)의 FE/BE 계약을 바꿀 때만은 예외다.**
지원하러 넘어간 사람이 규격을 말없이 고치면 원래 담당자 작업이 통째로 깨진다.
계약 변경은 반드시 양쪽 담당자 확인을 받는다. ([`CONTRIBUTING.md`](../CONTRIBUTING.md))

### 한 역할을 여러 명이 맡을 때

같은 역할이라도 **파일을 갈라서 잡는다.** 같은 파일을 동시에 고치면 충돌이 나고,
3일 일정에서 충돌 해결은 순수 손해다.

| 역할 | 나누는 방법 (예시) |
| --- | --- |
| Data Architect 3명 | ERD 초안 / `schema.sql` / `seed.sql` |
| API Architect 3명 | 도메인 엔드포인트 / AI 확장 지점(`07`) / Postman Mock |
| Product/UX 2명 | Use-Case(`02`) / 와이어프레임(`03`) |
| FE·BE 3명씩 | 화면 단위 · 엔드포인트 단위로 나눈다 |

### 확인이 필요한 편중

**박상명이 4개 역할을 맡고, 그중 PM과 DevOps는 단독이다.** 3일차에 PM 업무(발표
총괄·데모 시연 준비)와 DevOps 업무(E2E 연동 검증)가 **동시에 피크**를 친다.

- [ ] **PM 백업 1명 지정** — 발표 자료를 함께 만들 사람
- [ ] **E2E 검증 백업 1명 지정** — 2~3일차 연동 확인을 같이 볼 사람

> 최인서와 손현아는 역할 조합이 완전히 같다. 위 "파일을 갈라서 잡는다"를
> 특히 두 사람 사이에서 먼저 정해 둔다.

## 분담 확정 후 할 일

- [x] `.github/CODEOWNERS`의 주석을 풀고 위 표의 GitHub ID로 교체
- [x] 팀원 전원 저장소 Collaborator 초대 — 5명 전원 `write` 권한, 수락 완료
- [ ] 전원 [README의 "팀원 온보딩"](../README.md#팀원-온보딩) 1~6단계 완료
- [ ] PM·DevOps 백업 지정 (위 "확인이 필요한 편중")

## 연락

- 소통 채널: TBD
- 데일리 체크인: TBD
