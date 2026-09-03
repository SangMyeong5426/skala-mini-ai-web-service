-- users.login_id 추가 (로그인 필수 개정, docs/05-erd.md · 06-api-spec.md)
--
-- **`schema.sql` 을 다시 실행하지 마세요.** 맨 앞에서 모든 테이블을 DROP 합니다.
-- 팀 DB 에 실데이터가 있으므로 이 파일로 컬럼만 더합니다.
--
-- 이 파일은 `login_id` 하나만 책임집니다. 다른 마이그레이션과 순서를 가리지
-- 않으므로 폴더째 아무 순서로 실행해도 됩니다.
--
-- 적용: psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-users-login-id.sql

-- ① 먼저 nullable 로 추가한다. 기존 행이 있으면 NOT NULL 을 바로 걸 수 없다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_id VARCHAR(30);

-- ② 데모 계정은 seed.sql 과 같은 아이디를 쓴다.
--
--    아래 ③ 의 규칙을 그냥 태우면 'kim@skala.dev' 는 앞부분 'kim' 이 4자 미만이라
--    'user_1' 이 된다. 그런데 seed.sql · database/README.md · docs · 로그인 화면의
--    안내는 전부 jiwoo28 이다. 그대로 두면 데모 로그인이 실패한다.
--
--    login_id = 'user_' || id 도 같이 잡는 이유는, 이 파일의 예전 판이 이미 돌아
--    'user_1' 이 들어간 DB 를 되돌리기 위해서다. 여러 번 실행해도 결과가 같다.
UPDATE users
SET login_id = 'jiwoo28'
WHERE email = 'kim@skala.dev'
  AND (login_id IS NULL OR login_id = 'user_' || id);

-- ③ 나머지 기존 행을 채운다. 이메일의 @ 앞부분을 소문자로 쓰고, 규칙에 맞지 않는
--    문자는 밑줄로 바꾼다. 겹치면 뒤에 id 를 붙인다.
UPDATE users
SET login_id = CASE
        WHEN length(regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9_]', '_', 'g')) >= 4
        THEN left(regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9_]', '_', 'g'), 24) || '_' || id
        ELSE 'user_' || id
    END
WHERE login_id IS NULL;

-- ④ 이제 제약을 건다.
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
