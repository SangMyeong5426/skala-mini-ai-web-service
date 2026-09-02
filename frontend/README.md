# Frontend

**React + TypeScript (Vite).** [ADR 0002](../docs/adr/0002-frontend-stack.md)에서 확정했다.

스캐폴딩은 **끝나 있다.** clone 후 아래만 하면 화면이 뜬다.

| | 버전 |
| --- | --- |
| React | 19 |
| TypeScript | 6 |
| Vite | 8 |

**Node.js 20 이상**이 필요하다. 설치는 [README의 "7. 개발 도구 설치"](../README.md).

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

## 무엇을 언제 만드는가

**라우팅과 화면 컴포넌트는 지금 만들지 않는다.** 기능 명세·유저플로우·
와이어프레임([`docs/01`](../docs/01-service-plan.md) · [`02`](../docs/02-use-case.md) ·
[`03`](../docs/03-wireframe.md))이 확정된 뒤에 시작한다. 화면을 모르는 채 구조를
짜면 두 번 일한다.

지금 이 폴더에 있는 것은 **Vite 기본 화면**이다. 첫 화면을 만들 때 지운다.

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
