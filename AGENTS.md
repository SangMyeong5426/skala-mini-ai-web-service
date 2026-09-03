# 작업 규칙

이 저장소에서 에이전트가 작업할 때 지킬 것.

## 이 문서는 두 이름으로 존재한다

[`AGENTS.md`](AGENTS.md)와 [`CLAUDE.md`](CLAUDE.md)는 **같은 문서다.**
내용이 한 글자도 다르지 않다. 도구마다 읽는 파일이 달라서 두 이름으로 둔 것이다.

| 파일 | 읽는 도구 |
| --- | --- |
| `AGENTS.md` | Codex, Cursor 등 대부분의 코딩 에이전트 |
| `CLAUDE.md` | Claude Code |

팀원마다 쓰는 도구가 다르므로 둘 다 필요하다. 어느 쪽을 열어도 된다.

**한쪽을 고치면 반드시 다른 쪽도 같은 내용으로 만든다.**

```bash
cp CLAUDE.md AGENTS.md
```

깜빡해도 CI가 잡는다. 두 파일이 다르면 `Docs Sync` 검사가 실패한다.

## 이 프로젝트의 성격

SKALA Full-Stack Engineering의 **3일짜리 Mini-project**다. 운영 서비스가 아니다.

- 평가 대상은 **완성도가 아니라 설계 타당성**이다. 정량 60% + Peer Review 40%.
- **AI는 기본이 Mock이다.** `AI_PROVIDER=mock`이 기본값이고 3일차 데모도 이걸로 돌린다 —
  발표가 네트워크·비용·응답 시간에 묶이면 안 된다. Mock이든 실제든 응답은
  `docs/07-ai-ready.md`의 스키마를 **정확히** 지킨다.
- **실제 모델을 부르는 곳은 둘뿐이다.** `AI_PROVIDER=openai`일 때만 나간다.
  - `BAG_CHECK` — 짐 사진을 인식해 내 체크리스트에 자동 등록한다.
  - `PACKING_LIST` — 여행지·날짜·기간·이동수단과 내 목록을 보고 빠진 준비물을 추천한다.
    날씨는 Open-Meteo에서 읽어 프롬프트에 넣는다(키 불필요).

  이 서비스의 약속이 그 둘이라 Mock으로는 설계 타당성을 보일 수 없어서 실제로 붙였다.
  `WEIGHT_ESTIMATE`·`RULE_CHECK`는 07이 **애초에 AI를 두지 않기로 한 자리**다(무게는 산식,
  반입 판정은 규칙 엔진). `openai`에서도 Mock 그대로 둔다.
- **`openai`는 회사가 아니라 프로토콜 이름이다.** 실제로 어디로 나갈지는 `AI_BASE_URL`이 정한다.
  팀은 무료 티어를 쓰려고 Gemini의 OpenAI 호환 엔드포인트를 가리켜 두었다
  (`backend/.env.example`). 코드는 어느 쪽인지 모르고, 알 필요도 없다.
- **여기서 더 늘리지 않는다.** LLM SDK를 설치하지 않는다 — `RestClient`로 직접 부른다.
  새 `jobType`을 만들지 않고, 위 둘 밖의 자리에 AI를 붙이지 않는다.
- 주된 산출물은 `docs/`의 설계 문서다. 코드는 **우선순위 상위 화면 + Mock API**까지다.
  PDF 최소 기준은 화면 1~2개, 팀 목표는 인증 포함 1차 7화면(`S-00`~`S-06`)이다.
  설계는 S-00 로그인·회원가입과 기존 업무 화면 10개, 총 11개를 다룬다 — 설계 범위와 구현 범위를 혼동하지 않는다.

**기능을 늘리지 않는다.** 요청받은 것만 하고, 화면이나 엔드포인트를 임의로
추가하지 않는다. 3일 안에 못 끝내는 것을 벌여 놓으면 발표에서 손해다.

## 명세와 코드를 함께 고친다

`docs/`의 문서와 코드는 짝이다. 한쪽만 고치지 않는다.

| 코드를 바꿨다면 | 함께 고칠 문서 |
| --- | --- |
| API 엔드포인트·응답 | `docs/06-api-spec.md` |
| DB 스키마 (`database/schema.sql`) | `docs/05-erd.md` |
| 화면 흐름 | `docs/03-wireframe.md` |
| AI 입출력 규격 | `docs/07-ai-ready.md` |

문서와 코드가 어긋나 있으면 **혼자 판단해서 맞추지 말고 사용자에게 묻는다.**
어느 쪽이 틀렸는지는 작성자만 안다.

