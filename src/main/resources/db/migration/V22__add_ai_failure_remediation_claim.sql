-- #421: 실패 등록 단위별 단 한 번의 자동 보완 claim과 암호화 입력 이력.
-- 원본 Snapshot/등록 단위는 보존하고, 새 시도·Snapshot은 별도 행으로 추가한다.

ALTER TABLE ai_registration_unit
    ADD CONSTRAINT ux_ai_registration_unit__id_snapshot UNIQUE (id, snapshot_id);

CREATE TABLE ai_registration_unit_retry
(
    id                         uuid                        NOT NULL,
    job_id                     uuid                        NOT NULL,
    source_snapshot_id         uuid                        NOT NULL,
    source_registration_unit_id uuid                       NOT NULL,
    candidate_unit_id          varchar(4096)               NOT NULL,
    failure_reason             varchar(64)                 NOT NULL,
    approved_source_identifiers jsonb                       NOT NULL,
    input_hash                 bytea                       NOT NULL,
    ciphertext                 bytea,
    encryption_key_id          varchar(128),
    expires_at                 timestamp(6) with time zone,
    provider_attempt_no        smallint,
    outcome                    varchar(16)                 NOT NULL DEFAULT 'PENDING',
    result_snapshot_id         uuid,
    created_at                 timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at                timestamp(6) with time zone,
    CONSTRAINT pk_ai_registration_unit_retry PRIMARY KEY (id),
    CONSTRAINT ux_ai_registration_unit_retry__job_unit UNIQUE (job_id, source_registration_unit_id),
    CONSTRAINT ux_ai_registration_unit_retry__job_candidate_unit UNIQUE (job_id, candidate_unit_id),
    CONSTRAINT fk_ai_registration_unit_retry__job FOREIGN KEY (job_id)
        REFERENCES ai_extraction_job (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit_retry__source_unit FOREIGN KEY
        (source_registration_unit_id, source_snapshot_id)
        REFERENCES ai_registration_unit (id, snapshot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit_retry__source_snapshot FOREIGN KEY (source_snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit_retry__result_snapshot FOREIGN KEY (result_snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_registration_unit_retry__input_hash CHECK (octet_length(input_hash) = 32),
    CONSTRAINT ck_ai_registration_unit_retry__candidate_unit_id CHECK (btrim(candidate_unit_id) <> ''),
    CONSTRAINT ck_ai_registration_unit_retry__source_ids CHECK (
        jsonb_typeof(approved_source_identifiers) = 'array'
        AND NOT jsonb_path_exists(approved_source_identifiers, '$[*] ? (@.type() != "string")')
    ),
    CONSTRAINT ck_ai_registration_unit_retry__cipher_pair CHECK (
        (ciphertext IS NULL AND encryption_key_id IS NULL)
        OR (ciphertext IS NOT NULL AND octet_length(ciphertext) > 0
            AND encryption_key_id IS NOT NULL AND btrim(encryption_key_id) <> ''
            AND expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_ai_registration_unit_retry__expiry CHECK (
        expires_at IS NULL OR (expires_at > created_at
                               AND expires_at <= created_at + interval '24 hours')
    ),
    CONSTRAINT ck_ai_registration_unit_retry__outcome CHECK (
        outcome IN ('PENDING', 'SUCCEEDED', 'BLOCKED')
    ),
    CONSTRAINT ck_ai_registration_unit_retry__provider_attempt_no CHECK (
        provider_attempt_no IS NULL OR provider_attempt_no BETWEEN 1 AND 4
    ),
    CONSTRAINT ck_ai_registration_unit_retry__completion CHECK (
        (outcome = 'PENDING' AND result_snapshot_id IS NULL AND finished_at IS NULL)
        OR (outcome IN ('SUCCEEDED', 'BLOCKED') AND result_snapshot_id IS NOT NULL
            AND finished_at IS NOT NULL AND finished_at >= created_at)
    )
);

CREATE INDEX ix_ai_registration_unit_retry__expires_at
    ON ai_registration_unit_retry (expires_at)
    WHERE ciphertext IS NOT NULL;

CREATE FUNCTION assert_ai_registration_unit_retry_provenance()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    snapshot_job_id uuid;
BEGIN
    SELECT job_id INTO snapshot_job_id
      FROM ai_candidate_snapshot
     WHERE id = NEW.source_snapshot_id;

    IF snapshot_job_id IS DISTINCT FROM NEW.job_id THEN
        RAISE EXCEPTION 'retry source snapshot must belong to job';
    END IF;

    IF NEW.result_snapshot_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM ai_candidate_snapshot
         WHERE id = NEW.result_snapshot_id AND job_id = NEW.job_id
    ) THEN
        RAISE EXCEPTION 'retry result snapshot must belong to job';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_ai_registration_unit_retry__provenance
BEFORE INSERT OR UPDATE OF job_id, source_snapshot_id, result_snapshot_id
ON ai_registration_unit_retry
FOR EACH ROW EXECUTE FUNCTION assert_ai_registration_unit_retry_provenance();
