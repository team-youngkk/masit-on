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
