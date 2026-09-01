-- skala-mini-ai-web-service 스키마
--
-- docs/05-erd.md 의 ERD와 짝이다. 한쪽만 고치지 않는다.
-- Supabase / Neon 의 SQL Editor에 붙여넣어 실행한다.

-- 개발 중 재실행을 위해 기존 테이블을 지운다.
-- 운영 DB가 아니므로 데이터 손실을 감수한다.
DROP TABLE IF EXISTS ai_jobs CASCADE;
DROP TABLE IF EXISTS users CASCADE;


-- ── 사용자 ────────────────────────────────────────────────
CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    nickname    VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);


-- TBD: 서비스 도메인 테이블을 여기에 추가한다.


-- ── AI 작업 ───────────────────────────────────────────────
-- AI-Ready 원칙 2 (Structured Data).
-- 지금은 Mock이 채우고, 나중에 LLM이 같은 자리를 채운다.
-- 실제 AI를 붙일 때 이 테이블은 바뀌지 않는다.
CREATE TABLE ai_jobs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- PENDING | COMPLETED | FAILED
    -- AI-Ready 원칙 3 (Asynchronous Pipeline): 상태를 DB에 둬야
    -- 응답이 느린 AI를 비동기로 처리할 수 있다.
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    job_type        VARCHAR(50)  NOT NULL,

    -- 입출력을 jsonb로 두는 근거는 docs/05-erd.md 참조.
    -- 내부 구조는 docs/07-ai-ready.md 의 JSON Schema로 고정한다.
    input_payload   JSONB        NOT NULL,
    output_payload  JSONB,

    -- 메타데이터: 코드 변경 없이 모델을 바꿔 끼우기 위한 칸
    model_name      VARCHAR(100),
    error_message   TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT ai_jobs_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

-- 사용자별 작업 목록 조회용
CREATE INDEX idx_ai_jobs_user_created ON ai_jobs (user_id, created_at DESC);

-- 상태별 조회용 (처리 중인 작업 찾기)
CREATE INDEX idx_ai_jobs_status ON ai_jobs (status);
