-- V5: AI 작업 사전 멱등 조회 인덱스
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
