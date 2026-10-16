-- #421: Provider 응답을 받았지만 결과 반영 전에 Worker가 종료된 경우 재호출 없이 복구한다.
ALTER TABLE ai_registration_unit_retry
    ADD COLUMN provider_result_ciphertext bytea,
    ADD COLUMN provider_result_key_id varchar(128),
    ADD COLUMN provider_result_at timestamp(6) with time zone,
    ADD CONSTRAINT ck_ai_registration_unit_retry__provider_result_pair CHECK (
        (provider_result_ciphertext IS NULL AND provider_result_key_id IS NULL AND provider_result_at IS NULL)
        OR (provider_result_ciphertext IS NOT NULL AND octet_length(provider_result_ciphertext) > 0
            AND provider_result_key_id IS NOT NULL AND btrim(provider_result_key_id) <> ''
            AND provider_result_at IS NOT NULL AND provider_result_at >= created_at
            AND provider_result_at <= created_at + interval '24 hours'
            AND provider_attempt_no IS NOT NULL)
    );
