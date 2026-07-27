---
related_documents:
  - ../README.md
  - authentication-api.md
  - reference-data-api.md
  - visit-registration-api.md
  - ../../../04-product/prd/admin/admin-data-management.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../07-adr/platform/web-003-routing-boundary.md
---

# 관리자 등록 API

[WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 관리자 등록 흐름은 다음 두 단계로 나눈다.

1. [관리자 인증 API](authentication-api.md)로 사전 발급 계정의 JWT Access Token과 Refresh Token을 발급한다.
2. [관리자 기본 데이터 API](reference-data-api.md)로 외부 정보를 미리 확인하고 맛집, 유튜버, 영상을 각각 등록한다.
3. [관리자 방문 관계 등록 API](visit-registration-api.md)로 이미 등록된 세 대상을 연결한다.

모든 `/api/admin` 등록 요청은 `Authorization: Bearer` JWT Access Token과 동일한 `ADMIN` 등록 권한을 요구한다. 로그인·재발급·로그아웃의 예외 matcher와 Refresh Token 쿠키 경로는 [관리자 인증 API](authentication-api.md)를 따른다. 수정·삭제·승인 상태 관리, 일반 사용자 등록, 자동 등록과 원본 영상 업로드는 MVP에 포함하지 않는다.
