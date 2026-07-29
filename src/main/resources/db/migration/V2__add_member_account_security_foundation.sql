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
