# Backend

**Java 21 / Spring Boot 4.1.1.** [ADR 0001](../docs/adr/0001-backend-stack.md)에서 확정했다.

스캐폴딩은 **끝나 있다.** clone 후 아래만 하면 빌드가 돈다.

## 실행

```bash
./gradlew build      # DB 없이도 통과한다 (테스트는 인메모리 H2로 돈다)
./gradlew bootRun    # .env 가 있어야 뜬다. DATABASE_URL 이 필요하다
```

| | |
| --- | --- |
| 포트 | `8080` |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI 문서 | <http://localhost:8080/v3/api-docs> |

**Java 21이 필요하다.** 설치는 [README의 "7. 개발 도구 설치"](../README.md).
`build.gradle`의 `java.toolchain`이 컴파일을 21로 고정하므로, 다른 버전을 깔았어도
전원이 같은 바이트코드로 돈다.

> Gradle 첫 빌드는 의존성을 받느라 몇 분 걸린다. **각자 미리 한 번 돌려 둔다.**
> 발표 당일에 처음 받지 않는다.

## 환경 변수

`.env.example`을 `.env`로 복사해서 쓴다. **`.env`는 커밋하지 않는다.**

```bash
cp .env.example .env
```

DB 접속 정보와 AI API 키는 저장소가 아닌 **팀 채널로 공유한다.**

**`.env`는 `build.gradle`의 `bootRun` 태스크가 읽어서 넘긴다.** 따로 할 일은 없다.
IDE에서 실행할 때는 실행 구성에 환경 변수를 직접 넣거나, 셸에서
`set -a; source .env; set +a`를 먼저 실행한다.

> JDBC URL 접두사는 `jdbc:postgresql://` 다. Supabase·Neon 대시보드가 주는
> `postgresql://USER:PASSWORD@HOST/DB` 를 그대로 붙여넣으면 뜨지 않는다.
> 계정 정보는 `DATABASE_USERNAME` · `DATABASE_PASSWORD`로 분리한다.

## 지금 들어 있는 것

| | |
| --- | --- |
| `config/CorsConfig.java` | `CORS_ALLOWED_ORIGINS`를 읽어 `/api/**`에 적용 |
| `application.properties` | 모든 값을 환경 변수에서 읽는다 (AI-Ready 원칙 4) |
| `application-test.properties` | 테스트용 H2. DB 없이 빌드가 통과하는 이유 |
| springdoc | Swagger UI와 OpenAPI 문서 자동 생성 |

**엔터티·Repository·Controller는 아직 없다.** 데이터 모델과 API 명세
([`docs/05`](../docs/05-erd.md) · [`06`](../docs/06-api-spec.md))가 확정된 뒤에 만든다.

## 이 스택에서 밟기 쉬운 함정

세팅하면서 실제로 겪은 것들이다. **Spring Boot 4는 3과 다른 부분이 있다.**

| 함정 | 내용 |
| --- | --- |
| `spring-boot-starter-web`이 없다 | Boot 4에서 **`spring-boot-starter-webmvc`** 로 이름이 바뀌었다 |
| `spring-dotenv`가 안 먹는다 | `META-INF/spring.factories`로 등록하는데 **Boot 4가 그 방식을 없앴다.** 조용히 무시된다. 그래서 Gradle이 직접 `.env`를 읽게 해 뒀다 |
| `src/test/resources/application.properties` | 이름이 같으면 main의 설정을 **통째로 가린다.** main에 넣은 `app.*` 값이 테스트에서 사라져 컨텍스트가 안 뜬다. 그래서 `application-test.properties` + `@ActiveProfiles("test")`를 쓴다 |
| DB 없이 테스트가 깨진다 | JPA가 DataSource를 요구한다. 테스트 프로필의 H2로 해결해 뒀다 |
| Spring Boot 3을 못 만든다 | `start.spring.io`가 **4.0.0 미만을 거부한다.** ADR 0001 참조 |

## 구현 시 지킬 것

### 계층을 지킨다

Controller → Service → Repository. **이 스택을 고른 이유가 여기에 있다.**
5명이 각자 짜도 구조가 흩어지지 않고, 발표 4번 섹션에서 폴더 구조 한 장으로
설명이 끝난다. Entity를 Controller까지 내보내지 말고 DTO로 변환한다.

### CORS

`CorsConfig`가 이미 `CORS_ALLOWED_ORIGINS`(기본 `http://localhost:5173`)를 읽어
`/api/**`에 적용한다. 프런트엔드 포트를 바꾸면 `.env`만 고친다.

### AI 확장 지점

`docs/07-ai-ready.md`의 규격대로 **Mock을 먼저 만든다.** 실제 LLM은 부르지 않는다.

- 응답 JSON은 `07-ai-ready.md`의 출력 스키마를 **정확히** 지킨다.
  Mock이 스키마를 어기면 나중에 실제 AI를 붙일 때 프런트엔드를 고쳐야 하고,
  그러면 AI-Ready 설계가 무너진다.
- `POST /api/ai-jobs`는 `202 Accepted`로 즉시 응답하고, 결과는
  `GET /api/ai-jobs/{id}`로 조회하게 한다. Mock이라도 이 구조를 지킨다.
  **Mock은 즉시 끝나므로 `@Async`나 큐가 없어도 된다.**
- `AI_PROVIDER=mock`이면 Mock 응답을, 다른 값이면 실제 API를 호출하도록
  인터페이스 하나에 구현 둘(`MockAiClient`, `RealAiClient`)을 두고 지금은
  `mock` 쪽만 구현한다. **이 인터페이스가 아키텍처 다이어그램의 "교체되는 상자"다.**

> 가상 스레드는 `spring.threads.virtual.enabled=true`로 이미 켜져 있다.
> 실제 LLM을 붙였을 때 느린 요청이 스레드를 오래 잡아도 비용이 낮게 유지된다.
> **Java 21을 고른 이유다.** (ADR 0001)

### 비밀값

API 키·DB 비밀번호를 코드나 `application.properties`에 직접 쓰지 않는다.
전부 환경 변수로 읽는다. (AI-Ready 원칙 4: Security & Config Isolation)