## AI-Ready 설계를 깨뜨리지 않는다

이 프로젝트의 주제다. 아래를 어기면 설계가 무너진다.

- Mock 응답 JSON은 `docs/07-ai-ready.md`의 출력 스키마를 **정확히** 지킨다.
  나중에 실제 LLM이 같은 스키마로 응답해야 프런트엔드를 고치지 않는다.
- AI 호출 엔드포인트는 Mock이라도 **비동기 구조**를 지킨다.
  `POST` → `202 Accepted` + 작업 ID, `GET`으로 상태 조회.
- 프런트엔드는 Mock이 즉시 응답하더라도 **폴링으로 구현한다.**
- API 키·모델명·temperature는 코드에 쓰지 않는다. 전부 환경 변수로 읽는다.

## JPA 엔티티를 쓸 때

`application.properties` 가 `spring.jpa.hibernate.ddl-auto=validate` 다.
**매핑이 하나라도 어긋나면 앱이 아예 뜨지 않는다.** 오류는 `BeanCreationException`
안쪽에 묻혀 있어 원인을 찾는 데 시간이 오래 걸린다.

아래는 실제 Supabase(PostgreSQL 17.6 · Hibernate 7.4.5)에 붙여 **직접 재현하고
고쳐 본 것**이다. 추측이 아니다.

| `schema.sql` 의 타입 | 이렇게 쓴다 | 안 그러면 |
| --- | --- | --- |
| `GENERATED ALWAYS AS IDENTITY` | `@GeneratedValue(strategy = GenerationType.IDENTITY)` | `Schema validation: missing sequence [trips_seq]` |
| `JSONB` | `@JdbcTypeCode(SqlTypes.JSON)` + `String` | `found [jsonb (Types#OTHER)], but expecting [varchar(255) (Types#VARCHAR)]` |
| `NUMERIC(4,3)` | `BigDecimal` + `@Column(precision=4, scale=3)` | `found [numeric (Types#NUMERIC)], but expecting [float(53) (Types#FLOAT)]` |
| `CHAR(2)` · `CHAR(3)` | `@JdbcTypeCode(SqlTypes.CHAR)` | `found [bpchar (Types#CHAR)], but expecting [varchar(255) (Types#VARCHAR)]` |
| `TIMESTAMPTZ` | `OffsetDateTime` | `LocalDateTime` 도 검증은 통과하지만 `06-api-spec.md` 가 ISO 8601 UTC(`...Z`)를 계약으로 못박았다 |

**`@GeneratedValue` 를 전략 없이 쓰지 않는다.** Hibernate 6/7 의 기본값(`AUTO`)은
시퀀스를 찾는데 `schema.sql` 에는 시퀀스가 없다.

**`hypersistence-utils` 같은 라이브러리를 넣지 않는다.** `@JdbcTypeCode` 만으로 된다.

### 복합 기본키 두 개

`item_detections` 와 `item_rule_checks` 는 복합 PK 다. `@IdClass` 를 쓰고
**ID 클래스에 `equals` · `hashCode` 를 반드시 넣는다.** 없으면 기동할 때마다
`HHH000038: Composite id class does not override equals()` 경고가 뜬다.

### 지연 로딩

`spring.jpa.open-in-view=false` 다. 컨트롤러까지 엔티티를 들고 가지 않는다.
서비스 계층(`@Transactional`) 안에서 DTO 로 바꿔서 내보낸다.

### 엔티티를 만든 직후

```bash
cd backend && ./gradlew bootRun
```

`Initialized JPA EntityManagerFactory` 와 `Started MiniAiWebServiceApplication`
두 줄이 뜨는 것을 확인하고 커밋한다. **`validate` 는 읽기 전용이라 DB 를 건드리지 않는다.**

## Git

- `main`에 직접 커밋·push하지 않는다. 작업 브랜치를 만든다.
- 브랜치: `<type>/<kebab-case>` — `feat` `fix` `refactor` `docs` `test` `chore` `ci` `hotfix`
- 커밋·PR 제목: `type(scope): 요약` — scope는 `fe` `be` `db` `api` `docs`
- 상세는 `CONTRIBUTING.md`.

### PR 본문·리뷰를 올릴 때

**`gh`에 본문을 셸 인자로 넘기지 않는다.** 큰따옴표 안의 백틱은 셸이 명령으로
실행하고, 그 자리는 출력으로 바뀐다. 출력이 비면 내용이 통째로 사라진다.

