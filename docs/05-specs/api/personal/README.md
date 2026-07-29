---
related_documents:
  - ../README.md
  - personal-restaurant-api.md
  - ../../../04-product/prd/personal/personal-restaurant-management.md
  - ../../../04-product/prd/account/member-authentication.md
  - ../../../02-analysis/first-expansion-workstreams.md
---

# 개인화 API

[WS-06](../../../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리)은 로그인 회원의 찜과 최근 본 맛집을 `/api/me` 경계 아래에서만 다룬다.

- 찜 추가·해제와 상태 확인은 모두 `restaurantId` 하나를 키로 하는 동일 자원 경로를 사용한다.
- 찜 상태는 배치 전용 API나 기존 공개 목록·상세 응답 변경 대신 [GET `/api/me/favorites/{restaurantId}`](personal-restaurant-api.md#api-personal-003-맛집별-현재-회원-찜-상태-조회)를 선택한다. 현재 범위에서 가장 작은 명시적 본인 API이며, 회원별 상태를 공개 캐시 가능한 조회 계약과 분리한다.
- 최근 본 맛집 생성은 별도 공개 쓰기 API 없이 공개 맛집 상세의 `200 OK` 성공 뒤 서버 내부 부수효과로만 기록한다.
- 모든 개인화 경로는 `memberId` 같은 다른 회원 식별자 입력을 받지 않고, 인증된 Principal을 현재 회원으로 고정한다.
