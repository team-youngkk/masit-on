-- V3: 2차 확장 스키마
-- 근거: docs/05-specs/data/second-expansion-data-contract.md
-- 기존 V1/V2 행의 backfill이나 외부 호출 없이 부모에서 자식 순서로 적용한다.

-- ---------------------------------------------------------------------------
-- 1. 개인 컬렉션
-- ---------------------------------------------------------------------------
CREATE TABLE personal_collection
(
    id          uuid                        NOT NULL,
    member_id   uuid                        NOT NULL,
    name        varchar(50)                 NOT NULL,
    created_at  timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_personal_collection PRIMARY KEY (id),
    CONSTRAINT fk_personal_collection__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_personal_collection__name CHECK (char_length(btrim(name)) BETWEEN 1 AND 50)
);

CREATE TABLE collection_restaurant
(
    collection_id  uuid                        NOT NULL,
    restaurant_id  uuid                        NOT NULL,
    added_at       timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_collection_restaurant PRIMARY KEY (collection_id, restaurant_id),
    CONSTRAINT fk_collection_restaurant__collection FOREIGN KEY (collection_id)
        REFERENCES personal_collection (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_collection_restaurant__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- ---------------------------------------------------------------------------
-- 2. 큐레이션
-- ---------------------------------------------------------------------------
CREATE TABLE curation
(
    id                  uuid                        NOT NULL,
    title               varchar(100)                NOT NULL,
    description         varchar(1000)               NOT NULL DEFAULT '',
    publication_status  varchar(16)                 NOT NULL DEFAULT 'DRAFT',
    main_position       smallint,
    created_by          uuid                        NOT NULL,
    updated_by          uuid                        NOT NULL,
    created_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at        timestamp(6) with time zone,
    CONSTRAINT pk_curation PRIMARY KEY (id),
    CONSTRAINT fk_curation__created_by FOREIGN KEY (created_by)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_curation__updated_by FOREIGN KEY (updated_by)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_curation__title CHECK (char_length(btrim(title)) BETWEEN 1 AND 100),
    CONSTRAINT ck_curation__publication_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_curation__publication_state CHECK (
        (publication_status = 'DRAFT' AND main_position IS NULL)
        OR (publication_status = 'PUBLISHED' AND main_position IS NOT NULL
            AND main_position BETWEEN 1 AND 5 AND published_at IS NOT NULL)
    ),
    CONSTRAINT uq_curation__status_main_position
        UNIQUE (publication_status, main_position) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE curation_restaurant
(
    curation_id    uuid                        NOT NULL,
    restaurant_id  uuid                        NOT NULL,
    position       smallint                    NOT NULL,
    added_at       timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_curation_restaurant PRIMARY KEY (curation_id, restaurant_id),
    CONSTRAINT fk_curation_restaurant__curation FOREIGN KEY (curation_id)
        REFERENCES curation (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_curation_restaurant__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_curation_restaurant__position UNIQUE (curation_id, position),
    CONSTRAINT ck_curation_restaurant__position CHECK (position BETWEEN 1 AND 20)
);

-- ---------------------------------------------------------------------------
-- 3. 제보·신고와 관리자 상태 이력
-- ---------------------------------------------------------------------------
CREATE TABLE submission
(
    id                   uuid                        NOT NULL,
    member_id            uuid,
    target_type          varchar(32)                 NOT NULL,
    candidate            jsonb                       NOT NULL,
    target_fingerprint   bytea                       NOT NULL,
    description          text                        NOT NULL,
    evidence_url         varchar(2048),
    status               varchar(16)                 NOT NULL DEFAULT 'RECEIVED',
    member_reason        varchar(1000),
    internal_note        text,
    result_action_type   varchar(16),
    result_target_type   varchar(32),
    result_target_id     uuid,
    created_at           timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    terminal_at          timestamp(6) with time zone,
    member_unlinked_at   timestamp(6) with time zone,
    CONSTRAINT pk_submission PRIMARY KEY (id),
    CONSTRAINT fk_submission__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_submission__target_type CHECK (
        target_type IN ('RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP')
    ),
    CONSTRAINT ck_submission__candidate_object CHECK (jsonb_typeof(candidate) = 'object'),
    CONSTRAINT ck_submission__fingerprint_length CHECK (octet_length(target_fingerprint) = 32),
    CONSTRAINT ck_submission__description CHECK (char_length(btrim(description)) BETWEEN 10 AND 2000),
    CONSTRAINT ck_submission__evidence_url_https CHECK (
        evidence_url IS NULL OR (btrim(evidence_url) <> '' AND evidence_url LIKE 'https://%')
    ),
    CONSTRAINT ck_submission__status CHECK (
        status IN ('RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT ck_submission__member_reason CHECK (
        (member_reason IS NULL OR btrim(member_reason) <> '')
        AND (status NOT IN ('REJECTED', 'COMPLETED') OR member_reason IS NOT NULL)
    ),
    CONSTRAINT ck_submission__terminal_state CHECK (
        (status IN ('REJECTED', 'COMPLETED') AND terminal_at IS NOT NULL)
        OR (status NOT IN ('REJECTED', 'COMPLETED') AND terminal_at IS NULL)
    ),
    CONSTRAINT ck_submission__result CHECK (
        (status = 'COMPLETED'
            AND result_action_type IS NOT NULL
            AND result_action_type IN ('CREATED', 'UPDATED', 'HIDDEN')
            AND result_target_type IS NOT NULL
            AND result_target_type IN ('RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP')
            AND result_target_id IS NOT NULL)
        OR (status <> 'COMPLETED' AND result_action_type IS NULL
            AND result_target_type IS NULL AND result_target_id IS NULL)
    ),
    CONSTRAINT ck_submission__member_unlinked CHECK (
        member_id IS NULL OR member_unlinked_at IS NULL
    )
);

CREATE TABLE report
(
    id                   uuid                        NOT NULL,
    member_id            uuid,
    target_type          varchar(32)                 NOT NULL,
    target_id            uuid                        NOT NULL,
    report_type          varchar(32)                 NOT NULL,
    description          text                        NOT NULL,
    evidence_url         varchar(2048),
    status               varchar(16)                 NOT NULL DEFAULT 'RECEIVED',
    member_reason        varchar(1000),
    internal_note        text,
    result_action_type   varchar(16),
    result_target_type   varchar(32),
    result_target_id     uuid,
    created_at           timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    terminal_at          timestamp(6) with time zone,
    member_unlinked_at   timestamp(6) with time zone,
    CONSTRAINT pk_report PRIMARY KEY (id),
    CONSTRAINT fk_report__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_report__target_type CHECK (
        target_type IN ('RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP')
    ),
    CONSTRAINT ck_report__report_type CHECK (
        report_type IN ('ERROR', 'CLOSED', 'UNAVAILABLE', 'WRONG_RELATIONSHIP', 'INAPPROPRIATE_CONTENT')
    ),
    CONSTRAINT ck_report__description CHECK (char_length(btrim(description)) BETWEEN 10 AND 2000),
    CONSTRAINT ck_report__evidence_url_https CHECK (
        evidence_url IS NULL OR (btrim(evidence_url) <> '' AND evidence_url LIKE 'https://%')
    ),
    CONSTRAINT ck_report__status CHECK (
        status IN ('RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT ck_report__member_reason CHECK (
        (member_reason IS NULL OR btrim(member_reason) <> '')
        AND (status NOT IN ('REJECTED', 'COMPLETED') OR member_reason IS NOT NULL)
    ),
    CONSTRAINT ck_report__terminal_state CHECK (
        (status IN ('REJECTED', 'COMPLETED') AND terminal_at IS NOT NULL)
        OR (status NOT IN ('REJECTED', 'COMPLETED') AND terminal_at IS NULL)
    ),
    CONSTRAINT ck_report__result CHECK (
        (status = 'COMPLETED'
            AND result_action_type IS NOT NULL
            AND result_action_type IN ('CREATED', 'UPDATED', 'HIDDEN')
            AND result_target_type IS NOT NULL
            AND result_target_type IN ('RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP')
            AND result_target_id IS NOT NULL)
        OR (status <> 'COMPLETED' AND result_action_type IS NULL
            AND result_target_type IS NULL AND result_target_id IS NULL)
    ),
    CONSTRAINT ck_report__member_unlinked CHECK (
        member_id IS NULL OR member_unlinked_at IS NULL
    )
);

CREATE TABLE moderation_history
(
    id                   uuid                        NOT NULL,
    submission_id        uuid,
    report_id            uuid,
    admin_account_id     uuid                        NOT NULL,
    from_status          varchar(16)                 NOT NULL,
    to_status            varchar(16)                 NOT NULL,
    member_reason        varchar(1000),
    internal_note        text,
    result_action_type   varchar(16),
    result_target_type   varchar(32),
    result_target_id     uuid,
    trace_id             varchar(64)                 NOT NULL,
    created_at           timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_moderation_history PRIMARY KEY (id),
    CONSTRAINT fk_moderation_history__submission FOREIGN KEY (submission_id)
        REFERENCES submission (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_moderation_history__report FOREIGN KEY (report_id)
        REFERENCES report (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_moderation_history__admin_account FOREIGN KEY (admin_account_id)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_moderation_history__exactly_one_request CHECK (
        (submission_id IS NOT NULL) <> (report_id IS NOT NULL)
    ),
    CONSTRAINT ck_moderation_history__from_status CHECK (
        from_status IN ('RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT ck_moderation_history__to_status CHECK (
        to_status IN ('RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT ck_moderation_history__member_reason CHECK (
        member_reason IS NULL OR btrim(member_reason) <> ''
    ),
    CONSTRAINT ck_moderation_history__result CHECK (
        (to_status = 'COMPLETED'
            AND result_action_type IS NOT NULL
            AND result_action_type IN ('CREATED', 'UPDATED', 'HIDDEN')
            AND result_target_type IS NOT NULL
            AND result_target_type IN ('RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP')
            AND result_target_id IS NOT NULL)
        OR (to_status <> 'COMPLETED' AND result_action_type IS NULL
            AND result_target_type IS NULL AND result_target_id IS NULL)
    ),
    CONSTRAINT ck_moderation_history__trace_id_not_blank CHECK (btrim(trace_id) <> '')
);

-- ---------------------------------------------------------------------------
-- 4. 사용자 알림과 생성 멱등성 기록
-- ---------------------------------------------------------------------------
CREATE TABLE notification
(
    id             uuid                        NOT NULL,
    member_id      uuid                        NOT NULL,
    submission_id  uuid,
    report_id      uuid,
    status         varchar(16)                 NOT NULL,
    title          varchar(100)                NOT NULL,
    message        varchar(500)                NOT NULL,
    read_at        timestamp(6) with time zone,
    created_at     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification__member_account FOREIGN KEY (member_id)
        REFERENCES member_account (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notification__submission FOREIGN KEY (submission_id)
        REFERENCES submission (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_notification__report FOREIGN KEY (report_id)
        REFERENCES report (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_notification__exactly_one_request CHECK (
        (submission_id IS NOT NULL) <> (report_id IS NOT NULL)
    ),
    CONSTRAINT ck_notification__status CHECK (
        status IN ('IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED')
    ),
    CONSTRAINT ck_notification__title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_notification__message_not_blank CHECK (btrim(message) <> '')
);

CREATE TABLE idempotency_record
(
    id               uuid                        NOT NULL,
    actor_type       varchar(16)                 NOT NULL,
    actor_id         uuid                        NOT NULL,
    api_scope        varchar(64)                 NOT NULL,
    key_hash         bytea                       NOT NULL,
    request_hash     bytea                       NOT NULL,
    response_status  smallint                    NOT NULL,
    response_body    jsonb                       NOT NULL,
    resource_id      uuid                        NOT NULL,
    created_at       timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_record__actor_scope_key
        UNIQUE (actor_type, actor_id, api_scope, key_hash),
    CONSTRAINT ck_idempotency_record__actor_type CHECK (actor_type IN ('MEMBER', 'ADMIN')),
    CONSTRAINT ck_idempotency_record__api_scope CHECK (api_scope IN (
        'POST:/api/me/collections',
        'POST:/api/admin/curations',
        'POST:/api/me/submissions',
        'POST:/api/me/reports'
    )),
    CONSTRAINT ck_idempotency_record__key_hash_length CHECK (octet_length(key_hash) = 32),
    CONSTRAINT ck_idempotency_record__request_hash_length CHECK (octet_length(request_hash) = 32),
    CONSTRAINT ck_idempotency_record__response_status CHECK (response_status = 201),
    CONSTRAINT ck_idempotency_record__response_body_object CHECK (jsonb_typeof(response_body) = 'object'),
    CONSTRAINT ck_idempotency_record__expiry CHECK (expires_at > created_at)
);

-- ---------------------------------------------------------------------------
-- 5. 조회·중복·정리 인덱스
-- ---------------------------------------------------------------------------
CREATE INDEX ix_personal_collection__member_updated
    ON personal_collection (member_id, updated_at DESC, id);

CREATE INDEX ix_collection_restaurant__collection_added
    ON collection_restaurant (collection_id, added_at DESC, restaurant_id);

CREATE INDEX ix_collection_restaurant__restaurant
    ON collection_restaurant (restaurant_id, collection_id);

CREATE INDEX ix_favorite__restaurant_member
    ON favorite (restaurant_id, member_id);

CREATE INDEX ix_curation__admin_updated
    ON curation (publication_status, updated_at DESC, id);

CREATE INDEX ix_curation_restaurant__restaurant
    ON curation_restaurant (restaurant_id, curation_id);

CREATE UNIQUE INDEX ux_submission__open_member_target
    ON submission (member_id, target_type, target_fingerprint)
    WHERE member_id IS NOT NULL AND status NOT IN ('REJECTED', 'COMPLETED');

CREATE UNIQUE INDEX ux_report__open_member_target_type
    ON report (member_id, target_type, target_id, report_type)
    WHERE member_id IS NOT NULL AND status NOT IN ('REJECTED', 'COMPLETED');

CREATE INDEX ix_submission__member_created
    ON submission (member_id, created_at DESC, id);

CREATE INDEX ix_report__member_created
    ON report (member_id, created_at DESC, id);

CREATE INDEX ix_submission__admin_queue
    ON submission (status, created_at, id);

CREATE INDEX ix_report__admin_queue
    ON report (status, created_at, id);

CREATE INDEX ix_submission__unlink_terminal
    ON submission (terminal_at)
    WHERE member_id IS NOT NULL AND terminal_at IS NOT NULL;

CREATE INDEX ix_report__unlink_terminal
    ON report (terminal_at)
    WHERE member_id IS NOT NULL AND terminal_at IS NOT NULL;

CREATE UNIQUE INDEX ux_moderation_history__submission_status
    ON moderation_history (submission_id, to_status)
    WHERE submission_id IS NOT NULL;

CREATE UNIQUE INDEX ux_moderation_history__report_status
    ON moderation_history (report_id, to_status)
    WHERE report_id IS NOT NULL;

CREATE UNIQUE INDEX ux_notification__submission_status
    ON notification (submission_id, status)
    WHERE submission_id IS NOT NULL;

CREATE UNIQUE INDEX ux_notification__report_status
    ON notification (report_id, status)
    WHERE report_id IS NOT NULL;

CREATE INDEX ix_notification__member_created
    ON notification (member_id, created_at DESC, id);

CREATE INDEX ix_notification__member_unread
    ON notification (member_id, created_at DESC, id)
    WHERE read_at IS NULL;

CREATE INDEX ix_notification__cleanup_created
    ON notification (created_at, member_id);

CREATE INDEX ix_idempotency_record__expires
    ON idempotency_record (expires_at);
