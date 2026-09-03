# 다이어그램 이미지

설계 문서에 넣을 이미지를 여기 둔다. `docs/`의 문서들이 이 폴더를 참조한다.

## 2026-09-03 개정 반영 상태

**MD만 개정된 상태다.** 현재 PNG·PUML·SVG를 최신 개정 흐름으로 발표하지 않는다.

| 파일 | 다음 갱신 내용 |
| --- | --- |
| `02-usecase.*` | UC-05 추가 준비물 추천, UC-06 내 체크리스트 관리, 사진 승인 등록 책임 |
| `03-userflow.*` | 사진 승인 즉시 완료 등록 → 별도 추천 → 선택분 미완료 추가 → 실제 완료 확인 |
| `04-architecture.*` | Service의 사진 승인·추천 채택·중복 방지 책임 주석 |
| `05-erd.*` | 기존 테이블·컬럼 유지, 후보 JSON과 내 목록의 저장 경계·서버 연결 필드 주석 |

최신 흐름은 [`03-wireframe.md`](../03-wireframe.md)의 Mermaid와 화면 상세를 따른다.
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

개정 내용을 반영해 재생성한 이미지를 3일차 발표에서 쓴다.
[`docs/checklist.md`](../checklist.md)의 발표 구성 표에서 어느 섹션에 어떤
다이어그램이 필요한지 확인한다.
