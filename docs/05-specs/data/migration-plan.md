---
status: accepted
related_documents:
  - physical-data-model.md
  - table-definitions.md
  - constraint-mapping.md
  - index-strategy.md
  - seed-data-plan.md
  - ../../07-adr/data/data-004-flyway.md
  - ../../07-adr/data/data-009-pre-release-migration-consolidation.md
  - second-expansion-data-contract.md
---

# 맛잇온 Flyway 마이그레이션 계획

## 1. 파일 규칙

- 위치: `src/main/resources/db/migration`
- 형식: `V{정수}__{lower_snake_case_description}.sql`
- repeatable migration은 MVP에서 사용하지 않는다.
- 한 번 공유 환경에 적용된 파일은 수정·이름 변경하지 않는다.
- 모든 테이블·제약·인덱스 이름을 명시하고 `IF NOT EXISTS`로 실패를 숨기지 않는다.
- 마이그레이션 계정은 DDL 권한, 런타임 계정은 필요한 DML 권한만 가진다.

## 2. 초기 순서

초기 스키마는 `V1__create_initial_schema.sql` 하나로 적용한다. DDL은 [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md)과 [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md)의 확정 결정을 따른다.

| 순서 | 내용 | 선행 |
|---:|---|---|
| 1 | `region`, `food_category`, `admin_account`; PK·UK·CHECK | 없음 |
| 2 | `restaurant`, `creator`, `video`, `visit`; 상태·FK·복합 UK·CHECK | 1 |
| 3 | `confirmation_token`; Token 제약·FK | 1 |
| 4 | `index-strategy.md`의 초기 일반·partial index | 2, 3 |
| 5 | 서울 자치구 25개, 음식 카테고리 10개 | 1 |

핵심 도메인 테이블은 부모 순서 `restaurant/creator` → `video` → `visit`로 작성한다. 순환 FK가 없으므로 테이블 생성 후 임시 무제약 상태를 두지 않는다. 위 다섯 구간은 한 파일 안에서 이 순서를 유지하고, 파일이 하나이므로 전체가 한 transaction으로 적용된다.

### 2.1. 릴리스 전 마이그레이션 통합

[ADR-DATA-004](../../07-adr/data/data-004-flyway.md) 10절의 `적용된 마이그레이션`은 개발 Docker와 공유 데이터베이스를 포함한 **모든 환경**에 적용된 것을 뜻한다([ADR-DATA-009](../../07-adr/data/data-009-pre-release-migration-consolidation.md)).

| 구분 | 규칙 |
|---|---|
| 운영에 적용된 마이그레이션 | 수정·통합·삭제하지 않는다. 변경은 새 버전 파일로만 추가한다 |
| 운영에 적용되지 않은 마이그레이션 | ADR-DATA-009 10절 강제 규칙을 모두 증명하면 운영 배포 직전 1회 통합할 수 있다 |

통합은 10절 강제 규칙의 예외이며 절차 준수가 아니라 증명으로 성립한다. 통합 PR은 다음을 모두 증명해야 병합할 수 있다.

1. 통합 대상 버전이 운영 `flyway_schema_history`에 없음을 조회 결과로 증명한다. 운영 데이터베이스가 없으면 그 사실을 근거로 대신한다.
2. 통합 전후 SQL 본문이 주석과 공백을 제외하고 동일함을 대조 결과로 증명한다.
3. 빈 데이터베이스 적용 결과가 통합 전과 같음을 자동화 테스트로 증명한다.
4. 개발·공유 데이터베이스 재생성 절차를 PR 본문 최상단에 명시하고 병합 담당자가 병합 직후 공지한다.
5. 삭제된 파일명을 참조하는 문서·주석이 남지 않음을 확인한다. 파일명 전체와 버전 번호 단독 표기를 모두 검색한다.

통합 대상을 이미 적용한 데이터베이스는 checksum 불일치와 파일 부재로 Flyway `validate`가 실패해 애플리케이션이 기동하지 않는다. 잘못된 스키마로 조용히 동작하지 않는다는 점이 이 예외를 허용하는 근거다. 다음으로 재생성한다.

```bash
docker compose down -v
```

Testcontainers 기반 자동화 테스트는 매 실행 시 빈 데이터베이스를 생성하므로 영향을 받지 않는다.

