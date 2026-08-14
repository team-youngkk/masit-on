-- V8: 신규 AI 모델과 기존 작업 이력의 모델 버전 허용 범위

ALTER TABLE ai_extraction_job
    DROP CONSTRAINT ck_ai_extraction_job__model_version,
    ADD CONSTRAINT ck_ai_extraction_job__model_version CHECK (
        model_version IN ('gemini-3-flash-preview', 'gemini-3.5-flash-lite')
    );
