-- users.password_hash 추가 (로그인 필수 개정, docs/05-erd.md · 06-api-spec.md)
--
-- **`schema.sql` 을 다시 실행하지 마세요.** 맨 앞에서 모든 테이블을 DROP 합니다.
-- 팀 DB 에 실데이터가 있으므로 이 파일로 컬럼만 더합니다.
--
-- 이 파일은 `password_hash` 하나만 책임집니다. `login_id` 는 건드리지 않는다 —
-- 그쪽은 2026-09-03-add-users-login-id.sql 이 혼자 맡는다. 다른 마이그레이션과
-- 순서를 가리지 않으므로 폴더째 아무 순서로 실행해도 됩니다.
--
-- 적용: psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-users-password-hash.sql
--
-- 이미 적용했는지 확인:
--   SELECT column_name FROM information_schema.columns
--   WHERE table_schema='public' AND table_name='users' AND column_name='password_hash';

-- ① 먼저 nullable 로 추가한다. 기존 행이 있으면 NOT NULL 을 바로 걸 수 없다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- ② 데모 계정을 seed.sql 과 같은 상태로 맞춘다.
--
--    이 DB 의 kim@skala.dev 는 seed.sql 이 만드는 그 계정인데, 비밀번호가
--    생기기 전에 들어간 행이라 값이 없다.
--
--    이 해시는 새로 만든 비밀값이 아니라 database/seed.sql 에 이미 커밋되어
--    있는 값이다(skala1234). 데모용이고 실계정이 아니다.
UPDATE users
SET password_hash = '$2a$10$RYoqAoiWyZnVTLi29EOAZ.9XyNvNutsciYMwfXKkzGnAdg4RbrYPG'
WHERE email = 'kim@skala.dev' AND password_hash IS NULL;

-- ③ 남은 행은 로그인할 수 없게 막는다.
--    BCrypt 해시 형식이 아니므로 어떤 비밀번호와도 일치하지 않는다 — 실패 쪽으로 닫는다.
--    해당 회원은 회원가입을 다시 하거나 관리자가 값을 넣어야 한다.
UPDATE users
SET password_hash = 'LOCKED-비밀번호-재설정-필요'
WHERE password_hash IS NULL;

-- ④ 이제 제약을 건다.
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;

-- 확인:
--   SELECT id, login_id, email, left(password_hash, 7) AS hash_prefix FROM users ORDER BY id;
