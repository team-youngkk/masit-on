---
related_documents:
  - ../README.md
  - ../discovery/map-discovery-api.md
  - ../../../07-adr/integration/map-001-map-bounds-search.md
  - ../../../01-requirements/non-functional-requirements.md
---

# 좌표 공통 계약

## 1. 표현

지도 좌표는 WGS84 십진수 JSON number를 사용한다. 위도 필드는 `latitude`, 경도 필드는 `longitude`이며 위도는 `-90`~`90`, 경도는 `-180`~`180` 범위다. 소수 자릿수와 문자열 표현을 식별 의미로 사용하지 않는다.

## 2. 지도 조회 경계

지도 API는 좌표를 응답에만 제공한다. 현재 지도 뷰포트의 `south`, `west`, `north`, `east`는 API 입력·Query Key·URL·로그에 포함하지 않는다. 지도 이동·확대·축소는 서버 조회 조건이 아니다.

좌표 없는·범위 오류·비공개·삭제 맛집은 지도 결과에서 제외한다. 사용자 현재 위치, 위치 권한, 기기 식별자, 반경과 이동 이력은 이 계약의 입력·응답·로그에 포함하지 않는다.

구체적인 조회 상한·필터·오류는 [지도 탐색 API](../discovery/map-discovery-api.md)를 따른다.
