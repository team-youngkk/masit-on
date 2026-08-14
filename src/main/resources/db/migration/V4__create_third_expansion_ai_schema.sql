-- V4: 3차 확장 AI 스키마와 배포 전 누적 변경 통합본

-- 기존 V4 AI 스키마와 구 V5~V7 누적 변경을 적용 순서와 의미 그대로 보존한다.
-- 근거: docs/05-specs/data/third-expansion-ai-video-data-contract.md,
--       docs/07-adr/integration/ext-003-ai-extraction-async-reliability.md
--
-- Worker는 아래 두 쿼리 경로를 사용한다.
--   1. QUEUED: priority DESC(REALTIME 우선), created_at, id 순으로 FOR UPDATE SKIP LOCKED claim
--   2. lease 만료 RUNNING: lease_expires_at, priority DESC, created_at, id 순으로 recovery claim
-- CURRENT_TIMESTAMP를 partial-index predicate에 넣을 수 없으므로, 만료 판정은 쿼리에서 하고
-- 상태별 partial btree index로 후보 범위를 제한한다.

-- ---------------------------------------------------------------------------
-- 1. AI 추출 작업·임시 입력
-- ---------------------------------------------------------------------------
CREATE TABLE ai_extraction_job
(
    id                  uuid                        NOT NULL,
    source              varchar(16)                 NOT NULL,
    priority            varchar(16)                 NOT NULL,
    youtube_channel_id  varchar(128)                NOT NULL,
    youtube_video_id    varchar(128)                NOT NULL,
    video_url           varchar(2048)               NOT NULL,
    input_mode          varchar(24)                 NOT NULL,
    input_hash          bytea                       NOT NULL,
    provider            varchar(32)                 NOT NULL,
    model_version       varchar(128)                NOT NULL,
    prompt_version      varchar(64)                 NOT NULL,
    schema_version      varchar(64)                 NOT NULL,
    execution_status    varchar(16)                 NOT NULL DEFAULT 'QUEUED',
    result_completeness varchar(16),
    attempt_count       smallint                    NOT NULL DEFAULT 0,
    lease_owner         varchar(128),
    lease_expires_at    timestamp(6) with time zone,
    error_category      varchar(64),
    created_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at          timestamp(6) with time zone,
    finished_at         timestamp(6) with time zone,
    CONSTRAINT pk_ai_extraction_job PRIMARY KEY (id),
    CONSTRAINT ux_ai_job__idempotency UNIQUE (
        youtube_channel_id, youtube_video_id, input_hash, provider, model_version, prompt_version, schema_version
    ),
    CONSTRAINT ck_ai_extraction_job__source CHECK (source IN ('WEBHOOK', 'ADMIN')),
    CONSTRAINT ck_ai_extraction_job__priority CHECK (priority IN ('REALTIME', 'BACKFILL')),
    CONSTRAINT ck_ai_extraction_job__youtube_channel_id_not_blank CHECK (btrim(youtube_channel_id) <> ''),
    CONSTRAINT ck_ai_extraction_job__youtube_video_id_not_blank CHECK (btrim(youtube_video_id) <> ''),
    CONSTRAINT ck_ai_extraction_job__video_url CHECK (
        video_url ~ '^https://(www\.)?youtube\.com/' OR video_url ~ '^https://youtu\.be/'
    ),
    CONSTRAINT ck_ai_extraction_job__input_mode CHECK (input_mode IN ('GEMINI_VIDEO_URL', 'ADMIN_TEXT')),
    CONSTRAINT ck_ai_extraction_job__source_input_mode CHECK (
        source = 'ADMIN' OR input_mode = 'GEMINI_VIDEO_URL'
    ),
    CONSTRAINT ck_ai_extraction_job__input_hash_length CHECK (octet_length(input_hash) = 32),
    CONSTRAINT ck_ai_extraction_job__provider CHECK (provider = 'GOOGLE_GEMINI'),
    CONSTRAINT ck_ai_extraction_job__model_version CHECK (
        model_version IN ('gemini-3-flash-preview', 'gemini-3.5-flash-lite')
    ),
    CONSTRAINT ck_ai_extraction_job__prompt_version_not_blank CHECK (btrim(prompt_version) <> ''),
    CONSTRAINT ck_ai_extraction_job__schema_version_not_blank CHECK (btrim(schema_version) <> ''),
    CONSTRAINT ck_ai_extraction_job__execution_status
        CHECK (execution_status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_extraction_job__result_completeness
        CHECK (result_completeness IS NULL OR result_completeness IN ('COMPLETE', 'PARTIAL')),
    CONSTRAINT ck_ai_extraction_job__attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_ai_extraction_job__lease_pair CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner IS NOT NULL AND btrim(lease_owner) <> '' AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_ai_extraction_job__state_timestamps CHECK (
        (execution_status = 'QUEUED'
            AND started_at IS NULL AND finished_at IS NULL
            AND lease_owner IS NULL AND lease_expires_at IS NULL
            AND result_completeness IS NULL AND error_category IS NULL)
        OR (execution_status = 'RUNNING'
            AND started_at IS NOT NULL AND finished_at IS NULL
            AND lease_owner IS NOT NULL AND lease_expires_at > started_at
            AND result_completeness IS NULL AND error_category IS NULL)
        OR (execution_status = 'SUCCEEDED'
            AND started_at IS NOT NULL AND finished_at IS NOT NULL AND finished_at >= started_at
            AND lease_owner IS NULL AND lease_expires_at IS NULL
            AND result_completeness IS NOT NULL AND error_category IS NULL)
        OR (execution_status = 'FAILED'
            AND started_at IS NOT NULL AND finished_at IS NOT NULL AND finished_at >= started_at
            AND lease_owner IS NULL AND lease_expires_at IS NULL
            AND result_completeness IS NULL AND error_category IS NOT NULL AND btrim(error_category) <> '')
    ),
    CONSTRAINT ck_ai_extraction_job__started_after_created
        CHECK (started_at IS NULL OR started_at >= created_at)
);

CREATE TABLE ai_extraction_temporary_input
(
    job_id            uuid                        NOT NULL,
    ciphertext        bytea                       NOT NULL,
    encryption_key_id varchar(128)                NOT NULL,
    expires_at        timestamp(6) with time zone NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ai_extraction_temporary_input PRIMARY KEY (job_id),
    CONSTRAINT fk_ai_extraction_temporary_input__job FOREIGN KEY (job_id)
        REFERENCES ai_extraction_job (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_extraction_temporary_input__ciphertext_not_empty CHECK (octet_length(ciphertext) > 0),
    CONSTRAINT ck_ai_extraction_temporary_input__key_id_not_blank CHECK (btrim(encryption_key_id) <> ''),
    CONSTRAINT ck_ai_extraction_temporary_input__expires_after_created CHECK (expires_at > created_at)
);

CREATE FUNCTION assert_ai_extraction_temporary_input_job()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    job_source ai_extraction_job.source%TYPE;
    job_input_mode ai_extraction_job.input_mode%TYPE;
BEGIN
    SELECT source, input_mode INTO job_source, job_input_mode
    FROM ai_extraction_job
    WHERE id = NEW.job_id;

    IF job_source <> 'ADMIN' OR job_input_mode <> 'ADMIN_TEXT' THEN
        RAISE EXCEPTION 'temporary input requires an ADMIN/ADMIN_TEXT extraction job';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_ai_extraction_temporary_input__admin_text_job
BEFORE INSERT OR UPDATE OF job_id ON ai_extraction_temporary_input
FOR EACH ROW
EXECUTE FUNCTION assert_ai_extraction_temporary_input_job();

CREATE FUNCTION assert_ai_extraction_temporary_input_expiry()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    job_status ai_extraction_job.execution_status%TYPE;
    job_finished_at ai_extraction_job.finished_at%TYPE;
BEGIN
    IF TG_TABLE_NAME = 'ai_extraction_temporary_input' THEN
        SELECT execution_status, finished_at INTO job_status, job_finished_at
        FROM ai_extraction_job
        WHERE id = NEW.job_id;

        IF job_status IN ('SUCCEEDED', 'FAILED')
            AND (NEW.expires_at < job_finished_at
                OR NEW.expires_at > job_finished_at + interval '24 hours') THEN
            RAISE EXCEPTION 'temporary input must expire within 24 hours of job completion';
        END IF;
    ELSIF NEW.execution_status IN ('SUCCEEDED', 'FAILED') AND EXISTS (
        SELECT 1
        FROM ai_extraction_temporary_input
        WHERE job_id = NEW.id
          AND (expires_at < NEW.finished_at
              OR expires_at > NEW.finished_at + interval '24 hours')
    ) THEN
        -- Worker completion may occur after the initial retention window. Extend the
        -- encrypted input to the contract window before validating the terminal state.
        UPDATE ai_extraction_temporary_input
           SET expires_at = NEW.finished_at + interval '24 hours'
         WHERE job_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_ai_extraction_temporary_input__expiry_after_job_completion
BEFORE INSERT OR UPDATE OF expires_at, job_id ON ai_extraction_temporary_input
FOR EACH ROW
EXECUTE FUNCTION assert_ai_extraction_temporary_input_expiry();

CREATE TRIGGER tr_ai_extraction_job__temporary_input_expiry
BEFORE UPDATE OF execution_status, finished_at ON ai_extraction_job
FOR EACH ROW
EXECUTE FUNCTION assert_ai_extraction_temporary_input_expiry();

-- ---------------------------------------------------------------------------
-- 2. 후보 Snapshot·통제 태그·태그 검수 이력
-- ---------------------------------------------------------------------------
CREATE TABLE ai_candidate_snapshot
(
    id                uuid                        NOT NULL,
    job_id            uuid                        NOT NULL,
    snapshot_version  integer                     NOT NULL,
    candidate_fields  jsonb                       NOT NULL,
    candidate_tags    jsonb                       NOT NULL,
    field_confidences jsonb                       NOT NULL,
    evidence          jsonb                       NOT NULL,
    missing_fields    jsonb                       NOT NULL,
    review_status     varchar(24)                 NOT NULL,
    reviewed_by       uuid,
    review_reason     varchar(1000),
    reviewed_at       timestamp(6) with time zone,
    created_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ai_candidate_snapshot PRIMARY KEY (id),
    CONSTRAINT ux_ai_snapshot__job_version UNIQUE (job_id, snapshot_version),
    CONSTRAINT fk_ai_candidate_snapshot__job FOREIGN KEY (job_id)
        REFERENCES ai_extraction_job (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_candidate_snapshot__reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_candidate_snapshot__version CHECK (snapshot_version > 0),
    CONSTRAINT ck_ai_candidate_snapshot__candidate_fields_object CHECK (jsonb_typeof(candidate_fields) = 'object'),
    CONSTRAINT ck_ai_candidate_snapshot__candidate_tags_array CHECK (jsonb_typeof(candidate_tags) = 'array'),
    CONSTRAINT ck_ai_candidate_snapshot__field_confidences_object CHECK (jsonb_typeof(field_confidences) = 'object'),
    CONSTRAINT ck_ai_candidate_snapshot__evidence_object CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT ck_ai_candidate_snapshot__missing_fields_array CHECK (jsonb_typeof(missing_fields) = 'array'),
    CONSTRAINT ck_ai_candidate_snapshot__review_status
        CHECK (review_status IN ('AUTO_CONFIRMED', 'AUTO_BLOCKED', 'AUTO_REJECTED', 'MANUAL_OVERRIDE')),
    CONSTRAINT ck_ai_candidate_snapshot__review_state CHECK (
        (review_status = 'AUTO_CONFIRMED'
            AND reviewed_by IS NULL AND review_reason IS NULL AND reviewed_at IS NOT NULL)
        OR (review_status = 'AUTO_BLOCKED'
            AND reviewed_by IS NULL AND reviewed_at IS NOT NULL)
        OR (review_status = 'AUTO_REJECTED'
            AND reviewed_by IS NULL AND btrim(review_reason) <> '' AND reviewed_at IS NOT NULL)
        OR (review_status = 'MANUAL_OVERRIDE'
            AND reviewed_by IS NOT NULL AND btrim(review_reason) <> '' AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_ai_candidate_snapshot__reviewed_after_created
        CHECK (reviewed_at IS NULL OR reviewed_at >= created_at)
);

CREATE FUNCTION ai_evidence_matches_contract(value jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    evidence_type text;
BEGIN
    IF jsonb_typeof(value) <> 'object' THEN
        RETURN false;
    END IF;
    evidence_type := value ->> 'type';
    IF evidence_type = 'UNKNOWN' THEN
        RETURN value - 'type' = '{}'::jsonb;
    ELSIF evidence_type = 'TIMESTAMP' THEN
        RETURN jsonb_typeof(value -> 'startMs') = 'number'
            AND jsonb_typeof(value -> 'endMs') = 'number'
            AND (value ->> 'startMs')::numeric >= 0
            AND (value ->> 'endMs')::numeric >= (value ->> 'startMs')::numeric;
    ELSIF evidence_type = 'TEXT_RANGE' THEN
        RETURN jsonb_typeof(value -> 'startOffset') = 'number'
            AND jsonb_typeof(value -> 'endOffset') = 'number'
            AND (value ->> 'startOffset')::numeric >= 0
            AND (value ->> 'endOffset')::numeric >= (value ->> 'startOffset')::numeric
            AND btrim(coalesce(value ->> 'sourceHash', '')) <> '';
    END IF;
    RETURN false;
END;
$$;

CREATE FUNCTION assert_ai_candidate_snapshot_json_contract()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    candidate jsonb;
    evidence_item jsonb;
    confidence_item jsonb;
    evidence_type text;
BEGIN
    FOR candidate IN SELECT value FROM jsonb_array_elements(NEW.candidate_tags) LOOP
        IF jsonb_typeof(candidate) <> 'object'
            OR NOT (candidate ?& ARRAY['candidateTagId', 'tagType', 'rawLabel', 'normalizedCode', 'label', 'confidence', 'evidence'])
            OR (candidate ->> 'candidateTagId') IS NULL
            OR btrim(candidate ->> 'candidateTagId') = ''
            OR (candidate ->> 'tagType') NOT IN ('MENU', 'TASTE', 'OCCASION', 'ATMOSPHERE')
            OR (candidate ->> 'rawLabel') IS NULL
            OR btrim(candidate ->> 'rawLabel') = ''
            OR (candidate ->> 'normalizedCode') IS NULL
            OR btrim(candidate ->> 'normalizedCode') = ''
            OR (candidate ->> 'label') IS NULL
            OR btrim(candidate ->> 'label') = ''
            OR jsonb_typeof(candidate -> 'confidence') <> 'number'
            OR (candidate ->> 'confidence')::numeric NOT BETWEEN 0 AND 1
            OR jsonb_typeof(candidate -> 'evidence') <> 'object' THEN
            RAISE EXCEPTION 'candidate tag does not match S1 contract';
        END IF;

        evidence_item := candidate -> 'evidence';
        evidence_type := evidence_item ->> 'type';
        IF evidence_type = 'TIMESTAMP' THEN
            IF jsonb_typeof(evidence_item -> 'startMs') <> 'number'
                OR jsonb_typeof(evidence_item -> 'endMs') <> 'number'
                OR (evidence_item ->> 'startMs')::numeric < 0
                OR (evidence_item ->> 'endMs')::numeric < (evidence_item ->> 'startMs')::numeric THEN
                RAISE EXCEPTION 'candidate tag timestamp evidence is invalid';
            END IF;
        ELSIF evidence_type = 'TEXT_RANGE' THEN
            IF jsonb_typeof(evidence_item -> 'startOffset') <> 'number'
                OR jsonb_typeof(evidence_item -> 'endOffset') <> 'number'
                OR (evidence_item ->> 'startOffset')::numeric < 0
                OR (evidence_item ->> 'endOffset')::numeric < (evidence_item ->> 'startOffset')::numeric
                OR btrim(coalesce(evidence_item ->> 'sourceHash', '')) = '' THEN
                RAISE EXCEPTION 'candidate tag text evidence is invalid';
            END IF;
        ELSIF evidence_type = 'UNKNOWN' THEN
            IF NOT ai_evidence_matches_contract(evidence_item) THEN
                RAISE EXCEPTION 'candidate tag unknown evidence must not contain a location';
            END IF;
        ELSE
            RAISE EXCEPTION 'candidate tag evidence type is invalid';
        END IF;
    END LOOP;

    FOR confidence_item IN SELECT value FROM jsonb_each(NEW.field_confidences) LOOP
        IF jsonb_typeof(confidence_item) <> 'number'
            OR (confidence_item #>> '{}')::numeric NOT BETWEEN 0 AND 1 THEN
            RAISE EXCEPTION 'field confidence is outside the S1 range';
        END IF;
    END LOOP;

    FOR evidence_item IN SELECT value FROM jsonb_each(NEW.evidence) LOOP
        IF NOT ai_evidence_matches_contract(evidence_item) THEN
            RAISE EXCEPTION 'field evidence does not match S1 contract';
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_ai_candidate_snapshot__json_contract
BEFORE INSERT OR UPDATE OF candidate_tags, field_confidences, evidence
ON ai_candidate_snapshot
FOR EACH ROW
EXECUTE FUNCTION assert_ai_candidate_snapshot_json_contract();

CREATE FUNCTION ai_jsonb_aliases_are_unique(value jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF jsonb_typeof(value) <> 'array' THEN
        RETURN false;
    END IF;

    RETURN NOT EXISTS (
        SELECT 1
        FROM jsonb_array_elements(value) AS alias_element(alias_value)
        WHERE jsonb_typeof(alias_value) <> 'string' OR btrim(alias_value #>> '{}') = ''
    )
    AND NOT EXISTS (
        SELECT 1
        FROM jsonb_array_elements(value) AS alias_element(alias_value)
        GROUP BY alias_value
        HAVING count(*) > 1
    );
END;
$$;

CREATE TABLE tag_definition
(
    id                       uuid                        NOT NULL,
    tag_code                 varchar(64)                 NOT NULL,
    tag_type                 varchar(24)                 NOT NULL,
    display_name             varchar(100)                NOT NULL,
    aliases                  jsonb                       NOT NULL,
    status                   varchar(16)                 NOT NULL DEFAULT 'ACTIVE',
    source                   varchar(16)                 NOT NULL,
    created_from_snapshot_id uuid,
    created_at               timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tag_definition PRIMARY KEY (id),
    CONSTRAINT ux_tag_definition__code UNIQUE (tag_code),
    CONSTRAINT fk_tag_definition__created_from_snapshot FOREIGN KEY (created_from_snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_tag_definition__code_not_blank CHECK (btrim(tag_code) <> ''),
    CONSTRAINT ck_tag_definition__type CHECK (tag_type IN ('MENU', 'TASTE', 'OCCASION', 'ATMOSPHERE')),
    CONSTRAINT ck_tag_definition__display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_tag_definition__aliases_array CHECK (jsonb_typeof(aliases) = 'array'),
    CONSTRAINT ck_tag_definition__aliases_text_unique CHECK (ai_jsonb_aliases_are_unique(aliases)),
    CONSTRAINT ck_tag_definition__status CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    CONSTRAINT ck_tag_definition__source CHECK (source IN ('SEED', 'AI_AUTO', 'MANUAL_OVERRIDE')),
    CONSTRAINT ck_tag_definition__snapshot_source CHECK (
        (source = 'AI_AUTO' AND created_from_snapshot_id IS NOT NULL)
        OR (source IN ('SEED', 'MANUAL_OVERRIDE') AND created_from_snapshot_id IS NULL)
    ),
    CONSTRAINT ck_tag_definition__updated_after_created CHECK (updated_at >= created_at)
);

CREATE TABLE ai_candidate_tag_review
(
    id                            uuid                        NOT NULL,
    snapshot_id                   uuid                        NOT NULL,
    candidate_tag_id              varchar(128)                NOT NULL,
    decision                      varchar(24)                 NOT NULL,
    replacement_tag_definition_id uuid,
    reason                        varchar(1000),
    decision_source               varchar(16)                 NOT NULL,
    reviewed_by                   uuid,
    reviewed_at                   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ai_candidate_tag_review PRIMARY KEY (id),
    CONSTRAINT fk_ai_candidate_tag_review__snapshot FOREIGN KEY (snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_candidate_tag_review__replacement_tag_definition FOREIGN KEY (replacement_tag_definition_id)
        REFERENCES tag_definition (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_candidate_tag_review__reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_candidate_tag_review__candidate_tag_id_not_blank CHECK (btrim(candidate_tag_id) <> ''),
    CONSTRAINT ck_ai_candidate_tag_review__decision
        CHECK (decision IN ('AUTO_ACCEPT', 'AUTO_REJECT', 'AUTO_MERGE', 'MANUAL_OVERRIDE')),
    CONSTRAINT ck_ai_candidate_tag_review__decision_source CHECK (decision_source IN ('SYSTEM', 'ADMIN')),
    CONSTRAINT ck_ai_candidate_tag_review__decision_pair CHECK (
        (decision = 'AUTO_MERGE' AND replacement_tag_definition_id IS NOT NULL)
        OR (decision <> 'AUTO_MERGE' AND replacement_tag_definition_id IS NULL)
    ),
    CONSTRAINT ck_ai_candidate_tag_review__decision_actor CHECK (
        (decision IN ('AUTO_ACCEPT', 'AUTO_REJECT', 'AUTO_MERGE')
            AND decision_source = 'SYSTEM' AND reviewed_by IS NULL)
        OR (decision = 'MANUAL_OVERRIDE' AND decision_source = 'ADMIN' AND reviewed_by IS NOT NULL)
    )
);

CREATE FUNCTION prevent_ai_candidate_tag_review_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ai_candidate_tag_review is append-only';
END;
$$;

CREATE TRIGGER tr_ai_candidate_tag_review__append_only
BEFORE UPDATE OR DELETE ON ai_candidate_tag_review
FOR EACH ROW
EXECUTE FUNCTION prevent_ai_candidate_tag_review_mutation();

CREATE FUNCTION assert_ai_candidate_tag_review_replacement_active()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.replacement_tag_definition_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM tag_definition
        WHERE id = NEW.replacement_tag_definition_id
          AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'AUTO_MERGE replacement tag definition must be ACTIVE';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_ai_candidate_tag_review__active_replacement
BEFORE INSERT ON ai_candidate_tag_review
FOR EACH ROW
EXECUTE FUNCTION assert_ai_candidate_tag_review_replacement_active();

-- ---------------------------------------------------------------------------
-- 3. 확정 Visit 태그·시도 이력·YouTube 채널 감시
-- ---------------------------------------------------------------------------
CREATE TABLE visit_tag
(
    id                uuid                        NOT NULL,
    visit_id          uuid                        NOT NULL,
    tag_definition_id uuid                        NOT NULL,
    source            varchar(24)                 NOT NULL,
    confidence        numeric(5, 4),
    evidence          jsonb                       NOT NULL,
    extractor_version varchar(128),
    created_at        timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_visit_tag PRIMARY KEY (id),
    CONSTRAINT ux_visit_tag__visit_tag UNIQUE (visit_id, tag_definition_id),
    CONSTRAINT fk_visit_tag__visit FOREIGN KEY (visit_id)
        REFERENCES visit (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_visit_tag__tag_definition FOREIGN KEY (tag_definition_id)
        REFERENCES tag_definition (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_visit_tag__source CHECK (source IN ('AI_AUTO_CONFIRMED', 'ADMIN_OVERRIDE')),
    CONSTRAINT ck_visit_tag__confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_visit_tag__evidence_object CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT ck_visit_tag__ai_evidence CHECK (
        source <> 'AI_AUTO_CONFIRMED'
        OR (
            confidence IS NOT NULL
            AND btrim(extractor_version) <> ''
            AND (
                (evidence ->> 'type' = 'TIMESTAMP' AND evidence ? 'startMs' AND evidence ? 'endMs')
                OR (evidence ->> 'type' = 'TEXT_RANGE'
                    AND evidence ? 'startOffset' AND evidence ? 'endOffset' AND evidence ? 'sourceHash'
                    AND btrim(evidence ->> 'sourceHash') <> '')
            )
        )
    )
);

CREATE TABLE ai_extraction_attempt
(
    id                   uuid                        NOT NULL,
    job_id               uuid                        NOT NULL,
    attempt_no           smallint                    NOT NULL,
    provider_request_id  varchar(128),
    started_at           timestamp(6) with time zone NOT NULL,
    finished_at          timestamp(6) with time zone NOT NULL,
    outcome              varchar(16)                 NOT NULL,
    error_category       varchar(64),
    input_tokens         integer,
    output_tokens        integer,
    estimated_cost_minor bigint,
    CONSTRAINT pk_ai_extraction_attempt PRIMARY KEY (id),
    CONSTRAINT ux_ai_attempt__job_no UNIQUE (job_id, attempt_no),
    CONSTRAINT fk_ai_extraction_attempt__job FOREIGN KEY (job_id)
        REFERENCES ai_extraction_job (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_extraction_attempt__number CHECK (attempt_no > 0),
    CONSTRAINT ck_ai_extraction_attempt__provider_request_id_not_blank
        CHECK (provider_request_id IS NULL OR btrim(provider_request_id) <> ''),
    CONSTRAINT ck_ai_extraction_attempt__timestamps CHECK (finished_at >= started_at),
    CONSTRAINT ck_ai_extraction_attempt__outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'PARTIAL')),
    CONSTRAINT ck_ai_extraction_attempt__error CHECK (
        (outcome = 'FAILED' AND error_category IS NOT NULL AND btrim(error_category) <> '')
        OR (outcome IN ('SUCCEEDED', 'PARTIAL') AND error_category IS NULL)
    ),
    CONSTRAINT ck_ai_extraction_attempt__input_tokens CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_ai_extraction_attempt__output_tokens CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_ai_extraction_attempt__estimated_cost CHECK (
        estimated_cost_minor IS NULL OR estimated_cost_minor >= 0
    )
);

CREATE TABLE youtube_channel_watch
(
    id                      uuid                        NOT NULL,
    creator_id              uuid                        NOT NULL,
    youtube_channel_id      varchar(128)                NOT NULL,
    enabled                 boolean                     NOT NULL DEFAULT false,
    subscription_status     varchar(24)                 NOT NULL DEFAULT 'UNKNOWN',
    subscription_token_hash bytea,
    last_notification_at    timestamp(6) with time zone,
    last_renewed_at         timestamp(6) with time zone,
    last_error_category     varchar(64),
    created_at              timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_youtube_channel_watch PRIMARY KEY (id),
    CONSTRAINT ux_channel_watch__creator UNIQUE (creator_id),
    CONSTRAINT ux_channel_watch__youtube_channel UNIQUE (youtube_channel_id),
    CONSTRAINT fk_youtube_channel_watch__creator FOREIGN KEY (creator_id)
        REFERENCES creator (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_youtube_channel_watch__channel_id_not_blank CHECK (btrim(youtube_channel_id) <> ''),
    CONSTRAINT ck_youtube_channel_watch__subscription_status
        CHECK (subscription_status IN ('ACTIVE', 'INACTIVE', 'RENEWAL_FAILED', 'UNKNOWN')),
    CONSTRAINT ck_youtube_channel_watch__token_hash_not_empty
        CHECK (subscription_token_hash IS NULL OR octet_length(subscription_token_hash) > 0),
    CONSTRAINT ck_youtube_channel_watch__last_error_not_blank
        CHECK (last_error_category IS NULL OR btrim(last_error_category) <> ''),
    CONSTRAINT ck_youtube_channel_watch__updated_after_created CHECK (updated_at >= created_at)
);

-- ---------------------------------------------------------------------------
-- 4. Worker claim·관리자 검수·태그 조회 인덱스
-- ---------------------------------------------------------------------------
CREATE INDEX ix_ai_job__claim
    ON ai_extraction_job (priority DESC, created_at, id)
    WHERE execution_status = 'QUEUED';

CREATE INDEX ix_ai_job__expired_lease_claim
    ON ai_extraction_job (lease_expires_at, priority DESC, created_at, id)
    WHERE execution_status = 'RUNNING';

CREATE INDEX ix_ai_job__review
    ON ai_extraction_job (created_at DESC, id)
    WHERE execution_status = 'SUCCEEDED';

CREATE INDEX ix_ai_snapshot__review
    ON ai_candidate_snapshot (review_status, created_at DESC, id);

CREATE INDEX ix_ai_tag_review__candidate
    ON ai_candidate_tag_review (snapshot_id, candidate_tag_id, reviewed_at DESC, id DESC);

CREATE INDEX ix_visit_tag__tag_lookup
    ON visit_tag (tag_definition_id, visit_id);

-- ---------------------------------------------------------------------------
-- 5. 통제 태그 기준 데이터 (18건)
-- ---------------------------------------------------------------------------
-- ON CONFLICT DO NOTHING을 사용하지 않는다. 기존 값과 다른 기준 데이터는 migration 실패로 드러낸다.
INSERT INTO tag_definition (
    id, tag_code, tag_type, display_name, aliases, status, source, created_at, updated_at
)
VALUES
    ('30000000-0000-4000-8000-000000000001', 'MENU_NAENGMYEON', 'MENU', '냉면', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000002', 'MENU_GUKBAP', 'MENU', '국밥', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000003', 'MENU_RAMEN', 'MENU', '라멘', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000004', 'MENU_SUSHI', 'MENU', '스시', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000005', 'MENU_PIZZA', 'MENU', '피자', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000006', 'MENU_SAMGYEOPSAL', 'MENU', '삼겹살', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000007', 'TASTE_SPICY', 'TASTE', '매운맛', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000008', 'TASTE_SWEET', 'TASTE', '단맛', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000009', 'TASTE_SAVORY', 'TASTE', '감칠맛', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000010', 'TASTE_LIGHT', 'TASTE', '담백한 맛', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000011', 'OCCASION_SOLO', 'OCCASION', '혼밥', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000012', 'OCCASION_DATE', 'OCCASION', '데이트', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000013', 'OCCASION_GROUP', 'OCCASION', '모임', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000014', 'OCCASION_LATE_NIGHT', 'OCCASION', '야식', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000015', 'ATMOSPHERE_CASUAL', 'ATMOSPHERE', '캐주얼', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000016', 'ATMOSPHERE_QUIET', 'ATMOSPHERE', '조용한', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000017', 'ATMOSPHERE_LIVELY', 'ATMOSPHERE', '활기찬', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-4000-8000-000000000018', 'ATMOSPHERE_BAR', 'ATMOSPHERE', '바', '[]'::jsonb, 'ACTIVE', 'SEED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------------------
-- 6. AI 작업 재사용 조회 인덱스 (구 V5)
-- ---------------------------------------------------------------------------
-- AI 작업 사전 멱등 조회 인덱스
-- 근거: 관리자 재접수는 외부 YouTube 검증 전에 기존 작업을 조회해야 한다.

CREATE INDEX ix_ai_job__video_input_versions
    ON ai_extraction_job (
        youtube_video_id, input_hash, provider, model_version, prompt_version, schema_version,
        created_at DESC, id DESC
    );

CREATE INDEX ix_ai_job__video_mode_versions
    ON ai_extraction_job (
        youtube_video_id, input_mode, provider, model_version, prompt_version, schema_version,
        created_at DESC, id DESC
    );

CREATE INDEX ix_ai_temporary_input__expires_at
    ON ai_extraction_temporary_input (expires_at, job_id);

-- ---------------------------------------------------------------------------
-- 7. 관리자 검수 감사·재시도 사유 (구 V6)
-- ---------------------------------------------------------------------------
ALTER TABLE ai_candidate_snapshot
    ADD COLUMN registered_restaurant_id uuid,
    ADD COLUMN registered_creator_id uuid,
    ADD COLUMN registered_video_id uuid,
    ADD COLUMN registered_visit_id uuid,
    ADD COLUMN restaurant_created boolean,
    ADD COLUMN creator_created boolean,
    ADD COLUMN video_created boolean,
    ADD COLUMN visit_created boolean;

ALTER TABLE ai_candidate_snapshot
    ADD CONSTRAINT fk_ai_snapshot__registered_restaurant FOREIGN KEY (registered_restaurant_id)
        REFERENCES restaurant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ai_snapshot__registered_creator FOREIGN KEY (registered_creator_id)
        REFERENCES creator(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ai_snapshot__registered_video FOREIGN KEY (registered_video_id)
        REFERENCES video(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ai_snapshot__registered_visit FOREIGN KEY (registered_visit_id)
        REFERENCES visit(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT ck_ai_snapshot__registration_flags CHECK (
        (registered_restaurant_id IS NULL AND restaurant_created IS NULL)
        OR (registered_restaurant_id IS NOT NULL AND restaurant_created IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_snapshot__registration_flags_creator CHECK (
        (registered_creator_id IS NULL AND creator_created IS NULL)
        OR (registered_creator_id IS NOT NULL AND creator_created IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_snapshot__registration_flags_video CHECK (
        (registered_video_id IS NULL AND video_created IS NULL)
        OR (registered_video_id IS NOT NULL AND video_created IS NOT NULL)
    ),
    ADD CONSTRAINT ck_ai_snapshot__registration_flags_visit CHECK (
        (registered_visit_id IS NULL AND visit_created IS NULL)
        OR (registered_visit_id IS NOT NULL AND visit_created IS NOT NULL)
    );

ALTER TABLE ai_candidate_tag_review
    ADD COLUMN manual_tag_code varchar(64),
    ADD CONSTRAINT ck_ai_candidate_tag_review__manual_tag_code CHECK (
        manual_tag_code IS NULL OR btrim(manual_tag_code) <> ''
    );

CREATE TABLE ai_extraction_manual_review (
    id uuid NOT NULL PRIMARY KEY,
    snapshot_id uuid NOT NULL REFERENCES ai_candidate_snapshot(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    decision varchar(16) NOT NULL CHECK (decision IN ('CONFIRM','DISCARD','ROLLBACK')),
    previous_review_status varchar(24) NOT NULL CHECK (previous_review_status IN ('AUTO_CONFIRMED','AUTO_BLOCKED','AUTO_REJECTED')),
    reviewed_by uuid NOT NULL REFERENCES admin_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    reason varchar(1000) NOT NULL CHECK (btrim(reason) <> ''),
    reviewed_at timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_ai_manual_review__snapshot ON ai_extraction_manual_review(snapshot_id, reviewed_at DESC, id DESC);

ALTER TABLE ai_extraction_job
    ADD COLUMN retry_reason varchar(1000),
    ADD CONSTRAINT ck_ai_extraction_job__retry_reason
        CHECK (retry_reason IS NULL OR (source = 'ADMIN' AND btrim(retry_reason) <> ''));

-- ---------------------------------------------------------------------------
-- 8. 태그 롤백 provenance (구 V7)
-- ---------------------------------------------------------------------------
ALTER TABLE visit_tag
    ADD COLUMN created_from_snapshot_id uuid,
    ADD CONSTRAINT fk_visit_tag__created_from_snapshot
        FOREIGN KEY (created_from_snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE INDEX ix_visit_tag__created_from_snapshot
    ON visit_tag (created_from_snapshot_id)
    WHERE created_from_snapshot_id IS NOT NULL;
