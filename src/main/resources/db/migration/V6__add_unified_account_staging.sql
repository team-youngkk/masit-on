-- V6: 통합 계정 전환의 확장 단계
-- 근거: docs/05-specs/data/migration-plan.md 13.1절,
--       docs/05-specs/data/table-definitions.md 4.1절
--
-- 이 마이그레이션은 역할 열과 공동 승인된 운영 입력을 적재할 staging만 만든다.
-- 실제 이메일, 승인 입력, 계정 복사, legacy FK cutover는 후속 계약 단계의 증거가
-- 승인된 뒤에만 별도 전진 마이그레이션으로 수행한다.

ALTER TABLE member_account
    ADD COLUMN role varchar(16) NOT NULL DEFAULT 'MEMBER';

ALTER TABLE member_account
    ADD CONSTRAINT ck_member_account__role
        CHECK (role IN ('MEMBER', 'ADMIN'));

CREATE TABLE admin_account_migration_map
(
    admin_account_id      uuid                        NOT NULL,
    normalized_email      varchar(254)                NOT NULL,
    migration_disposition varchar(24)                 NOT NULL,
    member_account_id     uuid,
    approval_record_id    varchar(100)                NOT NULL,
    created_at            timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_admin_account_migration_map PRIMARY KEY (admin_account_id),
    CONSTRAINT uk_admin_account_migration_map__normalized_email UNIQUE (normalized_email),
    CONSTRAINT uk_admin_account_migration_map__member_account UNIQUE (member_account_id),
    CONSTRAINT fk_admin_account_migration_map__admin_account FOREIGN KEY (admin_account_id)
        REFERENCES admin_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_admin_account_migration_map__member_account FOREIGN KEY (member_account_id)
        REFERENCES member_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_admin_account_migration_map__normalized_email CHECK (
        normalized_email = lower(btrim(normalized_email))
        AND normalized_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    ),
    CONSTRAINT ck_admin_account_migration_map__migration_disposition CHECK (
        migration_disposition IN ('MIGRATE_ACTIVE', 'PRESERVE_INACTIVE')
    ),
    CONSTRAINT ck_admin_account_migration_map__approval_record_id_not_blank CHECK (
        btrim(approval_record_id) <> ''
    )
);
