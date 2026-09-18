---
related_documents:
  - README.md
  - ../../data/table-definitions.md
  - ../../../07-adr/integration/ext-005-kakao-place-periodic-revalidation.md
  - ../../../08-planning/issue-377-kakao-place-revalidation.md
---

# 관리자 Kakao 장소 재검증 API

Issue #377의 재검증은 일반 사용자 조회가 아니라 `/api/admin` 관리자 경계에서만 실행·조회한다. Kakao place ID는 내부 동일성 값이므로 응답에 포함하지 않는다.

## 1. 재검증 실행

`POST /api/admin/restaurants/{restaurantId}/place-revalidation`

- JWT Access Token과 `ADMIN` 권한이 필요하다.
- Kakao 호출은 요청 트랜잭션 밖에서 수행한다.
- 응답은 `202 Accepted`이며 외부 호출 결과가 확정되면 상태·감사 이력이 저장된다.
- 재검증 기능이 비활성화된 기본 설정에서는 Kakao를 호출하지 않고 `503 RESTAURANT_PLACE_REVALIDATION_DISABLED`를 반환한다.
- 존재하지 않거나 삭제된 Restaurant는 `404 RESOURCE_NOT_FOUND`다.
- 이미 실행 중인 lease가 있으면 새 호출을 만들지 않고 `404 RESOURCE_NOT_FOUND`로 처리한다.
- 실행 도중 lease 또는 Restaurant 본문 변경을 감지한 stale 결과는 반영되지 않으며 공통 오류 envelope의 `409 RESTAURANT_PLACE_REVALIDATION_STALE`를 반환한다.

응답 예시:

```json
{
  "outcome": "AUTO_CORRECTED",
  "nextAttemptAt": "2026-09-19T00:00:00Z"
}
```

정상 `outcome`은 `VERIFIED`, `AUTO_CORRECTED`, `REVIEW_REQUIRED`, `MATCH_NOT_FOUND`, `RETRY_SCHEDULED`, `RETRY_EXHAUSTED` 중 하나다. lease 충돌 시에만 응답용 `STALE_DISCARDED`가 사용되며 상태·감사 행은 추가되지 않는다.

## 2. 현재 상태 조회

`GET /api/admin/restaurants/{restaurantId}/place-revalidation`

응답은 `status`, 시도 횟수, 마지막 확인 시각, 판정 사유, 오류 코드와 다음 재시도 시각을 포함한다.
정상 완료·검토 상태의 `nextAttemptAt`은 설정된 정기 재검증 주기를 가리키며, `RETRY_EXHAUSTED`만 자동 재검증에서 제외한다.

```json
{
  "status": "REVIEW_REQUIRED",
  "attemptCount": 1,
  "nextAttemptAt": "2026-09-19T00:00:00Z",
  "lastCheckedAt": "2026-09-18T00:00:00Z",
  "reasonCode": "DISTRICT_CHANGED",
  "errorCode": null
}
```

## 3. 변경 감사 조회

`GET /api/admin/restaurants/{restaurantId}/place-revalidation/audits?size=20`

`size`는 1~100이다. 감사의 `observedValues`, `previousValues`, `appliedValues`는 Kakao place ID를 제외한 표시·주소·전화번호·좌표와 URL만 제공한다. 감사 행은 append-only이며 변경 전 값과 자동 적용 값을 함께 확인할 수 있다.

자동 보정은 같은 Kakao place ID와 URL이 확인되고 같은 자치구의 도로명주소, 상호명·전화번호·좌표 쌍만 달라진 경우에 한정한다. place ID/URL 불일치, 자치구 변경, 모호한 매칭은 `REVIEW_REQUIRED`, 검색 결과 부재는 `MATCH_NOT_FOUND`다. 429·5xx·timeout은 기존 Restaurant를 변경하지 않고 `RETRY_SCHEDULED`로 남긴다.
