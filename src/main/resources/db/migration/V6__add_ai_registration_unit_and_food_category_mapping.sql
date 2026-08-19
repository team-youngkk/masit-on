-- V6: AI 등록 단위·카테고리 매핑 기준정보 추가와 Snapshot 절단 표시 보강
-- 근거: docs/05-specs/data/third-expansion-ai-video-data-contract.md 5, 5.1, 5.2, 5.3, 11절
--
-- 적용 순서: ai_candidate_snapshot 보강 -> ai_registration_unit -> food_category_mapping(+seed)
--            -> ai_registration_unit_review -> 데이터 계약 11절 인덱스 후보

-- ---------------------------------------------------------------------------
-- 1. ai_candidate_snapshot 보강: 후보 수 상한으로 인한 절단 여부
-- ---------------------------------------------------------------------------
ALTER TABLE ai_candidate_snapshot
    ADD COLUMN candidate_truncated boolean NOT NULL DEFAULT false;

-- ---------------------------------------------------------------------------
-- 2. ai_registration_unit: Snapshot을 장소 단위로 나눈 등록 단위와 판정·등록 결과
-- ---------------------------------------------------------------------------
CREATE FUNCTION ai_registration_unit_reused_resources_are_valid(value jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    IF jsonb_typeof(value) <> 'array' THEN
        RETURN false;
    END IF;

    RETURN NOT EXISTS (
        SELECT 1
        FROM jsonb_array_elements(value) AS resource_element(resource_value)
        WHERE jsonb_typeof(resource_value) <> 'string'
           OR (resource_value #>> '{}') NOT IN ('creator', 'video')
    );
END;
$$;

CREATE TABLE ai_registration_unit
(
    id                       uuid                        NOT NULL,
    snapshot_id              uuid                        NOT NULL,
    unit_index               integer                     NOT NULL,
    restaurant_name          varchar(255)                NOT NULL,
    review_status            varchar(24)                 NOT NULL,
    block_reason             varchar(64),
    place_decision           jsonb,
    category_decision        jsonb,
    registered_restaurant_id uuid,
    registered_creator_id    uuid,
    registered_video_id      uuid,
    registered_visit_id      uuid,
    reused_resources         jsonb                       NOT NULL DEFAULT '[]'::jsonb,
    executed_by              varchar(16)                 NOT NULL,
    decided_at               timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rolled_back_at           timestamp(6) with time zone,
    discarded_at             timestamp(6) with time zone,
    CONSTRAINT pk_ai_registration_unit PRIMARY KEY (id),
    CONSTRAINT ux_ai_registration_unit__snapshot_index UNIQUE (snapshot_id, unit_index),
    CONSTRAINT fk_ai_registration_unit__snapshot FOREIGN KEY (snapshot_id)
        REFERENCES ai_candidate_snapshot (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit__restaurant FOREIGN KEY (registered_restaurant_id)
        REFERENCES restaurant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit__creator FOREIGN KEY (registered_creator_id)
        REFERENCES creator (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit__video FOREIGN KEY (registered_video_id)
        REFERENCES video (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit__visit FOREIGN KEY (registered_visit_id)
        REFERENCES visit (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_registration_unit__unit_index CHECK (unit_index > 0),
    CONSTRAINT ck_ai_registration_unit__restaurant_name_not_blank CHECK (btrim(restaurant_name) <> ''),
    CONSTRAINT ck_ai_registration_unit__review_status
        CHECK (review_status IN ('AUTO_CONFIRMED', 'AUTO_BLOCKED', 'AUTO_REJECTED', 'MANUAL_OVERRIDE')),
    CONSTRAINT ck_ai_registration_unit__block_reason_values CHECK (
        block_reason IS NULL OR block_reason IN (
            'PLACE_NOT_FOUND', 'PLACE_AMBIGUOUS', 'CATEGORY_UNRESOLVED', 'MISSING_REQUIRED_FIELD',
            'VISIT_EVIDENCE_REQUIRED', 'DUPLICATE_CONFLICT', 'EXTERNAL_SERVICE_ERROR'
        )
    ),
    CONSTRAINT ck_ai_registration_unit__block_reason_pair CHECK (
        (review_status IN ('AUTO_BLOCKED', 'AUTO_REJECTED') AND block_reason IS NOT NULL)
        OR (review_status NOT IN ('AUTO_BLOCKED', 'AUTO_REJECTED') AND block_reason IS NULL)
    ),
    CONSTRAINT ck_ai_registration_unit__place_decision_object
        CHECK (place_decision IS NULL OR jsonb_typeof(place_decision) = 'object'),
    CONSTRAINT ck_ai_registration_unit__category_decision_object
        CHECK (category_decision IS NULL OR jsonb_typeof(category_decision) = 'object'),
    CONSTRAINT ck_ai_registration_unit__reused_resources
        CHECK (ai_registration_unit_reused_resources_are_valid(reused_resources)),
    CONSTRAINT ck_ai_registration_unit__executed_by CHECK (executed_by IN ('WORKER', 'ADMIN')),
    -- 롤백과 폐기는 동시에 값을 가질 수 없다 (5.1절: 롤백은 등록된 단위를, 폐기는 등록되지 않은 단위를 대상으로 한다).
    CONSTRAINT ck_ai_registration_unit__manual_override_exclusive
        CHECK (rolled_back_at IS NULL OR discarded_at IS NULL),
    -- 롤백·폐기는 MANUAL_OVERRIDE 상태에서만 값을 가질 수 있다.
    CONSTRAINT ck_ai_registration_unit__manual_override_gate CHECK (
        (rolled_back_at IS NULL AND discarded_at IS NULL) OR review_status = 'MANUAL_OVERRIDE'
    ),
    -- 등록 결과 4종 + place_decision/category_decision은 모두 NULL이거나 모두 NOT NULL이다.
    CONSTRAINT ck_ai_registration_unit__registration_result_pair CHECK (
        (registered_restaurant_id IS NULL AND registered_creator_id IS NULL
            AND registered_video_id IS NULL AND registered_visit_id IS NULL
            AND place_decision IS NULL AND category_decision IS NULL)
        OR (registered_restaurant_id IS NOT NULL AND registered_creator_id IS NOT NULL
            AND registered_video_id IS NOT NULL AND registered_visit_id IS NOT NULL
            AND place_decision IS NOT NULL AND category_decision IS NOT NULL)
    ),
    -- 5.1절 표: 등록 결과 존재 여부는 상태와 완전한 동치다. AUTO_CONFIRMED와 롤백·폐기 되지 않은
    -- MANUAL_OVERRIDE는 항상 등록 결과가 존재하고, 그 밖의 모든 상태(AUTO_BLOCKED, AUTO_REJECTED,
    -- 롤백된·폐기된 MANUAL_OVERRIDE)는 항상 존재하지 않는다.
    CONSTRAINT ck_ai_registration_unit__registration_result_status CHECK (
        (registered_restaurant_id IS NOT NULL) = (
            review_status IN ('AUTO_CONFIRMED', 'MANUAL_OVERRIDE')
            AND rolled_back_at IS NULL AND discarded_at IS NULL
        )
    ),
    CONSTRAINT ck_ai_registration_unit__rolled_back_after_decided
        CHECK (rolled_back_at IS NULL OR rolled_back_at >= decided_at),
    CONSTRAINT ck_ai_registration_unit__discarded_after_decided
        CHECK (discarded_at IS NULL OR discarded_at >= decided_at)
);

CREATE INDEX ix_ai_registration_unit__status
    ON ai_registration_unit (review_status, decided_at);

-- ---------------------------------------------------------------------------
-- 3. food_category_mapping: BR-AIEXTRACT-010 카테고리 매핑 기준정보
-- ---------------------------------------------------------------------------
CREATE TABLE food_category_mapping
(
    id               uuid                        NOT NULL,
    source_type      varchar(24)                 NOT NULL,
    pattern          varchar(128)                NOT NULL,
    match_type       varchar(16)                 NOT NULL,
    food_category_id uuid                        NOT NULL,
    priority         smallint                    NOT NULL,
    active           boolean                     NOT NULL DEFAULT true,
    created_at       timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_food_category_mapping PRIMARY KEY (id),
    CONSTRAINT fk_food_category_mapping__food_category FOREIGN KEY (food_category_id)
        REFERENCES food_category (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_food_category_mapping__source_type
        CHECK (source_type IN ('KAKAO_PLACE_CATEGORY', 'MENU_EXPRESSION')),
    CONSTRAINT ck_food_category_mapping__match_type CHECK (match_type IN ('EXACT', 'PARTIAL')),
    -- 5.2절: pattern은 공백 제거·소문자 통일로 정규화해 저장하고 대조 시에도 같은 정규화를 적용한다.
    CONSTRAINT ck_food_category_mapping__pattern_normalized CHECK (
        btrim(pattern) <> '' AND pattern !~ '\s' AND pattern = lower(pattern)
    ),
    CONSTRAINT ck_food_category_mapping__priority CHECK (priority >= 1),
    CONSTRAINT ck_food_category_mapping__updated_after_created CHECK (updated_at >= created_at)
);

-- 5.2절: "unique 제약은 활성 행에만 적용한다." 비활성 행은 append-only 이력 보존을 위해
-- 같은 키로 여러 벌 남을 수 있어야 하므로 일반 unique 제약이 아닌 partial unique index를 쓴다.
CREATE UNIQUE INDEX ux_food_category_mapping__pattern
    ON food_category_mapping (source_type, pattern, match_type)
    WHERE active;

-- ResolveVerifiedRestaurantReferenceService.MENU_CATEGORY 고정 키워드를 이관한 seed다 (39건).
-- 이 서비스의 조회 로직을 매핑 테이블 사용으로 전환하는 것은 이 마이그레이션의 범위가 아닌 별도 Task다.
INSERT INTO food_category_mapping (
    id, source_type, pattern, match_type, food_category_id, priority, active, created_at, updated_at
)
VALUES
    ('40000000-0000-4000-8000-000000000001', 'MENU_EXPRESSION', '한식', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000002', 'MENU_EXPRESSION', '한식집', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000003', 'MENU_EXPRESSION', '한식당', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000004', 'MENU_EXPRESSION', '냉면', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000005', 'MENU_EXPRESSION', '물냉면', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000006', 'MENU_EXPRESSION', '비빔냉면', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000007', 'MENU_EXPRESSION', '국밥', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000008', 'MENU_EXPRESSION', '삼겹살', 'EXACT', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000009', 'MENU_EXPRESSION', '중식', 'EXACT', '20000000-0000-4000-8000-000000000002', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000010', 'MENU_EXPRESSION', '중식집', 'EXACT', '20000000-0000-4000-8000-000000000002', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000011', 'MENU_EXPRESSION', '중국집', 'EXACT', '20000000-0000-4000-8000-000000000002', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000012', 'MENU_EXPRESSION', '일식', 'EXACT', '20000000-0000-4000-8000-000000000003', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000013', 'MENU_EXPRESSION', '일식집', 'EXACT', '20000000-0000-4000-8000-000000000003', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000014', 'MENU_EXPRESSION', '라멘', 'EXACT', '20000000-0000-4000-8000-000000000003', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000015', 'MENU_EXPRESSION', '스시', 'EXACT', '20000000-0000-4000-8000-000000000003', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000016', 'MENU_EXPRESSION', '초밥', 'EXACT', '20000000-0000-4000-8000-000000000003', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000017', 'MENU_EXPRESSION', '양식', 'EXACT', '20000000-0000-4000-8000-000000000004', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000018', 'MENU_EXPRESSION', '양식집', 'EXACT', '20000000-0000-4000-8000-000000000004', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000019', 'MENU_EXPRESSION', '이탈리안', 'EXACT', '20000000-0000-4000-8000-000000000004', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000020', 'MENU_EXPRESSION', '프렌치', 'EXACT', '20000000-0000-4000-8000-000000000004', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000021', 'MENU_EXPRESSION', '피자', 'EXACT', '20000000-0000-4000-8000-000000000004', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000022', 'MENU_EXPRESSION', '동남아', 'EXACT', '20000000-0000-4000-8000-000000000005', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000023', 'MENU_EXPRESSION', '동남아음식', 'EXACT', '20000000-0000-4000-8000-000000000005', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000024', 'MENU_EXPRESSION', '태국음식', 'EXACT', '20000000-0000-4000-8000-000000000005', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000025', 'MENU_EXPRESSION', '베트남음식', 'EXACT', '20000000-0000-4000-8000-000000000005', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000026', 'MENU_EXPRESSION', '인도음식', 'EXACT', '20000000-0000-4000-8000-000000000006', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000027', 'MENU_EXPRESSION', '인도·남아시아음식', 'EXACT', '20000000-0000-4000-8000-000000000006', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000028', 'MENU_EXPRESSION', '커리', 'EXACT', '20000000-0000-4000-8000-000000000006', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000029', 'MENU_EXPRESSION', '분식', 'EXACT', '20000000-0000-4000-8000-000000000007', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000030', 'MENU_EXPRESSION', '분식집', 'EXACT', '20000000-0000-4000-8000-000000000007', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000031', 'MENU_EXPRESSION', '김밥', 'EXACT', '20000000-0000-4000-8000-000000000007', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000032', 'MENU_EXPRESSION', '떡볶이', 'EXACT', '20000000-0000-4000-8000-000000000007', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000033', 'MENU_EXPRESSION', '카페', 'EXACT', '20000000-0000-4000-8000-000000000008', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000034', 'MENU_EXPRESSION', '디저트', 'EXACT', '20000000-0000-4000-8000-000000000008', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000035', 'MENU_EXPRESSION', '카페·디저트', 'EXACT', '20000000-0000-4000-8000-000000000008', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000036', 'MENU_EXPRESSION', '술집', 'EXACT', '20000000-0000-4000-8000-000000000009', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000037', 'MENU_EXPRESSION', '주점', 'EXACT', '20000000-0000-4000-8000-000000000009', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000038', 'MENU_EXPRESSION', '포차', 'EXACT', '20000000-0000-4000-8000-000000000009', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000039', 'MENU_EXPRESSION', '기타', 'EXACT', '20000000-0000-4000-8000-000000000010', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Kakao Local API category_name(예: "음식점 > 한식 > 냉면")의 계층 표현에서 쓰이는 대표 토큰을
-- PARTIAL로 대조하는 초기 seed다. 10개 canonical food_category 전부를 최소 1건씩 덮는 시작값이며
-- 운영 데이터로 점진 보강하는 것을 전제한다. 배타적 exhaustive coverage를 목표로 하지 않는다.
INSERT INTO food_category_mapping (
    id, source_type, pattern, match_type, food_category_id, priority, active, created_at, updated_at
)
VALUES
    ('40000000-0000-4000-8000-000000000040', 'KAKAO_PLACE_CATEGORY', '한식', 'PARTIAL', '20000000-0000-4000-8000-000000000001', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000041', 'KAKAO_PLACE_CATEGORY', '중식', 'PARTIAL', '20000000-0000-4000-8000-000000000002', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000042', 'KAKAO_PLACE_CATEGORY', '일식', 'PARTIAL', '20000000-0000-4000-8000-000000000003', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000043', 'KAKAO_PLACE_CATEGORY', '양식', 'PARTIAL', '20000000-0000-4000-8000-000000000004', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000044', 'KAKAO_PLACE_CATEGORY', '동남아', 'PARTIAL', '20000000-0000-4000-8000-000000000005', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000045', 'KAKAO_PLACE_CATEGORY', '인도', 'PARTIAL', '20000000-0000-4000-8000-000000000006', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000046', 'KAKAO_PLACE_CATEGORY', '커리', 'PARTIAL', '20000000-0000-4000-8000-000000000006', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000047', 'KAKAO_PLACE_CATEGORY', '분식', 'PARTIAL', '20000000-0000-4000-8000-000000000007', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000048', 'KAKAO_PLACE_CATEGORY', '카페', 'PARTIAL', '20000000-0000-4000-8000-000000000008', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000049', 'KAKAO_PLACE_CATEGORY', '디저트', 'PARTIAL', '20000000-0000-4000-8000-000000000008', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000050', 'KAKAO_PLACE_CATEGORY', '술집', 'PARTIAL', '20000000-0000-4000-8000-000000000009', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000051', 'KAKAO_PLACE_CATEGORY', '주점', 'PARTIAL', '20000000-0000-4000-8000-000000000009', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('40000000-0000-4000-8000-000000000052', 'KAKAO_PLACE_CATEGORY', '기타', 'PARTIAL', '20000000-0000-4000-8000-000000000010', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------------------
-- 4. ai_registration_unit_review: 등록 단위 사후 조작(CONFIRM/DISCARD/ROLLBACK/ADJUST_CATEGORY)의
--    append-only 감사 이력
-- ---------------------------------------------------------------------------
CREATE TABLE ai_registration_unit_review
(
    id                          uuid                        NOT NULL,
    registration_unit_id        uuid                        NOT NULL,
    decision                    varchar(24)                 NOT NULL,
    reason                      varchar(1000),
    submitted_supplements       jsonb,
    previous_category_decision  jsonb,
    reverted_registration       jsonb,
    reviewed_by                 uuid                        NOT NULL,
    reviewed_at                 timestamp(6) with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ai_registration_unit_review PRIMARY KEY (id),
    CONSTRAINT fk_ai_registration_unit_review__registration_unit FOREIGN KEY (registration_unit_id)
        REFERENCES ai_registration_unit (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_registration_unit_review__reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES member_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_ai_registration_unit_review__decision
        CHECK (decision IN ('CONFIRM', 'DISCARD', 'ROLLBACK', 'ADJUST_CATEGORY')),
    -- CONFIRM·DISCARD·ADJUST_CATEGORY는 사유가 필수다. ROLLBACK은 권장일 뿐이라 NULL을 허용하되,
    -- 값을 제출했다면 공백만으로는 채울 수 없다.
    CONSTRAINT ck_ai_registration_unit_review__reason CHECK (
        (decision IN ('CONFIRM', 'DISCARD', 'ADJUST_CATEGORY') AND reason IS NOT NULL AND btrim(reason) <> '')
        OR (decision = 'ROLLBACK' AND (reason IS NULL OR btrim(reason) <> ''))
    ),
    CONSTRAINT ck_ai_registration_unit_review__submitted_supplements CHECK (
        (decision = 'CONFIRM' AND submitted_supplements IS NOT NULL
            AND jsonb_typeof(submitted_supplements) = 'object')
        OR (decision <> 'CONFIRM' AND submitted_supplements IS NULL)
    ),
    CONSTRAINT ck_ai_registration_unit_review__previous_category_decision CHECK (
        (decision = 'ADJUST_CATEGORY' AND previous_category_decision IS NOT NULL
            AND jsonb_typeof(previous_category_decision) = 'object')
        OR (decision <> 'ADJUST_CATEGORY' AND previous_category_decision IS NULL)
    ),
    CONSTRAINT ck_ai_registration_unit_review__reverted_registration CHECK (
        (decision = 'ROLLBACK' AND reverted_registration IS NOT NULL
            AND jsonb_typeof(reverted_registration) = 'object')
        OR (decision <> 'ROLLBACK' AND reverted_registration IS NULL)
    )
);

CREATE INDEX ix_ai_registration_unit_review__unit
    ON ai_registration_unit_review (registration_unit_id, reviewed_at DESC, id DESC);
