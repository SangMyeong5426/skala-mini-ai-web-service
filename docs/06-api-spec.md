# REST API 명세

> 발표 3번 섹션: REST API 명세 요약 (Mock API 포함)
>
> 채점 기준: **"Mock API 엔드포인트 구성 완성도 및 RESTful 규격
> (Method, Path, Status Code) 준수 여부"**, **"Mock API를 활용한 실제 데이터
> 바인딩 및 화면 시연"**

**이 문서가 FE와 BE 사이의 계약이다.** 여기가 먼저 고정되면 두 사람이 서로를
기다리지 않고 동시에 작업할 수 있다. 바꿀 때는 반드시 양쪽에 알린다.

> **2026-09-03 개정 계약:** [Notion 개정안](https://app.notion.com/p/3d0c2ab24ce881d9b06cc065c47b1eb7)에
> 바탕의 사진 우선 흐름을 유지하며, 최신 사용자 결정으로 **사진은 승인 없이 자동 완료 등록**, 추천 채택은 미완료 등록으로 정했다. 이후 [로그인 최종 결정](functional-specification.md)을
> 반영해 기존 업무 API 18개에 인증 API 4개를 더한 **총 22개**로 정의한다. 현재 코드·시드의
> 후속 반영 상태는 [문서 지도](README.md#개정안-반영-상태)에 적는다.

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| Base URL (로컬) | `http://localhost:8080/api` — Spring Boot 기본 포트. [ADR 0001](adr/0001-backend-stack.md)에서 확정 |
| 요청·응답 형식 | `application/json; charset=utf-8` |
| 경로 | 소문자 복수형 명사를 기본으로 한다. 예외: 집계 `/inspection`, 인증 전용 `/auth/signup`·`login`·`logout`·`session` |
| 시각 형식 | ISO 8601 UTC (`2026-09-03T05:30:00Z`) |
| 인증 | **서비스 전체 로그인 필수.** 서버 세션·HttpOnly 쿠키를 사용하고 사용자 ID는 세션에서 결정한다. 요청의 임의 userId나 고정 시드 사용자를 신뢰하지 않는다 |

브라우저 연동 시 `CORS_ALLOWED_ORIGINS`에 지정한 origin만 허용한다.
생성 응답의 `Location`을 React의 `response.headers.get('Location')`으로 읽을 수
있도록 백엔드는 `Access-Control-Expose-Headers: Location`을 설정한다.

### Status Code 사용 규칙

**루브릭이 Status Code 준수를 명시적으로 본다.** 아무 데나 200을 쓰지 않는다.

| 코드 | 언제 쓰는가 |
| --- | --- |
| `200 OK` | 조회 성공, 수정 성공 |
| `201 Created` | 생성 성공. `Location` 헤더에 새 리소스 경로를 넣는다. 회원가입은 공개 회원 상세 API가 없어 본문만 반환 |
| `202 Accepted` | **비동기 작업을 접수했고 아직 끝나지 않았다.** AI 호출이 여기 해당 |
| `204 No Content` | 삭제·로그아웃 성공. 본문 없음 |
| `400 Bad Request` | 요청 형식·값이 잘못됨 |
| `401 Unauthorized` | 로그인 실패·보호 자원 요청의 세션 없음/만료 |
| `403 Forbidden` | CSRF 토큰 누락·불일치. 세션 상태 재확인 후 필요 시 로그인 |
| `413 Payload Too Large` | 요청 전체 크기 초과 — 사진 여러 장 (`spring.servlet.multipart.max-request-size`) |
| `404 Not Found` | 리소스 없음 또는 로그인한 사용자 소유가 아님 |
| `409 Conflict` | 중복 등 상태 충돌 |
| `500 Internal Server Error` | 서버 오류 |

### 오류 응답 형식

모든 오류는 같은 모양으로 돌려준다. FE가 오류 처리 코드를 한 번만 쓰면 된다.

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "제목은 1자 이상 100자 이하여야 합니다.",
    "field": "title"
  }
}
```

## 엔드포인트 목록

**Status Code 를 함께 적는다.** 루브릭이 `Method, Path, Status Code` 셋을 나란히 본다.

**업무 API 1~18과 기존 `/uploads/**` 파일 조회는 모두 인증 대상이다.** 아래 주요 오류에
공통 `401`을 반복 기재하지 않는다. 상태 변경은 CSRF 검증 대상이며 CSRF 필터가 먼저
거부하면 `403`일 수 있다. FE는 이때 세션 상태를 다시 확인한다. 공개 API는 가입·로그인·
인증 상태 조회뿐이다. 로그아웃은 로그인한 사용자만 처리한다.

### 인증 (UC-01)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 19 | `POST` | `/api/auth/signup` | 닉네임·아이디·비밀번호·이메일로 가입 | `201` (회원 결과, 자동 로그인 안 함) | `400` `403` `409` |
| 20 | `POST` | `/api/auth/login` | 아이디·비밀번호 로그인, 서버 세션 생성 | `200` + 세션 쿠키 | `400` `401` `403` |
| 21 | `GET` | `/api/auth/session` | 로그인 여부·본인 정보·CSRF 토큰 확인 | `200` (미인증도 상태만 반환) | `500` |
| 22 | `POST` | `/api/auth/logout` | 서버 세션 폐기·쿠키 만료 | `204` | `401` `403` |

### 여행 (UC-02 · UC-09)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GET` | `/api/trips` | 내 여행 목록 | `200` | — |
| 2 | `POST` | `/api/trips` | 여행 등록 | **`201`** + `Location` | `400` 날짜 역전·필수값 누락 |
| 3 | `GET` | `/api/trips/{tripId}` | 여행 상세 | `200` | `404` |
| 4 | `PATCH` | `/api/trips/{tripId}` | 여행 수정 | `200` | `400` `404` |
| 5 | `DELETE` | `/api/trips/{tripId}` | 여행 삭제 | **`204`** | `404` |

### 체크리스트 (UC-05 · UC-06)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 6 | `GET` | `/api/trips/{tripId}/items` | 체크리스트 조회 | `200` | `404` |
| 7 | `POST` | `/api/trips/{tripId}/items` | 직접 추가·추천 채택 | **`201`** + `Location`, 재승인·기존 항목 연결은 `200` | `400` `404` `409` |
| 8 | `PATCH` | `/api/trips/{tripId}/items/{itemId}` | 항목 수정·완료 처리 | `200` | `400` `404` |
| 9 | `DELETE` | `/api/trips/{tripId}/items/{itemId}` | 항목 삭제 | **`204`** | `404` |

### 짐 사진 (UC-03)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 10 | `POST` | `/api/trips/{tripId}/photos` | 사진 업로드 (`multipart/form-data`) | **`201`** + `Location` | `400` 형식·용량 초과<br>`413` 요청 한도 초과 |
| 11 | `GET` | `/api/trips/{tripId}/photos` | 사진 목록 | `200` | `404` |
| 12 | `DELETE` | `/api/trips/{tripId}/photos/{photoId}` | 사진 삭제 | **`204`** | `404` |

### 인식 결과 · 사후 수정 (UC-04)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 13 | `GET` | `/api/trips/{tripId}/detections` | 인식 결과 목록 | `200` | `404` |
| 14 | `PATCH` | `/api/trips/{tripId}/detections/{detectionId}` | **자동 등록 결과의 이름·수량·연결 사후 수정** | `200` | `400` `404` `409` |

> **14번은 자동 등록 이후의 선택적 수정이다.** 사진 인식은 BAG_CHECK 완료 처리에서 승인 없이 내 목록에 반영된다. 사진 승인 게이트는 최신 사용자 결정으로 제거했다.

### 검수 결과 (UC-06 · UC-07 · UC-10)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 15 | `GET` | `/api/trips/{tripId}/inspection` | **준비 상태 + 예상 무게 + 반입 판정 통합** | `200` | `404` |

> 화면 `S-06` 하나가 세 영역을 함께 그린다. 세 번 호출하지 않고 한 번에 받는다.
> 영역별로 아직 계산되지 않았으면 해당 키가 `null` 이고, 프런트엔드는 그 영역만
> 로딩 상태로 그린다.

### 반입 규정 (UC-07)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 16 | `GET` | `/api/rules?transport=&keyword=` | 규정 조회 | `200` | `400` 필수 파라미터 누락 |

### 여행 일정 · 캘린더 (S-11)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 23 | `GET` | `/api/trips/{tripId}/itineraries` | 여행 일정 목록 (시간순) | `200` | `404` |
| 24 | `POST` | `/api/trips/{tripId}/itineraries` | 일정 추가 | **`201`** + `Location` | `400` 시각 역전·필수값 누락<br>`404` |
| 25 | `PATCH` | `/api/trips/{tripId}/itineraries/{itineraryId}` | 일정 수정 | `200` | `400` `404` |
| 26 | `DELETE` | `/api/trips/{tripId}/itineraries/{itineraryId}` | 일정 삭제 | **`204`** | `404` |
| 27 | `GET` | `/api/calendar?from=&to=` | 캘린더 — 기간의 여행 구간 + 일정 | `200` | `400` 범위 누락·역전·과다 |

> **캘린더는 별도 자원이 아니다.** 여행 기간과 일정을 날짜로 묶어 만든 <b>조회 전용</b>
> 응답이라 `POST`가 없다. 같은 일정을 두 곳에 저장하면 한쪽만 고쳤을 때 달력과 상세가
> 어긋난다 ([`05-erd.md`](05-erd.md)).
>
> 여행을 두 번 조회하게 하지 않는다 — 달력은 여행 구간과 일정을 겹쳐 그리므로 나눠 받으면
> 두 응답의 도착 순서에 따라 화면이 깜빡인다. `from`·`to`는 **필수**다. 서버가 어느 달을
> 보여줄지 추측하지 않는다. 한 번에 조회할 수 있는 범위는 366일이다.

### 3D 가방 정리 (S-12)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 28 | `GET` | `/api/trips/{tripId}/packing-layout` | 배치 + 정리 대기 목록 | `200` | `404` |
| 29 | `PUT` | `/api/trips/{tripId}/packing-layout` | 배치 **전체 교체** | `200` | `400` 남의 항목·중복 배치<br>`404` |
| 30 | `DELETE` | `/api/trips/{tripId}/packing-layout` | **정리 초기화** | **`204`** | `404` |

> `PATCH`가 아니라 **`PUT`** 인 이유는 "지금 화면의 배치 전부"를 저장하는 동작이기 때문이다.
> 드래그마다 요청을 보내면 네트워크 순서가 뒤집혔을 때 물건이 엉뚱한 자리에 남는다.
> 이번에 오지 않은 항목은 **가방에서 뺀 것**으로 처리한다.
>
> 초기화는 배치만 지운다. 체크리스트 항목과 완료 상태는 그대로다.

### AI 확장 지점 (UC-04 · 05 · 07 · 08 · 10)

| # | Method | Path | 설명 | 성공 | 주요 오류 |
| --- | --- | --- | --- | --- | --- |
| 17 | `POST` | `/api/ai-jobs` | **AI 작업 생성** | **`202`** + `Location` | `400` 잘못된 입력, `409` 무게 작업의 현재 목록·가방 상태와 입력 불일치 |
| 18 | `GET` | `/api/ai-jobs/{jobId}` | 작업 상태·결과 조회 | `200` | `404` |

**엔드포인트는 둘뿐이다.** AI 작업이 늘어도 `jobType` 값만 늘고 경로는 그대로다.
근거는 [ADR 0003](adr/0003-ai-job-endpoint.md).

### 헷갈리기 쉬운 두 가지

| 상황 | 코드 | 왜 |
| --- | --- | --- |
| `POST /api/ai-jobs` 로 작업 접수 | **`202`** | 접수만 했고 **아직 안 끝났다**. `200` 이 아니다 |
| `GET /api/ai-jobs/{jobId}` 인데 아직 `PENDING` | **`200`** | **조회 자체는 성공했다.** `202` 가 아니다. 본문의 `status` 로 구분한다 |

## 회원가입·로그인 계약 (UC-01)

### 가입·로그인 입력

| 필드 | 가입 | 로그인 | 검증·저장 |
| --- | --- | --- | --- |
| `nickname` | 필수 | — | 앞뒤 공백 제거 후 2~50자, 공백만 불가, 중복 허용 |
| `loginId` | 필수 | 필수 | 앞뒤 공백 제거·소문자 정규화, `[a-z0-9_]{4,30}`, 고유값. 내부 userId와 구분 |
| `password` | 필수 | 필수 | 8자 이상·UTF-8 72바이트 이하. 공백 제거·임의 자르기 없음. BCrypt 해시로 저장 |
| `email` | 필수 | — | 이메일 형식·최대 255자, 앞뒤 공백 제거·소문자 정규화 후 고유값 |

가입은 위 **4개 필드만** 받는다. 비밀번호 확인 필드·추가 프로필·이메일 인증은 요구하지 않는다.
닉네임·이메일로 로그인하지 않는다. 서버와 DB의 고유 제약으로 동시 중복 가입도 거부한다.
비밀번호 원문·해시·세션 쿠키는 응답·로그·AI 입력에 남기지 않는다.

**가입 요청 — `POST /api/auth/signup`**

```json
{
  "nickname": "김지우",
  "loginId": "jiwoo28",
  "password": "<사용자가 입력한 비밀번호>",
  "email": "jiwoo@example.com"
}
```

`201 Created`와 아래 본문을 반환한다. 회원 상세 공개 API를 별도로 두지 않으므로 이 인증
가입 응답은 `Location`을 생략한다. 가입만으로 인증 세션을 만들지 않으며 S-00 로그인 모드로
이동한다. 비밀번호는 비우고 아이디만 유지한다.

```json
{
  "user": { "userId": 12, "loginId": "jiwoo28", "nickname": "김지우", "email": "jiwoo@example.com" }
}
```

**로그인 요청 — `POST /api/auth/login`**

```json
{ "loginId": "jiwoo28", "password": "<사용자가 입력한 비밀번호>" }
```

성공은 `200`과 위와 같은 `user` 객체, 서버 세션 쿠키다. 로그인 시 세션 ID를 교체하고
인증 정보를 저장한다. 실패는 존재하지 않는 아이디·틀린 비밀번호를 구분하지 않는 `401`이다.

```json
{ "error": { "code": "INVALID_CREDENTIALS", "message": "아이디 또는 비밀번호를 확인해 주세요." } }
```

가입 중복은 `409 / DUPLICATE_LOGIN_ID` 또는 `DUPLICATE_EMAIL`과 해당 `field`를 반환한다.
형식 위반은 공통 `400 / VALIDATION_FAILED` 형식을 쓴다.

### 세션·CSRF·로그아웃

`GET /api/auth/session`은 앱 진입과 로그인·로그아웃 성공 후 호출한다. 응답은 `Cache-Control:
no-store`이며, CSRF 토큰 준비를 위해 로그인 전에도 익명 서버 세션 쿠키가 생길 수 있다.
**쿠키 존재만으로 로그인했다고 판단하지 않는다.** `authenticated`와 서버 인증 상태를 확인한다.

```json
{
  "authenticated": false,
  "user": null,
  "csrfToken": "<서버가 발급한 CSRF 토큰>"
}
```

로그인 후에는 `authenticated=true`, `user`는 위 본인 객체다. `csrfToken`은 항상 문자열이다.
이 공개 응답은 다른 사람의 정보·여행·작업을 반환하지 않는다.

- 서버 세션은 Spring Security·HttpSession을 사용한다. 단일 BE의 메모리에 보관하고 재시작 시
  재로그인한다. 세션 테이블·JWT·Redis·Refresh Token을 추가하지 않는다.
- 쿠키 `JSESSIONID`: `HttpOnly`, `SameSite=Lax`, `Path=/`, HTTPS에서는 `Secure`.
  로컬 HTTP에서만 Secure를 해제한다. 세션 유휴 만료는 기본 30분(서버 환경 설정), 자동 로그인 없음.
- FE는 쿠키를 JS로 읽거나 localStorage에 인증 정보를 보관하지 않고 `credentials: include`로
  요청한다. 개발 프록시와 배포는 같은 사이트를 기준으로 한다. CORS는 지정 origin과 credentials만 허용한다.
- 가입·로그인·로그아웃을 포함한 모든 POST/PATCH/DELETE는 세션 응답의 `csrfToken`을
  `X-CSRF-TOKEN` 헤더로 보낸다. 프레임워크의 CSRF 검증을 유지하며 로그인이 성공하거나
  로그아웃하면 토큰을 다시 조회한다. 토큰은 FE 메모리에만 보관한다.
- `403 / CSRF_INVALID`이면 세션을 다시 조회한다. 미인증이면 화면·폴링·사용자 상태를 정리해
  S-00으로 이동한다. 인증 상태면 새 토큰을 받고 입력을 유지해 사용자가 다시 제출하도록 한다.
  승인·가입 같은 변경 요청을 자동 재전송하지 않는다.
- `POST /api/auth/logout` 성공은 `204`, 서버 세션 폐기와 쿠키 만료다. FE는 본인 자료·사진 URL·
  AI 작업 상태·폴링을 비우고 S-00으로 이동한다. 서버 실패 시 로그아웃 완료로 표시하지 않고 재시도한다.

프레임워크 동작의 근거: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html),
[세션 관리](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html),
[비밀번호 저장](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
쿠키 옵션·유휴 시간·입력 제한은 이 프로젝트의 설계 결정이다.

### 모든 서비스 자료의 소유권

| 대상 | 서버 확인 |
| --- | --- |
| 여행·체크리스트·검수 | `trips.user_id == 세션 userId`, 자식 항목이 URL의 tripId에 속하는지도 확인 |
| 사진·인식·파일 | photoId → trip_photos → trips 소유권. 인식·사진 ID를 다른 여행과 조합해도 거부 |
| AI 접수 | 요청 userId를 받지 않음. `ai_jobs.user_id`는 세션에서 채우고 tripId·photoIds·itemIds의 소유권을 검증 |
| AI 결과 | `ai_jobs.user_id == 세션 userId`. 챗봇은 tripId가 null이어도 userId는 필수 |
| 추천 채택 | jobId·candidateIndex와 대상 여행이 같은 사용자 소유인지 확인 |
| 규정·무게 마스터 | 사용자별 소유 데이터는 아니지만 서비스 조회는 로그인 필수 |

소유권 불일치는 존재 여부를 노출하지 않는 `404 / NOT_FOUND`다. 로그인 전 보호 GET은
`401 / AUTH_REQUIRED`이며, 상태 변경 요청은 앞서 설명한 CSRF 거부 순서를 고려한다.
401은 FE가 로그인 화면으로 전환할 JSON 응답이고 서버가 HTML 로그인 페이지로 리다이렉트하지 않는다.

기존 `/uploads/**` 경로도 인증·소유권을 확인하는 서버 처리로 바꿔야 한다. 공개 정적 파일
핸들러를 인증 규칙에서 제외하지 않으며, 경로만 아는 다른 회원에게도 파일을 주지 않는다.
사진·본인 자료 응답은 `Cache-Control: private, no-store`로 브라우저에 보관되지 않게 한다.
22개 JSON API와 별도로 **기존 사진 파일 조회 경로를 보호**하는 것이며 파일용 신규 API는 추가하지 않는다.

### 수용 기준 — 실제 구현 후 검증

- 비로그인 홈·챗봇·여행·AI 결과·사진 파일 접근이 차단된다. 세션 쿠키만 있고 인증되지 않은 경우도 같다.
- 가입 필드는 4개, 로그인 필드는 2개이며 중복 아이디·이메일과 잘못된 비밀번호를 일관되게 처리한다.
- A 계정의 tripId·photoId·itemId·jobId로 B가 조회·수정·채택해도 `404`이고 데이터는 변하지 않는다.
- 로그아웃·만료 후 보호 요청은 거부되고 폴링·이전 사용자의 화면 데이터가 남지 않는다.
- 로그인·로그아웃 뒤 CSRF 토큰을 새로 받아 정상 요청하고 누락·틀린 토큰은 거부한다.
- 로그인 전에 접수하지 못하며, 로그인 중 접수한 AI 작업은 세션 만료 후에도 서버에 남는다.
  본인이 다시 로그인한 뒤만 조회할 수 있다. 세션 만료를 AI 작업 자체의 `FAILED`로 바꾸지 않는다.

## AI 확장 지점 엔드포인트 (Mock)

**AI-Ready 원칙 3 (Asynchronous Pipeline)을 구현한 형태다.** 지금은 Mock이
즉시 고정 JSON을 돌려주지만, 나중에 실제 LLM·비전 모델을 붙여도 이 규격은 그대로다.
LLM 호출은 수 초가 걸리므로 처음부터 비동기 구조로 열어 둔다.

### `jobType` 4종

| `jobType` | Use-Case | 화면 | 지금 | 나중 |
| --- | --- | --- | --- | --- |
| `PACKING_LIST` | UC-05 추가 준비물 추천 | `S-05` | Mock 고정 JSON | LLM |
| `BAG_CHECK` | UC-04 사진 물품 인식 | `S-04` | Mock 고정 인식 결과 | 비전 모델 |
| `WEIGHT_ESTIMATE` | UC-10 예상 무게 산정 | `S-06` `S-07` | Mock 고정 범위 | 품목 중량 DB + LLM 보정 |
| `RULE_CHECK` | UC-07 · UC-08 반입 규정 | `S-06` `S-08` `S-09` | Mock 고정 판정 | LLM 구조화 + 규칙 엔진 |

**`input`·`output`의 내부 구조는 [`07-ai-ready.md`](07-ai-ready.md)의 JSON Schema로
고정한다.** 이 문서는 봉투(HTTP 계약)만 정한다.

> **`input`·`output` 의 내부 구조는 [`07-ai-ready.md`](07-ai-ready.md)의 JSON Schema 가 정본이다.**
> 아래 예시는 그 스키마로 검증했다 — 요청 예시의 `input` 과 완료 예시의 `output` 이
> 그대로 통과한다 (07 "기계 검증"). 이 예시를 고치면 07 의 스키마도 같이 고친다.

### `POST /api/ai-jobs` — AI 작업 생성

작업을 **접수만** 하고 즉시 응답한다. 결과를 기다리지 않는다.

**Request**

```json
{
  "jobType": "PACKING_LIST",
  "tripId": 12,
  "input": {
    "destination": "도쿄",
    "startDate": "2026-10-01",
    "endDate": "2026-10-04",
    "transport": "FLIGHT",
    "purpose": "TOUR",
    "note": "친구 2명, 디즈니랜드, 사진 많이 찍을 예정",
    "alreadyPacked": [
      {
        "name": "충전기",
        "category": "ELECTRONIC",
        "qty": 1
      },
      {
        "name": "보조배터리",
        "category": "ELECTRONIC",
        "qty": 1
      },
      {
        "name": "상의",
        "category": "CLOTHING",
        "qty": 4
      },
      {
        "name": "하의",
        "category": "CLOTHING",
        "qty": 2
      },
      {
        "name": "속옷",
        "category": "CLOTHING",
        "qty": 4
      },
      {
        "name": "가위",
        "category": "ETC",
        "qty": 1
      },
      {
        "name": "화장품 용기",
        "category": "TOILETRY",
        "qty": 1
      },
      {
        "name": "검정 파우치",
        "category": "ETC",
        "qty": 1
      }
    ]
  }
}
```

> `alreadyPacked`는 화면이 가진 실제 준비 완료(`PREPARED`) 물품 목록이며 사진 자동 등록과
> 직접 완료 확인을 포함한다. 기존 입력 스키마를 유지해 필수로 보내되, 화면에 없으면 `[]`도 가능하다.
> **서버가 최종 입력을 결정한다.** 요청 형식·여행 소유권을 검증한 뒤 현재 내 목록을 읽고,
> 그중 PREPARED 항목의 이름·분류·수량으로 `alreadyPacked`를 덮어써 작업 입력으로 저장한다.
> 값이 오래됐거나 `[]`여도 이 차이로 `409`를 반환하지 않는다. 추천·Mock은 보정된 입력을
> 사용하고, 미완료 항목도 현재 내 목록에 있으면 중복 추천에서 제외한다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `jobType` | ✅ | 위 4종 중 하나. 다른 값이면 `400` |
| `tripId` | — | **`RULE_CHECK` 는 여행 없이도 된다.** 챗봇(UC-08 · 화면 `S-09`)은 여행을 등록하지 않아도 쓸 수 있는 보조 흐름이다. 나머지 `jobType` 은 필수 |
| `input` | ✅ | `jobType` 별 스키마는 `07-ai-ready.md` |

**판정은 Mock 이 아니라 규칙 엔진이 낸다.** `AI_PROVIDER` 와 무관하게 `verdict`·`ruleId`·
`conditionNote`·`sourceUrl`·`checkedAt` 은 `transport_rules` 에서 나온다. 아래 표의 판정도
그 결과이고, Mock 이 정하는 것은 **물품과 속성을 어떻게 구조화하느냐**까지다.

`RULE_CHECK` 챗봇 Mock은 S-09의 대표 질문 12개를 지원한다. 질문 문구의 공백·영문 대소문자는
달라도 된다. 표에 있는 수치가 있으면 그 수치로 구조화하고, 지원 물품이지만 판정 수치가 없으면
속성을 비워 둔다 — 그러면 규칙 엔진이 `NEED_MORE_INFO` 와 후속 질문을 만든다.
지원하지 않는 수치나 물품은 규정을 지어내지 않고
`ASK_AIRLINE`으로 응답한다. 내부 `input`·`output` 필드는 07의 스키마를 그대로 사용한다.

| 대표 질문 | Mock 판정 |
| --- | --- |
| `20000mAh 보조배터리 기내 되나요?` | `NEED_MORE_INFO` — 정격 Wh 확인 질문 |
| `보조배터리 용량을 모르겠어요` | `NEED_MORE_INFO` — 정격 Wh 확인 질문 |
| `100Wh 보조배터리 기내 반입되나요?` | `CABIN_OK` — 기내 반입, 위탁 금지 안내 |
| `120Wh 보조배터리 기내 반입되나요?` | `ASK_AIRLINE` — 항공사 사전 승인 안내 |
| `200Wh 보조배터리 기내 반입되나요?` | `CHECKED_FORBIDDEN` — 기내·위탁 모두 금지 안내 |
| `50ml 화장품 기내 반입되나요?` | `CABIN_OK` — 1L 지퍼백 조건 안내 |
| `120ml 화장품 기내 반입되나요?` | `CHECKED_OK` — 위탁수하물 안내 |
| `화장품 용량을 모르겠어요` | `NEED_MORE_INFO` — 용량 확인 질문 |
| `날 길이 5cm 가위 기내 반입되나요?` | `CABIN_OK` — 기내 반입 안내 |
| `날 길이 7cm 가위 기내 반입되나요?` | `CHECKED_OK` — 위탁수하물 안내 |
| `가위 길이를 모르겠어요` | `NEED_MORE_INFO` — 날 길이 확인 질문 |
| `노트북 기내 반입되나요?` | `CABIN_OK` — 보안검색 시 분리 안내 |

보조배터리·화장품·가위 답변의 `followUpQuestion`에 사용자가 각각 `100Wh예요`,
`50ml예요`, `5cm예요`라고 답하면, 직전
`results[]`의 입력 허용 필드 5개를 `items[]`로 함께 보내고 새 작업을 만든다. Mock은
확인된 속성, `CABIN_OK`, `followUpQuestion: null`을 반환한다. 대화 전용 ID나
별도 대화 저장 API는 만들지 않는다.

후속 턴의 `items`와 새 질문의 물품이 다르면 이전 물품 식별값을 유지한 안전 fallback으로
`ASK_AIRLINE`을 반환하며, 새 물품 fixture의 답변이나 후속 질문을 섞지 않는다.

`RULE_CHECK input`은 07의 필수 필드·타입·길이·배열 크기·추가 필드 금지 규칙을 접수 전에
검증한다. 어기면 작업을 만들지 않고 `400 VALIDATION_FAILED`를 반환한다.

**Response — `202 Accepted`**

```http
HTTP/1.1 202 Accepted
Location: /api/ai-jobs/1041
```

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "PENDING",
  "createdAt": "2026-09-03T05:30:00Z",
  "pollAfterMs": 500
}
```

> `pollAfterMs` 는 프런트엔드에 **다음 폴링까지 기다릴 시간**을 알려준다.
> Mock 은 `AI_MOCK_DELAY_MS` 설정을 따라 즉시 끝나지만, 실제 AI 를 붙이면
> 이 값만 늘리면 된다. **프런트엔드 코드는 고치지 않는다.**

### `GET /api/ai-jobs/{jobId}` — 상태·결과 조회

프런트엔드는 이 엔드포인트를 **폴링**한다. Mock 이 즉시 응답해도 폴링으로 구현한다.

**처리 중 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "PENDING",
  "output": null,
  "createdAt": "2026-09-03T05:30:00Z",
  "completedAt": null,
  "pollAfterMs": 500
}
```

**완료 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "COMPLETED",
  "output": {
    "items": [
      {
        "name": "변환 플러그",
        "category": "ELECTRONIC",
        "qty": 1,
        "priority": "REQUIRED",
        "reason": "여행지에서 충전기를 연결할 때 필요한 어댑터입니다.",
        "source": "AI",
        "acceptedItemId": null
      },
      {
        "name": "상비약",
        "category": "MEDICINE",
        "qty": 1,
        "priority": "RECOMMENDED",
        "reason": "개인적으로 사용하는 약이 있다면 준비 여부를 확인하세요.",
        "source": "AI",
        "acceptedItemId": null
      },
      {
        "name": "여권",
        "category": "DOCUMENT",
        "qty": 1,
        "priority": "REQUIRED",
        "reason": "해외 여행 출국 전 여권 준비 여부를 확인하세요.",
        "source": "RULE",
        "acceptedItemId": null
      }
    ],
    "tips": [
      "일본 콘센트는 A타입, 100V입니다.",
      "10월 초 도쿄 계절 평균은 낮 24도, 아침 16도입니다."
    ],
    "weatherSource": "SEASONAL",
    "weatherAsOf": "2026-09-03"
  },
  "modelName": "mock",
  "createdAt": "2026-09-03T05:30:00Z",
  "completedAt": "2026-09-03T05:30:01Z"
}
```

**실패 — `200 OK`**

```json
{
  "jobId": 1041,
  "jobType": "PACKING_LIST",
  "status": "FAILED",
  "output": null,
  "errorMessage": "추천을 만들지 못했습니다. 내 체크리스트는 유지됩니다. 다시 시도하거나 직접 추가해 주세요.",
  "createdAt": "2026-09-03T05:30:00Z",
  "completedAt": "2026-09-03T05:30:01Z"
}
```

> **`FAILED` 도 `200` 이다.** 조회 자체는 성공했기 때문이다. `500` 을 쓰면
> 프런트엔드가 네트워크 오류와 AI 실패를 구분하지 못한다.
> 추천 실패를 알려도 이미 자동 등록되거나 사용자가 채택한 내 목록은 유지한다. 실패를 이유로 기본 목록을
> 자동으로 채택하거나 기존 항목을 삭제하지 않는다.

### 폴링 규약

```text
POST /api/ai-jobs              → 202 + jobId + pollAfterMs
     ↓ pollAfterMs 만큼 대기
GET  /api/ai-jobs/{jobId}      → 200 status=PENDING    ┐
     ↓ pollAfterMs 만큼 대기                            │ 반복
GET  /api/ai-jobs/{jobId}      → 200 status=COMPLETED  ┘
```

| 항목 | 값 |
| --- | --- |
| 최대 폴링 횟수 | 60회 |
| 초과 시 | 화면에 *"시간이 오래 걸립니다"* 와 재시도 버튼. **작업은 서버에 남는다** |
| 화면 이탈 | 결과는 저장된다. 다시 들어오면 `GET` 한 번으로 받는다 |

## 주요 응답 예시

각 예시는 해당 동작 시점의 응답이다. 추천 작업의 최초 완료 응답은 채택 전이며,
아래 내 목록 조회·검수 예시는 사용자가 후보를 채택한 후의 상태를 보여준다.
두 조회는 **같은 tripId 12의 전체 내 목록 7개(완료 6·미완료 1)**를 사용한다.
변환 플러그는 채택했고 여권 필수 후보는 미채택 상태이며, 추천 재조회 시 플러그의
`acceptedItemId`는 `7`이다. 최초 완료 응답의 `null`과 시점을 구분한다.

### `GET /api/trips/{tripId}/inspection` — 검수 결과 (화면 `S-06`)

**이 서비스의 차별점 셋이 한 응답에 있다.** 아래는 자동 등록 완료 항목 8개·채택 후 미완료 1개인
상태다. 부분 집계 예시의 `customs`는 보조배터리 한 건만 보여준다. 기존 SQL 시드의
사진 인식 가위 미등록 상태를 그대로 재현하는 예시는 아니다.

```json
{
  "tripId": 12,
  "readiness": {
    "prepared": [
      {
        "itemId": 2,
        "name": "상의",
        "qty": 4,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 3,
        "name": "하의",
        "qty": 2,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 4,
        "name": "속옷",
        "qty": 4,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 5,
        "name": "충전기",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 6,
        "name": "보조배터리",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 11,
        "name": "가위",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 8,
        "name": "화장품 용기",
        "qty": 1,
        "photoStatus": "CONFIRMED"
      },
      {
        "itemId": 9,
        "name": "검정 파우치",
        "qty": 1,
        "photoStatus": "NEEDS_CHECK"
      }
    ],
    "unprepared": [
      {
        "itemId": 7,
        "name": "변환 플러그",
        "qty": 1,
        "photoStatus": "NOT_IN_PHOTO"
      }
    ],
    "completionRate": 0.889,
    "unacceptedRequiredCount": 1
  },
  "weight": {
    "minG": 4610,
    "typicalG": 5480,
    "maxG": 7010,
    "limitG": 10000,
    "verdict": "ROOM",
    "confidence": "MEDIUM",
    "confidenceReason": "자동 등록 8개 중 6개의 무게를 계산했습니다. 미완료 1개와 무게 정보가 없는 2개는 제외했습니다.",
    "excludedCount": 3,
    "contributions": [
      {
        "name": "상의",
        "typicalG": 200,
        "qty": 4,
        "subtotalG": 800
      },
      {
        "name": "하의",
        "typicalG": 400,
        "qty": 2,
        "subtotalG": 800
      },
      {
        "name": "보조배터리",
        "typicalG": 280,
        "qty": 1,
        "subtotalG": 280
      }
    ]
  },
  "customs": [
    {
      "itemId": 6,
      "name": "보조배터리",
      "verdict": "NEED_MORE_INFO",
      "missingInfo": "배터리 정격(Wh)",
      "reason": "보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라 달라집니다. 라벨의 Wh 를 확인해 주세요.",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02"
    }
  ],
  "notice": "사진 분석 결과는 가방 전체를 확인한 것이 아닙니다. 사진에서 확인되지 않은 물건은 직접 확인해 주세요."
}
```

**필드 이름에 설계가 들어 있다.**

| 필드 | 왜 이 이름인가 |
| --- | --- |
| `photoStatus=NOT_IN_PHOTO` | 사진에서 못 찾았을 뿐 없다는 뜻이 아니다. 실제 완료와 별도다 |
| `weight.minG` `typicalG` `maxG` | **단일 값이 아니라 범위다.** 명세 F-10: *"결과를 실측값처럼 표현하지 않는다"* |
| `weight.excludedCount` | 계산에서 뺀 항목 수를 **숨기지 않는다** |
| `customs[].missingInfo` | 판정을 단정하지 않고 **무엇이 부족한지** 알려준다 |
| `customs[].sourceUrl` `checkedAt` | 명세 9절 *"규정 최신성"* — 출처와 확인 날짜를 항상 함께 |

`weight.verdict`: `ROOM`(여유) · `NEAR`(근접) · `OVER_RISK`(초과 가능성) · `UNKNOWN`(정보 부족)

### `GET /api/trips/{tripId}/detections` — 자동 등록된 인식 결과 (S-04)

```json
{
  "detections": [
    { "detectionId": 2, "photoId": 1, "name": "보조배터리", "qty": 1,
      "confidence": 0.880, "confidenceLevel": "HIGH",
      "missingInfo": "배터리 정격(Wh)", "labelText": null,
      "linkedItems": [{ "itemId": 6, "confirmedByUser": false }] },
    { "detectionId": 6, "photoId": 2, "name": "화장품 용기", "qty": 1,
      "confidence": 0.640, "confidenceLevel": "MEDIUM",
      "missingInfo": "용량(ml)", "labelText": null,
      "linkedItems": [{ "itemId": 8, "confirmedByUser": false }] },
    { "detectionId": 8, "photoId": 2, "name": "검정 파우치", "qty": 1,
      "confidence": 0.430, "confidenceLevel": "LOW",
      "missingInfo": null, "labelText": null,
      "linkedItems": [{ "itemId": 9, "confirmedByUser": false }] }
  ]
}
```

세 물품은 이미 내 체크리스트에 `PREPARED`로 등록되어 있다. `confirmedByUser=false`는
아직 사후 수정을 하지 않았다는 뜻이며 승인 대기·등록 실패가 아니다. `approved`는
신규 요청·응답에서 사용하지 않는다. 기존 DB 컬럼은 호환 목적으로만 남긴다(05).
`missingInfo`·`labelText`는 BAG_CHECK 출력 그대로다. S-04의 확인 필요 안내는 LOW 또는
속성 부족에 붙지만 등록을 막지 않는다. 규정 판정의 속성 보완과 사진 인식 신뢰도는 구분한다.

### BAG_CHECK 완료 시 자동 등록 — 별도 승인 요청 없음

1. 서버는 인식 결과를 검증한 뒤 해당 여행의 쓰기 트랜잭션 안에서 `detected_objects`,
   `item_detections`, `checklist_items`, 작업 결과·`COMPLETED`를 함께 저장한다.
   저장이 실패하면 전체를 롤백하고 작업 오류를 기록한다. 목록 저장 전 완료 응답을 내지 않는다.
2. 성공한 사진의 이름 있는 인식 물품은 신뢰도·속성 부족과 무관하게 자동 등록한다.
   신규 기본값은 `source=PHOTO`, `checkStatus=PREPARED`, `priority=RECOMMENDED`,
   `category=ETC`다. 마스터에 명확히 대응하는 카테고리는 보강할 수 있다.
   식별된 물품이 없는 사진·실패 사진에는 항목을 만들어내지 않는다.
3. 기존 인식 연결을 우선 재사용하고, 없으면 앞뒤·연속 공백을 정리한 같은 이름의 내 항목에
   연결한다. 동일 항목이 없으면 생성한다. 기존 항목의 최초 출처는 유지한다.
   여러 사진에서 같은 물품을 인식하면 수량을 합산하지 않고 큰 관측값을 사용한다.
   자동 연결은 `confirmed_by_user=false`여도 유효하다.
4. 한 작업의 완료 처리는 한 번만 한다. 같은 완료 처리 재시도·GET 폴링은 항목을 다시 생성하거나
   사용자의 수정·삭제를 되돌리지 않는다. 새로운 사진 분석 작업도 기존 연결·동일 이름 항목을
   재사용한다. 사용자가 사후 수정한 이름·수량·준비 상태는 덮어쓰지 않는다.
   사용자 item PATCH도 해당 사진 연결의 사후 확인을 기록해 이후 분석에서 보존한다.
5. 현재 내 목록에 새롭게 매칭된 물품은 `PREPARED`로 처리한다. 같은 사진의 이미 반영된
   연결을 다시 읽는 것만으로 사용자가 바꾼 `UNCHECKED`를 되돌리지 않는다.
6. FE는 BAG_CHECK 완료를 확인한 뒤 내 목록을 다시 조회하고 PACKING_LIST를 요청한다.
   자동 등록 물품을 포함한 현재 내 목록 전체를 제외한 후보만 아래쪽 추천 영역에 표시한다.
   S-04의 수정 입력을 기다리지 않는다. 추천이 실패해도 이미 등록된 물품은 유지한다.

### `PATCH /api/trips/{tripId}/detections/{detectionId}` — 선택적 사후 수정

이 API는 자동 등록된 결과의 수정용이다. 체크리스트를 처음 만들기 위한 승인 API가 아니다.
아래는 앞선 조회 예시 이후 사용자가 화장품 용기의 이름을 수정한 별도 시점이다.

```json
// Request
{ "name": "선크림", "qty": 1, "category": "TOILETRY" }
```

```json
// Response 200
{ "detectionId": 6, "name": "선크림", "qty": 1,
  "linkedItems": [{ "itemId": 8, "name": "선크림", "confirmedByUser": true,
                    "source": "PHOTO", "checkStatus": "PREPARED" }] }
```

| 요청 | 처리 |
| --- | --- |
| 이름·수량·category 수정 | 인식 결과와 연결 항목을 한 트랜잭션에서 갱신. 기존 준비 상태·출처 유지. 이름 1~100자·qty 1~99 등 item 검증 적용 |
| `matchedItemIds: [8]` 또는 `[8, 9]` | 같은 여행의 기존 항목인지 확인한 뒤 연결 배열 전체를 교체. 교체 전후 항목의 수량·준비 상태를 자동 합산하거나 완료 처리하지 않는다 |
| `matchedItemIds: []` | 인식 연결만 해제. 목록 삭제는 item DELETE로 명시적으로 수행하며, 연결 해제만으로 목록·준비 상태를 삭제하지 않는다 |
| `matchedItemIds` 생략 | 기존 연결을 유지한다. 이미 삭제된 항목을 재생성하지 않는다 |
| `approved` 전송 | 지원하지 않는 이전 계약이므로 `400`. FE에 승인 버튼·요청을 두지 않는다 |

연결 항목이 여러 개이고 이름·수량을 한 값으로 덮어쓰면 모호한 경우에는 `409`와 수정 안내를
반환한다. 연결 변경만 요청하거나 각 item PATCH로 명확히 정정할 수 있다. 실패 시 부분 저장하지 않는다.
사후 수정된 연결은 `confirmed_by_user=true`로 기록한다. 이 값은 사후 수정 보호·사진 확인
표시의 근거이며 최초 등록의 조건은 아니다. 오인식 삭제는 기존 item DELETE를 사용한다.

## 도메인 API 요청·응답

**필드명이 계약이다.** 여기가 비면 FE와 BE가 같은 엔드포인트를 다른 필드명으로
구현해도 이 문서로 판별할 수 없다.

### `POST /api/trips` — 여행 등록

```json
// Request
{
  "origin": "서울",
  "destination": "도쿄",
  "countryCode": "JP",
  "startDate": "2026-10-01",
  "endDate": "2026-10-04",
  "purpose": "TOUR",
  "transport": "FLIGHT",
  "airline": "대한항공",
  "departureAirport": "ICN",
  "arrivalAirport": "NRT",
  "bagType": "CARRY_ON",
  "bagEmptyG": 3200,
  "weightLimitG": 10000,
  "note": "친구 2명, 디즈니랜드"
}
```

| 필드 | 필수 | 값 |
| --- | --- | --- |
| `origin` `destination` | ✅ | **이동수단과 무관하게 필수** |
| `startDate` `endDate` | ✅ | `startDate <= endDate` 아니면 `400` |
| `purpose` | ✅ | `TOUR` `BUSINESS` `REST` `STUDY` |
| `transport` | ✅ | `FLIGHT` `TRAIN` `BUS` `CAR` |
| `airline` `departureAirport` `arrivalAirport` | — | 비우면 **일반 기준만 적용**되고 정확도가 낮아진다 |
| `bagType` | — | `CARRY_ON` `MEDIUM` `LARGE` |

```http
HTTP/1.1 201 Created
Location: /api/trips/12
```

```json
{ "tripId": 12, "origin": "서울", "destination": "도쿄",
  "startDate": "2026-10-01", "endDate": "2026-10-04",
  "transport": "FLIGHT", "status": "DRAFT", "createdAt": "2026-09-03T05:30:00Z" }
```

### `GET /api/trips` — 목록 (화면 `S-01`)

```json
{
  "trips": [
    {
      "tripId": 12,
      "origin": "서울",
      "destination": "도쿄",
      "startDate": "2026-10-01",
      "endDate": "2026-10-04",
      "transport": "FLIGHT",
      "status": "CONFIRMED",
      "completionRate": 0.889
    },
    {
      "tripId": 2,
      "origin": "서울",
      "destination": "오사카",
      "startDate": "2026-05-02",
      "endDate": "2026-05-04",
      "transport": "FLIGHT",
      "status": "DONE",
      "completionRate": 1.0
    },
    {
      "tripId": 3,
      "origin": "서울",
      "destination": "부산",
      "startDate": "2026-03-14",
      "endDate": "2026-03-15",
      "transport": "TRAIN",
      "status": "DONE",
      "completionRate": 1.0
    }
  ]
}
```

### `GET /api/trips/{tripId}/items` — 체크리스트 (화면 `S-05`)

```json
{
  "items": [
    {
      "itemId": 2,
      "name": "상의",
      "category": "CLOTHING",
      "qty": 4,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 3,
      "name": "하의",
      "category": "CLOTHING",
      "qty": 2,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 4,
      "name": "속옷",
      "category": "CLOTHING",
      "qty": 4,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 5,
      "name": "충전기",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 6,
      "name": "보조배터리",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 11,
      "name": "가위",
      "category": "ETC",
      "qty": 1,
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED",
      "photoStatus": "CONFIRMED"
    },
    {
      "itemId": 8,
      "name": "화장품 용기",
      "category": "TOILETRY",
      "qty": 1,
      "photoStatus": "CONFIRMED",
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED"
    },
    {
      "itemId": 9,
      "name": "검정 파우치",
      "category": "ETC",
      "qty": 1,
      "photoStatus": "NEEDS_CHECK",
      "priority": "RECOMMENDED",
      "source": "PHOTO",
      "checkStatus": "PREPARED"
    },
    {
      "itemId": 7,
      "name": "변환 플러그",
      "category": "ELECTRONIC",
      "qty": 1,
      "priority": "REQUIRED",
      "source": "AI",
      "checkStatus": "UNCHECKED",
      "photoStatus": "NOT_IN_PHOTO"
    }
  ],
  "completionRate": 0.889,
  "recommendationJobId": 1041,
  "unacceptedRequiredCount": 1
}
```

| 필드 | 값 |
| --- | --- |
| `category` | `DOCUMENT` `CLOTHING` `ELECTRONIC` `TOILETRY` `MEDICINE` `ETC` |
| `priority` | `REQUIRED` `RECOMMENDED` |
| `source` | `RULE` `PHOTO` `AI` `USER` — 최초 등록 경로. `PHOTO`는 사진 인식 완료 시 승인 없이 신규 생성, `AI`·`RULE`은 해당 출처의 후보를 사용자가 채택, `USER`는 직접 추가 |
| `checkStatus` | `PREPARED`가 사진 자동 등록 또는 사용자 직접 완료 상태, 나머지 `UNCHECKED` `NEEDS_CHECK` `NOT_IN_PHOTO`는 미완료. 신규 채택·직접 추가는 `UNCHECKED` |
| `photoStatus` | `CONFIRMED` `NEEDS_CHECK` `NOT_IN_PHOTO` — 인식 연결에서 계산하는 별도 사진 상태 |

### `POST /api/trips/{tripId}/items` — 직접 추가·추천 채택

```json
// Request — 직접 추가
{ "name": "우산", "category": "ETC", "qty": 1, "priority": "RECOMMENDED" }
```

`201 Created` + `Location: /api/trips/12/items/12`. 추천 참조가 없으면 서버가
`source=USER`, `checkStatus=UNCHECKED`로 채운다. 이미 챙겼다면 이후 PATCH로 완료 처리한다.

```json
// Request — 추천 후보 선택·승인, 내용과 수량은 사용자가 수정 가능
{ "name": "변환 플러그", "category": "ELECTRONIC", "qty": 1,
  "priority": "REQUIRED", "recommendation": { "jobId": 1041, "candidateIndex": 0 } }
```

```json
// Response 201 — 선택하지 않은 다른 추천은 내 목록에 넣지 않는다
{ "itemId": 7, "name": "변환 플러그", "category": "ELECTRONIC", "qty": 1,
  "priority": "REQUIRED", "source": "AI", "checkStatus": "UNCHECKED",
  "photoStatus": "NOT_IN_PHOTO" }
```

- `candidateIndex`는 완료된 추천의 `output.items`에서 **0부터 시작하는 위치**다. 작업 완료 후
  후보 배열 순서·원래 내용은 바꾸지 않는다. 서버는 후보의 `acceptedItemId`만 갱신한다.
- 서버가 같은 여행·사용자의 `COMPLETED / PACKING_LIST` 작업인지, 위치가 유효한지 확인한다.
  다른 여행의 작업은 `404`, 미완료 작업은 `409`, 잘못된 위치·값은 `400`이다.
- 신규 항목은 후보의 `source`(`AI` 또는 `RULE`)를 서버가 복사하고 `UNCHECKED`로 만든다.
  클라이언트가 임의로 `source`·완료 상태를 지정하지 않는다.
- 후보의 `acceptedItemId`가 이미 있으면 같은 항목을 `200`으로 반환한다. 재시도 본문의
  이름·수량으로 기존 항목을 다시 덮어쓰거나 완료 상태를 되돌리지 않는다.
- 최초 채택이라도 원래 후보명 또는 수정된 이름과 같은 내 항목이 있으면 그 항목에 연결해
  `200`으로 반환한다. 상태·수량·출처를 유지하며 차이는 항목 PATCH로 사용자가 정정한다.
- 이름 비교는 앞뒤 공백 제거·연속 공백 정리 후 일치를 기준으로 한다. 명백한 동의어는
  후보 생성 단계에서 제외하고, 자동으로 동일시하기 어려운 물품은 사용자 연결로 확인한다.
- 같은 여행의 항목 추가·수정·삭제·사진 자동 등록·추천 채택은 여행 단위 트랜잭션으로 직렬화한다.
  항목과 `acceptedItemId`를 함께 저장해 동시 클릭에도 중복 생성을 막는다(05).
- 이름 1~100자(공백만 금지), qty 1~99 정수, category·priority는 위 enum을 검증한다.
- 여러 후보를 선택하면 기존 단건 POST를 후보별로 호출한다. 일부 실패 시 성공한 항목은
  유지하고 실패 후보만 재시도한다. 전체를 다시 보내도 이미 채택된 후보는 `200`이다.
- 화면에서 후보를 정렬·숨겨도 `candidateIndex`는 원래 응답 배열의 위치를 사용한다.
  이후 사진 자동 등록 등으로 같은 물품이 내 목록에 생겼다면 표시 시 현재 내 목록과 대조해
  `추가됨`으로 처리한다. 저장된 후보 배열을 재정렬하거나 삭제하지 않는다.

### `PATCH /api/trips/{tripId}/items/{itemId}` — 수정·실제 완료 처리

```json
// Request — 보낸 필드만 바꾼다. 추천 채택과 별도 동작이다
{ "checkStatus": "PREPARED", "qty": 2 }
```

사진 없이 직접 챙김 완료를 확인한 항목도 완료율·무게에 포함한다. 수량·완료 여부 변경 후
준비율을 다시 계산하고 현재 입력으로 무게 작업을 다시 요청한다. 사진 재분석만으로
`PREPARED`를 취소하지 않는다. `photoStatus`는 조회 전용이며 PATCH로 받지 않는다.

### `DELETE /api/trips/{tripId}/items/{itemId}` — 삭제

`204 No Content`. 같은 트랜잭션에서 그 항목을 참조하는 해당 여행의 추천
`acceptedItemId`도 `null`로 해제한다. 화면은 내 목록·추천을 다시 읽는다.
사진 자동 등록 항목을 삭제하면 해당 item 연결도 삭제한다. 완료 작업의 GET·새로고침·재시도는
삭제한 물품을 다시 만들지 않는다. 사용자가 같은 사진을 **새 작업으로 명시적으로 재분석**하면
다시 인식된 물품이 새로 등록될 수 있으므로 재분석 버튼에 이를 안내한다.

### 완료율·사진 상태·현재 무게의 공통 규약

- `completionRate = PREPARED 항목 수 / 내 목록 전체 항목 수`. 빈 목록은 `0`이다.
  항목의 qty로 가중하지 않는다. 홈·내 목록·검수 결과에 같은 식을 사용한다.
- 서버는 비율을 **소수 셋째 자리까지 HALF_UP 반올림**한 JSON 숫자로 반환한다(8/9 → `0.889`,
  후행 0 강제 없음). FE는 이 값에 100을 곱해 소수 첫째 자리에서 HALF_UP 반올림한 정수 %로
  표시한다(`0.889` → `89%`). 홈·S-05·S-06 모두 같은 표시 함수를 쓰고 비율을 다시 계산하지 않는다.
- `unacceptedRequiredCount`는 내 목록 조회와 `inspection.readiness`에 함께 반환하는 **조회 시 계산값**이다.
  가장 최근 완료된 PACKING_LIST 작업에서 `priority=REQUIRED`이고, 유효한 `acceptedItemId`도
  없고 현재 내 목록에 같은 이름의 항목도 없는 후보를 센다. 이름 비교는 위 채택 규약을 따른다.
  AI·RULE 출처 모두 포함하며, 미완료로 채택한 필수 물품은 이 경고 대신 내 목록 미완료로 표시한다.
  후보가 있으면 0 이상의 정수, 완료된 추천 작업 자체가 없으면 `null`(필수 추천 확인 전)이다.
  빈 후보 배열을 가진 완료 작업은 `0`이다. 컬럼·엔드포인트를 추가하지 않는다.
- S-05·S-06은 `unacceptedRequiredCount > 0`일 때 **`미채택 필수 후보 n건`**과 S-05 추천 영역으로
  가는 `확인하기`를 표시한다. 내 목록 완료율이 `1`이어도 경고를 유지한다. `null`은 0건으로
  표현하지 않고 `필수 추천 확인 전`을 표시한다. 경고는 완료율·무게·최종 저장을 변경하지 않는다.
  완료율은 선택한 목록의 준비 상태이며 모든 여행 필수품을 갖췄다는 보장이 아니다.
- `photoStatus`: 유효한 사진 연결 중 HIGH/MEDIUM 또는 사후 확인된 연결이 있으면 `CONFIRMED`,
  LOW 연결만 있고 사후 확인되지 않았으면 `NEEDS_CHECK`, 연결이 없으면 `NOT_IN_PHOTO`다.
  `approved`는 사용하지 않는다. 속성 부족은 별도 규정 안내이며, 사진 상태와 준비 완료는 독립적이다.
- `readiness.prepared`와 `readiness.unprepared`는 내 목록을 완료 여부로 나눈다. 낮은 신뢰도로
  `photoStatus=NEEDS_CHECK`인 사진 물품도 자동 등록된 PREPARED라면 완료 집계에 포함한다.
  미채택 추천은 내 목록 집계에 넣지 않는다.
- S-06의 `사진 확인`은 S-04의 **이미 등록된** 물품 사후 수정으로 연결한다. 사용자는 이름·수량·
  연결을 필요할 때 PATCH하고 돌아온다. 수정하지 않아도 내 목록은 유지된다. 등록용 승인 버튼·
  `approved=true` 요청을 보내지 않는다. 복귀 시 현재 준비 상태·무게를 다시 조회한다.
- `GET items`는 내 목록과 가장 최근 완료된 추천 작업의 `recommendationJobId`(없으면 `null`)를
  반환한다. 재접속 시 이 ID로 후보를 다시 읽는다. 생성 중인 새 추천은 기존 내 목록을 가리지 않는다.
- `inspection.weight`는 가장 최근 완료된 무게 작업 중 **현재 입력과 같은 결과**만 반환한다.
  현재 입력과 다르거나 결과가 없으면 `null`이다. 완료 여부·이름·수량·가방 정보 및 계산 제외
  목록을 작업 입력과 대조한다. 오래된 작업이 뒤늦게 끝나도 현재 결과로 사용하지 않는다.
- S-06·S-07에서 무게가 `null`이면 현재 입력으로 `WEIGHT_ESTIMATE`를 요청·폴링한다.
  추천 채택만으로는 합계가 늘지 않으며, 실제 완료 확인 후에만 계산 대상에 포함된다.

### `POST /api/trips/{tripId}/photos` — 사진 업로드

`multipart/form-data`. 파트 이름은 `files`(복수 가능), `bagKind`(`CABIN`|`CHECKED`).

```json
// 201 Created
{ "photos": [{ "photoId": 1, "fileUrl": "/uploads/demo/bag-01.jpg",
               "bagKind": "CABIN", "uploadedAt": "2026-09-03T05:31:00Z" }] }
```

### `GET /api/rules?transport=FLIGHT&keyword=보조배터리`

```json
{
  "rules": [
    { "ruleId": 1, "verdict": "CABIN_OK", "conditionNote": "100Wh 이하",
      "description": "보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.",
      "sourceUrl": "https://www.airport.kr/ap_ko/905/subview.do",
      "checkedAt": "2026-09-02" }
  ]
}
```

`transport` 는 필수다. 없으면 `400`.

### `GET /api/trips/{tripId}/itineraries` — 여행 일정 (화면 `S-11`)

```json
{
  "itineraries": [
    { "itineraryId": 1, "tripId": 1, "kind": "FLIGHT", "title": "인천 → 나리타",
      "place": "ICN", "code": "KE703",
      "startAt": "2026-10-01T00:20:00Z", "endAt": "2026-10-01T02:50:00Z", "note": "2시간 30분" },
    { "itineraryId": 2, "tripId": 1, "kind": "LODGING", "title": "신주쿠 체크인",
      "place": "호텔 그레이스 신주쿠", "code": null,
      "startAt": "2026-10-01T06:00:00Z", "endAt": null, "note": "15:00 체크인" }
  ]
}
```

| 필드 | 값 |
| --- | --- |
| `kind` | `FLIGHT` `LODGING` `ACTIVITY` `TRANSPORT` `OTHER` |
| `place` | 공항·호텔·장소 이름. **지도 좌표는 두지 않는다** (범위 밖) |
| `code` | 항공편명(`KE703`)처럼 종류마다 다른 짧은 식별자 |
| `endAt` | **nullable.** 끝나는 시각을 모르는 일정이 많다(체크인 등) |

> **목적지는 일정에 없다.** `trips.destination` 에만 있다. 일정마다 다시 적으면 여행을
> 고쳤을 때 일정이 옛 목적지를 가리킨다 ([`05-erd.md`](05-erd.md) 3NF).
>
> 시각은 다른 응답과 같이 **ISO 8601 UTC** 다. 현지 시간 변환은 화면이 한다 —
> 서버가 어느 시간대로 보여줄지 추측하지 않는다.

`POST` 는 `kind` · `title` · `startAt` 이 필수이고 `201 Created` + `Location` 이다.
`PATCH` 는 보낸 필드만 바꾼다. `endAt` 이 `startAt` 보다 빠르면 `400` 이다.

### `GET /api/calendar?from=2026-10-01&to=2026-10-31` — 캘린더 (화면 `S-11`)

```json
{
  "from": "2026-10-01",
  "to": "2026-10-31",
  "trips": [
    { "tripId": 1, "origin": "서울", "destination": "도쿄",
      "startDate": "2026-10-01", "endDate": "2026-10-04",
      "transport": "FLIGHT", "status": "CONFIRMED" }
  ],
  "days": [
    { "date": "2026-10-01", "tripIds": [1], "itineraries": [ /* 위와 같은 모양 */ ] },
    { "date": "2026-10-02", "tripIds": [1], "itineraries": [ /* … */ ] }
  ]
}
```

| 필드 | 쓰임 |
| --- | --- |
| `trips[]` | 달력에 **색칠할 구간**과 목적지. 화면은 이것으로 기간을 칠한다 |
| `days[]` | **무언가 있는 날만** 담는다. 빈 날까지 채우면 한 달에 빈 칸 31개가 오간다 |
| `days[].tripIds` | 그 날 진행 중인 여행. 여행이 겹치면 둘 이상이다 |

날짜 묶음은 **UTC 기준**이다. 자정 근처 일정이 화면의 현지 날짜와 다르게 보일 수 있고,
이는 시각 계약(ISO 8601 UTC)을 따른 결과다.

### `GET /api/trips/{tripId}/packing-layout` — 3D 가방 정리 (화면 `S-12`)

```json
{
  "tripId": 1,
  "placements": [
    { "itemId": 2, "compartment": "MAIN_LEFT", "posX": 0.300, "posY": 0.650, "posZ": 0.200, "rotated": false },
    { "itemId": 6, "compartment": "MESH",      "posX": 0.600, "posY": 0.250, "posZ": 0.000, "rotated": true }
  ],
  "unplaced": [
    { "itemId": 1, "name": "여권", "category": "DOCUMENT", "qty": 1 }
  ]
}
```

| 필드 | 값 |
| --- | --- |
| `compartment` | `MAIN_LEFT` `MAIN_RIGHT` `FRONT_POCKET` `MESH` `TOP` |
| `posX` `posY` `posZ` | **0~1 상대값.** 픽셀이 아니다 — 화면 크기·기기가 달라도 같은 자리에 놓인다. `posZ` 는 깊이이자 쌓임 순서다 |
| `rotated` | 눕힘·세움. 같은 물건이라도 방향에 따라 자리를 다르게 먹는다 |
| `unplaced[]` | 아직 자리를 안 잡은 물품. 화면의 "정리 대기" 목록이다 |

```json
// PUT 요청 — 지금 화면의 배치 전부를 보낸다
{ "placements": [
    { "itemId": 2, "compartment": "TOP", "posX": 0.111, "posY": 0.222, "posZ": 0.333, "rotated": true }
] }
```

- 이번에 오지 않은 항목은 **가방에서 뺀 것**이다. 응답은 `GET` 과 같은 모양이다.
- 그 여행의 체크리스트 항목만 배치할 수 있다. 다른 여행의 `itemId` 는 `400` 이다 —
  조용히 무시하면 화면은 저장됐다고 믿는다.
- 같은 물품을 두 번 배치하면 `400` 이다. 한 물품은 한 자리이므로 어느 쪽이 맞는지
  서버가 정하게 두지 않는다.
- **배치는 완료 상태와 무관하다.** 가방에 넣었다고 `checkStatus` 가 바뀌지 않는다.
  실제 챙김 확인은 `PATCH /items/{itemId}` 가 한다.

> **필드명은 `camelCase`, DB 컬럼은 `snake_case`다.** 경계에서 변환한다.
> `origin` · `checkStatus` · `bagEmptyG` 처럼 [`05-erd.md`](05-erd.md)의 컬럼과
> 1:1로 대응하므로 어느 쪽을 봐도 같은 것을 가리킨다.

## Mock 서버 운영 방식

**Mock 을 백엔드 안에 둔다.** Postman Mock Server 를 쓰지 않는다.

| | 근거 |
| --- | --- |
| **데모** | `checklist.md` 의 데모 사고 방지 — *"인터넷이 끊겨도 되도록 Mock을 로컬 백엔드에 둔다"* |
| **발표** | `AiClient` 인터페이스에 구현 둘(`MockAiClient` · `RealAiClient`)을 두면 **교체 지점이 코드로 드러난다** |
| **일관성** | Mock 도 같은 DB(`ai_jobs`)에 기록한다. 폴링·상태 전이가 실제와 똑같이 돈다 |

```java
public interface AiClient {
    AiJobResult run(AiJobType type, JsonNode input);
}
```

```properties
# .env 만 바꾸면 교체된다. 코드는 고치지 않는다.
AI_PROVIDER=mock          # mock | openai | anthropic
AI_MOCK_DELAY_MS=0        # 발표 때 1000~2000 으로 두면 로딩 화면을 보여줄 수 있다
```

- Postman Collection 링크: TBD — 팀 외부 공유용으로만 쓴다
- Postman Mock URL: **쓰지 않음**

## OpenAPI 명세

**손으로 쓰지 않는다.** `springdoc-openapi` 가 컨트롤러에서 자동 생성한다.

| | 주소 |
| --- | --- |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI 문서 | <http://localhost:8080/v3/api-docs> |

2일차 산출물인 REST API 명세가 여기서 나온다. 발표 때 **실제로 열어 보여주면**
"Mock API 엔드포인트 구성 완성도"를 말이 아니라 화면으로 증명할 수 있다.

> 컨트롤러를 구현하기 전까지는 `paths` 가 비어 있다. 이 문서가 **먼저 고정되고**
> 구현이 그것을 따라간다. 둘이 어긋나면 이 문서가 기준이다.
