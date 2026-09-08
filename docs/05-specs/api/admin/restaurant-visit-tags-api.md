---
id: API-ADMIN-VISIT-TAGS-001
related_documents:
  - ../../../08-planning/restaurant-visit-tag-editing.md
  - ../../../08-planning/admin-tag-definition-creation.md
  - ../../../04-product/prd/detail/restaurant-detail.md
  - ../../data/third-expansion-ai-video-data-contract.md
  - ../discovery/natural-language-restaurant-discovery-api.md
  - ../common/authentication-contract.md
  - ../common/error-contract.md
  - tag-definition-api.md
---

# 관리자 맛집 방문 태그 API

## 1. 범위와 권한

FR-AIEXTRACT-007·FR-NLSEARCH-004의 상세 화면 사후 보정 경로다. 두 API 모두 Bearer JWT와 현재 ADMIN 권한이 필요하며 공개 상세 응답은 변경하지 않는다. 인증 실패 401, MEMBER 403, 오류는 공통 traceId를 포함한다. 응답은 Cache-Control: no-store다.

## 2. 조회

`GET /api/admin/restaurants/{restaurantId}/visit-tags`

공개·활성 맛집만 조회한다. 해당 맛집에 속한 PUBLIC/ACTIVE Visit 중 Creator·Video도 PUBLIC/ACTIVE/AVAILABLE인 관계만 items에 포함한다. 태그가 없는 Visit도 포함한다. 없는/비공개/삭제 맛집은 404, 유효 방문 없음은 200과 빈 items다. 식별자는 불투명 문자열이다. 서버 내부 식별자 형식에 맞지 않는 경로 값은 공통 계약에 따라 400 INVALID_IDENTIFIER다.

```json
{
  "items": [{
    "visitId": "visit-id",
    "creatorName": "채널명",
    "videoTitle": "영상 제목",
    "videoUrl": "https://www.youtube.com/watch?v=video-id",
    "version": "opaque-version",
    "tags": [{"code": "MENU_NAENGMYEON", "displayName": "냉면", "type": "MENU", "source": "AI_AUTO_CONFIRMED"}]
  }],
  "tagOptions": [{"code": "MENU_NAENGMYEON", "displayName": "냉면", "type": "MENU"}]
}
```

`tags`는 현재 연결된 태그(비활성 정의 포함), `tagOptions`는 이 응답 시점의 ACTIVE 정의다. 비활성 태그는 제거할 수 있으나 새로 선택·저장할 수 없다. `version`은 현재 연결 상태와 최신 감사 이력을 반영한 불투명 동시성 토큰이다. 프론트는 계산·해석하지 않고 저장 시 그대로 돌려준다. 이슈 #363 이후 활성 목록의 갱신 기준은 [태그 정의 목록 API](tag-definition-api.md#2-활성-태그-정의-목록)이며 기존 `tagOptions`는 #358 소비자 호환을 위해 유지한다.

## 3. 방문 태그 교체

`PUT /api/admin/restaurants/{restaurantId}/visits/{visitId}/tags`

```json
{"expectedVersion":"opaque-version","tagCodes":["MENU_NAENGMYEON"],"reason":"영상 근거 확인 후 혼밥 태그 제거"}
```

- expectedVersion 필수, 비어 있지 않은 문자열(최대 128자).
- tagCodes 필수 배열, 0~50개, 중복/빈 값/미등록 코드 금지. 기존에 연결된 비활성 코드는 유지하거나 제거할 수 있지만 제거 후 재추가는 금지한다. 코드 최대 64자. 빈 배열은 전부 해제다. 검색 필터의 최대 5개 제한과 저장 태그 개수는 별개다.
- reason 필수, trim 후 1~1000자.
- 조회와 동일한 공개·활성·이용 가능 상태 및 restaurantId 소속을 다시 확인한다. 불일치/없는 대상은 404.
- Visit 행 잠금 뒤 현재 version을 비교한다. 불일치는 409 `VISIT_TAG_CONCURRENT_UPDATE`이며 쓰기는 0건이다.
- 새 태그는 ADMIN_OVERRIDE, evidence는 UNKNOWN으로 저장한다. 유지 태그의 원래 출처·근거·Snapshot을 보존하고 제거 대상만 삭제한다. 후보 Snapshot·과거 AI 판단은 수정하지 않는다.
- 연결 변경과 감사 이력을 한 DB 트랜잭션에서 커밋한다. 감사는 변경 전후 태그 코드, 사유, 현재 member_account의 관리자 ID, 시각을 보존한다. 동일 선택의 no-op은 새 감사 없이 성공한다.
- 성공 204. 소비자는 관리자 태그 조회를 재실행한다. 입력 오류는 400 `INVALID_FIELD_VALUE`.

## 4. 화면 처리와 검색

ADMIN에서만 조회하며 방문별 태그를 표시한다. 저장 버튼과 취소 버튼, 사유 입력, 저장 중 중복 제출 방지, 실패 사유와 재시도, 409 최신 조회 후 재편집을 제공한다. 로그아웃/계정 변경 시 캐시와 편집 상태를 폐기한다. 공개 상세 서버 렌더링은 유지한다.

검색은 기존 VisitTag를 사용하므로 다음 요청부터 변경을 반영한다. 다중 태그는 동일 Visit 내 AND이며 다른 방문의 태그를 합치지 않는다. 이슈 #363에서 새 태그 정의를 생성할 수 있지만 생성만으로 VisitTag와 감사 이력을 만들지 않는다. 생성 성공 뒤 새 태그를 로컬 선택에 추가하고 이 PUT을 별도로 저장해야 연결과 `visit_tag_revision`이 함께 확정된다. 자연어 파서가 새 용어를 동적으로 읽는 기능은 #364 범위다.

## 5. 데이터 보강

V9 `visit_tag_revision`은 별도 사후 보정 감사다. id UUID PK, visit_id UUID FK visit, before_tag_codes/after_tag_codes JSONB 배열, reason varchar(1000), changed_by_member_id nullable UUID FK member_account (ON DELETE SET NULL), changed_at timestamptz, 순서가 명확한 revision을 둔다. Visit별 revision은 unique이며 Visit 잠금 안에서 증가한다. 일반 UPDATE/DELETE는 금지하며 회원 삭제의 FK SET NULL에 의한 계정 연결 익명화만 허용한다. 기존 V1~V8은 수정하지 않는다. 기존 AI 후보 감사의 legacy admin FK는 사용하지 않는다. 태그 hash와 최신 revision을 포함해 외부 경로의 연결 변경도 version에 반영한다.

이 계약은 2026-09-05 사용자의 구현 요청을 근거로 작성했다. 별도 팀 소유자의 사전 승인을 받았다는 의미는 아니며 API·데이터 소유자 리뷰를 병합 전에 받는다.
