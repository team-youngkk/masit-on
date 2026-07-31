-- V1: 초기 스키마 baseline
-- 기준 데이터·인덱스·제약을 포함한 MVP 초기 스키마 전체를 하나의 baseline으로 적용한다.
-- 근거: docs/05-specs/data/table-definitions.md, constraint-mapping.md,
--       index-strategy.md 2절, seed-data-plan.md 2~4절, migration-plan.md
--
-- 이 파일은 운영 환경 최초 배포 전에 기존 V1~V5를 통합한 결과이며 적용 결과 스키마는 동일하다.
-- 통합 이후 모든 스키마 변경은 V2 이상의 새 파일로 추가하고 이 파일을 수정하지 않는다.
--
-- 적용 순서: 참조·관리자 테이블 -> 핵심 도메인 테이블 -> 확인 Token -> 조회 인덱스 -> 기준 데이터

-- ---------------------------------------------------------------------------
-- 1. 참조·관리자 테이블
-- ---------------------------------------------------------------------------
CREATE TABLE region
(
    id           uuid                     NOT NULL,
    code         varchar(32)              NOT NULL,
    name         varchar(20)              NOT NULL,
    sort_order   smallint                 NOT NULL,
    active       boolean                  NOT NULL DEFAULT true,
    created_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_region PRIMARY KEY (id),
    CONSTRAINT uk_region__code UNIQUE (code),
    CONSTRAINT uk_region__name UNIQUE (name),
    CONSTRAINT uk_region__sort_order UNIQUE (sort_order),
    CONSTRAINT ck_region__sort_order CHECK (sort_order BETWEEN 1 AND 25),
    CONSTRAINT ck_region__code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_region__name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE food_category
(
    id           uuid                     NOT NULL,
    code         varchar(32)              NOT NULL,
    name         varchar(30)              NOT NULL,
    sort_order   smallint                 NOT NULL,
    active       boolean                  NOT NULL DEFAULT true,
    created_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_food_category PRIMARY KEY (id),
    CONSTRAINT uk_food_category__code UNIQUE (code),
    CONSTRAINT uk_food_category__name UNIQUE (name),
    CONSTRAINT uk_food_category__sort_order UNIQUE (sort_order),
    CONSTRAINT ck_food_category__sort_order CHECK (sort_order BETWEEN 1 AND 10),
    CONSTRAINT ck_food_category__code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_food_category__name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE admin_account
(
    id             uuid                     NOT NULL,
    login_id       varchar(100)             NOT NULL,
    password_hash  varchar(255)             NOT NULL,
    role           varchar(16)              NOT NULL DEFAULT 'ADMIN',
    active         boolean                  NOT NULL DEFAULT true,
    created_at     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_admin_account PRIMARY KEY (id),
    CONSTRAINT uk_admin_account__login_id UNIQUE (login_id),
    CONSTRAINT ck_admin_account__login_id_not_blank CHECK (btrim(login_id) <> ''),
    CONSTRAINT ck_admin_account__password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_admin_account__role CHECK (role = 'ADMIN')
);

-- ---------------------------------------------------------------------------
-- 2. 핵심 도메인 테이블 (부모 순서: restaurant/creator -> video -> visit)
-- ---------------------------------------------------------------------------
CREATE TABLE restaurant
(
    id                 uuid                        NOT NULL,
    region_id          uuid                        NOT NULL,
    food_category_id   uuid                        NOT NULL,
    name               varchar(100)                NOT NULL,
    kakao_place_id     varchar(64)                 NOT NULL,
    kakao_place_url    varchar(2048)                NOT NULL,
    road_address       varchar(255)                NOT NULL,
    detail_address     varchar(200),
    phone_number       varchar(20)                 NOT NULL,
    publication_status varchar(16)                 NOT NULL DEFAULT 'PUBLIC',
    lifecycle_status   varchar(16)                 NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         timestamp(6) with time zone,
    CONSTRAINT pk_restaurant PRIMARY KEY (id),
    CONSTRAINT uk_restaurant__kakao_place_id UNIQUE (kakao_place_id),
    CONSTRAINT fk_restaurant__region FOREIGN KEY (region_id)
        REFERENCES region (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_restaurant__food_category FOREIGN KEY (food_category_id)
        REFERENCES food_category (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_restaurant__name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_restaurant__kakao_place_id_not_blank CHECK (btrim(kakao_place_id) <> ''),
    CONSTRAINT ck_restaurant__kakao_place_url_not_blank CHECK (btrim(kakao_place_url) <> ''),
    CONSTRAINT ck_restaurant__road_address_not_blank CHECK (btrim(road_address) <> ''),
    CONSTRAINT ck_restaurant__detail_address_not_blank
        CHECK (detail_address IS NULL OR btrim(detail_address) <> ''),
    CONSTRAINT ck_restaurant__phone_number
        CHECK (char_length(phone_number) BETWEEN 7 AND 20 AND phone_number ~ '^[0-9 +()\\-]+$'),
    CONSTRAINT ck_restaurant__publication_status CHECK (publication_status IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_restaurant__lifecycle_status CHECK (lifecycle_status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_restaurant__deleted_pair
        CHECK ((lifecycle_status = 'ACTIVE' AND deleted_at IS NULL)
            OR (lifecycle_status = 'DELETED' AND deleted_at IS NOT NULL AND publication_status = 'PRIVATE'))
);

CREATE TABLE creator
(
    id                             uuid                        NOT NULL,
    external_channel_id            varchar(64)                 NOT NULL,
    channel_name                   text                        NOT NULL,
    channel_url                    varchar(2048)               NOT NULL,
    publication_status             varchar(16)                 NOT NULL DEFAULT 'PUBLIC',
    lifecycle_status               varchar(16)                 NOT NULL DEFAULT 'ACTIVE',
    external_availability_status   varchar(16)                 NOT NULL DEFAULT 'AVAILABLE',
    external_status_checked_at     timestamp(6) with time zone NOT NULL,
    created_at                     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                     timestamp(6) with time zone,
    CONSTRAINT pk_creator PRIMARY KEY (id),
    CONSTRAINT uk_creator__external_channel_id UNIQUE (external_channel_id),
    CONSTRAINT uk_creator__id_external_channel_id UNIQUE (id, external_channel_id),
    CONSTRAINT ck_creator__external_channel_id_not_blank CHECK (btrim(external_channel_id) <> ''),
    CONSTRAINT ck_creator__channel_name_not_blank CHECK (btrim(channel_name) <> ''),
    CONSTRAINT ck_creator__channel_url_not_blank CHECK (btrim(channel_url) <> ''),
    CONSTRAINT ck_creator__publication_status CHECK (publication_status IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_creator__lifecycle_status CHECK (lifecycle_status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_creator__external_availability_status
        CHECK (external_availability_status IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT ck_creator__external_unavailable_private
        CHECK (external_availability_status = 'AVAILABLE' OR publication_status = 'PRIVATE'),
    CONSTRAINT ck_creator__deleted_pair
        CHECK ((lifecycle_status = 'ACTIVE' AND deleted_at IS NULL)
            OR (lifecycle_status = 'DELETED' AND deleted_at IS NOT NULL AND publication_status = 'PRIVATE'))
);

CREATE TABLE video
(
    id                              uuid                        NOT NULL,
    creator_id                      uuid,
    external_video_id               varchar(32)                 NOT NULL,
    publisher_external_channel_id   varchar(64)                 NOT NULL,
    title                           text                        NOT NULL,
    source_url                      varchar(2048)               NOT NULL,
    thumbnail_url                   varchar(2048)               NOT NULL,
    published_at                    timestamp(6) with time zone,
    publication_status              varchar(16)                 NOT NULL DEFAULT 'PUBLIC',
    lifecycle_status                varchar(16)                 NOT NULL DEFAULT 'ACTIVE',
    external_availability_status    varchar(16)                 NOT NULL DEFAULT 'AVAILABLE',
    external_status_checked_at      timestamp(6) with time zone NOT NULL,
    created_at                      timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                      timestamp(6) with time zone,
    CONSTRAINT pk_video PRIMARY KEY (id),
    CONSTRAINT uk_video__external_video_id UNIQUE (external_video_id),
    CONSTRAINT uk_video__id_creator_id UNIQUE (id, creator_id),
    CONSTRAINT fk_video__creator_channel FOREIGN KEY (creator_id, publisher_external_channel_id)
        REFERENCES creator (id, external_channel_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_video__external_video_id_not_blank CHECK (btrim(external_video_id) <> ''),
    CONSTRAINT ck_video__publisher_external_channel_id_not_blank
        CHECK (btrim(publisher_external_channel_id) <> ''),
    CONSTRAINT ck_video__title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_video__source_url_not_blank CHECK (btrim(source_url) <> ''),
    CONSTRAINT ck_video__thumbnail_url_not_blank CHECK (btrim(thumbnail_url) <> ''),
    CONSTRAINT ck_video__publication_status CHECK (publication_status IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_video__lifecycle_status CHECK (lifecycle_status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_video__external_availability_status
        CHECK (external_availability_status IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT ck_video__external_unavailable_private
        CHECK (external_availability_status = 'AVAILABLE' OR publication_status = 'PRIVATE'),
    CONSTRAINT ck_video__deleted_pair
        CHECK ((lifecycle_status = 'ACTIVE' AND deleted_at IS NULL)
            OR (lifecycle_status = 'DELETED' AND deleted_at IS NOT NULL AND publication_status = 'PRIVATE'))
);

CREATE TABLE visit
(
    id                  uuid                        NOT NULL,
    restaurant_id       uuid                        NOT NULL,
    creator_id          uuid                        NOT NULL,
    video_id            uuid                        NOT NULL,
    publication_status  varchar(16)                 NOT NULL DEFAULT 'PUBLIC',
    lifecycle_status    varchar(16)                 NOT NULL DEFAULT 'ACTIVE',
    created_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          timestamp(6) with time zone,
    CONSTRAINT pk_visit PRIMARY KEY (id),
    CONSTRAINT uk_visit__restaurant_creator_video UNIQUE (restaurant_id, creator_id, video_id),
    CONSTRAINT fk_visit__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_visit__creator FOREIGN KEY (creator_id)
        REFERENCES creator (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_visit__video_creator FOREIGN KEY (video_id, creator_id)
        REFERENCES video (id, creator_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_visit__publication_status CHECK (publication_status IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_visit__lifecycle_status CHECK (lifecycle_status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_visit__deleted_pair
        CHECK ((lifecycle_status = 'ACTIVE' AND deleted_at IS NULL)
            OR (lifecycle_status = 'DELETED' AND deleted_at IS NOT NULL AND publication_status = 'PRIVATE'))
);

-- ---------------------------------------------------------------------------
-- 3. 확인 Token 테이블
-- ---------------------------------------------------------------------------
CREATE TABLE confirmation_token
(
    id                          uuid                        NOT NULL,
    token_hash                  bytea                       NOT NULL,
    admin_account_id            uuid                        NOT NULL,
    resource_type               varchar(16)                 NOT NULL,
    candidate_schema_version    smallint                    NOT NULL DEFAULT 1,
    identity_key                varchar(128)                NOT NULL,
    candidate_snapshot          jsonb                       NOT NULL,
    status                      varchar(16)                 NOT NULL DEFAULT 'ISSUED',
    issued_at                   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at                  timestamp(6) with time zone NOT NULL,
    completed_at                timestamp(6) with time zone,
    result_resource_id          uuid,
    CONSTRAINT pk_confirmation_token PRIMARY KEY (id),
    CONSTRAINT uk_confirmation_token__token_hash UNIQUE (token_hash),
    CONSTRAINT fk_confirmation_token__admin_account FOREIGN KEY (admin_account_id)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_confirmation_token__token_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_confirmation_token__resource_type
        CHECK (resource_type IN ('RESTAURANT', 'CREATOR', 'VIDEO')),
    CONSTRAINT ck_confirmation_token__schema_version CHECK (candidate_schema_version > 0),
    CONSTRAINT ck_confirmation_token__identity_key_not_blank CHECK (btrim(identity_key) <> ''),
    CONSTRAINT ck_confirmation_token__snapshot_object
        CHECK (jsonb_typeof(candidate_snapshot) = 'object'),
    CONSTRAINT ck_confirmation_token__status CHECK (status IN ('ISSUED', 'CREATED', 'DUPLICATE')),
    CONSTRAINT ck_confirmation_token__expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_confirmation_token__completion_pair
        CHECK ((status = 'ISSUED' AND completed_at IS NULL AND result_resource_id IS NULL)
            OR (status IN ('CREATED', 'DUPLICATE') AND completed_at IS NOT NULL AND result_resource_id IS NOT NULL))
);

-- ---------------------------------------------------------------------------
-- 4. 초기 조회 인덱스
-- ---------------------------------------------------------------------------
CREATE INDEX ix_restaurant__public_order
    ON restaurant (name COLLATE "C", road_address COLLATE "C", id)
    INCLUDE (region_id, food_category_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_restaurant__public_region_order
    ON restaurant (region_id, name COLLATE "C", road_address COLLATE "C", id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_restaurant__public_category_order
    ON restaurant (food_category_id, name COLLATE "C", road_address COLLATE "C", id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_creator__public_name
    ON creator (channel_name COLLATE "C", id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE'
        AND external_availability_status = 'AVAILABLE';

CREATE INDEX ix_video__creator
    ON video (creator_id)
    WHERE creator_id IS NOT NULL;

CREATE INDEX ix_visit__creator_restaurant
    ON visit (creator_id, restaurant_id, video_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_visit__restaurant_creator
    ON visit (restaurant_id, creator_id, video_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_visit__video
    ON visit (video_id, creator_id)
    WHERE publication_status = 'PUBLIC' AND lifecycle_status = 'ACTIVE';

CREATE INDEX ix_confirmation_token__admin_issued
    ON confirmation_token (admin_account_id, issued_at DESC);

CREATE INDEX ix_confirmation_token__cleanup_issued
    ON confirmation_token (expires_at)
    WHERE status = 'ISSUED';

CREATE INDEX ix_confirmation_token__cleanup_completed
    ON confirmation_token (completed_at)
    WHERE status IN ('CREATED', 'DUPLICATE');

-- ---------------------------------------------------------------------------
-- 5. 기준 데이터 (region 25건, food_category 10건)
-- ON CONFLICT DO NOTHING을 사용하지 않는다. 기준값이 이미 다른 값으로 존재하면
-- 실패시켜 drift를 드러낸다.
-- ---------------------------------------------------------------------------
INSERT INTO region (id, code, name, sort_order, active, created_at, updated_at)
VALUES
    ('10000000-0000-4000-8000-000000000001', 'SEOUL_JONGNO', '종로구', 1, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000002', 'SEOUL_JUNG', '중구', 2, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000003', 'SEOUL_YONGSAN', '용산구', 3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000004', 'SEOUL_SEONGDONG', '성동구', 4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000005', 'SEOUL_GWANGJIN', '광진구', 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000006', 'SEOUL_DONGDAEMUN', '동대문구', 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000007', 'SEOUL_JUNGNANG', '중랑구', 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000008', 'SEOUL_SEONGBUK', '성북구', 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000009', 'SEOUL_GANGBUK', '강북구', 9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000010', 'SEOUL_DOBONG', '도봉구', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000011', 'SEOUL_NOWON', '노원구', 11, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000012', 'SEOUL_EUNPYEONG', '은평구', 12, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000013', 'SEOUL_SEODAEMUN', '서대문구', 13, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000014', 'SEOUL_MAPO', '마포구', 14, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000015', 'SEOUL_YANGCHEON', '양천구', 15, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000016', 'SEOUL_GANGSEO', '강서구', 16, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000017', 'SEOUL_GURO', '구로구', 17, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000018', 'SEOUL_GEUMCHEON', '금천구', 18, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000019', 'SEOUL_YEONGDEUNGPO', '영등포구', 19, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000020', 'SEOUL_DONGJAK', '동작구', 20, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000021', 'SEOUL_GWANAK', '관악구', 21, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000022', 'SEOUL_SEOCHO', '서초구', 22, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000023', 'SEOUL_GANGNAM', '강남구', 23, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000024', 'SEOUL_SONGPA', '송파구', 24, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-4000-8000-000000000025', 'SEOUL_GANGDONG', '강동구', 25, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO food_category (id, code, name, sort_order, active, created_at, updated_at)
VALUES
    ('20000000-0000-4000-8000-000000000001', 'KOREAN', '한식', 1, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000002', 'CHINESE', '중식', 2, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000003', 'JAPANESE', '일식', 3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000004', 'WESTERN', '양식', 4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000005', 'SOUTHEAST_ASIAN', '동남아 음식', 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000006', 'SOUTH_ASIAN', '인도·남아시아 음식', 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000007', 'BUNSIK', '분식', 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000008', 'CAFE_DESSERT', '카페·디저트', 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000009', 'BAR_PUB', '술집·주점', 9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-4000-8000-000000000010', 'OTHER', '기타', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
