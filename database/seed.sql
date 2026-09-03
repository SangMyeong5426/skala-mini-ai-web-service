-- 데모용 초기 데이터
--
-- 3일차 시연에서 빈 화면을 피하려면 필요하다.
-- schema.sql 을 실행한 뒤에 넣는다.
--
-- 시나리오: 김지우(페르소나 1)의 3박 4일 도쿄 여행
--   docs/01-service-plan.md 의 1차 핵심 페르소나 그대로다.

-- ── 사용자 ────────────────────────────────────────────────
-- 이번 데모에서 인증 흐름을 구현하지 않으므로 이 사용자 하나를 고정으로 쓴다.
-- password_hash 는 데모용 bcrypt 해시다. 평문을 저장하지 않는다.
INSERT INTO users (email, password_hash, nickname) VALUES
  ('kim@skala.dev', '$2b$12$Kx8fJ0qN3vZ1sWmT7pLuAeR5yQdH2cVbXn9gM4tJ6oB1iE0aS3wDy', '김지우');


-- ── 품목별 무게 마스터 ────────────────────────────────────
-- is_dense = true 인 것은 이미지 추정 오차가 커서 실측을 먼저 요청한다.
INSERT INTO item_weights (keyword, category, min_g, typical_g, max_g, is_dense, note) VALUES
  ('여권',        'DOCUMENT',    30,    35,    45, FALSE, NULL),
  ('상의',        'CLOTHING',   120,   200,   350, FALSE, '소재에 따라 차이가 크다'),
  ('하의',        'CLOTHING',   250,   400,   650, FALSE, NULL),
  ('속옷',        'CLOTHING',    40,    60,    90, FALSE, NULL),
  ('겉옷',        'CLOTHING',   300,   600,  1200, FALSE, '계절에 따라 차이가 크다'),
  ('휴대전화',    'ELECTRONIC', 150,   190,   240, FALSE, NULL),
  ('충전기',      'ELECTRONIC',  50,    90,   180, FALSE, NULL),
  ('보조배터리',  'ELECTRONIC', 180,   280,   450, TRUE,  '정격(Wh)에 따라 무게가 다르다. 라벨 확인 필요'),
  ('변환 플러그', 'ELECTRONIC',  40,    70,   120, FALSE, NULL),
  ('노트북',      'ELECTRONIC', 900,  1400,  2200, TRUE,  '모델 확인 필요'),
  ('화장품',      'TOILETRY',    50,   200,   500, TRUE,  '용량(ml) 확인 필요'),
  ('세면도구',    'TOILETRY',   150,   300,   500, FALSE, NULL),
  ('상비약',      'MEDICINE',    30,    80,   150, FALSE, NULL),
  ('우산',        'ETC',        200,   350,   600, FALSE, NULL),
  ('가위',        'ETC',         40,    70,   120, TRUE,  '날 길이 확인 필요');


-- ── 반입 규정 마스터 ──────────────────────────────────────
-- 출처와 확인 날짜를 함께 저장한다. 명세 9절 "규정 최신성".
INSERT INTO transport_rules (transport, keyword, verdict, condition_note, description, source_url, checked_at) VALUES
  ('FLIGHT', '보조배터리', 'CABIN_OK',       '100Wh 이하',
   '보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '보조배터리', 'ASK_AIRLINE',    '100Wh 초과 160Wh 이하',
   '100Wh를 넘으면 항공사 사전 승인이 필요합니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '보조배터리', 'CHECKED_FORBIDDEN', '160Wh 초과',
   '160Wh를 넘는 보조배터리는 기내·위탁 모두 반입할 수 없습니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '액체',       'CABIN_OK',       '용기당 100ml 이하, 총 1L 이하',
   '액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '액체',       'CHECKED_OK',     '100ml 초과',
   '100ml를 넘는 액체는 위탁수하물로 부치세요.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '가위',       'CHECKED_OK',     '날 길이 6cm 초과',
   '날 길이 6cm를 넘는 가위는 기내 반입이 제한됩니다. 위탁수하물로 부치세요.',
   'https://www.airport.kr/ap_ko/907/subview.do', '2026-09-02'),

  ('FLIGHT', '가위',       'CABIN_OK',       '날 길이 6cm 이하',
   '날 길이 6cm 이하 가위는 기내 반입이 가능합니다.',
   'https://www.airport.kr/ap_ko/907/subview.do', '2026-09-02'),

  ('FLIGHT', '노트북',     'CABIN_OK',       NULL,
   '노트북은 기내 반입 가능합니다. 보안검색 시 가방에서 꺼내 주세요.',
   'https://www.airportal.go.kr/library/security.do', '2026-09-02');


