---
id: DATA-E3-AI-001
title: 3차 확장 AI 영상 추출 데이터 계약
status: Accepted
owner: 김인안
reviewers:
  - 박진영
related_documents:
  - README.md
  - data-model.md
  - entity-definitions.md
  - constraints.md
  - lifecycle-rules.md
  - data-traceability.md
  - ../api/admin/ai-video-extraction-api.md
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - ../../01-requirements/non-functional-requirements.md
  - ../../02-analysis/third-expansion-domain-boundaries.md
  - ../../04-product/prd/admin/ai-video-information-extraction.md
  - ../../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../../08-planning/third-expansion-evaluation-strategy.md
---

# 3차 확장 AI 영상 추출 데이터 계약

## 1. 결정 상태와 범위

이 문서는 AI 영상 추출 작업, 후보 Snapshot, 통제 태그, Gemini 영상 입력 이력과 YouTube 채널 감시 상태의 논리·물리 데이터 경계를 정의하는 Accepted 계약이다. Google Gemini API는 `gemini-3.5-flash-lite`, Gemini Developer API global endpoint, Free Tier 전용·유료 호출 금지, 현재 Prompt `P7`, 결과 Schema `S1`을 사용한다. 기존 Prompt `P1`·`P2`·`P3`·`P4`·`P5`·`P6` 작업과 Snapshot은 재현성을 위한 역사적 이력으로만 보존한다. 컬럼·제약·인덱스의 정본은 이 문서와 [`V4__create_third_expansion_ai_schema.sql`](../../../src/main/resources/db/migration/V4__create_third_expansion_ai_schema.sql)의 대응을 검증하는 방식으로 관리한다.

**`ai_registration_unit`(5.1절)과 `food_category_mapping`(5.2절)은 `합의 대기` 상태다.** 두 테이블은 새 Flyway 마이그레이션과 seed를 요구하므로 Flyway 순서 소유자(박진영)와 restaurant 도메인 소유자의 합의 전에는 확정 계약으로 사용하지 않는다. 합의 후 별도 커밋에서 이 표시를 제거한다. 그 밖의 절은 종전대로 Accepted다.

- AI 후보 데이터는 기존 `Restaurant`, `Creator`, `Video`, `Visit`의 정식 데이터를 대체하지 않는다.
- 자동 검증과 기존 외부 검증 전에는 정식 Entity를 생성·수정·공개하지 않는다. 관리자 사전 승인은 요구하지 않는다.
- 원본 영상·전체 자막·전사·Gemini 응답 전문은 저장하지 않는다.
- Webhook과 관리자 추가는 동일 작업 경계에서 `source`만 구분한다.
- 초기 데이터 적립은 `BACKFILL` 우선순위로 관리하고 Webhook 실시간 작업보다 낮게 처리한다.

## 2. 논리 개념과 저장 여부

| 논리 개념 | 물리 저장 | 소유 | 결정 |
|---|---|---|---|
| AI Extraction Job | 저장 | WS-15 | 영상 식별자·입력 해시·유입 경로·상태·lease·버전·시각을 보존 |
| AI Extraction Temporary Input | 저장 | WS-15 | 관리자 보완 텍스트만 암호화해 작업 종료 후 24시간 이내까지 임시 보존 |
| AI Candidate Snapshot | 저장 | WS-15 | 필드별 후보·신뢰도·`TIMESTAMP`·`TEXT_RANGE`·`UNKNOWN` 근거·완전성·자동 등록 상태를 버전별 보존 |
| AI Registration Unit | 저장 | WS-15 | Snapshot을 장소 단위로 나눈 등록 단위와 단위별 판정 상태·장소·카테고리 근거·등록 결과를 보존 |
| AI Candidate Tag Review | 저장 | WS-15 | 후보 태그별 자동 판단·사후 보정·사유·주체·시각을 append-only로 보존 |
| Tag Definition | 저장 | WS-15·WS-14 협업 | 허용 태그 코드·유형·표시명·별칭·활성 상태를 보존 |
| Visit Tag | 저장 | WS-15 생성·WS-14 조회 | 관리자 확정 태그와 확정 Visit의 연결·근거·버전을 보존 |
| AI Extraction Attempt | 저장 | WS-15 | 시도 횟수·오류 범주·처리 시간·Token·무료 quota 사용량 메타데이터만 보존 |
| YouTube Channel Watch | 저장 | WS-15 | 관리자 활성화 채널·구독 상태·갱신·마지막 알림 상태를 보존 |
| Webhook Raw Payload | 저장하지 않음 | 해당 없음 | 검증에 필요한 식별자·해시·수신 시각만 Job에 반영 |
| Video/Subtitle Original | 저장하지 않음 | 기존 Video 아님 | URL과 외부 식별자만 후보 경계에서 참조 |

