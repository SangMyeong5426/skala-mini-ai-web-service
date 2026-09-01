# Backend

**스택 미정.** [ADR 0001](../docs/adr/0001-backend-stack.md)에서 결정한다.

## 아직 비어 있다

1일차에 백엔드 스택을 확정한 뒤 이 폴더에 스캐폴딩한다.
**Backend Developer가 담당한다.**

확정 후 이 README를 실행 방법으로 교체하고, ADR 0001의 후속 작업 체크리스트를
따라간다.

## 환경 변수

`.env.example`을 `.env`로 복사해서 쓴다. **`.env`는 커밋하지 않는다.**

```bash
cp .env.example .env
```

DB 접속 정보와 AI API 키는 저장소가 아닌 **팀 채널로 공유한다.**

## 구현 시 지킬 것

### CORS

프런트엔드가 `http://localhost:5173`에서 뜨고 백엔드는 다른 포트에서 뜬다.
개발용 origin을 허용해 두지 않으면 2일차 FE-BE 연동이 브라우저에서 막힌다.
**연동 실패의 가장 흔한 원인이다.**

### AI 확장 지점

`docs/07-ai-ready.md`의 규격대로 **Mock을 먼저 만든다.** 실제 LLM은 부르지 않는다.

- 응답 JSON은 `07-ai-ready.md`의 출력 스키마를 **정확히** 지킨다.
  Mock이 스키마를 어기면 나중에 실제 AI를 붙일 때 프런트엔드를 고쳐야 하고,
  그러면 AI-Ready 설계가 무너진다.
- `POST /api/ai-jobs`는 `202 Accepted`로 즉시 응답하고, 결과는
  `GET /api/ai-jobs/{id}`로 조회하게 한다. Mock이라도 이 구조를 지킨다.
- `AI_PROVIDER=mock`이면 Mock 응답을, 다른 값이면 실제 API를 호출하도록
  분기점을 만들어 둔다. 지금은 `mock` 쪽만 구현한다.

### 비밀값

API 키·DB 비밀번호를 코드나 설정 파일에 직접 쓰지 않는다.
전부 환경 변수로 읽는다. (AI-Ready 원칙 4: Security & Config Isolation)
