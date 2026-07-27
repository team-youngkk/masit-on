-- V3: confirmation_token
-- 근거: docs/05-specs/data/table-definitions.md 9절, constraint-mapping.md 2~4절

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