### 2.2. 초기 스키마 통합 이력 (2026-07-29)

초기 스키마는 원래 `V1`~`V5` 다섯 파일이었고 MVP 구현(T-03)과 로컬 검증(T-14)에서 그 형태로 적용됐다. 운영 데이터베이스에 적용된 적이 없으므로 [ADR-DATA-009](../../07-adr/data/data-009-pre-release-migration-consolidation.md)에 따라 `V1__create_initial_schema.sql` 하나로 통합했다. 적용 결과 스키마와 기준 데이터는 통합 전과 동일하다.

### 2.3. 1차 확장 마이그레이션 통합 이력 (2026-07-31)

1차 확장 스키마는 원래 `V2`~`V6` 다섯 파일(회원 계정·보안 기반, 회원 인증 강화, 개인 맛집 관계, 맛집 좌표, Creator 상세 표시 열)이었다. 운영 데이터베이스(`main`)에는 이 시점까지 `V1`만 적용돼 있어 V2~V6는 운영 미적용 상태였으므로 [ADR-DATA-009](../../07-adr/data/data-009-pre-release-migration-consolidation.md) 6절 예외에 따라 `V2__add_expansion_1_schema.sql` 하나로 통합했다. 통합 전후 SQL 본문은 주석·공백을 제외하고 동일하며, 통합 대상을 적용한 개발·공유 데이터베이스는 `docker compose down -v`로 재생성해야 한다. 같은 버전 구간(V2~V6)은 다시 통합하지 않는다.

### 2.4. 3차 확장 AI 마이그레이션 통합 이력 (2026-08-14)

3차 확장 AI 스키마는 통합 전 `V4` 기본 스키마, 구 `V5` 재사용 조회 인덱스, 구 `V6` 관리자 검수 감사·재시도 사유, 구 `V7` 태그 롤백 provenance로 구성됐다. 운영 배포 전 누적 변경의 SQL 순서와 의미를 보존해 [`V4__create_third_expansion_ai_schema.sql`](../../../src/main/resources/db/migration/V4__create_third_expansion_ai_schema.sql) 하나의 1~8절로 통합했다. 통합본의 6~8절 주석에는 통합 전 구간을 표시하고, 통합 전후 SQL은 주석·공백을 제외한 정규화 본문이 동일하다.

운영 RDS가 존재하는 현재 환경에서는 통합 대상 버전이 운영 `flyway_schema_history`에 없음을 읽기 전용 조회 결과로 확인해야 한다. PR #192에서는 2026-08-14 11:18 KST 서울 리전 `ap-northeast-2`의 RDS `masiton-db`를 SSM `i-0b451f18bca827cc9` 경유로 읽기 전용 조회했고, `V1`·`V2`·`V3`만 존재하며 `V4`~`V7`은 없음을 확인했다. 실제 운영 배포와 마이그레이션 적용은 수행하지 않았다. 통합 대상을 이미 적용한 개발·공유 데이터베이스는 `docker compose down -v`로 재생성해야 하며, 같은 AI 버전 구간은 다시 통합하지 않는다.

## 3. 관리자 계정 부트스트랩

관리자 계정은 공용 seed에 넣지 않는다. 환경별 비밀번호 해시를 Git에 커밋하지 않고 다음 절차를 사용한다.

1. 배포 환경의 비밀 관리 경로에서 초기 login ID와 일회용 비밀번호를 제공한다.
2. 별도 부트스트랩 명령이 PasswordEncoder로 해시해 `admin_account`를 한 번 생성한다.
3. 동일 `login_id`가 있으면 덮어쓰지 않고 실패한다.
4. 평문·해시·Token을 로그에 출력하지 않는다.

운영 계정을 Flyway placeholder나 SQL 파일에 넣지 않는다.

## 4. 배포 절차

1. 운영 RDS 스냅샷 상태와 복구 가능 여부를 확인한다.
2. 새 애플리케이션이 기존 스키마에서도 기동 가능한 expand 단계인지 검토한다.
3. 빈 PostgreSQL 17.10에 초기 스키마 baseline을 적용한다.
4. 이전 릴리스 스키마에 신규 버전만 적용하는 업그레이드 테스트를 수행한다.
5. Flyway `validate`와 checksum을 확인한다.
6. 마이그레이션을 애플리케이션보다 먼저 적용한다.
7. 실패하면 애플리케이션 배포를 중단하고 원인에 따라 전진 수정 migration 또는 스냅샷 복구를 선택한다.
8. 성공 후 JPA `validate`, seed 검증, 핵심 조회·등록 smoke test를 수행한다.

