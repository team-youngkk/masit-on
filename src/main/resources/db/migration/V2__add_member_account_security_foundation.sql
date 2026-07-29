CREATE TABLE member_account
(
    id                uuid                        NOT NULL,
    email             varchar(320)                NOT NULL,
    password_hash     varchar(255)                NOT NULL,
    email_verified_at timestamp(6) with time zone,
    status            varchar(16)                 NOT NULL DEFAULT 'ACTIVE',
    withdrawn_at      timestamp(6) with time zone,
    created_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_account PRIMARY KEY (id),
    CONSTRAINT uk_member_account__email UNIQUE (email),
    CONSTRAINT ck_member_account__email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT ck_member_account__password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_member_account__status CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_member_account__withdrawal_pair CHECK (
        (status = 'ACTIVE' AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
    )
);

CREATE TABLE member_action_token
(
    id                uuid                        NOT NULL,
    member_account_id uuid                        NOT NULL,
    token_hash        bytea                       NOT NULL,
    action_type       varchar(32)                 NOT NULL,
    issued_at         timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        timestamp(6) with time zone NOT NULL,
    consumed_at       timestamp(6) with time zone,
    CONSTRAINT pk_member_action_token PRIMARY KEY (id),
    CONSTRAINT uk_member_action_token__token_hash UNIQUE (token_hash),
    CONSTRAINT fk_member_action_token__member_account FOREIGN KEY (member_account_id)
        REFERENCES member_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_member_action_token__token_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_member_action_token__action_type CHECK (action_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_member_action_token__expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_member_action_token__consumed_after_issue CHECK (consumed_at IS NULL OR consumed_at >= issued_at)
);

CREATE TABLE member_session_revocation
(
    session_id        uuid                        NOT NULL,
    member_account_id uuid                        NOT NULL,
    revoked_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_member_session_revocation PRIMARY KEY (session_id),
    CONSTRAINT fk_member_session_revocation__member_account FOREIGN KEY (member_account_id)
        REFERENCES member_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_member_session_revocation__expiry CHECK (expires_at > revoked_at)
);

CREATE INDEX ix_member_action_token__member_action_issued
    ON member_action_token (member_account_id, action_type, issued_at DESC);

CREATE INDEX ix_member_session_revocation__expires_at
    ON member_session_revocation (expires_at);
