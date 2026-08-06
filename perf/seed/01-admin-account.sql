-- perf/seed/01-admin-account.sql
-- NFR-PERFORMANCE-006 부하 테스트용 기준 데이터 시드 (1/9).
--
-- 전체 실행 순서:
--   00-cleanup.sql (재시드 시에만) -> 01-admin-account.sql -> 02-restaurant.sql
--   -> 03-creator.sql -> 04-video.sql -> 05-visit.sql -> 06-member-account.sql
--   -> 07-favorite.sql -> 08-curation.sql -> 09-analyze.sql
-- 선행 조건: V1~V3 마이그레이션이 이미 적용된 빈 DB(또는 00-cleanup.sql로 시드만 비운 DB).
--
-- 이 파일이 하는 일: curation.created_by/updated_by가 admin_account FK를 요구하므로
-- (스키마 사실 11번), 큐레이션 시드가 참조할 관리자 계정 1건을 만든다.
--
-- id 결정 규칙: 모든 UUID PK는 DB 기본값이 없어(스키마 사실 1번) 시드가 직접 만들어야 한다.
-- md5(고정 문자열)를 하이픈 위치로 재배열해 uuid로 캐스팅하는 방식을 이 시드 전체에서 일관되게
-- 쓴다. 이유: (a) 재실행해도 항상 같은 id가 나와 재현 가능한 측정이 되고, (b) 확장 설치 없이
-- 코어 함수(md5, regexp_replace)만으로 동작하며, (c) 원본 문자열(마커)이 그대로 남아 있어
-- 사람이 봐도 어떤 시드 대상인지 추적할 수 있다.
--
-- password_hash는 실제 비밀번호 해시가 아니라 명백한 placeholder 문자열이다. 이 계정은
-- 로그인 흐름 부하 테스트 대상이 아니라 curation FK 충족용이므로 실제 인증에 쓰지 않는다.

INSERT INTO admin_account (id, login_id, password_hash, role, active, created_at, updated_at)
SELECT
    (regexp_replace(md5('perf-seed-admin-account'),
        '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '\1-\2-\3-\4-\5'))::uuid,
    'perf-seed-admin',
    'perf-seed-not-a-real-hash-placeholder',
    'ADMIN',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
ON CONFLICT (id) DO NOTHING;
