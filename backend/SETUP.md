# 백엔드 환경 설정 및 개발 가이드

기준일: **2026-09-03**. Java 21 · Spring Boot 4 · Spring Data JPA · Supabase PostgreSQL을 사용한다.
기존 스캐폴딩에 실행 검증과 개발 설정을 보완했다. 새 프로젝트를 생성할 필요가 없다.

## 1. 기획을 백엔드 작업으로 연결하기

서비스는 여행자가 가방 사진으로 준비 상태·예상 무게·항공 반입 여부를 확인하는 도구다.
주 경로는 **여행 등록 → 사진 등록 → 인식 결과 승인 → 체크리스트 → 검수 결과**다.
사진에서 찾지 못한 항목은 “사진에서 미확인”으로 표시하고, 승인 전 인식 결과는
체크리스트·무게·반입 판정에 최종 반영하지 않는다.

| 읽을 문서 | 백엔드에서 확인할 내용 |
| --- | --- |
| [서비스 기획](../docs/01-service-plan.md) | 페르소나, 로그인 제외, 실제 AI 호출 제외 |
| [Use-Case](../docs/02-use-case.md) · [화면 흐름](../docs/03-wireframe.md) | 사용자 승인과 사진 우선 흐름. 구현은 1차 S-01~S-06부터 |
| [아키텍처](../docs/04-architecture.md) · [ADR 0001](../docs/adr/0001-backend-stack.md) | Java·Spring 선택 이유, 계층별 책임 |
| [ERD](../docs/05-erd.md) · [DDL](../database/schema.sql) | 10개 테이블, 속성을 가진 N:M 연결 테이블 2개 |
| [API 명세](../docs/06-api-spec.md) | 18개 설계 엔드포인트, camelCase JSON, 상태 코드·오류 규격 |
| [AI-Ready](../docs/07-ai-ready.md) · [ADR 0003](../docs/adr/0003-ai-job-endpoint.md) | AI 4종을 하나의 작업 접수·조회 경로로 처리. 입출력 Schema는 아직 TBD |
| [협업 규칙](../CONTRIBUTING.md) · [일정](../docs/checklist.md) | 브랜치·PR 규칙, 3일 범위와 개발 순서 |

**현재 구현된 것은 실행 환경이다.** Entity·Repository·도메인 Controller·AI 인터페이스와
Mock 구현은 아직 없다. Swagger에 API 목록이 비어 있는 것은 정상이다.
설계 문서에 나오는 기능을 이번 환경 설정 작업에서 구현 완료한 것으로 보지 않는다.

평가 근거: `docs/checklist.md`의 **개발환경 세팅·Scaffolding**과 발표 4번의
**폴더 구조·연동 상태 설명**을 이 문서와 재현 가능한 실행 명령으로 확인한다.
Mock 데이터 바인딩과 AI Schema 타당성은 후속 기능 구현·설계 작업에서 검증한다.

## 2. 필요한 도구

| 도구 | 버전·설정 | 용도 |
| --- | --- | --- |
| JDK | **Temurin 21** | 컴파일·실행·테스트 |
| Gradle | **Wrapper 9.7.1** | 저장소의 `gradlew` 사용. 시스템 Gradle 설치 불필요 |
| Spring Boot | **4.1.1** | `build.gradle`에 고정 |
| springdoc | **3.1.0** | Swagger UI·OpenAPI. Boot 4 지원 계열 사용 |
| PostgreSQL | Supabase 팀 프로젝트 | 실제 개발 데이터. 로컬 검증 기준은 PostgreSQL 17 |
| Git | 설치된 Git | 브랜치·PR 협업 |
| IDE | IntelliJ IDEA 또는 Java 개발 가능한 에디터 | Gradle 프로젝트로 `backend` 열기 |
| Node.js | 루트 README의 FE 요구 버전 | React·TypeScript 프런트엔드와 함께 실행할 때 |
| Docker | 선택 | 기존 DB 확인·초기화 스크립트와 로컬 PostgreSQL을 사용할 때 |

