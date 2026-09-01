-- 데모용 초기 데이터
--
-- 3일차 Live Demo에서 빈 화면을 보여주지 않기 위해 필요하다.
-- schema.sql 을 실행한 뒤에 실행한다.

INSERT INTO users (email, nickname) VALUES
    ('demo@example.com', '데모사용자'),
    ('team@example.com', '팀원');


-- TBD: 서비스 도메인 데이터


-- AI 작업 예시 — 완료된 상태
-- output_payload 는 docs/07-ai-ready.md 의 출력 JSON Schema를 지켜야 한다.
-- Mock이 돌려주는 것과 같은 모양이어야 한다.
INSERT INTO ai_jobs (user_id, status, job_type, input_payload, output_payload, model_name, completed_at)
VALUES (
    1,
    'COMPLETED',
    'TBD',
    '{"TBD": "TBD"}'::jsonb,
    '{"TBD": "TBD"}'::jsonb,
    'mock',
    now()
);

-- AI 작업 예시 — 처리 중 상태 (로딩 화면 시연용)
INSERT INTO ai_jobs (user_id, status, job_type, input_payload)
VALUES (
    1,
    'PENDING',
    'TBD',
    '{"TBD": "TBD"}'::jsonb
);
