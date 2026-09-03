# Backend

**Java 21 / Spring Boot 4.1.1.** [ADR 0001](../docs/adr/0001-backend-stack.md)에서 확정했다.

스캐폴딩은 **끝나 있다.** clone 후 아래만 하면 빌드가 돈다.

> **이 브랜치를 pull 했다면 DB 를 먼저 갱신한다.** 테이블 둘(`trip_itineraries` ·
> `item_placements`)과 컬럼 하나(`users.login_id`)가 늘었고 `ddl-auto=validate` 라,
> 갱신하지 않으면 앱이 아예 뜨지 않는다.
>
> ```bash
> # 순서대로. 1번이 2번보다 먼저다 (database/README.md 참고)
> psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-users-password-hash.sql
> psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-users-login-id.sql
> psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-itineraries-and-placements.sql
> ```
>
> 데모 계정은 `jiwoo28` / `skala1234` 다 (`database/seed.sql`).
>
> **`schema.sql` 전체 재실행·`scripts/db-apply` 는 쓰지 않는다** — 맨 앞에서 모든 테이블을
> DROP 해 팀 DB 의 실데이터를 지운다. 자세한 내용은 [`database/README.md`](../database/README.md).

처음 합류했다면 **[백엔드 환경·개발 가이드 (`SETUP.md`)](SETUP.md)**를 먼저 읽는다.
JDK·IDE 설정, Supabase 연결, 환경 변수, 개발 순서와 검증 결과를 모아 두었다.

## 실행

```bash
./gradlew build      # DB 없이도 통과한다 (테스트는 인메모리 H2로 돈다)
./gradlew bootTestRun --args='--spring.profiles.active=test' # DB 없이 Swagger 확인
./gradlew bootRun    # Supabase 접속 정보가 필요하다 (.env 또는 환경 변수)
```

| | |
| --- | --- |
| 포트 | `8080` |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI 문서 | <http://localhost:8080/v3/api-docs> |

**Java 21이 필요하다.** 설치는 [README의 "6. 개발 도구 설치"](../README.md).
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

**`.env` 자동 주입은 Gradle `bootRun`에만 적용된다.** 파일 형식·우선순위·다른 실행
방법은 [SETUP.md 4절](SETUP.md#4-환경-변수), DB 연결은 [5절](SETUP.md#5-supabase-postgresql-연결)을 따른다.

## 지금 들어 있는 것

| | |
| --- | --- |
| `config/CorsConfig.java` | `CORS_ALLOWED_ORIGINS`를 읽어 `/api/**`에 적용 |
| `config/UploadConfig.java` | `UPLOAD_DIR`의 파일을 `/uploads/**`로 제공 |
| `application.properties` | DB·CORS·AI 환경 변수, JPA·연결 풀·UTC 설정 |
| `application-test.properties` | 테스트용 H2. DB 없이 빌드가 통과하는 이유 |
| springdoc 3.1.0 | Boot 4 지원 계열. Swagger UI와 OpenAPI 문서 자동 생성 |
| `MiniAiWebServiceApplicationTests` | Swagger 문서·UI, CORS 허용·차단·`Location` 노출 검증 |
| `../.github/workflows/backend.yml` | PR·main 반영 시 Java 21로 빌드와 테스트 |

**도메인 API 26개가 구현돼 있다.** `docs/05-erd.md` 의 테이블 12개에 대응하는 엔터티와
`docs/06-api-spec.md` 의 엔드포인트 **30개 전부**다 — 업무 18개(1~18) · 인증 4개(19~22) ·
일정·캘린더 5개(23~27) · 3D 가방 정리 3개(28~30).

```
com.skala.miniai
├── common/      오류 봉투 · 도메인 코드값 · 완료율 계산 · jsonb 접근 · 시드 사용자
├── config/      CORS · 업로드 · 비동기
└── domain/
    ├── auth/         가입·로그인·세션·로그아웃 (서버 세션 · CSRF · BCrypt)
    ├── trip/         여행 (소유권 확인을 다른 도메인이 빌려 쓴다)
    ├── itinerary/    여행 일정
    ├── calendar/     캘린더 (테이블 없음 — 여행 기간 + 일정 조합)
    ├── checklist/    내 목록 · 추천 채택 · 사진 상태 계산
    ├── packing/      3D 가방 정리 배치
    ├── photo/        사진 업로드 · 인식 결과 승인
    ├── master/       무게·규정 마스터
    ├── inspection/   검수 결과 (준비 상태 + 무게 + 반입 판정)
    └── ai/           AI 작업 접수·폴링 · Mock 클라이언트
```

**AI 는 전부 Mock 이다.** `MockAiClient` 가 `resources/mock/<jobType>.json` 을 돌려주고,
그 내용은 `docs/07-ai-ready.md` 「예시」 절 output 을 **스크립트로 추출**한 것이다.
실제 LLM 을 붙일 때 바꾸는 것은 `AiClient` 구현 하나뿐이다.

## 이 스택에서 밟기 쉬운 함정

세팅하면서 실제로 겪은 것들이다. **Spring Boot 4는 3과 다른 부분이 있다.**

| 함정 | 내용 |
| --- | --- |
| Boot 3 예제를 그대로 복사 | 이 프로젝트는 **`spring-boot-starter-webmvc`**, springdoc **3.1.0**을 사용한다 |
| `.env`가 자동으로 적용될 것이라고 가정 | 실행 방식과 dotenv 의존성을 추가하지 않은 근거는 [SETUP.md 4절](SETUP.md#4-환경-변수) 참조 |
| `src/test/resources/application.properties` | 이름이 같으면 main의 설정을 **통째로 가린다.** main에 넣은 `app.*` 값이 테스트에서 사라져 컨텍스트가 안 뜬다. 그래서 `application-test.properties` + `@ActiveProfiles("test")`를 쓴다 |
| DB 없이 테스트가 깨진다 | JPA가 DataSource를 요구한다. 테스트 프로필의 H2로 해결해 뒀다 |
| 다른 Boot 버전으로 재생성 | 생성기를 다시 돌리지 않고 저장소의 **4.1.1** 설정을 사용한다. 최초 생성 경위는 ADR 0001 참조 |

## 구현 시 지킬 것

### 엔티티 매핑 — 틀리면 앱이 안 뜬다

`ddl-auto=validate` 라 매핑이 하나만 어긋나도 기동이 실패한다.
**규약은 [`CLAUDE.md` 의 "JPA 엔티티를 쓸 때"](../CLAUDE.md#jpa-엔티티를-쓸-때)에
있다.** 실제 Supabase 에 붙여 재현하고 고쳐 본 것이라 그대로 따르면 된다.
여기 옮겨 적지 않는 이유는 두 곳이 갈라지기 때문이다.

`IDENTITY` · `JSONB` · `NUMERIC` · `CHAR` · 복합 PK 다섯 가지가 걸린다.

### 계층을 지킨다

Controller → Service → Repository. **이 스택을 고른 이유가 여기에 있다.**
5명이 각자 짜도 구조가 흩어지지 않고, 발표 4번 섹션에서 폴더 구조 한 장으로
설명이 끝난다. Entity를 Controller까지 내보내지 말고 DTO로 변환한다.

### CORS

`CorsConfig`가 이미 `CORS_ALLOWED_ORIGINS`(기본 `http://localhost:5173`)를 읽어
`/api/**`에 적용한다. 프런트엔드 포트를 바꾸면 `.env`만 고친다.
생성 응답의 `Location` 헤더도 브라우저에 노출한다.

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
