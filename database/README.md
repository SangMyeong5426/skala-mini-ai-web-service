# Database

PostgreSQL. **클라우드에 둔다** — Supabase 또는 Neon.
로컬에 DB를 설치하지 않는다. 팀원 5명이 같은 DB를 보는 편이 3일 일정에 유리하다.

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

## DB 생성

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

## 스키마를 바꿀 때

1. `database/schema.sql` 수정
2. `docs/05-erd.md`의 DSL과 관계 표도 **같은 PR에서** 수정
3. 팀 채널에 알린다 — 다른 사람의 로컬 DB는 자동으로 바뀌지 않는다

3일짜리 프로젝트이므로 마이그레이션 도구는 쓰지 않는다.
스키마가 바뀌면 테이블을 다시 만든다.
