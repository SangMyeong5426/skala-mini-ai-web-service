-- 테스트 전용 규정 마스터.
--
-- 예전에는 없어도 됐다. RULE_CHECK 의 verdict 를 Mock 픽스처가 들고 있었기 때문이다.
-- 지금은 RuleEngine 이 transport_rules 로 판정을 다시 매기므로, 이 표가 비어 있으면
-- 모든 물품이 ASK_AIRLINE 이 된다 — 규정을 모를 때 지어내지 않는 동작 그대로다.
--
-- database/seed.sql 의 FLIGHT 규정과 **같은 값**이다. 한쪽을 고치면 다른 쪽도 고친다.
-- 여기에 여행·체크리스트 같은 업무 데이터는 넣지 않는다. 테스트가 스스로 만든다.
INSERT INTO transport_rules (transport, keyword, verdict, condition_note, description, source_url, checked_at) VALUES
  ('FLIGHT', '보조배터리', 'CABIN_OK',          '100Wh 이하',
   '보조배터리는 기내 반입만 가능합니다. 위탁수하물로 부칠 수 없습니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '보조배터리', 'ASK_AIRLINE',       '100Wh 초과 160Wh 이하',
   '100Wh를 넘으면 항공사 사전 승인이 필요합니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '보조배터리', 'CHECKED_FORBIDDEN', '160Wh 초과',
   '160Wh를 넘는 보조배터리는 기내·위탁 모두 반입할 수 없습니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '액체',       'CABIN_OK',          '용기당 100ml 이하, 총 1L 이하',
   '액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '액체',       'CHECKED_OK',        '100ml 초과',
   '100ml를 넘는 액체는 위탁수하물로 부치세요.',
   'https://www.airport.kr/ap_ko/905/subview.do', '2026-09-02'),

  ('FLIGHT', '가위',       'CHECKED_OK',        '날 길이 6cm 초과',
   '날 길이 6cm를 넘는 가위는 기내 반입이 제한됩니다. 위탁수하물로 부치세요.',
   'https://www.airport.kr/ap_ko/907/subview.do', '2026-09-02'),

  ('FLIGHT', '가위',       'CABIN_OK',          '날 길이 6cm 이하',
   '날 길이 6cm 이하 가위는 기내 반입이 가능합니다.',
   'https://www.airport.kr/ap_ko/907/subview.do', '2026-09-02'),

  ('FLIGHT', '노트북',     'CABIN_OK',          NULL,
   '노트북은 기내 반입 가능합니다. 보안검색 시 가방에서 꺼내 주세요.',
   'https://www.airportal.go.kr/library/security.do', '2026-09-02');
