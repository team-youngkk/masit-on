-- V2: 1차 확장 스키마 (회원 보안, 개인화, 지도 좌표, Creator 상세 표시)
-- 근거: docs/05-specs/data/migration-plan.md 9절, table-definitions.md 14절,
--       index-strategy.md 5절, constraints.md
--
-- 이 파일은 운영 배포 전에 기존 V2~V6를 통합한 결과이며 적용 결과 스키마는 동일하다.
-- 통합 이후 모든 스키마 변경은 V3 이상의 새 파일로 추가하고 이 파일을 수정하지 않는다.
--
-- 적용 순서: 회원 계정·보안 기반 -> 회원 인증 강화(아웃박스·탈퇴·세션 복구 작업)
--           -> 개인 맛집 관계(찜·최근 조회) -> 맛집 좌표 -> Creator 상세 표시 열

-- ---------------------------------------------------------------------------
-- 1. 회원 계정·보안 기반 (구 V2)
-- ---------------------------------------------------------------------------
CREATE TABLE member_account
(
    id                uuid                        NOT NULL,
    email             varchar(320)                NOT NULL,
    password_hash     varchar(255)                NOT NULL,
    email_verified_at timestamp(6) with time zone,
    status            varchar(24)                 NOT NULL DEFAULT 'PENDING_VERIFICATION',
    deletion_requested_at timestamp(6) with time zone,
    created_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_account PRIMARY KEY (id),
    CONSTRAINT uk_member_account__email UNIQUE (email),
    CONSTRAINT ck_member_account__email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT ck_member_account__password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_member_account__status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DELETION_PENDING', 'DISABLED')
    ),
    CONSTRAINT ck_member_account__status_timestamps CHECK (
        (status = 'PENDING_VERIFICATION' AND email_verified_at IS NULL AND deletion_requested_at IS NULL)
        OR (status = 'ACTIVE' AND email_verified_at IS NOT NULL AND deletion_requested_at IS NULL)
        OR (status = 'DELETION_PENDING' AND email_verified_at IS NOT NULL AND deletion_requested_at IS NOT NULL)
        OR (status = 'DISABLED' AND deletion_requested_at IS NULL)
    )
);

CREATE TABLE member_action_token
(
    id                uuid                        NOT NULL,
    member_id         uuid                        NOT NULL,
    token_hash        bytea                       NOT NULL,
    purpose           varchar(32)                 NOT NULL,
    status            varchar(16)                 NOT NULL DEFAULT 'ISSUED',
    issued_at         timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        timestamp(6) with time zone NOT NULL,
    completed_at      timestamp(6) with time zone,
    CONSTRAINT pk_member_action_token PRIMARY KEY (id),
    CONSTRAINT uk_member_action_token__token_hash UNIQUE (token_hash),
    CONSTRAINT fk_member_action_token__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_member_action_token__token_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_member_action_token__purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_member_action_token__status CHECK (status IN ('ISSUED', 'USED', 'REVOKED')),
    CONSTRAINT ck_member_action_token__expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_member_action_token__completion_pair CHECK (
        (status = 'ISSUED' AND completed_at IS NULL)
        OR (status IN ('USED', 'REVOKED') AND completed_at IS NOT NULL AND completed_at >= issued_at)
    )
);

CREATE TABLE member_session_revocation
(
    session_id        uuid                        NOT NULL,
    revoked_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_member_session_revocation PRIMARY KEY (session_id),
    CONSTRAINT ck_member_session_revocation__expiry CHECK (expires_at > revoked_at)
);

CREATE UNIQUE INDEX ux_member_action_token__active_member_purpose
    ON member_action_token (member_id, purpose)
    WHERE status = 'ISSUED';

CREATE INDEX ix_member_session_revocation__expires_at
    ON member_session_revocation (expires_at);

