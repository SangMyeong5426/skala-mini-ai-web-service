# Frontend

Vue 3 + Vite.

## 아직 비어 있다

1일차 "FE 프로젝트 생성" 단계에서 이 폴더에 스캐폴딩한다.
**Frontend Developer가 담당한다.**

```bash
npm create vue@latest .
```

생성 후 이 README를 실행 방법으로 교체한다.

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

## 개발 시 지킬 것

- API 호출 주소를 코드에 하드코딩하지 않는다. `VITE_API_BASE_URL`을 쓴다.
  Mock 서버(Postman) ↔ 로컬 백엔드를 이 값 하나로 갈아 끼운다.
- AI 결과를 받는 화면은 **반드시 폴링으로 구현한다.** Mock이 즉시 응답하더라도
  마찬가지다. 그래야 나중에 실제 AI를 붙일 때 이 코드를 고치지 않는다.
  (`docs/06-api-spec.md`의 AI 확장 지점 엔드포인트 참조)
- 로딩·빈 상태·오류 상태 화면을 빠뜨리지 않는다.
