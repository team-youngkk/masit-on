-- V2: restaurant, creator, video, visit
-- 근거: docs/05-specs/data/table-definitions.md 5~8절, constraint-mapping.md 2~4절, physical-data-model.md 5절
-- 부모 순서: restaurant/creator -> video -> visit (선행 V1)

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
