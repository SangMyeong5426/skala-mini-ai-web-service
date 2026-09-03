-- 여행 일정(trip_itineraries) · 3D 가방 정리 배치(item_placements) 추가
--
-- **`schema.sql` 을 다시 실행하지 마세요.** 그 파일은 맨 앞에서 모든 테이블을 DROP 합니다.
-- 팀 Supabase 에는 이미 실데이터가 있으므로 전체 재적용은 그것을 지웁니다.
-- `scripts/db-apply` 도 같은 이유로 쓰지 않습니다.
--
-- 이 파일은 **새 테이블 둘만** 만듭니다. 기존 10개 테이블은 건드리지 않습니다.
-- 적용: Supabase SQL Editor 에 붙여넣고 실행하거나
--       psql "$DATABASE_URL" -f database/migrations/2026-09-03-add-itineraries-and-placements.sql
--
-- 이미 적용했는지 확인:
--   SELECT table_name FROM information_schema.tables
--   WHERE table_schema='public' AND table_name IN ('trip_itineraries','item_placements');

-- 여러 번 실행해도 안전하다. 이미 있으면 건너뛴다.
CREATE TABLE IF NOT EXISTS trip_itineraries (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id    BIGINT       NOT NULL REFERENCES trips(id) ON DELETE CASCADE,

    kind       VARCHAR(20)  NOT NULL,
    title      VARCHAR(100) NOT NULL,
    place      VARCHAR(100),
    code       VARCHAR(50),
    start_at   TIMESTAMPTZ  NOT NULL,
    end_at     TIMESTAMPTZ,
    note       TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT trip_itineraries_kind_check
        CHECK (kind IN ('FLIGHT','LODGING','ACTIVITY','TRANSPORT','OTHER')),
    CONSTRAINT trip_itineraries_time_check
        CHECK (end_at IS NULL OR start_at <= end_at)
);

CREATE INDEX IF NOT EXISTS idx_trip_itineraries_trip_time ON trip_itineraries (trip_id, start_at);

CREATE TABLE IF NOT EXISTS item_placements (
    checklist_item_id BIGINT       PRIMARY KEY REFERENCES checklist_items(id) ON DELETE CASCADE,

    compartment       VARCHAR(20)  NOT NULL,
    pos_x             NUMERIC(4,3) NOT NULL,
    pos_y             NUMERIC(4,3) NOT NULL,
    pos_z             NUMERIC(4,3) NOT NULL DEFAULT 0,
    rotated           BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT item_placements_compartment_check
        CHECK (compartment IN ('MAIN_LEFT','MAIN_RIGHT','FRONT_POCKET','MESH','TOP')),
    CONSTRAINT item_placements_pos_check
        CHECK (pos_x >= 0 AND pos_x <= 1
           AND pos_y >= 0 AND pos_y <= 1
           AND pos_z >= 0 AND pos_z <= 1)
);
