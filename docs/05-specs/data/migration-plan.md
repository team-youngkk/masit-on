---
status: accepted
related_documents:
  - physical-data-model.md
  - table-definitions.md
  - constraint-mapping.md
  - index-strategy.md
  - seed-data-plan.md
  - ../../07-adr/data/data-004-flyway.md
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

### 2.1. V1~V5 통합 이력 (2026-07-29)

초기 스키마는 원래 `V1`~`V5` 다섯 파일이었고 MVP 구현(T-03)과 로컬 검증(T-14)에서 그 형태로 적용됐다. 운영 환경 최초 배포 전에 baseline을 단순화하기 위해 다섯 파일을 `V1__create_initial_schema.sql` 하나로 통합했다. 적용 결과 스키마와 기준 데이터는 통합 전과 동일하다.

통합 시점에 운영 데이터베이스는 존재하지 않았으므로 운영 데이터에 영향이 없다. 다만 통합 전 스키마가 적용된 데이터베이스에서는 `flyway_schema_history`에 기록된 `V1` checksum이 새 파일과 다르고 `V2`~`V5` 기록에 대응하는 파일이 없어 Flyway `validate`가 실패한다. 해당 데이터베이스는 볼륨을 삭제한 뒤 다시 적용해야 한다.

```bash
docker compose down -v
```

Testcontainers 기반 자동화 테스트는 매 실행 시 빈 데이터베이스를 생성하므로 영향을 받지 않는다.

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

적용된 migration 파일 수정과 `flyway repair`로 잘못된 checksum을 덮는 행위는 금지한다. `repair`는 실제 파일 무결성과 복구 계획을 확인한 예외 운영 절차에서만 승인한다.

## 7. CI 검증

- PostgreSQL 17.10 Testcontainers 빈 DB 전체 migration
- 인덱스 생성까지 적용된 상태에서 기준 데이터가 적재되는 순서 검증
- 마이그레이션 2회 실행 시 checksum/중복 적용 차단 확인
- PK·UK·FK·CHECK 이름과 존재 여부 검사
- JPA `ddl-auto=validate`
- 중복 Restaurant/Creator/Video/Visit, 채널 불일치, 삭제 상태 쌍, Token 상태 쌍 위반 테스트
- 기준 데이터 수·code·name·순서·`OTHER` 단일성 검증
- 공개 목록·Creator 필터·상세 조회 실행계획 smoke test

## 8. 향후 첫 변경 번호

초기 스키마 baseline이 적용된 뒤 모든 변경은 `V2`부터 시작한다. 개발 중이라도 `V1`이 다른 팀원 또는 공유 DB에 적용됐다면 내용을 고치지 않고 `V2` 이상의 보정 migration을 추가한다.

2.1절의 통합은 운영 데이터베이스가 없던 시점의 일회성 baseline 정리이며 선례로 삼지 않는다. 운영 데이터베이스가 존재하는 시점부터 적용된 migration 파일 수정은 금지한다.
