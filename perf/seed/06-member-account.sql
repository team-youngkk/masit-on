-- perf/seed/06-member-account.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (6/9).
-- member_account 1,000건을 넣는다 (이번 이슈에서 팀이 구두 확정).
--
-- 선행 조건: 05-visit.sql까지 실행됨(순서상 독립이지만 파일명 순서를 그대로 따른다).
--
-- 모두 status='ACTIVE'로 만든다. ck_member_account__status_timestamps(스키마 사실 9번)
-- 때문에 ACTIVE면 email_verified_at NOT NULL AND deletion_requested_at IS NULL이어야
-- 하므로 그렇게 채운다. email은 실제 개인정보가 아니라 example.invalid 도메인의
-- 명백한 테스트용 값이다. password_hash도 실제 해시가 아닌 placeholder다.

INSERT INTO member_account (
    id, email, password_hash, email_verified_at, status, deletion_requested_at,
    created_at, updated_at
)
SELECT
    (regexp_replace(md5('perf-seed-member-' || gs::text),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    'perf-seed-member-' || lpad(gs::text, 5, '0') || '@example.invalid',
    'perf-seed-not-a-real-hash-placeholder',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 1000) AS gs
ON CONFLICT (id) DO NOTHING;