## 3. 공통 물리 규칙

- 내부 식별자는 [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md)에 따른 `uuid` 후보로 둔다.
- 모든 시각은 `timestamp(6) with time zone`, 이름은 단수 `lower_snake_case`를 사용한다.
- YouTube·Gemini 외부 식별자와 URL은 불투명 문자열로 저장하며 UUID 규칙을 전제하지 않는다.
- 입력 원문과 자동 수집 자막·전사 원문은 저장하지 않는다. 단, 관리자가 직접 제출한 보완 텍스트는 비동기 Worker 재시작 복구를 위해 별도 암호화 저장소에 임시 보존하고, 작업 종료 후 24시간 이내 삭제한다. 정규화 입력 해시는 작업 멱등성 검사용으로 보존하며 후보 Snapshot·시도·검수 이력은 1년 보존 후 정리한다.
- JSON 후보 필드는 Schema 버전을 함께 저장하고, 모델·Prompt·Schema 변경 시 기존 Snapshot을 덮어쓰지 않는다.
- Provider 호출은 DB 핵심 저장 트랜잭션과 분리한다. 후보 저장 실패와 정식 Entity 저장은 원자적으로 연결되지 않는다.
- AI는 자막에서 태그 후보를 생성할 수 있다. 후보는 정규화·동의어·문자 유사도·금지 표현·근거 검사를 통과하면 자동으로 `TagDefinition`을 만들거나 기존 정의와 통합하고, 자동 확정된 `Visit`에 `VisitTag`를 연결한다.

## 4. `ai_extraction_job`

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 작업 ID |
| `source` | `varchar(16)` | NN | `WEBHOOK/ADMIN` | 유입 경로 |
| `priority` | `varchar(16)` | NN | `REALTIME/BACKFILL` | 실시간·초기 적립 우선순위 |
| `youtube_channel_id` | `varchar(128)` | NN | 외부 식별자 | 감시·영상 채널 |
| `youtube_video_id` | `varchar(128)` | NN | 외부 식별자 | YouTube 영상 식별자 |
| `video_url` | `varchar(2048)` | NN | HTTPS YouTube URL | 외부 영상 링크 |
| `input_mode` | `varchar(24)` | NN | `GEMINI_VIDEO_URL/ADMIN_TEXT` | AI 입력 경로 |
| `input_hash` | `bytea` | NN | SHA-256 32 byte | 입력 동일성 |
| `provider` | `varchar(32)` | NN | `GOOGLE_GEMINI` 후보 | 제공자 |
| `model_version` | `varchar(128)` | NN | `gemini-3.5-flash-lite` 고정 | 모델 버전 |
| `prompt_version` | `varchar(64)` | NN | 빈 값 금지 | Prompt 버전 |
| `schema_version` | `varchar(64)` | NN | 빈 값 금지 | 결과 Schema 버전 |
| `retry_reason` | `varchar(1000)` | Yes | 관리자 재시도 작업에서만 non-blank | 해당 작업을 다시 요청한 사유 |
| `execution_status` | `varchar(16)` | NN | 상태 CHECK | `QUEUED/RUNNING/SUCCEEDED/FAILED` |
| `result_completeness` | `varchar(16)` | Yes | 성공 상태와 조합 | `COMPLETE/PARTIAL` |
| `attempt_count` | `smallint` | NN | 0 이상 | 실행 시도 횟수 |
| `lease_owner` | `varchar(128)` | Yes | 실행 상태와 조합 | Worker 소유자 |
| `lease_expires_at` | 시간 | Yes | lease 소유와 조합 | claim 만료 |
| `error_category` | `varchar(64)` | Yes | 실패 상태와 조합 | 정규화 오류 범주 |
| `created_at`, `started_at`, `finished_at` | 시간 | 상태별 | 시각 규칙 | 접수·시작·종료 |

멱등성 후보 키는 `(youtube_channel_id, youtube_video_id, input_hash, provider, model_version, prompt_version, schema_version)`다. 동일 키의 Webhook 반복, 관리자 재입력과 초기 적립 요청은 하나의 작업으로 수렴한다.

### 4.1 `ai_extraction_temporary_input`

관리자가 제출한 `supplementText`만 대상으로 한다. Webhook 작업에는 이 행을 만들지 않는다.

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `job_id` | `uuid` | NN | PK·FK → `ai_extraction_job.id` | 대상 작업 |
| `ciphertext` | `bytea` | NN | 애플리케이션 암호화 필수 | 보완 텍스트 암호문 |
| `encryption_key_id` | `varchar(128)` | NN | 비밀정보 원문 금지 | 암호화 키 식별자 |
| `expires_at` | 시간 | NN | `finished_at + 24시간` 이하 | 자동 삭제 기준 |
| `created_at` | 시간 | NN | 기본 현재 시각 | 임시 입력 생성 시각 |