-- ── 여행 (김지우 · 도쿄 3박 4일) ──────────────────────────
INSERT INTO trips (user_id, origin, destination, country_code, start_date, end_date,
                   purpose, transport, airline, departure_airport, arrival_airport,
                   bag_type, bag_empty_g, weight_limit_g, note, status) VALUES
  (1, '서울', '도쿄', 'JP', '2026-10-01', '2026-10-04',
   'TOUR', 'FLIGHT', '대한항공', 'ICN', 'NRT',
   'CARRY_ON', 3200, 10000, '친구 2명, 디즈니랜드, 사진 많이 찍을 예정', 'CONFIRMED');

-- ── 지난 여행 (S-01 홈의 '과거 여행' 목록) ────────────────
-- 반드시 위 도쿄 여행 **뒤에** 넣는다. 도쿄가 id = 1 로 남아야
-- checklist_items · trip_photos · ai_jobs 의 trip_id = 1 참조가 유지된다.
--
-- 체크리스트·사진은 넣지 않는다. S-10 여행 기록 상세는 3차라 데모에서
-- 클릭하지 않는다. 홈 카드가 비지 않는 것이 목적이다.
INSERT INTO trips (user_id, origin, destination, country_code, start_date, end_date,
                   purpose, transport, airline, departure_airport, arrival_airport,
                   bag_type, bag_empty_g, weight_limit_g, note, status) VALUES
  (1, '서울', '오사카', 'JP', '2026-05-02', '2026-05-04',
   'TOUR', 'FLIGHT', '아시아나항공', 'ICN', 'KIX',
   'CARRY_ON', 3200, 10000, '2박 3일, 유니버설 스튜디오', 'DONE'),
  (1, '서울', '부산', 'KR', '2026-03-14', '2026-03-15',
   'TOUR', 'TRAIN', NULL, NULL, NULL,
   'CARRY_ON', 3200, NULL, '1박 2일, KTX', 'DONE');


-- ── 체크리스트 (AI 추천 + 규칙 보강) ──────────────────────
-- source: RULE = 고정 필수 규칙, PHOTO = 사진에서 승인, AI = 추천, USER = 직접 추가
--
-- PHOTO 인 5개는 item_detections 에서 confirmed_by_user = TRUE 로 연결된 것들이다.
-- 화장품은 후보(화장품 용기·검정 파우치)만 있고 아직 승인 전이라 AI 로 둔다.
INSERT INTO checklist_items (trip_id, item_weight_id, name, category, qty, priority, source, check_status) VALUES
  (1,  1, '여권',        'DOCUMENT',   1, 'REQUIRED',    'RULE',  'NOT_IN_PHOTO'),
  (1,  2, '상의',        'CLOTHING',   4, 'RECOMMENDED', 'PHOTO', 'PREPARED'),
  (1,  3, '하의',        'CLOTHING',   2, 'RECOMMENDED', 'PHOTO', 'PREPARED'),
  (1,  4, '속옷',        'CLOTHING',   4, 'RECOMMENDED', 'PHOTO', 'PREPARED'),
  (1,  7, '충전기',      'ELECTRONIC', 1, 'REQUIRED',    'PHOTO', 'PREPARED'),
  (1,  8, '보조배터리',  'ELECTRONIC', 1, 'RECOMMENDED', 'PHOTO', 'PREPARED'),
  (1,  9, '변환 플러그', 'ELECTRONIC', 1, 'REQUIRED',    'AI',    'NOT_IN_PHOTO'),
  (1, 11, '화장품',      'TOILETRY',   1, 'RECOMMENDED', 'AI',    'NEEDS_CHECK'),
  (1, 13, '상비약',      'MEDICINE',   1, 'RECOMMENDED', 'AI',    'NOT_IN_PHOTO'),
  (1, 14, '우산',        'ETC',        1, 'RECOMMENDED', 'AI',    'NOT_IN_PHOTO');


-- ── 짐 사진 ───────────────────────────────────────────────
INSERT INTO trip_photos (trip_id, file_path, bag_kind) VALUES
  (1, 'demo/bag-01.jpg', 'CABIN'),
  (1, 'demo/bag-02.jpg', 'CABIN');