```bash
# 하지 않는다 — `psql` 이 실행되고 사라진다
gh pr review 15 --body "호스트의 `psql` 클라이언트가 필요합니다"

# 이렇게 한다
gh pr review 15 --body-file review.md
gh pr comment 15 --body-file - <<'EOF'
호스트의 `psql` 클라이언트가 필요합니다
EOF
```

heredoc은 **구분자를 따옴표로 감싼다**(`<<'EOF'`). 감싸지 않으면 안쪽이 그대로
해석된다.

> 실제로 겪은 일이다. PR #15에서 리뷰 본문의 `` `psql` ``과
> `` `docker exec … < database/schema.sql` ``이 사라져 문장이 *"호스트의  클라이언트가"* 로
> 올라갔다. **리다이렉션이 포함된 명령이 리뷰어 PC에서 실행됐을 수도 있다.**
> 마크다운을 쓰는 리뷰일수록 백틱이 많아 위험하다.

## 안전

- **비밀값을 파일에 쓰지 않는다.** DB 비밀번호, API 키. `.env.example`에는
  형식만 남기고 값은 비운다.
- `git push --force`, `git reset --hard`, 커밋 이력 재작성을 하지 않는다.
- `.env` 파일을 커밋하지 않는다.

### 이력 재작성의 유일한 예외

**팀원이 아직 clone하지 않은 세팅 단계**에서, 사용자가 명시적으로 지시한
경우에만 재작성할 수 있다. 그 외에는 어떤 이유로도 하지 않는다.

이 예외가 좁은 이유는 타이밍이다. 누군가 clone한 뒤에 `main` 이력을 바꾸면
그 사람의 저장소가 원격과 갈라져서, 각자 수동으로 복구해야 한다. 3일짜리
프로젝트에서 5명이 동시에 그 작업을 하는 것은 사실상 불가능하다.

재작성할 때는 아래를 지킨다.

1. 백업 ref를 먼저 만든다. (`git tag backup-before-rewrite HEAD`)
2. 재작성 전 **트리 해시**를 기록한다. (`git rev-parse HEAD^{tree}`)
3. 재작성 후 트리 해시가 같은지 확인한다. **다르면 즉시 되돌린다** —
   메시지만 바꾸려 했는데 파일 내용이 변했다는 뜻이다.
4. 커밋 수와 저자가 보존됐는지 확인한다.
5. 원격 반영 후 GitHub API로 결과를 다시 검증한다.

> 실제 적용 사례: 2026-09-02, 초기 커밋 3개에서 `Co-Authored-By` 트레일러를
> 제거했다. 팀원 초대 전이었고, 위 절차를 모두 거쳤다.

### 두 개의 관문

`main`은 **서로 다른 두 장치**로 보호된다. 하나만 통과해도 push되지 않는다.

| 관문 | 위치 | 누구에게 걸리나 | 우회 |
| --- | --- | --- | --- |
| `pre-push` 훅 | 로컬 (`.githooks/`) | `scripts/setup-git-hooks`를 실행한 사람 | `--no-verify` |
| 브랜치 보호 | GitHub 서버 | **관리자를 제외한** 전원 | 웹 UI에서 설정 변경 (사람만 가능) |

**브랜치 보호는 관리자에게 걸리지 않는다.** `Include administrators`가 꺼져 있어서다.
관리자에게 남는 관문은 로컬 훅 하나뿐이고 그것도 `--no-verify`로 넘어가므로,
**관리자 계정은 사실상 자기 규율로만 막힌다.** 관리자로 작업할 때 특히 주의한다.

정당한 이유로 보호를 잠시 풀었다면 **작업 직후 반드시 다시 건다.**

현재 브랜치 보호 설정은 아래와 같다.

| 항목 | 상태 |
| --- | --- |
| PR 없이 `main`에 push | 불가 (관리자 제외) |
| 필수 상태 검사 | `Validate PR conventions` |
| 필수 승인 수 | 0 — 승인 대기로 사람이 묶이지 않게 |
| 리뷰 대화 해결 | 필수 |
| force push · 브랜치 삭제 | 불가 |

## 문서를 쓸 때

- 한국어로 쓴다.
- 아직 정하지 못한 칸은 지우지 말고 `TBD`로 남긴다. 빈칸이 보여야 무엇이
  안 정해졌는지 팀이 안다.
- 채점 기준과 연결되는 내용은 그 근거를 문서 안에 적어 둔다.