- 평문 보완 텍스트는 DB·로그·API 응답에 남기지 않는다.
- Worker가 `QUEUED/RUNNING` 작업을 재시작 복구할 때만 복호화할 수 있다.
- `SUCCEEDED`·`FAILED` 작업은 종료 후 24시간 이내 암호문을 삭제한다. 관리자 재시도는 이전 입력을 재사용하지 않고 새 `supplementText`를 제출한다.
- 암호문 삭제 후에도 입력 해시와 작업·후보·시도·검수 이력은 계약된 보존 기간 동안 유지한다.

## 5. `ai_candidate_snapshot`

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | Snapshot ID |
| `job_id` | `uuid` | NN | FK → `ai_extraction_job.id` | 생성 작업 |
| `snapshot_version` | `integer` | NN | `(job_id, snapshot_version)` unique | 후보 버전 |
| `candidate_fields` | `jsonb` | NN | object·Schema 검증 | 맛집명·메뉴·주소·방문 후보 |
| `candidate_tags` | `jsonb` | NN | 배열·Tag Schema 검증 | 태그 후보 코드·유형·신뢰도·근거 |
| `field_confidences` | `jsonb` | NN | 필드별 범위 검증 | 신뢰도 |
| `evidence` | `jsonb` | NN | `TIMESTAMP`·`TEXT_RANGE`·`UNKNOWN` Schema | 필드별 근거 메타데이터 |
| `missing_fields` | `jsonb` | NN | 배열 | `UNKNOWN` 필드 |
| `review_status` | `varchar(24)` | NN | 상태 CHECK | `AUTO_CONFIRMED/AUTO_BLOCKED/AUTO_REJECTED/MANUAL_OVERRIDE` |
| `reviewed_by` | `uuid` | Yes | FK → `admin_account.id` | 검수 관리자 |
| `review_reason` | `varchar(1000)` | Yes | 폐기 시 필수 | 검수 사유 |
| `reviewed_at` | 시간 | Yes | 자동 등록 상태와 조합 | 자동 판정 또는 사후 보정 시각 |
| `created_at` | 시간 | NN | 기본 현재 시각 | Snapshot 생성 시각 |

`candidate_fields`는 자동 검증 전 후보 Schema다. 외부 검증 실패·모호한 장소·근거 부족 시 Snapshot을 유지하고 정식 Entity 행은 0건이어야 한다. `AUTO_CONFIRMED` 전환은 자동 등록 명령의 성공을 의미하므로 정식 등록 결과와 롤백 메타데이터를 별도 감사 또는 작업 결과로 연결한다.

`candidate_fields`는 필드 이름을 키로 갖는 object이며, 값은 그 필드에 남은 후보 수에 따라 두 형태를 가진다. `BR-AIEXTRACT-001`이 "하나의 장소로 판정할 수 없으면 확정하지 않고 복수 후보로 남긴다"를 요구하므로 후보가 여럿인 필드도 폐기하지 않고 보존한다.

| 후보 수 | `candidate_fields[필드]` | `field_confidences[필드]`·`evidence[필드]` |
|---|---|---|
| 1건 | 후보 값 문자열 | 각각 신뢰도와 근거를 기록한다 |
| 2건 이상 | `{ "value", "confidence", "evidence" }` 항목의 배열 | **키를 기록하지 않는다** |

후보가 여럿인 필드를 `field_confidences`·`evidence`에서 생략하는 것은 선택이 아니라 제약이다. `tr_ai_candidate_snapshot__json_contract` 트리거가 `field_confidences`의 모든 값을 0~1 숫자로, `evidence`의 모든 값을 단일 근거 object로 강제하므로 그 두 컬럼에는 배열을 담을 수 없다. 반면 `candidate_fields`에는 최상위 `jsonb_typeof = 'object'` CHECK만 걸려 있어 값 위치의 배열이 허용된다. 이 규칙 덕분에 복수 후보 보존에 스키마 변경이 필요하지 않다.

따라서 `candidate_fields`의 키가 있다고 해서 `field_confidences`·`evidence`에 같은 키가 있다고 가정할 수 없다. 후보를 읽는 쪽은 값이 문자열인지 배열인지 먼저 확인해야 한다.

후보가 여럿이라는 사실 자체는 자동 확정을 막지 않는다. `BR-AIEXTRACT-001`에 따라 시스템은 후보를 장소 단위 등록 단위로 나누고 단위마다 독립적으로 판정한다. Snapshot은 원본 후보를 그대로 보존하고, 등록 단위와 그 판정 결과는 `ai_registration_unit`이 소유한다. 한 등록 단위 안에서 같은 필드에 값이 둘 이상 남아 어느 값으로 등록할지 확정할 수 없는 경우에만 그 단위를 차단하며, 이때도 시스템이 임의로 하나를 고르지 않는다.

