# Database

PostgreSQL. **팀 공용 DB는 클라우드에 둔다** — Supabase 또는 Neon.
팀원 5명이 같은 DB를 보는 편이 3일 일정에 유리하다.
스키마를 시험하려고 로컬에 임시 DB를 띄우는 것은 예외다 —
아래 [개발용 로컬 DB](#개발용-로컬-db-선택) 참고.

이 폴더는 **백엔드 스택과 무관하다.** Data Architect가 담당한다.

> **언제 하는가.** DB 프로젝트를 만들고 연결 문자열을 받는 것은 주제와 무관하니
> 지금 해도 된다. 하지만 **`schema.sql` 작성과 실행은 기능 명세·유저플로우·
> 와이어프레임([`docs/01`](../docs/01-service-plan.md) · [`02`](../docs/02-use-case.md) ·
> [`03`](../docs/03-wireframe.md))이 확정된 뒤**에 한다. 테이블이 화면과 유스케이스를
> 따라가야 하기 때문이다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `schema.sql` | 테이블 정의(DDL). `docs/05-erd.md`의 ERD와 짝이다 |
| `seed.sql` | 데모용 초기 데이터. **3일차 시연에서 빈 화면을 피하려면 필요하다** |

## DB 생성 (팀 공용)

**여기가 기본이다.** 2일차 FE-BE 연동과 3일차 데모는 이 DB로 한다.

### Supabase

1. supabase.com 가입 → New project
2. `Project Settings → Database → Connection string → URI` 복사
3. `SQL Editor`에 `schema.sql` 붙여넣고 실행

### Neon

1. neon.tech 가입 → Create project
2. `Dashboard → Connection Details`에서 연결 문자열 복사
3. `SQL Editor`에 `schema.sql` 붙여넣고 실행

## 접속 정보 공유

연결 문자열에는 **비밀번호가 들어 있다.**

- 저장소에 커밋하지 않는다. `.gitignore`가 `.env`를 막지만, 문서에 붙여넣는 것은
  막지 못한다.
- 팀 채널(카톡·Slack 등)로 공유하고, 각자 `backend/.env`에 넣는다.
- 실수로 커밋했다면 즉시 팀에 알리고 **DB 비밀번호를 재발급한다.**

## 개발용 로컬 DB (선택)

**팀 공용 DB를 대체하지 않는다.** 클라우드 DB가 아직 없거나, 스키마를 마음껏
부수며 시험하고 싶을 때만 쓴다. 5명이 각자 로컬 DB를 띄우면 시드 데이터와
스키마가 갈라진다.

**아래 명령은 저장소 루트에서 실행한다.**

```bash
docker run -d --name skala-pg \
  -e POSTGRES_PASSWORD=devpass -e POSTGRES_DB=skala \
  -p 55432:5432 postgres:17-alpine

# 스키마 적용 — 컨테이너 안의 psql 을 쓴다
docker exec -i skala-pg psql -U postgres -d skala < database/schema.sql
```

> **호스트에 `psql`을 설치하지 않아도 된다.** `postgres` 이미지 안에 이미 들어 있다.
> 호스트의 `psql`을 쓰려면 별도 설치가 필요해서, Docker만 깐 팀원이 여기서 막힌다.

`backend/.env`를 아래처럼 두면 백엔드가 바로 붙는다.

```bash
DATABASE_URL=jdbc:postgresql://localhost:55432/skala
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=devpass
```

**포트를 5432가 아니라 55432로 쓰는 이유**는 로컬에 PostgreSQL이 이미 깔려 있는
사람과 충돌하지 않게 하려는 것이다.

> `devpass`는 **로컬 전용 예시값이다.** 클라우드 DB에는 같은 값을 쓰지 않는다.

> 이 구성으로 `schema.sql` 실행, `ai_jobs_status_check` 제약 동작, 백엔드
> HikariPool 연결까지 확인했다. 클라우드로 옮길 때 **바꾸는 것은 `.env` 세 줄뿐이고
> 코드는 고치지 않는다** — AI-Ready 원칙 4(Security & Config Isolation)가
> DB에도 그대로 적용된다.

정리할 때는 `docker rm -f skala-pg`.

## 스키마를 바꿀 때

1. `database/schema.sql` 수정
2. `docs/05-erd.md`의 DSL과 관계 표도 **같은 PR에서** 수정
3. 팀 채널에 알린다 — 다른 사람의 로컬 DB는 자동으로 바뀌지 않는다

3일짜리 프로젝트이므로 마이그레이션 도구는 쓰지 않는다.
스키마가 바뀌면 테이블을 다시 만든다.