## 5. 변경 유형별 전략

| 변경 | 전략 |
|---|---|
| nullable 컬럼 추가 | 먼저 schema expand, 다음 배포에서 코드 사용 |
| 필수 컬럼 추가 | nullable 추가 → backfill → 애플리케이션 전환 → `NOT NULL` 강화 |
| 컬럼 이름 변경 | 새 컬럼 추가·이중 호환 → backfill → 코드 전환 → 후속 버전에서 구 컬럼 제거 |
| 상태값 추가 | CHECK를 새 허용값으로 교체한 뒤 코드 배포 |
| FK·UK 추가 | 중복·고아 탐지 쿼리 → 정리 → 제약 추가 |
| 대형 인덱스 | 운영 데이터 규모·락 시간을 측정하고 필요 시 non-transactional `CREATE INDEX CONCURRENTLY`를 별도 migration으로 수행 |
| 컬럼·테이블 제거 | 최소 한 릴리스 미사용 확인 후 별도 destructive migration |

초기 스키마 baseline은 빈 DB 대상이므로 일반 transaction 안에서 수행한다. `CONCURRENTLY`가 필요한 후속 Flyway 파일은 해당 파일만 transaction 비활성화하고 한 파일에 다른 변경을 섞지 않는다.

## 6. 롤백과 복구

Flyway undo 파일과 하향 migration은 기본 경로로 사용하지 않는다. 배포 전에는 전진 수정 가능성을 검토하고, 적용 후 오류는 다음 순서로 대응한다.

1. 데이터 손실이 없고 호환 가능하면 새 버전의 전진 수정 SQL을 작성한다.
2. 애플리케이션만 문제면 이전 애플리케이션이 확장된 스키마와 호환되는지 확인 후 되돌린다.
3. 데이터 훼손 또는 호환 불가능 DDL이면 배포를 중지하고 RDS 스냅샷 복구를 판단한다.

운영에 적용된 migration 파일 수정과 `flyway repair`로 잘못된 checksum을 덮는 행위는 금지한다. `repair`는 실제 파일 무결성과 복구 계획을 확인한 예외 운영 절차에서만 승인한다.

## 7. CI 검증

- PostgreSQL 17.10 Testcontainers 빈 DB 전체 migration
- 인덱스 생성까지 적용된 상태에서 기준 데이터가 적재되는 순서 검증
- 마이그레이션 2회 실행 시 checksum/중복 적용 차단 확인
- 운영 스키마 스냅샷 위에 신규 마이그레이션만 추가 적용하는 업그레이드 검증 ([ADR-DATA-004](../../07-adr/data/data-004-flyway.md) 13절). 2.1절 통합을 수행한 릴리스에서도 유지한다
- PK·UK·FK·CHECK 이름과 존재 여부 검사
- JPA `ddl-auto=validate`
- 중복 Restaurant/Creator/Video/Visit, 채널 불일치, 삭제 상태 쌍, Token 상태 쌍 위반 테스트
- 기준 데이터 수·code·name·순서·`OTHER` 단일성 검증
- 공개 목록·Creator 필터·상세 조회 실행계획 smoke test

## 8. V2 1차 확장 스키마

`V2__add_expansion_1_schema.sql`은 회원 계정·보안 기반(`member_account`, `member_action_token`, `member_session_revocation`), 회원 인증 강화(메일 아웃박스·탈퇴 작업·세션 복구 작업), 개인 맛집 관계(`favorite`, `recent_restaurant_view`), 맛집 좌표, Creator 상세 표시 열을 모두 포함한다(2.3절 통합 이력). V1의 관리자·공개 조회 데이터는 수정하지 않으며, 빈 V1 데이터베이스와 기존 V1 스키마 모두에 전진 적용된다.

## 9. 1차 확장 마이그레이션 구성 (통합 이전 구간별 기록)

