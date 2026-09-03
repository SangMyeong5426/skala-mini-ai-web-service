# 다이어그램 이미지

설계 문서에 넣을 이미지를 여기 둔다. `docs/`의 문서들이 이 폴더를 참조한다.

## 2026-09-03 개정 반영 상태

**4종의 원본·PNG·SVG에 개정 흐름을 반영했다.** 아래 그림은 기능 설계이며,
현재 코드의 구현 완료를 뜻하지 않는다. 구현·시드의 후속 작업은 [반영 상태](../README.md#개정안-반영-상태)를 따른다.

| 파일 | 반영 내용 |
| --- | --- |
| `02-usecase.*` | UC-05 추가 준비물 추천, UC-06 내 체크리스트 관리, 사진 승인 등록 책임. 제외한 로그인에는 Actor 연결 없음 |
| `03-userflow.*` | 사진 승인 즉시 완료 등록 → 별도 추천 → 선택분 미완료 추가 → 실제 완료 확인. 필수 후보 경고·S-04 재확인·재사용·챗봇 경로 포함 |
| `04-architecture.*` | Service의 사진 승인·추천 채택·중복 방지·필수 후보 경고·완료 물품 무게 계산 책임. AI 작업의 비동기 폴링 유지 |
| `05-erd.*` | 기존 10개 테이블·컬럼·관계 유지. 후보 JSON과 내 목록의 저장 경계, 서버 연결 필드와 조회 계산값 표시 |

[`03-wireframe.md`](../03-wireframe.md)의 Mermaid·화면 상세와 교차 확인했다.
이미지 갱신 시 원본·PNG·SVG를 함께 생성한다. `03-userflow.puml`은 `@startdot` 형식이다.

## 파일 이름

문서에서 찾기 쉽도록 **참조하는 문서 번호를 앞에 붙인다.**

| 파일 | 내용 | 담당 | 참조하는 문서 |
| --- | --- | --- | --- |
| `02-usecase.png` | Use-Case 다이어그램 | Product/UX Designer | [`02-use-case.md`](../02-use-case.md) |
| `03-userflow.png` | 사용자 흐름도 | Product/UX Designer | [`03-wireframe.md`](../03-wireframe.md) |
| `03-wireframe-*.png` | 화면별 와이어프레임 | Product/UX Designer | [`03-wireframe.md`](../03-wireframe.md) |
| `04-architecture.png` | 시스템 아키텍처 | DevOps & Integration | [`04-architecture.md`](../04-architecture.md) |
| `05-erd.png` | ERD | Data Architect | [`05-erd.md`](../05-erd.md) |

## 내보내는 방법

### 저장소 다이어그램 재생성

저장소 루트에서 실행한다. 검증 환경은 **PlantUML 1.2026.7 · Graphviz 15.1.1**이다.
PlantUML과 Graphviz의 `dot`이 필요하며, 기본 한글 폰트는 `Apple SD Gothic Neo`다.
다른 OS에서는 사용 가능한 한글 폰트로 원본의 폰트명을 함께 바꾼 뒤 글자 표시를 확인한다.

```bash
plantuml -charset UTF-8 -tpng docs/images/{02-usecase,04-architecture,05-erd}.puml
plantuml -charset UTF-8 -tsvg docs/images/{02-usecase,04-architecture,05-erd}.puml
sed '1d;$d' docs/images/03-userflow.puml | dot -Tpng -Gdpi=150 -o docs/images/03-userflow.png
sed '1d;$d' docs/images/03-userflow.puml | dot -Tsvg -o docs/images/03-userflow.svg
```

User Flow는 DOT 본문을 같은 원본에서 추출해 PNG·SVG로 렌더한다. 렌더 후 한글·화살표·라벨 겹침,
잘림과 PNG별 1MB 이하를 확인한다. ERD는 DBML·SQL과 테이블·컬럼·관계를 다시 대조한다.

### 외부 설계 도구

| 도구 | 내보내기 |
| --- | --- |
| Figma | 프레임 선택 → 우측 하단 `Export` → PNG 2x |
| dbdiagram.io | 우측 상단 `Export` → PNG 또는 PDF |
| Miro | 보드 우클릭 → `Export image` |

## 문서에서 참조하기

```markdown
![ERD](images/05-erd.png)
```

`docs/` 안의 문서에서는 `docs/`를 빼고 `images/...`로 쓴다.

## 지킬 것

- **PNG를 쓴다.** GitHub와 발표 자료 양쪽에서 문제없이 열린다.
- **한 장에 1MB를 넘기지 않는다.** 3일짜리 프로젝트에 저장소를 무겁게 할
  이유가 없다. 크면 Figma에서 1x로 다시 내보낸다.
- **원본 링크를 문서에 함께 남긴다.** 이미지는 갱신이 늦어지기 마련이라,
  Figma·dbdiagram 링크가 있어야 최신본을 확인할 수 있다.
- 이미지를 갱신하면 **같은 파일 이름으로 덮어쓴다.** `erd-final-v2-진짜최종.png`
  같은 이름을 만들지 않는다.

## 발표 자료

위 개정 내용을 반영한 이미지를 3일차 발표에서 쓴다.
[`docs/checklist.md`](../checklist.md)의 발표 구성 표에서 어느 섹션에 어떤
다이어그램이 필요한지 확인한다.
