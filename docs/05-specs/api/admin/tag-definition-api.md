---
id: API-ADMIN-TAG-DEFINITION-001
related_documents:
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../04-product/prd/admin/admin-data-management.md
  - ../../../04-product/prd/detail/restaurant-detail.md
  - ../../../08-planning/admin-tag-definition-creation.md
  - ../../data/third-expansion-ai-video-data-contract.md
  - restaurant-visit-tags-api.md
  - ../common/authentication-contract.md
  - ../common/error-contract.md
---

# 관리자 태그 정의 조회·생성·생명주기 API

## 1. 범위와 권한

`FR-ADMIN-005`의 통제 태그 생성·관리 계약이다. 모든 API는 Bearer JWT와 요청 시점의 `ACTIVE/ADMIN` 권한이 필요하다. 인증 실패는 401, `MEMBER`는 403이며 모든 오류는 공통 오류 응답과 서버 생성 `traceId`를 포함한다. 성공 응답을 포함한 모든 응답은 `Cache-Control: no-store`다.

병합·물리 삭제는 제공하지 않는다. 공개 맛집 상세 API에는 태그 코드·별칭·관리 기능을 추가하지 않는다.

## 2. 활성 태그 정의 목록

### API-ADMIN-TAG-DEFINITION-001 활성 목록 조회

`GET /api/admin/tag-definitions`

`ACTIVE` 정의만 `type`, 정규화 표시명, `code` 순으로 안정적으로 제공한다. 페이지네이션이 필요 없는 관리자 선택 목록이며 결과가 없으면 200과 빈 `items`다.

```json
{
  "items": [
    {
      "code": "MENU_GUKBAP",
      "type": "MENU",
      "displayName": "국밥",
      "aliases": ["돼지국밥"],
      "status": "ACTIVE",
      "source": "SEED",
      "version": 0
    }
  ]
}
```

## 3. 태그 정의 생성

### API-ADMIN-TAG-DEFINITION-002 태그 정의 생성

`POST /api/admin/tag-definitions`

```json
{
  "code": "OCCASION_FAMILY",
  "type": "OCCASION",
  "displayName": "가족 모임",
  "aliases": ["가족식사", "가족 외식"]
}
```

입력 계약은 다음과 같다.

- `type`은 `MENU`, `TASTE`, `OCCASION`, `ATMOSPHERE` 중 하나다.
- `code`는 trim 후 3~64자이며 `^(MENU|TASTE|OCCASION|ATMOSPHERE)_[A-Z0-9]+(?:_[A-Z0-9]+)*$`를 만족하고 접두사가 `type`과 같아야 한다.
- `displayName`은 trim 후 1~100자다.
- `aliases`는 선택 입력이며 생략하면 빈 배열이다. 최대 20개이고 각 값은 trim 후 1~100자다.
- 표시명과 별칭은 각각 Unicode NFKC 정규화, 앞뒤 공백 제거, 연속 Unicode 공백을 한 칸으로 축약, ASCII `A-Z`를 `a-z`로 변환하는 순서로 `normalizedTerm`을 만든다. Java와 PostgreSQL은 같은 Unicode 경계 corpus로 결과 동등성을 검증한다.
- `normalizedTerm`은 1~200자여야 한다. 원문이 100자 이하여도 NFKC 결과가 200자를 넘으면 해당 `displayName` 또는 `aliases` 필드의 400 오류로 거부한다.
- 요청 내부와 기존 모든 태그의 표시명·별칭 사이에서 `normalizedTerm`은 전역 고유해야 한다. 상태가 `DEPRECATED`인 기존 정의의 용어도 재사용하지 않는다.
- 가격·평점·품질 단정, 영업시간·영업 여부, 방문 가능 여부와 근거 없는 홍보 표현 등 `BR-AIEXTRACT-008`의 금지 표현은 생성할 수 없다.

서버는 `status=ACTIVE`, `source=MANUAL_OVERRIDE`, `createdFromSnapshotId=null`로 저장한다. `tag_definition`과 표시명·별칭의 `tag_definition_term` 행 전체를 한 트랜잭션에서 생성한다. 성공은 201이며 생성된 정의를 반환한다. 소비자는 활성 목록을 재조회한다.

```json
{
  "code": "OCCASION_FAMILY",
  "type": "OCCASION",
  "displayName": "가족 모임",
  "aliases": ["가족식사", "가족 외식"],
  "status": "ACTIVE",
  "source": "MANUAL_OVERRIDE",
  "version": 0
}
```