아래는 1차 확장 구현 시점에 구간별로 작성했던 내용이며, 2.3절 통합 이후 다섯 구간 모두 `V2__add_expansion_1_schema.sql` 하나 안의 순서로 존재하고 개별 파일은 없다. 구간과 요구사항 대응은 추적 목적으로 유지한다.

| 순서(통합 전) | 구간 | 변경 | 호환성·검증 |
|---:|---|---|---|
| V2 | 회원 계정·보안 기반 | `member_account`, `member_action_token`, `member_session_revocation`과 회원 보안 인덱스 | V1 공개·관리자 데이터 불변, V1→V2 적용 |
| V3 | 회원 인증 강화 | 키 식별자와 AES-GCM 암호문을 가진 Action Token 메일 아웃박스, 탈퇴 재시도 작업, Redis↔PostgreSQL 세션 폐기 복구 작업과 dispatch 인덱스 | V2 회원 데이터는 유지, 원문 Token·이메일·클라이언트 주소를 새 테이블에 추가하지 않음 |
| V4 | 개인 맛집 관계 | `favorite`, `recent_restaurant_view`, 복합 PK·FK·회원 목록 인덱스·최근 기록 만료 cleanup 인덱스 | 빈 개인화 관계로 적용, V1→V4 적용과 중복 찜/upsert·30일 cleanup 실행계획 검증 |
| V5 | 맛집 좌표 | nullable `restaurant.latitude`, `restaurant.longitude`, null 쌍·범위 CHECK, 지도 partial B-tree | 기존 Restaurant 좌표는 모두 `NULL`. 과거 bounds 계약용 인덱스는 적용 이력으로 유지하고 현재 필터 기반 마커 조회 실행계획을 검증 |
| V6 | Creator 상세 표시 열 | `creator.profile_image_url`, `description`, `handle` | 기존 Creator 선택 표시값은 `NULL`, V1→V6 공개 상세 null 표현 검증 |

통합 후에는 위 다섯 구간을 가리키는 옛 파일명을 문서·코드 어디에도 남기지 않는다. 구간 이름만으로 참조하며, 실제 내용은 `V2__add_expansion_1_schema.sql` 안의 순서로 확인한다.

V3 구간 아웃박스는 Action Token만 FK로 참조한다. 수신자는 `member_action_token.member_id → member_account` 조인으로만 결정하므로 다른 회원의 Token을 전달할 수 없다. 탈퇴 작업은 Action Token을 먼저 지워 CASCADE로 아웃박스를 제거한다. 탈퇴·세션 복구 작업은 회원 FK를 두지 않아 정리 실행 중에도 재시도 상태를 유지하고, 성공 시 명시적으로 제거한다. V4 구간의 회원 FK는 `ON DELETE CASCADE`, 맛집 FK는 `ON DELETE RESTRICT`로 생성한다. 따라서 회원 물리 파기는 관계를 함께 제거하고, 맛집 물리 삭제는 관계를 먼저 정리하는 명시적 Command가 선행되어야 한다. V5와 V6 구간은 기존 행을 백필하거나 외부 API를 호출하지 않는다.

## 10. V3 2차 확장 스키마