-- ---------------------------------------------------------------------------
-- 2. 회원 인증 강화 - 메일 아웃박스·탈퇴 작업·세션 복구 (구 V3)
-- ---------------------------------------------------------------------------
CREATE TABLE member_action_mail_outbox
(
    id                     uuid                        NOT NULL,
    member_action_token_id uuid                        NOT NULL,
    purpose                varchar(32)                 NOT NULL,
    encrypted_token        bytea                       NOT NULL,
    encryption_nonce       bytea                       NOT NULL,
    encryption_key_id      varchar(64)                 NOT NULL,
    status                 varchar(16)                 NOT NULL DEFAULT 'PENDING',
    attempt_count          integer                     NOT NULL DEFAULT 0,
    next_attempt_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until           timestamp(6) with time zone,
    sent_at                timestamp(6) with time zone,
    created_at             timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_action_mail_outbox PRIMARY KEY (id),
    CONSTRAINT uk_member_action_mail_outbox__member_action_token UNIQUE (member_action_token_id),
    CONSTRAINT fk_member_action_mail_outbox__member_action_token FOREIGN KEY (member_action_token_id)
        REFERENCES member_action_token (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_member_action_mail_outbox__purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_member_action_mail_outbox__status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_member_action_mail_outbox__attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_member_action_mail_outbox__ciphertext_not_empty CHECK (octet_length(encrypted_token) > 16),
    CONSTRAINT ck_member_action_mail_outbox__nonce_length CHECK (octet_length(encryption_nonce) = 12),
    CONSTRAINT ck_member_action_mail_outbox__key_id_not_blank CHECK (btrim(encryption_key_id) <> ''),
    CONSTRAINT ck_member_action_mail_outbox__sent_pair CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL)
        OR (status <> 'SENT' AND sent_at IS NULL)
    )
);

CREATE TABLE member_deletion_job
(
    member_id        uuid                        NOT NULL,
    requested_at     timestamp(6) with time zone NOT NULL,
    next_attempt_at  timestamp(6) with time zone NOT NULL,
    attempt_count    integer                     NOT NULL DEFAULT 0,
    last_attempt_at  timestamp(6) with time zone,
    CONSTRAINT pk_member_deletion_job PRIMARY KEY (member_id),
    CONSTRAINT ck_member_deletion_job__attempt_count CHECK (attempt_count >= 0)
);

CREATE TABLE member_session_revocation_recovery
(
    session_id       uuid                        NOT NULL,
    revoked_at       timestamp(6) with time zone NOT NULL,
    expires_at       timestamp(6) with time zone NOT NULL,
    next_attempt_at  timestamp(6) with time zone NOT NULL,
    attempt_count    integer                     NOT NULL DEFAULT 0,
    last_attempt_at  timestamp(6) with time zone,
    CONSTRAINT pk_member_session_revocation_recovery PRIMARY KEY (session_id),
    CONSTRAINT ck_member_session_revocation_recovery__expiry CHECK (expires_at > revoked_at),
    CONSTRAINT ck_member_session_revocation_recovery__attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_member_action_mail_outbox__dispatch
    ON member_action_mail_outbox (status, next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_member_deletion_job__next_attempt
    ON member_deletion_job (next_attempt_at, requested_at);

CREATE INDEX ix_member_session_revocation_recovery__next_attempt
    ON member_session_revocation_recovery (next_attempt_at, expires_at);

-- ---------------------------------------------------------------------------
-- 3. 개인 맛집 관계 - 찜·최근 조회 (구 V4)
-- ---------------------------------------------------------------------------
CREATE TABLE favorite
(
    member_id    uuid                        NOT NULL,
    restaurant_id uuid                       NOT NULL,
    favorited_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_favorite PRIMARY KEY (member_id, restaurant_id),
    CONSTRAINT fk_favorite__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_favorite__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE recent_restaurant_view
(
    member_id      uuid                        NOT NULL,
    restaurant_id  uuid                        NOT NULL,
    last_viewed_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_recent_restaurant_view PRIMARY KEY (member_id, restaurant_id),
    CONSTRAINT fk_recent_restaurant_view__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_recent_restaurant_view__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX ix_favorite__member_favorited
    ON favorite (member_id, favorited_at DESC, restaurant_id);

CREATE INDEX ix_recent_restaurant_view__member_viewed
    ON recent_restaurant_view (member_id, last_viewed_at DESC, restaurant_id);

CREATE INDEX ix_recent_restaurant_view__cleanup_viewed
    ON recent_restaurant_view (last_viewed_at);

-- ---------------------------------------------------------------------------
-- 4. 맛집 좌표 (구 V5)
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 5. Creator 상세 표시 열 (구 V6)
-- ---------------------------------------------------------------------------
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
