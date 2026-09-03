# Frontend

**React + TypeScript (Vite).** [ADR 0002](../docs/adr/0002-frontend-stack.md)에서 확정했다.

스캐폴딩은 **끝나 있다.** clone 후 아래만 하면 화면이 뜬다.

| | 버전 |
| --- | --- |
| React | 19 |
| TypeScript | 6 |
| Vite | 8 |

**Node.js 20.19 이상 또는 22.12 이상**이 필요하다. Vite 8 · oxlint · plugin-react 의
`engines` 가 `^20.19.0 || >=22.12.0` 이라 20.18 이하와 21.x 에서는 돌지 않는다.
설치는 [README의 "6. 개발 도구 설치"](../README.md).

## 실행

```bash
npm install
npm run dev
```

기본 포트는 `5173`이다. 바꾸면 `docs/04-architecture.md`와 백엔드 CORS 설정도
함께 고친다.

## 환경 변수

`.env.example`을 `.env`로 복사해서 쓴다. **`.env`는 커밋하지 않는다.**

```bash
cp .env.example .env
```

Vite는 `VITE_` 로 시작하는 변수만 브라우저에 노출한다.
**따라서 이 폴더의 `.env`에는 비밀값을 절대 넣지 않는다.**
AI API 키 같은 것은 백엔드에만 둔다.

## 폴더 구조

뼈대는 만들어져 있다. Vite 기본 화면은 지웠다.

```
src/
  main.tsx            BrowserRouter 마운트
  App.tsx             공통 셸(헤더 + 라우트)
  routes.tsx          화면 10개 목록 — 여기가 화면의 정본이다
  types/api.ts        06·07 규격을 TypeScript 로
  api/client.ts       fetch 래퍼 하나
  hooks/useAiJob.ts   AI 작업 폴링
  components/States.tsx   로딩·AI 처리 중·빈 상태·오류·책임 고지
  pages/Placeholder.tsx   화면 껍데기 (담당자가 실제 화면으로 교체)
  styles/app.css      CSS 변수
```

**CSS 라이브러리를 넣지 않는다.** 3일 일정에서 Tailwind 설정에 쓰는 시간이
화면 하나보다 비싸다. 색·간격은 `styles/app.css` 의 CSS 변수에 모여 있다.

## 화면 하나 만드는 법

1. `src/pages/` 에 컴포넌트를 만든다. 예: `pages/Home.tsx`
2. `src/routes.tsx` 의 `screenElement` 에서 해당 화면만 바꿔 끼운다
3. 데이터는 `api/client.ts` 의 `api.get/post/patch/del` 로 가져온다
4. **로딩·빈 상태·오류를 반드시 그린다.** `components/States.tsx` 를 쓴다

```tsx
import { api } from '../api/client'
import { Skeleton, Empty, Failed } from '../components/States'
import type { TripSummary } from '../types/api'
```

AI 를 부르는 화면(`S-04`·`S-05`·`S-06`·`S-09`)은 `useAiJob` 을 쓴다.

```tsx
const job = useAiJob<PackingListOutput>()
await job.start('PACKING_LIST', input, tripId)
// job.phase: idle | running | done | failed | timeout
// job.polls 로 진행 표시를 그린다
```

`routes.tsx` 의 `tier` 가 우선순위다. `1` 이 데모 주 경로(`S-01`~`S-06`)고,
시간이 모자라면 `3` 부터 버린다.

## 사진 URL 은 `/api` 밑이 아니다

업로드된 짐 사진은 백엔드가 `/uploads/**` 로 내보낸다. `vite.config.ts` 의
프록시가 5173 → 8080 으로 넘긴다. **`api/client.ts` 를 거치지 않는다.**

```tsx
<img src={photo.fileUrl} />   {/* 예: /uploads/demo/bag-01.jpg */}
```

## 백엔드가 아직 없을 때

`backend/` 에 컨트롤러가 없으면 `api.get('/trips')` 는 **404** 가 난다.
화면은 오류 상태를 그리게 되므로, 그 상태를 만드는 데 이용하면 된다 —
`Failed` 컴포넌트가 제대로 뜨는지 먼저 확인할 수 있다.

응답 모양이 필요하면 `docs/06-api-spec.md` 의 예시 JSON 을 그대로 쓴다.
**타입은 이미 `types/api.ts` 에 있으므로 새로 만들지 않는다.**

임시로 화면을 채워 보려면 컴포넌트 안에 상수를 두고, 백엔드가 붙으면 지운다.
**Mock 서버 라이브러리(MSW 등)를 설치하지 않는다** — 3일 일정에 맞지 않고
`CLAUDE.md` 가 기능을 늘리지 말라고 정해 뒀다.

## 개발 시 지킬 것

- API 호출 주소를 코드에 하드코딩하지 않는다. `VITE_API_BASE_URL`을 쓴다.
  Mock 서버(Postman) ↔ 로컬 백엔드를 이 값 하나로 갈아 끼운다.
- AI 결과를 받는 화면은 **반드시 폴링으로 구현한다.** Mock이 즉시 응답하더라도
  마찬가지다. 그래야 나중에 실제 AI를 붙일 때 이 코드를 고치지 않는다.
  (`docs/06-api-spec.md`의 AI 확장 지점 엔드포인트 참조)
- 로딩·빈 상태·오류 상태 화면을 빠뜨리지 않는다.

### 타입을 규격의 사본으로 둔다

**TypeScript를 고른 이유가 여기에 있다.** ([ADR 0002](../docs/adr/0002-frontend-stack.md))

- `docs/06-api-spec.md`의 응답 규격을 `src/types/api.ts`에 타입으로 옮긴다.
- `docs/07-ai-ready.md`의 출력 JSON Schema도 같은 파일에 타입으로 선언한다.
- **명세가 바뀌면 타입도 같은 PR에서 바꾼다.** 문서와 코드가 짝이라는 규칙이
  여기에도 그대로 적용된다.

이렇게 두면 *"백엔드가 Mock에서 실제 LLM으로 바뀌어도 프런트엔드는 이 타입을
그대로 쓴다"* 를 발표에서 파일 하나로 보여줄 수 있다. AI-Ready 원칙 1
(Interface First)의 증거다.
