-- V6: Creator 상세 표시 열 추가
-- 근거: docs/05-specs/data/table-definitions.md 14.4절, constraints.md DATA-CONSTRAINT-014,
--       docs/05-specs/data/migration-plan.md V6 행
--
-- 관리자가 마지막으로 확인해 저장한 채널 표시 정보만 추가한다. 구독자 수·실시간 외부 조회·
-- 표시 정보 이력은 이 범위에 저장하지 않는다. 기존 creator 행은 변경하지 않고 nullable 열만
-- 추가하며, 세 값 모두 백필하거나 Flyway 안에서 외부 API(YouTube 등)를 호출하지 않는다.

ALTER TABLE creator
    ADD COLUMN profile_image_url varchar(2048),
    ADD COLUMN description       text,
    ADD COLUMN handle            varchar(255);

ALTER TABLE creator
    ADD CONSTRAINT ck_creator__profile_image_url_https
        CHECK (profile_image_url IS NULL
            OR (btrim(profile_image_url) <> '' AND profile_image_url LIKE 'https://%')),
    ADD CONSTRAINT ck_creator__description_not_blank
        CHECK (description IS NULL OR btrim(description) <> ''),
    ADD CONSTRAINT ck_creator__handle_not_blank
        CHECK (handle IS NULL OR btrim(handle) <> '');
