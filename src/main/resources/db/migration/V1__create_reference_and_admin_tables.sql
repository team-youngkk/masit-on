-- V1: region, food_category, admin_account
-- 근거: docs/05-specs/data/table-definitions.md 2~4절, constraint-mapping.md 2·4절

CREATE TABLE region
(
    id           uuid                     NOT NULL,
    code         varchar(32)              NOT NULL,
    name         varchar(20)              NOT NULL,
    sort_order   smallint                 NOT NULL,
    active       boolean                  NOT NULL DEFAULT true,
    created_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_region PRIMARY KEY (id),
    CONSTRAINT uk_region__code UNIQUE (code),
    CONSTRAINT uk_region__name UNIQUE (name),
    CONSTRAINT uk_region__sort_order UNIQUE (sort_order),
    CONSTRAINT ck_region__sort_order CHECK (sort_order BETWEEN 1 AND 25),
    CONSTRAINT ck_region__code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_region__name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE food_category
(
    id           uuid                     NOT NULL,
    code         varchar(32)              NOT NULL,
    name         varchar(30)              NOT NULL,
    sort_order   smallint                 NOT NULL,
    active       boolean                  NOT NULL DEFAULT true,
    created_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_food_category PRIMARY KEY (id),
    CONSTRAINT uk_food_category__code UNIQUE (code),
    CONSTRAINT uk_food_category__name UNIQUE (name),
    CONSTRAINT uk_food_category__sort_order UNIQUE (sort_order),
    CONSTRAINT ck_food_category__sort_order CHECK (sort_order BETWEEN 1 AND 10),
    CONSTRAINT ck_food_category__code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_food_category__name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE admin_account
(
    id             uuid                     NOT NULL,
    login_id       varchar(100)             NOT NULL,
    password_hash  varchar(255)             NOT NULL,
    role           varchar(16)              NOT NULL DEFAULT 'ADMIN',
    active         boolean                  NOT NULL DEFAULT true,
    created_at     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_admin_account PRIMARY KEY (id),
    CONSTRAINT uk_admin_account__login_id UNIQUE (login_id),
    CONSTRAINT ck_admin_account__login_id_not_blank CHECK (btrim(login_id) <> ''),
    CONSTRAINT ck_admin_account__password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_admin_account__role CHECK (role = 'ADMIN')
);
