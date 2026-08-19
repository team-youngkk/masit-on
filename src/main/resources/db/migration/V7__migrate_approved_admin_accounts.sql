-- V7: 공동 승인된 legacy 관리자 계정의 확장·복사 단계
-- 근거: docs/05-specs/data/migration-plan.md 13.1절
--
-- V6 staging 입력은 인증·데이터 소유자가 운영 변경 기록을 통해 적재한다.
-- 이 파일에는 실제 이메일, 비밀번호 또는 승인 입력을 포함하지 않는다.
-- legacy FK cutover와 legacy 테이블 제거는 13.2 계약 단계까지 수행하지 않는다.

-- PostgreSQL에서는 Flyway가 마이그레이션 전체를 하나의 트랜잭션으로 실행한다.
-- 따라서 아래 검증 하나라도 실패하면 뒤의 복사·역할 변경·매핑 확정도 모두 rollback된다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        LEFT JOIN admin_account_migration_map map
            ON map.admin_account_id = legacy.id
        GROUP BY legacy.id
        HAVING count(map.admin_account_id) <> 1
    ) THEN
        RAISE EXCEPTION 'All legacy admin accounts require exactly one approved migration map';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        JOIN admin_account_migration_map map
            ON map.admin_account_id = legacy.id
        WHERE (legacy.active AND map.migration_disposition <> 'MIGRATE_ACTIVE')
           OR (NOT legacy.active AND map.migration_disposition <> 'PRESERVE_INACTIVE')
    ) THEN
        RAISE EXCEPTION 'Legacy admin activity and migration disposition must match';
    END IF;

    -- A normalized match must resolve to one canonical member email only.
    IF EXISTS (
        SELECT 1
        FROM admin_account_migration_map map
        JOIN member_account member
            ON lower(btrim(member.email)) = map.normalized_email
        GROUP BY map.admin_account_id
        HAVING count(member.id) > 1
    ) OR EXISTS (
        SELECT 1
        FROM admin_account_migration_map map
        JOIN member_account member
            ON lower(btrim(member.email)) = map.normalized_email
        WHERE member.email <> map.normalized_email
    ) THEN
        RAISE EXCEPTION 'Migration map must resolve to one normalized member email';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        JOIN admin_account_migration_map map
            ON map.admin_account_id = legacy.id
        JOIN member_account member
            ON member.email = map.normalized_email
        WHERE legacy.active AND member.status <> 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'An active legacy admin cannot be promoted through a non-active member account';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        JOIN admin_account_migration_map map
            ON map.admin_account_id = legacy.id
        JOIN member_account member
            ON member.id = legacy.id
        WHERE member.email <> map.normalized_email
    ) THEN
        RAISE EXCEPTION 'Legacy admin UUID collides with a member account for a different email';
    END IF;

    -- Spring Security BCryptPasswordEncoder accepts these BCrypt variants and cost range.
    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        WHERE legacy.password_hash !~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'
    ) THEN
        RAISE EXCEPTION 'Legacy admin password hash is not a supported BCrypt value';
    END IF;

    -- A pre-populated member ID is permitted only when it is the resolved matching member.
    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        JOIN admin_account_migration_map map
            ON map.admin_account_id = legacy.id
        LEFT JOIN member_account member
            ON member.email = map.normalized_email
        WHERE map.member_account_id IS NOT NULL
          AND (member.id IS NULL OR map.member_account_id <> member.id)
    ) THEN
        RAISE EXCEPTION 'Migration map contains an unresolved member account mapping';
    END IF;
END
$$;

-- An approved active legacy administrator without a member account receives a new
-- ACTIVE/ADMIN account. The staging creation timestamp is the approved verification evidence.
INSERT INTO member_account (id, email, password_hash, status, email_verified_at, role)
SELECT legacy.id,
       map.normalized_email,
       legacy.password_hash,
       'ACTIVE',
       map.created_at,
       'ADMIN'
FROM admin_account legacy
JOIN admin_account_migration_map map
    ON map.admin_account_id = legacy.id
LEFT JOIN member_account member
    ON member.email = map.normalized_email
WHERE legacy.active
  AND map.migration_disposition = 'MIGRATE_ACTIVE'
  AND member.id IS NULL;

-- Inactive legacy administrators never gain an active session or an administrative role.
INSERT INTO member_account (id, email, password_hash, status, role)
SELECT legacy.id,
       map.normalized_email,
       legacy.password_hash,
       'DISABLED',
       'MEMBER'
FROM admin_account legacy
JOIN admin_account_migration_map map
    ON map.admin_account_id = legacy.id
LEFT JOIN member_account member
    ON member.email = map.normalized_email
WHERE NOT legacy.active
  AND map.migration_disposition = 'PRESERVE_INACTIVE'
  AND member.id IS NULL;

-- An already active member keeps its UUID, status and password, and receives ADMIN only.
UPDATE member_account member
SET role = 'ADMIN',
    updated_at = CURRENT_TIMESTAMP
FROM admin_account legacy
JOIN admin_account_migration_map map
    ON map.admin_account_id = legacy.id
WHERE legacy.active
  AND map.migration_disposition = 'MIGRATE_ACTIVE'
  AND member.email = map.normalized_email;

UPDATE admin_account_migration_map map
SET member_account_id = member.id
FROM member_account member
WHERE member.email = map.normalized_email;

-- Retain legacy tables and FKs for the compatibility observation period. This guard
-- protects against a partial or unexpected resolution even though the transaction rolls back.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM admin_account legacy
        LEFT JOIN admin_account_migration_map map
            ON map.admin_account_id = legacy.id
        LEFT JOIN member_account member
            ON member.id = map.member_account_id
        WHERE map.member_account_id IS NULL
           OR member.id IS NULL
           OR member.email <> map.normalized_email
    ) THEN
        RAISE EXCEPTION 'Every legacy admin must resolve to its approved member account';
    END IF;
END
$$;