## 4. 오류 계약

| 상태 | 코드 | 조건 |
|---:|---|---|
| 400 | `INVALID_FIELD_VALUE` | 필수값·원문 또는 정규화 결과 길이·배열 상한·유형·코드 형식 또는 접두사 불일치 |
| 400 | `TAG_TERM_FORBIDDEN` | 표시명 또는 별칭이 금지 표현 정책에 해당 |
| 401 | 공통 인증 오류 | 유효한 인증 없음 |
| 403 | 공통 권한 오류 | 현재 역할이 ADMIN이 아님 |
| 409 | `TAG_CODE_ALREADY_EXISTS` | 정규 코드가 기존 정의와 중복 |
| 409 | `TAG_TERM_ALREADY_EXISTS` | 정규화 표시명·별칭이 기존 정의 또는 동시 요청과 충돌 |

충돌 응답은 부분 생성된 태그 정의나 용어를 남기지 않는다. 동시 요청은 `tag_definition.tag_code`와 `tag_definition_term.normalized_term` DB unique 제약으로 하나만 성공시킨다. 서버는 제약 위반을 409로 변환하고 내부 SQL·제약 이름은 응답에 노출하지 않는다.

## 5. 맛집 상세 편집 연동

ADMIN 상세 패널은 활성 목록을 TanStack Query로 조회한다. 생성 성공 뒤 반환된 태그를 현재 목록 캐시에 반영하고 서버 목록을 invalidate/refetch하며, 새 코드를 현재 방문의 로컬 선택 상태에 추가한다. 다른 선택과 작성 중인 보정 사유는 유지한다.

생성 API는 `VisitTag` 또는 `visit_tag_revision`을 만들지 않는다. 관리자가 [방문 태그 교체 API](restaurant-visit-tags-api.md#3-방문-태그-교체)를 별도로 저장했을 때만 연결과 보정 감사가 같은 트랜잭션으로 생긴다. 태그 생성에 성공한 뒤 방문 저장이 취소되거나 실패해도 새 태그 정의는 활성 목록에 남는다.

로그아웃·계정 변경 시 목록 캐시와 생성·편집 상태를 폐기한다. 익명·MEMBER 화면은 이 API를 호출하지 않는다.

## 6. 관리 목록·상세·변경 이력

- `GET /api/admin/tag-definitions/management?status=ALL&page=1&size=20`: `ALL/ACTIVE/DEPRECATED` 상태 필터와 공통 1-base 페이지 계약으로 코드 오름차순 목록을 반환한다.
- `GET /api/admin/tag-definitions/{code}`: 현재 정의를 반환한다. 없는 코드는 404다.
- `GET /api/admin/tag-definitions/{code}/history?page=1&size=20`: 버전 내림차순 감사 이력을 반환한다.

정의 응답의 `version`은 0부터 시작하는 정수다. 감사 항목은 `id`, `action`, 변경 전후 전체 정의인 `before`·`after`, `reason`, `changedByMemberId`, `changedAt`, `version`을 포함한다. 회원 탈퇴 시 `changedByMemberId`만 `null`이 될 수 있다.

## 7. 내용 수정과 상태 전환

`PUT /api/admin/tag-definitions/{code}`는 `expectedVersion`, `displayName`, `aliases`, `reason`으로 표시 내용을 전체 교체한다. 코드·유형·출처는 바꿀 수 없다. 표시명·별칭 검증과 용어 전역 고유성은 생성 계약과 같다.

`POST /api/admin/tag-definitions/{code}/status`는 `expectedVersion`, `status(ACTIVE/DEPRECATED)`, `reason`으로 상태만 전환한다. 실질 변경은 정의 버전을 1 증가시키고 `UPDATE`, `DEPRECATE`, `REACTIVATE` 감사 한 건을 같은 트랜잭션에서 추가한다. 동일 내용·상태 요청은 버전 확인 뒤 감사 없이 현재 값을 반환한다.

낡은 버전은 409 `TAG_DEFINITION_VERSION_CONFLICT`, 용어 충돌은 409 `TAG_TERM_ALREADY_EXISTS`다. 비활성 정의는 자연어 사전과 신규 방문 태그 선택지에서 제외된다. #364의 동적 사전 cache는 별도 즉시 무효화 없이 최대 30초 안에 상태·용어 변경을 반영한다.
