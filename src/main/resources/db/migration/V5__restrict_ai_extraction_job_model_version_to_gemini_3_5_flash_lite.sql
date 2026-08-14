ALTER TABLE ai_extraction_job
    DROP CONSTRAINT ck_ai_extraction_job__model_version;

ALTER TABLE ai_extraction_job
    ADD CONSTRAINT ck_ai_extraction_job__model_version CHECK (
        model_version = 'gemini-3.5-flash-lite'
    );
