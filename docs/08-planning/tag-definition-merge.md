---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/admin-data-management.md
  - ../05-specs/api/admin/tag-definition-api.md
  - ../05-specs/data/table-definitions.md
  - ../05-specs/data/lifecycle-rules.md
  - admin-tag-definition-lifecycle.md
  - dynamic-natural-language-tag-dictionary.md
---

# 태그 정의 병합과 VisitTag 안전 이전 — 이슈 #366

## 1. 확정 결정

관리자는 같은 유형의 서로 다른 `ACTIVE` 정의만 병합한다. 원본과 대상의 현재 버전과 미리보기 fingerprint를 실행 요청에 함께 보내며, 서버는 같은 트랜잭션 안에서 다시 계산한 결과와 모두 일치할 때만 실행한다. 원본은 `DEPRECATED`로 바뀌고 version이 1 증가하며 기존 `tag_definition_audit`에 `DEPRECATE` 한 건을 남긴다. 병합된 원본은 재활성화할 수 없다.

병합 관계는 V13 `tag_definition_merge`에 원본별 한 건만 append-only로 남긴다. 대상은 이미 다른 원본을 받아 병합 경로의 중간 노드가 될 수 있다. 자연어 사전은 재귀적으로 최종 `ACTIVE` 대상을 해석한다. 원본의 `tag_definition_term`은 원본 소유로 유지하고 대상에 복사하지 않으므로 병합 별칭은 대상의 직접 별칭 20개 상한을 소비하지 않는다.

## 2. API 계약

| 경로 | 계약 |
|---|---|
| `GET /api/admin/tag-definitions/{sourceCode}/merge-preview?targetCode=...` | 현재 실행 가능한 원본·대상과 영향 건수를 계산하고 200을 반환한다. 실행 불가능하면 성공 응답의 blocker 필드로 표현하지 않고 오류로 반환한다. |
| `POST /api/admin/tag-definitions/{sourceCode}/merge` | `targetCode`, `expectedSourceVersion`, `expectedTargetVersion`, `previewFingerprint`, `reason`을 받아 미리보기와 동일한 병합을 원자적으로 실행한다. |

미리보기는 원본·대상의 현재 정의, `affectedVisitCount`, `movedVisitTagCount`, `deduplicatedVisitTagCount`, `previewFingerprint`를 반환한다. fingerprint는 원본·대상 ID와 version, 정렬된 영향 VisitTag ID·중복 여부를 canonical 직렬화한 뒤 SHA-256 소문자 64자리 hex로 계산한다. 실행 응답은 병합 ID·두 코드·병합 뒤 원본 version·세 건수·시각을 반환한다.

오류는 다음과 같이 고정한다.

| 상태 | 코드 | 조건 |
|---:|---|---|
| 400 | `INVALID_FIELD_VALUE` | 잘못된 코드·사유·fingerprint 형식, 자기 병합, 유형 불일치 |
| 404 | `RESOURCE_NOT_FOUND` | 원본 또는 대상 코드가 없음 |
| 409 | `TAG_DEFINITION_VERSION_CONFLICT` | 원본 또는 대상 expectedVersion 불일치 |
| 409 | `TAG_DEFINITION_MERGE_CONFLICT` | 원본이 이미 병합됨, 상태 변경, 비활성 대상, stale fingerprint, 순환 경로 |

## 3. VisitTag 이전과 provenance

영향 원본 `VisitTag`마다 변경 전에 원본 snapshot을 `visit_tag_merge_provenance`에 기록한다. 같은 Visit에 대상 연결이 없으면 원본 행의 `tag_definition_id`만 최종 대상으로 변경해 `id`, `source`, `confidence`, `evidence`, `extractorVersion`, `createdFromSnapshotId`, `createdAt`을 그대로 보존하고 결과를 `MOVED`로 기록한다.

