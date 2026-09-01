# 다이어그램 이미지

설계 문서에 넣을 이미지를 여기 둔다. `docs/`의 문서들이 이 폴더를 참조한다.

## 파일 이름

문서에서 찾기 쉽도록 **참조하는 문서 번호를 앞에 붙인다.**

| 파일 | 내용 | 담당 | 참조하는 문서 |
| --- | --- | --- | --- |
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

3일차 발표에서 이 이미지들을 그대로 쓴다.
[`docs/checklist.md`](../checklist.md)의 발표 구성 표에서 어느 섹션에 어떤
다이어그램이 필요한지 확인한다.
