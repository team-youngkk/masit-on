---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../05-specs/api/admin/tag-definition-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - dynamic-natural-language-tag-dictionary.md
---

# 관리자 태그 정의 생명주기 — 이슈 #365

## 확정 결정

사용자가 승인한 추천안에 따라 내용 수정 PUT과 상태 전환 POST를 분리한다. 코드·유형·출처는 불변이며 변경 사유와 expectedVersion을 필수로 받는다. 변경 전후 전체 정의는 JSONB snapshot, 행위·관리자·시각·버전은 일반 컬럼으로 감사 이력에 저장한다. 정의·용어·감사는 하나의 트랜잭션이며 감사 UPDATE/DELETE는 DB에서 거부한다.

자연어 검색에는 #364의 30초 TTL을 유지한다. 즉시 cache 무효화나 별도 메시징을 도입하지 않는다. 비활성 정의도 기존 방문 연결을 유지하거나 제거할 수 있지만 신규 연결은 거절한다. 제거 저장 후 재연결도 신규 연결이다. 비활성 정의의 현재 용어도 전역 고유성을 계속 점유한다. 표시명·별칭 수정으로 제거된 용어는 새 정의에서 사용할 수 있으며 이전 값은 감사 snapshot에 남는다.

## API와 화면

모든 API는 ACTIVE ADMIN, no-store, 공통 traceId 오류 계약을 적용한다.

| 경로 | 계약 |
|---|---|
| GET /api/admin/tag-definitions | 기존 활성 선택 목록 유지 |
| GET /api/admin/tag-definitions/management | 관리용 페이지 목록. status=ALL(기본)/ACTIVE/DEPRECATED, page=1, size=20(10/20/50). code 오름차순 |
| GET /api/admin/tag-definitions/{code} | 현재 정의 상세 |
| PUT /api/admin/tag-definitions/{code} | expectedVersion(0 이상 정수), displayName, aliases(필수 배열), reason으로 내용 전체 교체 |
| POST /api/admin/tag-definitions/{code}/status | expectedVersion, status(ACTIVE/DEPRECATED), reason으로 전환 |
| GET /api/admin/tag-definitions/{code}/history | page/size 공통 페이지 목록. version 내림차순 |

정의 응답은 기존 code/type/displayName/aliases/status/source와 version(정수)을 포함한다. 신규·기존 정의의 최초 version은 0이다. 실질 변경은 version을 1 증가시키고 UPDATE/DEPRECATE/REACTIVATE 감사 1건을 추가한다. 동일 내용·동일 상태 요청은 현재 version을 검증한 후 200으로 기존 값을 반환하며 감사·version을 추가하지 않는다. 감사 항목은 id, action, before, after(정의 전체 snapshot), reason, changedByMemberId, changedAt, version이다. 페이지 응답은 items와 page(number,size,totalElements,totalPages,hasNext)를 사용한다.

없는 정의는 404 RESOURCE_NOT_FOUND, 오래된 version은 409 TAG_DEFINITION_VERSION_CONFLICT, 용어 충돌은 409 TAG_TERM_ALREADY_EXISTS다. 내용 검증은 #363의 원문 100자·별칭 20개·정규화 200자·금지 표현 계약을 재사용한다. reason은 trim 후 안전한 텍스트 1~1000자다. 요청의 code/type 등 미정의 필드는 400으로 거부한다.

관리 화면 /admin/tag-definitions는 상태별 목록, 상세 편집, 상태 전환, 페이지별 감사 이력을 제공한다. 충돌 시 입력을 보존하고 명시적 최신값 다시 불러오기를 제공한다. 계정 전환 시 요청 취소·상태·캐시를 폐기한다. 맛집 상세에는 관리 화면 링크와 비활성 표시를 제공한다. 저장 성공 후 관리자 태그 및 방문 목록 캐시를 갱신하고 검색 반영은 최대 30초임을 안내한다.

## 데이터와 검증

V12는 tag_definition.version bigint NOT NULL DEFAULT 0 CHECK(version >= 0)와 tag_definition_audit를 추가한다. 감사에는 id UUID PK, tag_definition_id UUID FK RESTRICT, action, before_snapshot/after_snapshot JSONB object, reason varchar(1000), changed_by_member_id UUID FK SET NULL, changed_at timestamptz, version bigint를 둔다. 회원 탈퇴 시 행위자 연결만 익명화하고 감사 행은 보존한다. (tag_definition_id, version)는 unique이며 version > 0이다. 기존 migration은 수정하지 않는다.

정의 row 잠금과 version 검증으로 동시 수정 중 하나만 성공시킨다. 방문 연결의 태그 SHARE 잠금과 상태 변경 UPDATE 잠금으로 신규 연결과 폐기를 직렬화한다. DB unique가 용어 충돌을 최종 판정하며 실패 시 정의·용어·감사 모두 롤백한다.

필수 검증: 관리자 인가, no-store/traceId, 입력/미정의 필드, 중복 별칭, 동시 수정, 감사 원자성 및 UPDATE/DELETE 거부, V11→V12 참조 보존, 비활성 기존 연결 유지·제거·신규 거절, 재활성화, TTL 경과 후 사전 반영, Golden V1, 프론트 테스트·타입·빌드 및 전체 백엔드 CI.
