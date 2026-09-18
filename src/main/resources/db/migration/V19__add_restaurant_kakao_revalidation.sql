-- V19: 등록 후 Kakao 장소 재검증 상태·감사 이력
--
-- 기존 restaurant 행을 변경하거나 일괄 적재하지 않는다. 등록 흐름과 기존 데이터 보정
-- Worker가 restaurant_id 기준으로 PENDING 행을 lazy upsert한다. 이는 대용량 restaurant
-- 테이블의 장시간 scan·WAL 급증을 피하는 expand 단계다.
--
-- Worker는 짧은 DB transaction에서 PENDING/RETRY_SCHEDULED 또는 lease가 만료된 RUNNING
-- 행만 claim한 뒤 즉시 commit한다. Kakao 호출은 lease 보유 중 transaction 밖에서 실행하며,
-- 완료 시 audit INSERT와 last_execution_id를 조건으로 한 상태 UPDATE를 한 transaction에서
-- 수행한다. 만료된 이전 Worker의 완료 결과는 CAS가 0행을 갱신하므로 반영할 수 없다.

CREATE TABLE restaurant_kakao_revalidation
(
    restaurant_id       uuid                        NOT NULL,
    status              varchar(24)                 NOT NULL DEFAULT 'PENDING',
    attempt_count       integer                     NOT NULL DEFAULT 0,
    lease_owner         varchar(128),
    lease_expires_at    timestamp(6) with time zone,
    last_execution_id   uuid,
    last_checked_at     timestamp(6) with time zone,
    next_attempt_at     timestamp(6) with time zone DEFAULT CURRENT_TIMESTAMP,
    last_reason_code    varchar(48),
    last_error_code     varchar(32),
    last_error_message  varchar(1000),
    created_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_restaurant_kakao_revalidation PRIMARY KEY (restaurant_id),
    CONSTRAINT fk_restaurant_kakao_revalidation__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_restaurant_kakao_revalidation__status CHECK (
        status IN (
            'PENDING', 'RUNNING', 'VERIFIED', 'AUTO_CORRECTED',
            'REVIEW_REQUIRED', 'MATCH_NOT_FOUND', 'RETRY_SCHEDULED', 'RETRY_EXHAUSTED'
        )
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_restaurant_kakao_revalidation__lease_pair CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner IS NOT NULL AND btrim(lease_owner) <> '' AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__state_pair CHECK (
        (status = 'PENDING'
            AND lease_owner IS NULL AND last_execution_id IS NULL AND last_checked_at IS NULL
            AND next_attempt_at IS NOT NULL AND last_reason_code IS NULL AND last_error_code IS NULL
            AND last_error_message IS NULL)
        OR (status = 'RUNNING'
            AND lease_owner IS NOT NULL AND last_execution_id IS NOT NULL AND next_attempt_at IS NULL
            AND last_reason_code IS NULL AND last_error_code IS NULL AND last_error_message IS NULL)
        OR (status = 'RETRY_SCHEDULED'
            AND lease_owner IS NULL AND last_execution_id IS NOT NULL AND last_checked_at IS NOT NULL
            AND next_attempt_at IS NOT NULL AND last_reason_code IS NULL AND last_error_code IS NOT NULL)
        OR (status IN ('VERIFIED', 'AUTO_CORRECTED', 'REVIEW_REQUIRED', 'MATCH_NOT_FOUND')
            AND lease_owner IS NULL AND last_execution_id IS NOT NULL AND last_checked_at IS NOT NULL
            AND next_attempt_at IS NOT NULL AND last_reason_code IS NOT NULL AND last_error_code IS NULL
            AND last_error_message IS NULL)
        OR (status = 'RETRY_EXHAUSTED'
            AND lease_owner IS NULL AND last_execution_id IS NOT NULL AND last_checked_at IS NOT NULL
            AND next_attempt_at IS NULL AND last_reason_code IS NOT NULL AND last_error_code IS NULL
            AND last_error_message IS NULL)
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__reason_code CHECK (
        last_reason_code IS NULL OR last_reason_code IN (
            'NO_CHANGE', 'SAFE_FIELDS_CHANGED', 'KAKAO_PLACE_ID_MISMATCH',
            'KAKAO_PLACE_URL_MISMATCH', 'DISTRICT_CHANGED', 'IDENTITY_AMBIGUOUS',
            'KAKAO_PLACE_NOT_FOUND', 'RETRY_EXHAUSTED'
        )
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__reason_status CHECK (
        (status = 'VERIFIED' AND last_reason_code = 'NO_CHANGE')
        OR (status = 'AUTO_CORRECTED' AND last_reason_code = 'SAFE_FIELDS_CHANGED')
        OR (status = 'REVIEW_REQUIRED' AND last_reason_code IN (
            'KAKAO_PLACE_ID_MISMATCH', 'KAKAO_PLACE_URL_MISMATCH',
            'DISTRICT_CHANGED', 'IDENTITY_AMBIGUOUS'
        ))
        OR (status = 'MATCH_NOT_FOUND' AND last_reason_code = 'KAKAO_PLACE_NOT_FOUND')
        OR (status = 'RETRY_EXHAUSTED' AND last_reason_code = 'RETRY_EXHAUSTED')
        OR status IN ('PENDING', 'RUNNING', 'RETRY_SCHEDULED')
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__error_code CHECK (
        last_error_code IS NULL OR last_error_code IN ('HTTP_429', 'HTTP_5XX', 'TIMEOUT')
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__error_message_not_blank CHECK (
        last_error_message IS NULL OR btrim(last_error_message) <> ''
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation__checked_after_created CHECK (
        last_checked_at IS NULL OR last_checked_at >= created_at
    )
);

CREATE TABLE restaurant_kakao_revalidation_audit
(
    id                  uuid                        NOT NULL,
    execution_id        uuid                        NOT NULL,
    restaurant_id       uuid                        NOT NULL,
    status              varchar(24)                 NOT NULL,
    observed_values     jsonb                       NOT NULL,
    previous_values     jsonb                       NOT NULL,
    applied_values      jsonb,
    reason_code         varchar(48),
    error_code          varchar(32),
    error_message       varchar(1000),
    checked_at          timestamp(6) with time zone NOT NULL,
    next_attempt_at     timestamp(6) with time zone,
    CONSTRAINT pk_restaurant_kakao_revalidation_audit PRIMARY KEY (id),
    CONSTRAINT ux_restaurant_kakao_revalidation_audit__execution UNIQUE (execution_id),
    CONSTRAINT fk_restaurant_kakao_revalidation_audit__restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__status CHECK (
        status IN (
            'VERIFIED', 'AUTO_CORRECTED', 'REVIEW_REQUIRED',
            'MATCH_NOT_FOUND', 'RETRY_SCHEDULED', 'RETRY_EXHAUSTED'
        )
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__observed_object
        CHECK (jsonb_typeof(observed_values) = 'object'),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__previous_object
        CHECK (jsonb_typeof(previous_values) = 'object'),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__applied_object
        CHECK (applied_values IS NULL OR jsonb_typeof(applied_values) = 'object'),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__reason_code CHECK (
        reason_code IS NULL OR reason_code IN (
            'NO_CHANGE', 'SAFE_FIELDS_CHANGED', 'KAKAO_PLACE_ID_MISMATCH',
            'KAKAO_PLACE_URL_MISMATCH', 'DISTRICT_CHANGED', 'IDENTITY_AMBIGUOUS',
            'KAKAO_PLACE_NOT_FOUND', 'RETRY_EXHAUSTED'
        )
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__reason_status CHECK (
        (status = 'VERIFIED' AND reason_code = 'NO_CHANGE' AND applied_values IS NULL)
        OR (status = 'AUTO_CORRECTED' AND reason_code = 'SAFE_FIELDS_CHANGED'
            AND applied_values IS NOT NULL AND applied_values <> '{}'::jsonb)
        OR (status = 'REVIEW_REQUIRED' AND reason_code IN (
            'KAKAO_PLACE_ID_MISMATCH', 'KAKAO_PLACE_URL_MISMATCH',
            'DISTRICT_CHANGED', 'IDENTITY_AMBIGUOUS'
        ) AND applied_values IS NULL)
        OR (status = 'MATCH_NOT_FOUND' AND reason_code = 'KAKAO_PLACE_NOT_FOUND'
            AND applied_values IS NULL)
        OR (status = 'RETRY_EXHAUSTED' AND reason_code = 'RETRY_EXHAUSTED'
            AND applied_values IS NULL)
        OR (status = 'RETRY_SCHEDULED' AND reason_code IS NULL AND applied_values IS NULL)
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__error_code CHECK (
        error_code IS NULL OR error_code IN ('HTTP_429', 'HTTP_5XX', 'TIMEOUT')
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__error_status CHECK (
        (status = 'RETRY_SCHEDULED' AND error_code IS NOT NULL AND next_attempt_at IS NOT NULL)
        OR (status IN ('VERIFIED', 'AUTO_CORRECTED', 'REVIEW_REQUIRED', 'MATCH_NOT_FOUND')
            AND error_code IS NULL AND error_message IS NULL AND next_attempt_at IS NOT NULL)
        OR (status = 'RETRY_EXHAUSTED' AND error_code IS NULL AND error_message IS NULL
            AND next_attempt_at IS NULL)
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__error_message_not_blank CHECK (
        error_message IS NULL OR btrim(error_message) <> ''
    ),
    CONSTRAINT ck_restaurant_kakao_revalidation_audit__next_after_checked CHECK (
        next_attempt_at IS NULL OR next_attempt_at > checked_at
    )
);

CREATE INDEX ix_restaurant_kakao_revalidation__claim_due
    ON restaurant_kakao_revalidation (next_attempt_at, restaurant_id)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'VERIFIED', 'AUTO_CORRECTED',
                     'REVIEW_REQUIRED', 'MATCH_NOT_FOUND');

CREATE INDEX ix_restaurant_kakao_revalidation__claim_expired_lease
    ON restaurant_kakao_revalidation (lease_expires_at, restaurant_id)
    WHERE status = 'RUNNING';

CREATE INDEX ix_restaurant_kakao_revalidation_audit__restaurant_checked
    ON restaurant_kakao_revalidation_audit (restaurant_id, checked_at DESC, id DESC);

CREATE INDEX ix_restaurant__place_revalidation_seed
    ON restaurant (updated_at, id)
    WHERE lifecycle_status = 'ACTIVE';

CREATE FUNCTION prevent_restaurant_kakao_revalidation_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'restaurant_kakao_revalidation_audit is append-only';
END;
$$;

CREATE TRIGGER tr_restaurant_kakao_revalidation_audit__append_only
BEFORE UPDATE OR DELETE ON restaurant_kakao_revalidation_audit
FOR EACH ROW EXECUTE FUNCTION prevent_restaurant_kakao_revalidation_audit_mutation();