### 5.1 `ai_registration_unit`

한 작업이 여러 맛집을 등록할 수 있으므로 판정·등록 결과는 Snapshot이 아니라 등록 단위가 소유한다. 새 테이블이므로 새 Flyway 마이그레이션이 필요하고 Flyway 순서 소유자 합의 대상이다.

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 등록 단위 ID |
| `snapshot_id` | `uuid` | NN | FK → `ai_candidate_snapshot.id` | 근거 Snapshot |
| `unit_index` | `integer` | NN | `(snapshot_id, unit_index)` unique | Snapshot 안의 등록 단위 순번 |
| `restaurant_name` | `varchar(255)` | NN | 공백 금지 | 이 단위의 맛집명 후보 |
| `review_status` | `varchar(24)` | NN | 상태 CHECK | `AUTO_CONFIRMED/AUTO_BLOCKED/AUTO_REJECTED/MANUAL_OVERRIDE` |
| `block_reason` | `varchar(64)` | Yes | 차단 상태일 때 필수 | `PLACE_NOT_FOUND`, `PLACE_AMBIGUOUS`, `CATEGORY_UNRESOLVED`, 기존 검증 실패 코드 |
| `place_decision` | `jsonb` | Yes | 확정 시 필수 | 채택한 Kakao 장소 식별자·도로명주소와 `matchedBy` |
| `category_decision` | `jsonb` | Yes | 확정 시 필수 | 선정한 카테고리와 `resolvedBy`(`KAKAO_PLACE_CATEGORY`·`MENU_EXPRESSION`·`MANUAL_OVERRIDE`) |
| `registered_restaurant_id` | `uuid` | Yes | FK → `restaurant.id` | 등록한 맛집 |
| `registered_creator_id` | `uuid` | Yes | FK → `creator.id` | 등록하거나 재사용한 유튜버 |
| `registered_video_id` | `uuid` | Yes | FK → `video.id` | 등록하거나 재사용한 영상 |
| `registered_visit_id` | `uuid` | Yes | FK → `visit.id` | 같은 실행에서 만든 방문 관계 |
| `reused_resources` | `jsonb` | NN | 배열, 허용값 CHECK | 새로 만들지 않고 재사용한 자원. `restaurant`·`creator`·`video`·`visit` |
| `executed_by` | `varchar(16)` | NN | `WORKER`·`ADMIN` CHECK | 등록 실행 주체 |
| `decided_at` | 시간 | NN | 기본 현재 시각 | 판정 시각 |
| `rolled_back_at` | 시간 | Yes | `MANUAL_OVERRIDE`에서만 non-null | 관리자 롤백 시각 |

등록 결과 컬럼과 상태의 조합은 다음 규칙을 따른다. `MANUAL_OVERRIDE`는 사후 등록과 롤백 두 경우에 모두 쓰이므로 `rolled_back_at`으로 구분한다.

| `review_status` | `rolled_back_at` | 의미 | 네 등록 결과 컬럼 |
|---|---|---|---|
| `AUTO_CONFIRMED` | `NULL` | 자동 등록 완료 | 모두 존재 |
| `MANUAL_OVERRIDE` | `NULL` | 관리자 사후 보정 등록 완료 | 모두 존재 |
| `MANUAL_OVERRIDE` | non-null | 관리자 롤백 완료 | 모두 `NULL` |
| `AUTO_BLOCKED`·`AUTO_REJECTED` | `NULL` | 등록하지 않음 | 모두 `NULL` |

- CHECK 조건은 "등록 결과 컬럼이 모두 존재하거나 모두 `NULL`"이고, 값이 존재하는 경우는 `AUTO_CONFIRMED`이거나 `rolled_back_at`이 `NULL`인 `MANUAL_OVERRIDE`뿐이다. `place_decision`·`category_decision`도 같은 조건을 따른다.
- 롤백은 등록 결과 컬럼을 `NULL`로 되돌리고 `rolled_back_at`을 채운다. 되돌린 정식 Entity의 식별자는 감사 이력에 남는다.
- 맛집·유튜버·영상·방문 관계 4종 등록은 `BR-AIEXTRACT-011`에 따라 하나의 트랜잭션으로 저장한다. `executed_by`는 Worker 자동 실행과 관리자 실행을 구분하며 판정 기준은 두 경우가 같다.
- 유튜버·영상은 기존 행이 있으면 재사용한다. 재사용한 경우에도 참조 컬럼에 그 식별자를 기록하고 `reused_resources`에 자원 이름을 남긴다. 등록 단위 일괄 등록 API 응답은 감사 이력이 아니라 이 컬럼들에서 재구성한다.
- 등록 단위의 실패는 같은 Snapshot의 다른 등록 단위 행과 그 정식 등록 결과를 변경하지 않는다. 원자성 경계는 등록 단위 하나다.
- 관리자 사후 보정·롤백은 판정 이력을 덮어쓰지 않고 append-only 감사 이력을 추가한 뒤 `review_status`를 `MANUAL_OVERRIDE`로 전환한다.
- 작업 최상위 `ai_candidate_snapshot.review_status`는 등록 단위 판정의 요약이며, 단위별 권위 있는 값은 이 테이블이 가진다.

