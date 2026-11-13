ALTER TABLE ai_extraction_job ADD COLUMN failure_http_status integer;
ALTER TABLE ai_extraction_attempt ADD COLUMN failure_http_status integer;

ALTER TABLE ai_extraction_job ADD CONSTRAINT ck_ai_extraction_job_failure_http_status
    CHECK (failure_http_status IS NULL OR failure_http_status BETWEEN 100 AND 599);
ALTER TABLE ai_extraction_attempt ADD CONSTRAINT ck_ai_extraction_attempt_failure_http_status
    CHECK (failure_http_status IS NULL OR failure_http_status BETWEEN 100 AND 599);
