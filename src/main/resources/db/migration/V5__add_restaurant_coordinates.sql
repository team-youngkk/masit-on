-- V5: 맛집 좌표 열 추가
-- 근거: docs/07-adr/integration/map-001-map-bounds-search.md,
--       docs/05-specs/data/table-definitions.md 14.3절, index-strategy.md 5절
--
-- 기존 restaurant 행은 변경하지 않고 nullable 좌표 열만 추가한다. 좌표가 없는 맛집은
-- 일반 목록·상세에는 계속 노출되고 지도 bounds 조회에서만 제외된다. Flyway 안에서
-- Kakao API를 호출하거나 주소를 좌표로 임의 변환하지 않는다.

ALTER TABLE restaurant
    ADD COLUMN latitude  numeric(9,6),
    ADD COLUMN longitude numeric(9,6);

ALTER TABLE restaurant
    ADD CONSTRAINT ck_restaurant__latitude_range
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_restaurant__longitude_range
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_restaurant__coordinate_pair
        CHECK ((latitude IS NULL AND longitude IS NULL)
            OR (latitude IS NOT NULL AND longitude IS NOT NULL));

CREATE INDEX ix_restaurant__public_coordinate_bounds
    ON restaurant (latitude, longitude)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE'
        AND latitude IS NOT NULL AND longitude IS NOT NULL;