### 5.2 `food_category_mapping`

`BR-AIEXTRACT-010`의 카테고리 매핑 표를 코드 상수가 아닌 기준정보로 관리하기 위한 테이블이다. 기존 `food_category`의 10개 값은 그대로 두고 매핑 규칙만 분리한다. `food_category`에 흡수하지 않는 이유는 한 카테고리에 여러 표현이 대응하는 다대일 관계이고 표현마다 출처·일치 방식·우선순위가 다르기 때문이다.

새 테이블이므로 새 Flyway 마이그레이션과 seed가 필요하고 Flyway 순서 소유자 합의 대상이다. 기준정보 소유자는 restaurant 도메인이다.

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 매핑 행 ID |
| `source_type` | `varchar(24)` | NN | `KAKAO_PLACE_CATEGORY`·`MENU_EXPRESSION` CHECK | 대조 대상 근거 유형 |
| `pattern` | `varchar(128)` | NN | 공백 금지, 정규화 저장 | 대조할 표현 |
| `match_type` | `varchar(16)` | NN | `EXACT`·`PARTIAL` CHECK | 일치 방식 |
| `food_category_id` | `uuid` | NN | FK → `food_category.id` | 대응 카테고리 |
| `priority` | `smallint` | NN | 1 이상 | 복수 일치 시 우선순위. 작을수록 우선 |
| `active` | `boolean` | NN | 기본 `true` | 활성 여부 |
| `created_at`, `updated_at` | 시간 | NN | 기본 현재 시각 | 변경 이력 기준 시각 |

- `(source_type, pattern, match_type)`은 unique다. `pattern`은 공백 제거·소문자 통일로 정규화해 저장하고 대조 시에도 같은 정규화를 적용한다.
- 대조 순서는 `source_type`이 1순위(`KAKAO_PLACE_CATEGORY` 우선), 그 안에서 `match_type`이 2순위(`EXACT` 우선), 그 안에서 `priority` 오름차순이다. 같은 순위에서 서로 다른 카테고리로 일치하는 행이 둘 이상이면 임의로 고르지 않고 `CATEGORY_UNRESOLVED`로 차단한다.
- `active = false` 행은 대조에서 제외한다. 행을 삭제하지 않고 비활성화해 과거 판정 근거를 보존한다.
- 매핑 표 변경은 이미 등록된 결과를 소급 재계산하지 않는다. `updated_at`으로 변경 시점을 추적하고, 판정 시 사용한 매핑 행 식별자는 `ai_registration_unit.category_decision`에 남긴다.
- `기타` 카테고리는 이 표가 명시적으로 `기타`를 지정한 행에 일치했을 때만 사용한다. 일치하는 행이 없으면 `기타`로 대체하지 않고 차단한다.
- seed는 기존 `ResolveVerifiedRestaurantReferenceService`의 고정 키워드를 `MENU_EXPRESSION`·`EXACT` 행으로 이관하는 것에서 시작하고, Kakao 분류 표현은 `KAKAO_PLACE_CATEGORY` 행으로 추가한다. `TST-E3-AI-006`은 이 seed를 고정 데이터로 사용한다.

`candidate_tags`의 각 항목은 `candidateTagId`, `tagType`, `rawLabel`, `normalizedCode`, `label`, `confidence`, `evidence`를 가진다. `normalizedCode`가 기존 정의와 통합되지 않는 경우 자동 등록 규칙을 통과하면 새 `TagDefinition`을 만든다.

`evidence`는 다음 형태만 허용한다.

- `TIMESTAMP`: `startMs`, `endMs`를 저장한다.
- `TEXT_RANGE`: `startOffset`, `endOffset`, `sourceHash`를 저장한다. 원문 텍스트는 저장하지 않는다.
- `UNKNOWN`: 위치 필드를 저장하지 않는다.

## 6. `ai_candidate_tag_review`