기존 `V1__create_initial_schema.sql`과 `V2__add_expansion_1_schema.sql`은 수정하지 않는다. 2차 확장은 현행 `V3__add_expansion_2_schema.sql` 하나로 구현·적용하며 상세 순서는 [2차 확장 데이터 계약](second-expansion-data-contract.md#10-flyway-계획)을 따른다.

| 순서 | 변경 | 선행 |
|---:|---|---|
| 1 | 개인 컬렉션·구성 | `member_account`, `restaurant` |
| 2 | 큐레이션·구성 | `admin_account`, `restaurant` |
| 3 | 제보·신고·검토 이력 | 회원·관리자와 핵심 대상 |
| 4 | 알림·멱등 성공 기록 | 제보·신고 |
| 5 | 인기 집계와 신규 조회·고유·cleanup 인덱스 | 1~4, 기존 `favorite` |

기존 데이터 backfill, 기준 데이터, 외부 호출과 destructive DDL은 없다. V2→V3 업그레이드와 빈 DB 전체 적용을 모두 CI에서 검증한다.

## 11. V4 3차 확장 AI 영상 추출 스키마

`V4__create_third_expansion_ai_schema.sql`은 [3차 확장 AI 영상 추출 데이터 계약](third-expansion-ai-video-data-contract.md)과 [ADR-EXT-003](../../07-adr/integration/ext-003-ai-extraction-async-reliability.md)의 물리 구현이다. 기존 `V1`~`V3`를 수정하지 않고, 빈 DB 전체 적용과 `V3→V4` 전진 적용을 모두 검증한다.

| 순서 | 변경 | 검증 경계 |
|---:|---|---|
| 1 | `ai_extraction_job`, `ai_extraction_temporary_input`과 임시 입력 만료·Worker lease 제약 | Webhook·관리자 멱등성, 관리자 보완 텍스트 암호화·24시간 이내 삭제, QUEUED/RUNNING/terminal 상태 조합 |
| 2 | `ai_candidate_snapshot`, `ai_candidate_tag_review`와 JSON Schema 검증 함수·Trigger | Snapshot 버전·근거 유형·신뢰도·태그 후보·append-only 검수 이력 |
| 3 | `tag_definition`, `visit_tag`와 18개 통제 태그 기준 데이터 | ACTIVE 태그만 신규 검색·후보에 사용, Visit별 태그 중복·AI 근거 경계 |
| 4 | `ai_extraction_attempt`, `youtube_channel_watch` | 재시도·오류·quota 메타데이터, 채널별 감시 고유성·갱신 실패 상태 |
| 5 | Worker claim·lease recovery·관리자 검수·태그 조회 인덱스 | `FOR UPDATE SKIP LOCKED` 경로, 만료 lease 복구, 공개 태그 조회 |

이 마이그레이션은 원본 영상·전체 자막·Provider 응답 전문을 저장하지 않으며 외부 API를 호출하지 않는다. 실제 3차 완료 판정은 [3차 확장 테스트 추적표](../../08-planning/third-expansion-test-matrix.md), 평가 결과, Worker·quota·브라우저 증거까지 연결해 수행한다.

### 11.1 AI 누적 변경 통합 구성

AI 영상 추출 스키마·재사용 조회 인덱스·수동 검수 감사·재시도 사유·태그 롤백 provenance는 `V4__create_third_expansion_ai_schema.sql`에 적용 순서대로 포함한다. 외부 YouTube 검증 전 멱등 조회 인덱스는 `youtube_video_id`와 입력 hash 또는 입력 모드·Provider/Model/Prompt/Schema 버전을 선두 조건으로 사용하며, 최신 작업 조회와 `expires_at` 만료 행 선택을 지원한다. 기존 작업·태그 행의 provenance는 nullable로 유지하고 새 자동 확정·수동 보정 연결부터 Snapshot ID를 기록한다.

통합된 V4는 관리자 재시도 사유와 Snapshot 기반 `visit_tag` provenance를 추가한다. 기존 작업·태그 행의 provenance는 nullable로 유지하며, 새 자동 확정·수동 보정 연결부터 Snapshot ID를 기록해 롤백 시 해당 작업의 연결만 삭제한다.

### 11.2 Gemini 모델 전환 제약

통합 V4의 `model_version` CHECK 제약은 기존 `gemini-3-flash-preview` 작업 이력을 보존하면서 신규 작업부터 애플리케이션 계약의 `gemini-3.5-flash-lite`를 허용한다. 통합 V4의 스키마·인덱스·감사·provenance 계약은 함께 적용되며, 별도 모델 전환 migration은 두지 않는다.

## 12. 향후 변경 번호

초기 스키마 baseline 다음 변경은 `V2`로 적용됐고, 1차 확장 변경은 2.3절 통합 이후 다시 `V2` 하나로 적용됐다. 2차 확장은 `V3`, 3차 확장 AI 영상 추출과 누적 AI 변경·Gemini 모델 전환 제약은 통합 `V4`를 사용한다.

`V1`과 `V2`는 각각 적용된 시점부터 수정하지 않는다. 현행 `V3__add_expansion_2_schema.sql` 또는 `V4__create_third_expansion_ai_schema.sql`을 향후 통합하려면 2.1절과 ADR-DATA-009의 강제 규칙을 모두 증명해야 하며, 이미 운영에 적용된 파일은 통합·수정하지 않는다.