같은 Visit에 대상 연결이 이미 있으면 원본과 대상 양쪽 snapshot을 먼저 `SOURCE`·`TARGET`으로 기록한다. 기존 대상 행을 유지하고 원본 행을 삭제하며 두 provenance 행을 `DEDUPLICATED`로 기록한다. snapshot은 JSONB object 전체 값이며 ledger는 수정·삭제할 수 없다. 따라서 대표 행 하나로 합칠 때 사라지는 AI 근거와 관리자 보정 출처도 감사에서 재현할 수 있다.

## 4. 잠금, 원자성, 검색

서버는 원본·대상 정의를 UUID 오름차순으로 잠근 뒤 두 정의의 VisitTag를 Visit UUID·정의 UUID·VisitTag UUID 순서로 잠근다. 병합 자체는 Visit 행을 잠그지 않는다. 관리자 방문 태그 교체도 관찰한 기존 정의와 요청 정의를 UUID 순서의 공유 잠금으로 먼저 확보한 뒤 Visit 행을 잠그고 version과 현재 연결을 다시 검증한다. 따라서 병합과 교체가 경합해도 정의→VisitTag 또는 정의→Visit의 공통 순서를 지키며, 먼저 끝난 변경을 본 오래된 요청은 409로 끝난다. AI·관리자 신규 연결은 정의의 공유 잠금을 거쳐야 하므로 정의 잠금 뒤에는 해당 정의의 새 연결이 끼어들 수 없다. 두 version·상태·유형·기존 병합·순환 여부와 fingerprint를 잠금 뒤 다시 검증한다.

VisitTag provenance, FK 이동·중복 삭제, 원본 상태/version, `DEPRECATE` 감사와 병합 감사는 하나의 트랜잭션이다. 한 단계라도 실패하면 모두 롤백하며 병합 직후부터 동적 사전은 원본 용어를 재귀 경로의 최종 활성 코드로 해석한다. 기존 같은 Visit 태그 AND 의미는 최종 코드 기준으로 유지한다. 30초 사전 cache는 별도 즉시 무효화 없이 기존 TTL 안에 반영한다.

모든 신규 VisitTag와 정의 FK 변경은 V13 DB trigger가 실행 시점의 `ACTIVE` 정의만 허용한다. AI 관리자 확정 경로도 활성 정의를 `FOR SHARE`로 잠근 뒤 연결해 병합과 경합할 때 비활성 원본에 새 연결이 생기지 않게 한다. 병합된 원본은 용어 호환을 고정하기 위해 상태뿐 아니라 표시명·별칭 수정도 거절한다.

## 5. 복구

자동 역병합은 제공하지 않는다. 운영자는 `tag_definition_merge`, `visit_tag_merge_provenance`, `tag_definition_audit`의 snapshot과 건수를 조회해 원본 관계와 삭제된 중복 근거를 확인한다. 복원이 필요하면 감사 자료를 근거로 새 관리자 보정·정의 작업을 수행하고 별도 검증된 전진 변경으로 처리한다. append-only 행을 UPDATE/DELETE하거나 원본 정의를 재활성화해 되돌리지 않는다.

## 6. 필수 검증

- 미리보기와 실행 건수 일치, stale version·fingerprint 거절
- 같은 유형·서로 다른 ACTIVE 정의, 원본 단일 병합, 순환 거절
- 대상이 inbound merge를 가진 연쇄 경로와 최종 ACTIVE 코드 해석
- 이동 시 VisitTag ID·근거 보존, 중복 시 대상 유지·양쪽 snapshot 선기록
- 정의·VisitTag·두 감사의 전체 롤백과 결정적 동시 잠금
- 병합 원본 재활성화 거절, append-only UPDATE/DELETE 거절과 회원 탈퇴 actor `SET NULL`
- V12→V13 전진 적용, 기존 정의·용어·VisitTag 보존, PostgreSQL/Testcontainers 검증
- 자연어 검색의 병합 전 용어 호환, 같은 Visit AND 의미, 30초 cache 경계