태그 후보별 자동 판단과 관리자 사후 보정을 Snapshot과 분리해 append-only 이력으로 보존한다. 현재 판단은 해당 `candidate_tag_id`의 가장 최신 이력으로 계산한다.

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 검수 이력 ID |
| `snapshot_id` | `uuid` | NN | FK → `ai_candidate_snapshot.id` | 후보 Snapshot |
| `candidate_tag_id` | `varchar(128)` | NN | Snapshot 내 식별자 | 후보 태그 식별자 |
| `decision` | `varchar(24)` | NN | `AUTO_ACCEPT/AUTO_REJECT/AUTO_MERGE/MANUAL_OVERRIDE` | 자동 판단 또는 사후 보정 |
| `replacement_tag_definition_id` | `uuid` | Yes | `AUTO_MERGE`일 때 필수, `ACTIVE`만 허용 | 통합 대상 태그 |
| `reason` | `varchar(1000)` | Yes | 자동 차단·사후 보정 시 권장 | 판단 사유 |
| `decision_source` | `varchar(16)` | NN | `SYSTEM/ADMIN` | 판단 주체 유형 |
| `reviewed_by` | `uuid` | Yes | FK → `admin_account.id` | 사후 보정 관리자 |
| `reviewed_at` | 시간 | NN | 시각 규칙 | 판단 시각 |

- 이력 행은 수정·삭제하지 않으며, 재검수는 새 행을 추가한다.
- `AUTO_ACCEPT`는 후보 태그를 그대로 확정 대상으로 삼고, `AUTO_MERGE`는 `replacement_tag_definition_id`의 기존 `TagDefinition`을 확정 대상으로 삼는다.
- `UNKNOWN` 근거의 AI 후보는 자동 등록하지 않는다. `TIMESTAMP` 또는 `TEXT_RANGE` 근거를 가진 후보만 `AI_AUTO_CONFIRMED` `VisitTag`로 연결할 수 있다.
- 자동 정규화·중복 검사에서 확정되지 않은 태그는 `VisitTag`·공개 검색에 사용하지 않는다.

## 7. `tag_definition`

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 태그 정의 ID |
| `tag_code` | `varchar(64)` | NN | unique | API·검색·후보에서 사용하는 통제 코드 |
| `tag_type` | `varchar(24)` | NN | `MENU/TASTE/OCCASION/ATMOSPHERE` | 태그 유형 |
| `display_name` | `varchar(100)` | NN | 빈 값 금지 | 사용자·관리자 표시명 |
| `aliases` | `jsonb` | NN | 배열·중복 금지 | 자연어 해석·AI 후보 매칭용 허용 별칭 |
| `status` | `varchar(16)` | NN | `ACTIVE/DEPRECATED` | 검색·후보 허용 상태 |
| `source` | `varchar(16)` | NN | `SEED/AI_AUTO/MANUAL_OVERRIDE` | 태그 정의 생성 경로 |
| `created_from_snapshot_id` | `uuid` | Yes | FK → `ai_candidate_snapshot.id` | AI 자동 생성 근거 Snapshot |
| `created_at`, `updated_at` | 시간 | NN | 시각 규칙 | 생성·변경 |

AI·자연어 파서는 `ACTIVE` 정의만 사용한다. `DEPRECATED` 태그는 기존 확정 데이터의 이력을 보존하지만 신규 후보·검색 조건으로 사용하지 않는다.

초기 `tag_definition` seed는 다음 18개다. 18개는 초기값이며, AI가 기존에 없는 태그를 생성할 수 있다. 단, 태그 유형 자체를 새로 만들지는 않고 허용된 태그 유형·금지 표현·정규화·중복·근거 검사를 통과한 경우에만 자동 `ACTIVE`로 등록한다.

| 유형 | 초기 태그 코드 |
|---|---|
| `MENU` | `MENU_NAENGMYEON`, `MENU_GUKBAP`, `MENU_RAMEN`, `MENU_SUSHI`, `MENU_PIZZA`, `MENU_SAMGYEOPSAL` |
| `TASTE` | `TASTE_SPICY`, `TASTE_SWEET`, `TASTE_SAVORY`, `TASTE_LIGHT` |
| `OCCASION` | `OCCASION_SOLO`, `OCCASION_DATE`, `OCCASION_GROUP`, `OCCASION_LATE_NIGHT` |
| `ATMOSPHERE` | `ATMOSPHERE_CASUAL`, `ATMOSPHERE_QUIET`, `ATMOSPHERE_LIVELY`, `ATMOSPHERE_BAR` |

태그는 자막·영상에서 직접 확인 가능한 표현만 후보로 만들며, AI가 `TASTE_LIGHT`나 `ATMOSPHERE_QUIET`처럼 주관성이 높은 태그를 영상 근거 없이 추론하지 않는다. 별칭은 각 코드의 표시명과 사전에 승인된 동의어만 사용한다.