Spring Data JPA·Validation·PostgreSQL 드라이버·Lombok은 이미 의존성에 포함되어 있다.
H2는 **테스트 실행에만** 포함되며 배포 JAR에는 들어가지 않는다.
Figma·dbdiagram.io는 설계 협업 도구이고 백엔드 런타임 설치 항목이 아니다.

버전 근거: [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html),
[springdoc 공식 문서](https://springdoc.org/).

### JDK 확인

```bash
java -version
cd backend
./gradlew --version
```

macOS에서 다른 JDK가 선택되면 현재 터미널에서 다음을 실행한다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다.
JDK 설치 절차는 [루트 README](../README.md#6-개발-도구-설치-전원)를 참고한다.

## 3. 처음 실행하기

저장소 루트에서 Git 훅을 설치한다. 이미 설치했다면 반복하지 않아도 된다.

```bash
./scripts/setup-git-hooks
cd backend
./gradlew clean build
```

빌드는 Supabase 계정·`.env`·Docker 없이 통과해야 한다.
테스트는 `@ActiveProfiles("test")`로 H2에 연결한다.

### DB 연결 전: 서버와 Swagger 확인

```bash
./gradlew bootTestRun --args='--spring.profiles.active=test'
```

`bootTestRun`은 Spring Boot Gradle 플러그인이 제공하는 태스크다.
기존 테스트 리소스와 H2를 사용해 서버를 실행하므로 별도 로컬 DB 설정이 필요 없다.
`Ctrl+C`로 종료한다. **데이터는 종료 시 사라지며 실제 PostgreSQL 검증을 대신하지 않는다.**
`test` 프로필의 CORS origin은 재현성을 위해 `http://localhost:5173`으로 고정한다.

| 확인 대상 | 주소·예상 결과 |
| --- | --- |
| Swagger UI | [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) → UI로 이동 |
| OpenAPI JSON | [localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) → `200` |
| Swagger 설정 | [localhost:8080/v3/api-docs/swagger-config](http://localhost:8080/v3/api-docs/swagger-config) → `200` |
| 도메인 API | 아직 미구현. 문서에 경로가 있어도 호출 시 `404`가 날 수 있음 |

포트가 이미 사용 중이면 실행 인자에 `--server.port=18080`을 추가하고 위 URL의 포트를 바꾼다.

### Supabase 연결 후: 일반 개발 실행

1. 아래 환경 변수 절차로 DB 접속 정보를 준비한다.
2. 팀 DB에 스키마가 적용되어 있는지 확인한다.
3. `backend`에서 실행한다.

```bash
./gradlew bootRun
```

일반 실행에는 H2가 없으므로 **PostgreSQL 연결이 필수**다.
`./gradlew bootRun --args='--spring.profiles.active=test'`로 H2를 켤 수는 없다.
테스트 서버를 원하면 위의 `bootTestRun`을 사용한다.

## 4. 환경 변수

`backend`에서 최초 한 번만 복사한다. 이미 `.env`가 있으면 덮어쓰지 않는다.

```bash
cp .env.example .env
```

DB 비밀번호와 API 키는 커밋하지 않는다. `.env.example`의 DB 정보·API 키는 비워 둔다.
실제 비밀값을 이 문서·Issue·PR 본문에 붙여 넣지 않는다.

| 변수 | 기본값·작성 방법 | 실제 적용 범위 |
| --- | --- | --- |
| `PORT` | `8080` | HTTP 포트 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`, 여러 개면 쉼표 구분 | 일반 실행의 `/api/**` CORS |
| `DATABASE_URL` | 필수, JDBC 형식. 아래 Supabase 절차 참고 | PostgreSQL 연결 |
| `DATABASE_USERNAME` | 필수, Session pooler의 사용자 이름 | DB 계정 |
| `DATABASE_PASSWORD` | 필수, 저장소 밖에서 전달받은 값 | DB 비밀번호 |
| `DB_POOL_SIZE` | `5` | 서버 하나의 최대 연결 수. 최소 유휴 연결은 1개 |
| `UPLOAD_DIR` | `./uploads` | 파일 제공 경로. 업로드 API 자체는 후속 구현 |
| `MAX_UPLOAD_SIZE` | `10MB` | 파일 하나의 한도. 전체 요청 한도는 별도로 고정된 `30MB` |
| `WEATHER_PROVIDER` | `openmeteo` | 설정만 존재. 날씨 클라이언트는 미구현 |
| `AI_PROVIDER` · `AI_MODEL` | `mock` | 향후 Mock 구현에서 사용할 설정 |
| `AI_API_KEY` | 비워 둠 | 실제 AI 미사용. 현재 읽거나 호출하는 클라이언트 없음 |
| `AI_TEMPERATURE` · `AI_MAX_TOKENS` | `0.2` · `2048` | 향후 AI 구현의 파라미터 |
| `AI_MOCK_DELAY_MS` | `0` | 향후 Mock 구현의 지연 설정. 현재는 지연 처리가 없음 |

### `.env`가 적용되는 방식

- **Gradle `bootRun`**: `build.gradle`이 `backend/.env`를 읽어 프로세스에 전달한다.
  현재 구현에서는 파일에 있는 키가 같은 이름의 OS 환경 변수보다 우선한다.
- **Gradle `bootTestRun`**: `.env` 로더가 적용되지 않는다. `test` 프로필과 OS 환경 변수를 사용한다.
- **IDE에서 Java 클래스 직접 실행 / `java -jar`**: `.env`를 자동으로 읽지 않는다.
  실행 구성 또는 실행 프로세스의 환경 변수로 전달한다.
- **테스트**: DB는 `application-test.properties`의 H2 설정을 사용한다.

파일은 `KEY=value` 한 줄 형식이며 값 앞뒤 공백은 제거된다.
따옴표·`export`·여러 줄 값·`${변수}` 치환·값 뒤 주석은 지원하지 않는다.
주석은 별도의 `#` 줄에 작성한다. 값 안의 `=`·`#`·`$`는 문자 그대로 전달된다.
이 파일을 셸에서 `source`하면 값이 명령이나 변수로 해석될 수 있으므로 사용하지 않는다.

## 5. Supabase PostgreSQL 연결

Supabase 대시보드 **Connect → Session pooler**의 정보를 사용한다.
지속 실행되는 Spring 서버와 IPv4 개발망에 맞는 연결 방식이다.
공식 절차: [Supabase Spring Boot 가이드](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot),
[DB 연결 방식](https://supabase.com/docs/guides/database/connecting-to-postgres).

| `.env` 항목 | 작성할 형태 |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://<POOLER_HOST>:5432/postgres?sslmode=require` |
| `DATABASE_USERNAME` | `postgres.<PROJECT_REF>` — 대시보드의 값을 그대로 사용 |
| `DATABASE_PASSWORD` | DB 비밀번호. Supabase anon key나 service role key가 아님 |

`<POOLER_HOST>`·`<PROJECT_REF>`는 형식 설명이며 실제 값으로 바꾼다.
사용자 이름과 비밀번호를 URL 안에 넣지 않는다.
`sslmode=require`는 TLS 연결을 요구한다. 인증서·호스트명 검증까지 설정하려면
[PostgreSQL JDBC SSL 문서](https://jdbc.postgresql.org/documentation/ssl/)의 `verify-full` 설정을 따른다.

### 스키마와 데이터

- 스키마의 원본은 `database/schema.sql`, 데모 데이터는 `database/seed.sql`이다.
- 앱의 `ddl-auto=validate`는 **이미 존재하는 테이블과 Entity를 비교**한다.
  `create`·`update`로 바꿔 팀 공용 DB를 수정하지 않는다.
- `spring.sql.init.mode=never`라 서버가 SQL 파일을 자동 실행하지 않는다.
- 현재 Entity가 없으므로 서버 기동 성공만으로 10개 테이블의 정합성을 검증했다고 할 수 없다.

DB가 이미 준비되어 있으면 **초기화하지 않고** 연결만 확인한다.
저장소 루트의 `./scripts/check-db`는 Docker 안의 `psql`로 테이블·시드 현황을 조회한다.
Docker가 없는 경우 Supabase SQL Editor에서 아래 조회를 실행할 수 있다.

```sql
SELECT current_database(), current_user, version();
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

최초 스키마 적용은 [database/README.md](../database/README.md)를 따른다.
**`schema.sql`과 `scripts/db-apply`는 기존 테이블을 삭제한다.** 팀 DB 초기화 담당자와
대상을 확인한 뒤 실행한다. 이번 환경 설정에서는 DB 초기화를 실행하지 않았다.

## 6. IDE와 React 연동

### IntelliJ IDEA

1. `backend`를 Gradle 프로젝트로 연다. 모노레포를 열었다면 `backend/build.gradle`을 연결한다.
2. Project SDK·Language level·Gradle JVM을 **21**로 지정한다.
3. Gradle 배포는 **Wrapper**를 선택하고 프로젝트를 동기화한다.
4. IDE의 Gradle 실행 구성에서 `bootRun` 또는 `bootTestRun`을 실행한다.
   Gradle 프로젝트·작업 디렉터리는 `backend`다.
5. `MiniAiWebServiceApplication`을 직접 실행하는 경우 작업 디렉터리를 `backend`로
   두고 DB 환경 변수를 실행 구성에 넣는다. 애플리케이션 메인 클래스는
   `com.skala.miniai.MiniAiWebServiceApplication`이다.

Lombok을 사용하는 코드를 추가한 뒤 IDE만 오류를 표시하면 annotation processing
설정과 Gradle 동기화를 확인한다. `.idea`·개인 실행 설정·절대 경로는 커밋하지 않는다.

### React · TypeScript

프런트엔드의 `VITE_API_BASE_URL=http://localhost:8080/api`와 백엔드의
`CORS_ALLOWED_ORIGINS=http://localhost:5173`을 맞춘다.
프런트엔드 API 주소에는 `/api`가 있지만 **CORS origin에는 경로가 없다.**
`localhost`와 `127.0.0.1`도 서로 다른 origin이다.

서버 둘은 별도 터미널에서 실행한다. FE 절차는 [frontend/README.md](../frontend/README.md)를 따른다.
백엔드는 `Location`을 노출하므로 React에서 생성된 리소스의 주소를 읽을 수 있다.
근거: [Spring MVC CORS 설정](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html).

## 7. 코드 작업 기준

```text
backend/
├── build.gradle / settings.gradle / gradlew
├── .env.example                  환경 변수 양식
├── README.md / SETUP.md          빠른 안내 / 상세 개발환경
└── src/
    ├── main/
    │   ├── java/com/skala/miniai/
    │   │   ├── MiniAiWebServiceApplication.java
    │   │   └── config/          CORS, 로컬 사진 파일 제공
    │   └── resources/application.properties
    └── test/
        ├── java/com/skala/miniai/MiniAiWebServiceApplicationTests.java
        └── resources/application-test.properties
```

기능을 구현할 때 `com.skala.miniai` 아래에 해당 도메인 패키지를 만들고
Controller → Service → Repository로 역할을 나눈다. 빈 패키지나 공통 부모 클래스를
미리 만들지 않는다. HTTP 입력 검증은 DTO와 `jakarta.validation`, DB 변경 단위는
Service의 트랜잭션에서 처리한다. Entity를 응답으로 직접 내보내지 않는다.

API 필드명은 camelCase, DB는 snake_case다. 여행 날짜는 `LocalDate`, 기록 시각은
UTC `Instant` 등을 사용해 API의 ISO 8601 UTC 계약을 지킨다.
JDBC 시간대는 UTC로 설정되어 있으며 API 직렬화도 DTO에서 따로 확인한다.
DB 스키마를 수정하면 `docs/05-erd.md`, API를 바꾸면 `docs/06-api-spec.md`를 함께 수정한다.

현재 `/uploads/**`는 인증 없이 제공되는 데모용 경로다. 로그인 제외 결정에 따른
기존 제약이며, 개인정보가 포함된 사진 대신 시연용 사진을 사용한다.

### AI 작업의 시작 조건

`docs/07-ai-ready.md`의 입력·출력 Schema가 **TBD**이므로 임의의 필드를 만들어
`AiClient`·`MockAiClient`를 확정하지 않는다. API Architect와 스키마 확정 후 구현한다.

확정 후에도 다음 경계는 유지한다.

- `POST /api/ai-jobs` → **202 + jobId**, `GET /api/ai-jobs/{jobId}` → 상태·결과 조회.
- Mock도 DB에 작업을 기록하고 FE는 폴링한다. 가상 스레드 설정만으로 비동기 작업이 구현되지는 않는다.
- `BAG_CHECK`·`PACKING_LIST`·`WEIGHT_ESTIMATE`·`RULE_CHECK` 네 종류의 규격을 지킨다.
- 실제 Vision/LLM은 추후 구현체로 교체한다. 지금은 SDK·실제 호출·큐를 추가하지 않는다.
- 실제 구현체가 생기기 전에는 `AI_PROVIDER`만 바꿔도 실제 AI가 동작하지 않는다.
- 반입 규정의 최종 판단은 규칙 엔진, 인식 결과의 최종 승인은 사용자 책임으로 둔다.

### 후속 작업에서 확정할 사항

| 항목 | 상태·담당 |
| --- | --- |
| AI 4종 입출력 Schema·Mock fixture | **TBD** — API Architect, `docs/07-ai-ready.md` |
| 승인 물품의 체크리스트 자동 등록·출처 표시 규칙 | **TBD** — FE·BE·Data Architect. 화면은 사진 출처를 구분하지만 API·DB `source`는 `RULE/AI/USER`만 정의 |
| Supabase 접속 정보·스키마 적용 현황 | **확인 완료** — 사용자가 지정한 별도 로컬 체크아웃의 `.env`로 연결, 설계된 테이블 10개 존재 확인 |
| 도메인별 구현 담당 | **TBD** — BE 담당자끼리 엔드포인트·파일 단위로 분담 |

위 미확정 사항을 환경 설정 과정에서 임의로 변경하지 않았다.

## 8. 빌드·검증·협업

```bash
./gradlew test         # H2 + Swagger + CORS 테스트
./gradlew build        # 테스트와 실행 JAR 생성
./gradlew bootJar      # 테스트 없이 실행 JAR만 생성
```

실행 JAR는 `build/libs/mini-ai-web-service-0.0.1-SNAPSHOT.jar`다.
JAR로 실행할 때는 프로세스에 DB 환경 변수를 전달한 상태에서 실행한다.

```bash
java -jar build/libs/mini-ai-web-service-0.0.1-SNAPSHOT.jar
```

GitHub Actions의 **Backend Build**는 `backend/**` 변경 PR와 main 반영 시
Java 21로 `./gradlew build --no-daemon`을 실행한다. Supabase 비밀값은 필요 없다.
이번 작업에서는 워크플로 파일을 추가했으며 GitHub 원격 실행 결과는 아직 확인하지 않았다.
브랜치 보호의 필수 검사 설정은 변경하지 않았다.

`main`에 직접 커밋·push하지 않는다. 예: `chore/backend-development-setup`,
PR 제목 예: `chore(be): 백엔드 개발환경 설정 보완`.
API 계약 변경은 상대 FE 담당자 확인을 받고, 문서와 코드를 같은 PR에 넣는다.

### 이 작업에서 검증한 것

| 검증 | 결과 |
| --- | --- |
| Java 21 · Gradle Wrapper · `clean build` | 통과 |
| Spring 컨텍스트 + 테스트 H2 연결 | 통과 |
| OpenAPI JSON·Swagger 설정·UI | 자동 테스트 통과 |
| React origin의 JSON preflight·`Location` 노출 | 자동 테스트 통과 |
| 미허용 origin 차단 | 자동 테스트 통과 |
| `bootTestRun` 서버 HTTP 확인 | 18080에서 OpenAPI JSON·Swagger 설정·UI 모두 `200` 확인 |
| Supabase 실접속·테이블 존재 확인 | PostgreSQL 17.6 연결, 설계된 테이블 10개 모두 존재 |
| Supabase 연결 상태의 백엔드 실행 | Hikari 연결·서버 기동 성공, OpenAPI JSON·Swagger 설정·UI 모두 `200` |
| 도메인 API·AI Mock·FE 데이터 바인딩 | 미구현 — 이번 작업은 환경 설정 범위 |

Supabase 검증에는 사용자가 지정한 **별도 로컬 체크아웃의 `.env`**를 읽어
이 작업에서 빌드한 JAR의 프로세스 환경 변수로 전달했다. 접속 정보를 이 체크아웃의
`.env`에 복사하지 않았으므로, 여기서 `bootRun`을 실행하려면 이 폴더의 환경 변수도
설정해야 한다. 두 체크아웃의 코드와 설정 파일은 자동으로 동기화되지 않는다.

조회 당시 사용자 1명·여행 3개·체크리스트 항목 10개·사진 2개·인식 물품 8개를
확인했다. 테이블에는 CHECK 21개·외래 키 11개·기본 키 10개·UNIQUE 2개가 있다.
기존 데이터를 조회했으며 DDL·시드 재적용은 하지 않았다.
이 확인은 연결과 테이블·데이터 현황 확인이며, 아직 없는 Entity의 매핑 검증은 아니다.

## 9. 자주 막히는 지점

| 증상 | 확인할 것 |
| --- | --- |
| Java 버전·`JAVA_HOME` 오류 | 터미널과 IDE의 JDK를 각각 21로 설정 |
| 첫 빌드가 오래 걸림 | Gradle·Maven Central 다운로드 중인지 확인. 발표 전에 한 번 빌드 |
| `'url' must start with "jdbc"` / DB 설정 누락 | `.env`의 DB 세 값과 `jdbc:postgresql://` 접두사. DB 없이 확인하려면 `bootTestRun` |
| 인증 실패 | Session pooler 사용자와 DB 비밀번호 확인. 키·토큰과 혼동하지 않기 |
| 연결 시간 초과 | 프로젝트 실행 상태·pooler 호스트·포트·네트워크 확인 |
| 테이블 없음 / Schema validation 오류 | DDL·Entity 매핑을 비교. 공용 DB에서 `ddl-auto=update`로 회피하지 않기 |
| 브라우저 CORS 오류 | FE의 실제 origin과 `CORS_ALLOWED_ORIGINS` 일치 여부. 변경 후 서버 재시작 |
| `Location`을 못 읽음 | `/api/**` 요청인지, 현재 빌드에 CORS 헤더 노출 설정이 있는지 확인 |
| Swagger 목록이 비어 있음 | 현재는 정상. Controller 구현 후 해당 경로가 나타남 |
| IDE 실행에서만 DB 환경 변수가 없음 | Gradle `bootRun`을 쓰거나 Java 실행 구성에 환경 변수 직접 전달 |
| 포트 사용 중 | 기존 서버 종료 또는 `--server.port=18080`으로 실행 |

30분 이상 막히면 팀 채널에 증상과 비밀값을 제거한 오류를 공유한다.
