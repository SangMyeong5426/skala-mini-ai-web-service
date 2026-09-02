# Backend

**Java 21 / Spring Boot 3.** [ADR 0001](../docs/adr/0001-backend-stack.md)에서 확정했다.

## 아직 비어 있다

1일차에 이 폴더에 스캐폴딩한다. **Backend Developer가 담당한다.**

[start.spring.io](https://start.spring.io)에서 아래 구성으로 생성한다.

| 항목 | 값 |
| --- | --- |
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 3.2 이상 (기본값 그대로) |
| Java | **21** |
| Dependencies | Spring Web · Spring Data JPA · PostgreSQL Driver · Lombok · Validation |

여기에 Swagger UI용 `springdoc-openapi-starter-webmvc-ui` 를 `build.gradle`에
추가한다. **API 명세서가 2일차 산출물이므로 이게 있으면 `/swagger-ui.html`이
공짜로 나온다.**

생성 후 이 README를 실행 방법으로 교체한다.

## ⚠️ 먼저 JDK부터

**개발 PC에 JDK가 설치돼 있지 않다.** (ADR 0001 배경) **이게 밀리면 뒤가 전부
밀린다.** 1일차에 가장 먼저 끝낸다.

설치 방법은 [README의 7. 개발 도구 설치](../README.md#7-개발-도구-설치-역할별)에
있다. 요약하면 **Temurin 21을 각자 PC에 설치**하고, 버전은
`build.gradle`의 `java.toolchain`이 21로 고정한다.

```bash
java -version   # 21이 나와야 한다
```

Gradle 첫 빌드가 느리므로 **각자 미리 한 번 돌려 둔다.** 발표 당일에 처음
받지 않는다.

```bash
./gradlew build   # 첫 실행은 의존성을 받느라 몇 분 걸린다
```

## 환경 변수

`.env.example`을 `.env`로 복사해서 쓴다. **`.env`는 커밋하지 않는다.**

```bash
cp .env.example .env
```

**Spring Boot는 `.env`를 스스로 읽지 않는다.** `me.paulschwarz:spring-dotenv`
의존성을 넣거나, 실행 구성에서 환경 변수로 주입한다. `application.yml`에서는
`${PORT}` 처럼 참조한다.

DB 접속 정보와 AI API 키는 저장소가 아닌 **팀 채널로 공유한다.**

> JDBC URL 접두사는 `jdbc:postgresql://` 다. Supabase·Neon 대시보드가 주는
> `postgresql://USER:PASSWORD@HOST/DB` 를 그대로 붙여넣으면 뜨지 않는다.
> 계정 정보는 `DATABASE_USERNAME` · `DATABASE_PASSWORD`로 분리한다.

## 구현 시 지킬 것

### 계층을 지킨다

Controller → Service → Repository. **이 스택을 고른 이유가 여기에 있다.**
5명이 각자 짜도 구조가 흩어지지 않고, 발표 4번 섹션에서 폴더 구조 한 장으로
설명이 끝난다. Entity를 Controller까지 내보내지 말고 DTO로 변환한다.

### CORS

프런트엔드가 `http://localhost:5173`에서 뜨고 백엔드는 `8080`에서 뜬다.
개발용 origin을 허용해 두지 않으면 2일차 FE-BE 연동이 브라우저에서 막힌다.
**연동 실패의 가장 흔한 원인이다.**

### AI 확장 지점

`docs/07-ai-ready.md`의 규격대로 **Mock을 먼저 만든다.** 실제 LLM은 부르지 않는다.

- 응답 JSON은 `07-ai-ready.md`의 출력 스키마를 **정확히** 지킨다.
  Mock이 스키마를 어기면 나중에 실제 AI를 붙일 때 프런트엔드를 고쳐야 하고,
  그러면 AI-Ready 설계가 무너진다.
- `POST /api/ai-jobs`는 `202 Accepted`로 즉시 응답하고, 결과는
  `GET /api/ai-jobs/{id}`로 조회하게 한다. Mock이라도 이 구조를 지킨다.
  **Mock은 즉시 끝나므로 `@Async`나 큐가 없어도 된다.** 구조만 지키면
  AI-Ready 원칙 3(Asynchronous Pipeline)을 만족한다.

  > 나중에 실제 LLM을 붙이면 요청 하나가 수 초씩 스레드를 붙잡는다. 그때는
  > `application.yml`에 아래 한 줄이면 가상 스레드로 돈다.
  > **Java 21을 고른 이유가 이것이다.** ([ADR 0001](../docs/adr/0001-backend-stack.md))
  >
  > ```properties
  > spring.threads.virtual.enabled=true
  > ```
  >
  > 지금 켜 둬도 손해는 없다. 발표 5번 섹션(향후 로드맵)에서 쓸 수 있다.
- `AI_PROVIDER=mock`이면 Mock 응답을, 다른 값이면 실제 API를 호출하도록
  분기점을 만들어 둔다. 인터페이스 하나에 구현 둘(`MockAiClient`,
  `RealAiClient`)을 두고 지금은 `mock` 쪽만 구현한다.
  **이 인터페이스가 아키텍처 다이어그램의 "교체되는 상자"다.**

### 비밀값

API 키·DB 비밀번호를 코드나 `application.yml`에 직접 쓰지 않는다.
전부 환경 변수로 읽는다. (AI-Ready 원칙 4: Security & Config Isolation)
