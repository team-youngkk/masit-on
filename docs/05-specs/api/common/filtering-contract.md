---
related_documents:
  - region-contract.md
  - ../README.md
  - ../../../01-requirements/business-rules.md
  - pagination-contract.md
  - identifier-contract.md
  - ../discovery/restaurant-discovery-api.md
  - ../discovery/creator-discovery-api.md
---

# 검색·필터 계약

## 1. 전달 방식

| 조건 | 쿼리 이름 | 값 | 미지정 시 |
|---|---|---|---|
| 맛집 이름 검색 | `query` | string | 이름 조건 없음 |
| 전국 지역 | `regionCode` | 활성 시·도 또는 시·군·구의 10자리 행정코드 문자열 1개 | 레거시 필터도 없으면 지역 조건 없음 |
| 레거시 서울 지역 | `district` | 서울특별시 자치구 이름 1개, `regionCode`와 상호 배제 | 코드도 없으면 지역 조건 없음 |
| 대표 음식 카테고리 | `category` | 확정된 카테고리 이름 1개 | 카테고리 조건 없음 |
| 유튜버 | `creatorId` | `CreatorIdentifier` 1개 | 유튜버 조건 없음 |

대표 음식 카테고리는 `한식`, `중식`, `일식`, `양식`, `동남아 음식`, `인도·남아시아 음식`, `분식`, `카페·디저트`, `술집·주점`, `기타` 중 하나다.

`regionCode`는 #394의 [지역 계층 계약](region-contract.md)에 따라 일반 목록과 자연어 직접 필터에 추가한다. 지도 API의 기존 `district` 계약은 확장하지 않는다. 전국 선택지는 맛집 유무와 무관한 `GET /api/regions`를 사용한다.

## 2. 조합 규칙

- 서로 다른 지정 조건은 모두 만족해야 하는 AND로 조합한다.
- 같은 종류의 필터는 하나만 허용한다. 반복 쿼리, 쉼표 목록과 배열 형태의 복수 값은 `400 INVALID_FIELD_VALUE`다.
- 중복된 같은 필터 값도 복수 값 전달로 보고 거부한다.
- `query`는 앞뒤 공백을 제거하고 영문 대소문자를 구분하지 않은 맛집 이름 부분 일치에만 사용한다.
- 공백 제거 후 `query`가 비면 이름 조건을 적용하지 않는다.
- `query`는 앞뒤 공백 제거 후 Unicode 코드 포인트 기준 최대 100자다. 초과하면 `400 INVALID_FIELD_VALUE`다.
- 존재하지 않거나 공개되지 않은 `creatorId`는 `400 INVALID_FIELD_VALUE`로 처리한다. 유효하고 공개된 유튜버지만 방문 관계가 없으면 정상 빈 목록이다.
- `regionCode` 형식 오류·미등록·비활성 코드, `regionCode`와 `district` 동시 지정, 서울 자치구가 아닌 `district`, 사전 정의되지 않은 카테고리는 `400 INVALID_FIELD_VALUE`다.
- 시·도 코드는 자신과 활성 자식 지역의 맛집을 포함하고, 시·군·구 코드는 해당 지역만 포함한다. 세종은 하위 지역 없이 최상위 코드로 조회한다.
- 지역 표시명은 식별자로 사용하지 않는다. 응답 `district`는 전국 지역 표시명일 수 있으며 레거시 `district` 요청 허용값과 같다고 가정하지 않는다.
- 지원하지 않는 쿼리 파라미터는 조용히 무시하지 않고 `400 INVALID_REQUEST`로 처리한다.
- 클라이언트는 정렬 조건을 전달하지 않으며 공통 기본 정렬을 적용한다.

## 3. 빈 결과

유효한 조건을 모두 적용한 결과가 없으면 오류가 아니라 `200`과 빈 `items`·페이지 메타데이터를 반환한다.