## 8. `visit_tag`

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 연결 ID |
| `visit_id` | `uuid` | NN | FK → `visit.id` | 확정 방문 관계 |
| `tag_definition_id` | `uuid` | NN | FK → `tag_definition.id` | 확정 태그 정의 |
| `source` | `varchar(24)` | NN | `AI_AUTO_CONFIRMED/ADMIN_OVERRIDE` | 태그 확정 경로 |
| `confidence` | `numeric(5,4)` | Yes | 0 이상 1 이하 | AI 후보 신뢰도, 관리자 직접 입력이면 null 가능 |
| `evidence` | `jsonb` | NN | AI 확정은 `TIMESTAMP`·`TEXT_RANGE`, 관리자 확정은 계약된 근거 Schema | 최소 근거 메타데이터 |
| `extractor_version` | `varchar(128)` | Yes | 버전 조합 | AI 모델·Prompt·Schema 또는 수동 입력 버전 |
| `created_from_snapshot_id` | `uuid` | Yes | FK → `ai_candidate_snapshot.id` | AI 자동 확정·수동 보정 연결의 원인 Snapshot |
| `created_at` | 시간 | NN | 기본 현재 시각 | 연결 시각 |

`(visit_id, tag_definition_id)`는 unique다. 공개 검색은 공개·유효 `Visit`, `ACTIVE` `TagDefinition`, 자동 검증 완료 `VisitTag`만 사용하며, Visit가 비공개·삭제·무효가 되면 연결은 이력으로 남아도 검색에서 제외한다.

## 9. `ai_extraction_attempt`

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 시도 ID |
| `job_id` | `uuid` | NN | FK → `ai_extraction_job.id` `ON DELETE CASCADE` | 작업 |
| `attempt_no` | `smallint` | NN | `(job_id, attempt_no)` unique | 시도 번호 |
| `provider_request_id` | `varchar(128)` | Yes | 외부 요청 식별자 | 비밀정보가 아닌 추적값만 저장 |
| `started_at`, `finished_at` | 시간 | NN | 시각 규칙 | 처리 시간 |
| `outcome` | `varchar(16)` | NN | `SUCCEEDED/FAILED/PARTIAL` | 시도 결과 |
| `error_category` | `varchar(64)` | Yes | 실패 시 필수 | timeout·429·5xx·schema 등 |
| `input_tokens`, `output_tokens` | `integer` | Yes | 0 이상 | 무료 quota 사용량과 처리량 집계용 선택 메타데이터 |
| `estimated_cost_minor` | `bigint` | Yes | 0 이상 | 통화·단위 계약 후 사용 |

시도 테이블에는 입력 원문, 자막, 영상 바이트, Prompt 전문과 AI 응답 전문을 저장하지 않는다. `provider_request_id`도 외부 비밀정보가 아닌 경우에만 보존한다.

## 10. `youtube_channel_watch`

| 컬럼 | SQL 타입 후보 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 감시 설정 ID |
| `creator_id` | `uuid` | NN | FK → `creator.id`, unique | 정식 Creator |
| `youtube_channel_id` | `varchar(128)` | NN | unique | 외부 채널 식별자 |
| `enabled` | `boolean` | NN | 기본 false | 관리자 명시 활성화 |
| `subscription_status` | `varchar(24)` | NN | `ACTIVE/INACTIVE/RENEWAL_FAILED/UNKNOWN` | Webhook 구독 상태 |
| `subscription_token_hash` | `bytea` | Yes | 원문 저장 금지 | 구독 확인 Token 해시 |
| `last_notification_at` | 시간 | Yes |  | 마지막 유효 알림 |
| `last_renewed_at` | 시간 | Yes |  | 마지막 구독 갱신 |
| `last_error_category` | `varchar(64)` | Yes |  | 마지막 오류 범주 |
| `created_at`, `updated_at` | 시간 | NN | 시각 규칙 | 생성·변경 |

Creator 등록만으로 `enabled=true`가 되지 않는다. 감시 중지·구독 만료·갱신 실패는 과거 Job·Snapshot·정식 Entity를 삭제하지 않고 신규 Webhook 접수만 차단한다.

## 11. 제약·인덱스 후보

| 이름 | 정의 후보 | 목적 |
|---|---|---|
| `ux_ai_job__idempotency` | 영상·입력·Provider·버전 조합 unique | Webhook·관리자·초기 적립 중복 수렴 |
| `ix_ai_job__claim` | 상태·우선순위·생성 시각 | Worker claim과 실시간 우선 처리 |
| `ix_ai_job__review` | 실행·자동 등록 상태·생성 시각 | 관리자 예외 보정 목록 |
| `ux_ai_snapshot__job_version` | `(job_id, snapshot_version)` unique | Snapshot 버전 보존 |
| `ux_ai_registration_unit__snapshot_index` | `(snapshot_id, unit_index)` unique | Snapshot 안 등록 단위 순번 고유성 |
| `ix_ai_registration_unit__status` | `(review_status, decided_at)` | 차단 단위 예외 처리 목록 조회 |
| `ix_ai_tag_review__candidate` | `(snapshot_id, candidate_tag_id, reviewed_at)` | 최신 태그 판단 조회와 이력 정렬 |
| `ux_ai_attempt__job_no` | `(job_id, attempt_no)` unique | 재시도 이력 고유성 |
| `ux_channel_watch__creator` | `creator_id` unique | 채널 감시 중복 방지 |
| `ux_channel_watch__youtube_channel` | `youtube_channel_id` unique | 외부 채널 하나의 감시 설정 |
| `ux_tag_definition__code` | `tag_code` unique | 통제 태그 코드 고유성 |
| `ux_visit_tag__visit_tag` | `(visit_id, tag_definition_id)` unique | 같은 Visit의 태그 중복 방지 |
| `ix_visit_tag__tag_lookup` | `tag_definition_id`, 공개 Visit 상태 조합 | 태그 기반 맛집 조회 |
| `ix_ai_temporary_input__expires_at` | `expires_at`, `job_id` | 만료 임시 입력 cleanup 선택 |

