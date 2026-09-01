# 설계 문서

Mini-project의 산출물은 코드가 아니라 **설계 문서**다. 정량 평가 60점 중 대부분이
이 폴더의 내용으로 채점된다. 파일 이름의 번호는 작성 순서이자 발표 순서다.

## 문서 지도

| 문서 | 내용 | 주 담당 (R&R) | 관련 배점 |
| --- | --- | --- | --- |
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
