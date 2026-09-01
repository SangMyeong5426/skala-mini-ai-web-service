# Use-Case

> 발표 1번 섹션 (3분): 핵심 Actor 및 Use-Case 요약
>
> 채점 기준: **"Use-Case 정의 및 UI 와이어프레임 완성도"**,
> **"AI 확장 지점 정의 및 프롬프트/JSON 스키마 타당성"**

## Actor

| Actor | 설명 | 주요 목적 |
| --- | --- | --- |
| TBD | | |

<!-- Actor는 사람만이 아니다. 외부 시스템(결제사, LLM API)도 Actor가 될 수 있다. -->

## Use-Case 목록

`UC-01`부터 순서대로 번호를 붙인다. **AI 확장 지점**은 지금은 Mock으로 응답하지만
나중에 실제 AI가 들어올 자리다. 반드시 표시한다.

| ID | Use-Case | Actor | 설명 | AI 확장 지점 |
| --- | --- | --- | --- | --- |
| UC-01 | TBD | TBD | TBD | — |
| UC-02 | TBD | TBD | TBD | **O** |
| UC-03 | TBD | TBD | TBD | — |

## 주요 Use-Case 상세

각 Use-Case마다 아래 형식으로 적는다. 최소한 **AI 확장 지점이 있는 것**은 반드시 채운다.

### UC-01: TBD

| 항목 | 내용 |
| --- | --- |
| Actor | TBD |
| 사전 조건 | TBD |
| 기본 흐름 | 1. TBD<br>2. TBD<br>3. TBD |
| 대체·예외 흐름 | TBD |
| 사후 조건 | TBD |
| 관련 API | `docs/06-api-spec.md`의 TBD |
| 관련 화면 | `docs/03-wireframe.md`의 TBD |

## AI 확장 지점 정리

여기 적은 것이 `07-ai-ready.md`의 입력이 된다.

| Use-Case | 지금 (1~3일차) | 나중 (AI 결합 후) | 바뀌는 곳 |
| --- | --- | --- | --- |
| UC-02 | Mock API가 고정 JSON 반환 | LLM이 같은 스키마의 JSON 생성 | **백엔드 내부만.** FE·API 규격·DB는 그대로 |

> 이 표의 마지막 칸이 핵심이다. **"백엔드 내부만 바뀐다"**고 말할 수 있어야
> AI-Ready 설계가 성립한다. 프런트엔드나 API 규격이 함께 바뀌어야 한다면
> 그 설계는 AI-Ready가 아니다.