정확한 PostgreSQL partial index·FK 삭제 동작·lease claim SQL과 Gemini 모델 CHECK 제약은 [ADR-EXT-003](../../07-adr/integration/ext-003-ai-extraction-async-reliability.md), [테이블 정의](table-definitions.md), [제약조건](constraints.md), [인덱스 전략](index-strategy.md), [Flyway 계획](migration-plan.md)과 통합 `V4` DDL의 대응으로 확인한다.

## 12. 생명주기와 보존

1. Webhook 또는 관리자 요청이 `ai_extraction_job`을 생성한다.
2. Worker가 lease를 claim하고 Gemini URL 입력 또는 관리자 텍스트 입력을 처리한다.
3. 성공·부분 결과는 새로운 `ai_candidate_snapshot`으로 저장하고 작업을 종료한다.
4. 시스템은 Snapshot을 장소 단위 등록 단위로 나눠 `ai_registration_unit` 행을 만들고, 단위마다 Kakao 장소 동일성·대표 카테고리를 포함한 자동 검증을 수행해 `AUTO_CONFIRMED`, `AUTO_BLOCKED`, `AUTO_REJECTED`로 판정한다.
5. 자동 검증을 모두 통과한 등록 단위는 관리자 승인 없이 정식 Entity와 `VisitTag`를 생성·공개한다.
6. 실패·모호·중복 등록 단위는 정규화 오류와 시도 이력을 보존하고 제한된 수동 재시도·사후 보정을 허용한다. 같은 작업의 통과한 등록 단위는 되돌리지 않는다.
7. Snapshot·시도·작업·태그 판단·자동 등록·롤백 이력은 1년 보존 후 정리한다.

Webhook Raw Payload, 원본 영상, 자동 수집 전체 자막, Gemini 응답 전문은 저장하지 않는다. 관리자 보완 텍스트 암호문은 작업 종료 후 24시간 이내 삭제한다.

## 13. 데이터 완료 조건

- [ ] API 요청·응답 필드와 이 데이터 계약의 작업·Snapshot·시도·감시 상태가 일치한다.
- [ ] `WEBHOOK`·`ADMIN`·`BACKFILL` 중복 요청이 하나의 멱등성 키로 수렴한다.
- [ ] Worker 재기동·lease 만료·동시 claim 뒤 작업 유실·중복 후보·중복 정식 등록이 발생하지 않는다.
- [ ] 후보 Snapshot 버전과 모델·Prompt·Schema 버전이 과거 결과를 덮어쓰지 않고 보존된다.
- [ ] 허용 태그 정의·후보 태그·확정 `VisitTag`의 중복·공개·생명주기 규칙이 검증된다.
- [ ] 자동 태그 정규화·중복·근거 판단과 사후 보정 이력이 append-only로 보존되고, `UNKNOWN` AI 근거가 `AI_AUTO_CONFIRMED` `VisitTag`로 연결되지 않는다.
- [ ] 원본 영상·자동 수집 전체 자막·AI 응답 전문·보완 텍스트 평문이 저장·로그·API 응답에 노출되지 않는다.
- [ ] 보완 텍스트 암호문이 작업 종료 후 24시간 이내 삭제되고, 관리자 재시도가 이전 입력을 재사용하지 않는다.
- [x] `data-traceability.md`, 물리 테이블 정의, 제약·인덱스·Flyway 계획과 `V4` DDL의 구조적 대응이 문서화된다.
- [ ] Flyway 빈 DB·`V3→V4` 적용, 제약 위반, lease 동시성·정식 Entity 0건 테스트 결과가 보존된다.
- [ ] `ai_registration_unit`의 새 마이그레이션이 Flyway 순서 소유자 합의를 거쳐 추가되고, 단위별 상태·`registered_restaurant_id` 조합 제약이 검증된다.
- [ ] 다장소 영상에서 일부 등록 단위가 차단돼도 통과한 단위의 정식 Entity가 유지되는 원자성 경계가 검증된다.
