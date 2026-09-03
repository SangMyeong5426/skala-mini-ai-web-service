-- users.login_id 추가 (로그인 필수 개정, docs/05-erd.md · 06-api-spec.md)
--
-- **`schema.sql` 을 다시 실행하지 마세요.** 맨 앞에서 모든 테이블을 DROP 합니다.
-- 팀 DB 에 실데이터가 있으므로 이 파일로 컬럼만 더합니다.
--
-- 적용: psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-users-login-id.sql

-- ① 먼저 nullable 로 추가한다. 기존 행이 있으면 NOT NULL 을 바로 걸 수 없다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_id VARCHAR(30);

-- ② 기존 행에 값을 채운다. 이메일의 @ 앞부분을 소문자로 쓰고, 규칙에 맞지 않는
--    문자는 밑줄로 바꾼다. 겹치면 뒤에 id 를 붙인다.
UPDATE users
SET login_id = CASE
        WHEN length(regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9_]', '_', 'g')) >= 4
        THEN left(regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9_]', '_', 'g'), 24) || '_' || id
        ELSE 'user_' || id
    END
WHERE login_id IS NULL;

-- ③ 이제 제약을 건다.
ALTER TABLE users ALTER COLUMN login_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'users_login_id_key'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT users_login_id_key UNIQUE (login_id);
    END IF;
END $$;

-- 확인:
--   SELECT id, login_id, email FROM users ORDER BY id;