-- ── 사진에서 인식된 물품 ──────────────────────────────────
-- approved = false 인 것은 아직 사용자 승인 전이다.
-- 명세 9.2: "승인 전에는 최종 준비 상태에 반영되지 않아야 한다"
-- missing_info · label_text 는 docs/07-ai-ready.md BAG_CHECK 예시 output 과 같다.
INSERT INTO detected_objects (photo_id, name, qty, confidence, confidence_level, approved, missing_info, label_text) VALUES
  (1, '충전기',      1, 0.930, 'HIGH',   TRUE,  NULL,              NULL),
  (1, '보조배터리',  1, 0.880, 'HIGH',   TRUE,  '배터리 정격(Wh)', NULL),
  (1, '상의',        4, 0.810, 'HIGH',   TRUE,  NULL,              NULL),
  (1, '하의',        2, 0.790, 'MEDIUM', TRUE,  NULL,              NULL),
  (1, '속옷',        4, 0.720, 'MEDIUM', TRUE,  NULL,              NULL),
  (2, '화장품 용기', 1, 0.640, 'MEDIUM', FALSE, '용량(ml)',        NULL),   -- 확인 필요
  (2, '가위',        1, 0.910, 'HIGH',   TRUE,  '날 길이(cm)',     NULL),   -- 체크리스트에 없던 추가 물품
  (2, '검정 파우치', 1, 0.430, 'LOW',    FALSE, NULL,              NULL);   -- 무엇인지 불분명


-- ══════════════════════════════════════════════════════════
--  ★ N:M 1 — 체크리스트 항목 ↔ 인식 물품
--  다대다를 양쪽으로 다 보여주는 데이터를 넣었다.
--    한 항목 → 후보 여럿 : "화장품" ← 화장품 용기, 검정 파우치
--    한 후보 → 항목 여럿 : "검정 파우치" → 화장품, 상비약
--  명세 F-06: "유사한 후보를 제시하고 사용자가 연결하도록 한다"
-- ══════════════════════════════════════════════════════════
INSERT INTO item_detections (checklist_item_id, detected_object_id, match_confidence, confirmed_by_user) VALUES
  (5, 1, 0.950, TRUE),    -- 충전기      ← 충전기
  (6, 2, 0.920, TRUE),    -- 보조배터리  ← 보조배터리
  (2, 3, 0.880, TRUE),    -- 상의        ← 상의
  (3, 4, 0.850, TRUE),    -- 하의        ← 하의
  (4, 5, 0.800, TRUE),    -- 속옷        ← 속옷
  (8, 6, 0.710, FALSE),   -- 화장품      ← 화장품 용기  (승인 전)
  (8, 8, 0.310, FALSE),   -- 화장품      ← 검정 파우치  ┐ 같은 인식 결과가
  (9, 8, 0.280, FALSE);   -- 상비약      ← 검정 파우치  ┘ 두 항목의 후보다


-- ══════════════════════════════════════════════════════════
--  ★ N:M 2 — 체크리스트 항목 ↔ 반입 규정
--  "화장품" 하나가 규정 둘에 걸리는 경우를 넣었다.
-- ══════════════════════════════════════════════════════════
-- rule_id 는 transport_rules 의 삽입 순서다. 값을 바꾸면 근거 문구가 통째로 달라진다.
--   1~3  보조배터리 (100Wh 이하 / 100~160Wh / 160Wh 초과)
--   4~5  액체 (100ml 이하 / 100ml 초과)
--   6    가위
INSERT INTO item_rule_checks (checklist_item_id, rule_id, verdict, missing_info) VALUES
  (6, 1, 'CABIN_OK',       NULL),          -- 보조배터리 ← 100Wh 이하 규정
  (6, 2, 'NEED_MORE_INFO', '배터리 정격(Wh)'),  -- 보조배터리 ← 100Wh 초과 규정
  (8, 4, 'NEED_MORE_INFO', '용량(ml)'),    -- 화장품     ← 액체 100ml 이하 규정
  (8, 5, 'NEED_MORE_INFO', '용량(ml)');    -- 화장품     ← 액체 100ml 초과 규정


-- ── AI 작업 이력 ─────────────────────────────────────────
-- 완료된 output_payload 를 시드에 넣지 않는다.
--
-- docs/07-ai-ready.md 의 출력 JSON Schema 가 아직 확정되지 않았고
-- (properties 가 TBD, additionalProperties: false), 그 전에 완료 결과를 넣으면
-- AGENTS.md 의 "Mock 응답 JSON 은 07 의 출력 스키마를 정확히 지킨다" 를 어긴다.
-- 스키마가 확정되면 그때 맞춰 넣는다.
--
-- 데모에는 오히려 이 편이 낫다. 화면에서 직접 작업을 만들어야
-- POST → 202 → 폴링 → 렌더링 이 눈에 보인다.
