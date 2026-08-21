---
related_documents:
  - ../README.md
  - response-contract.md
  - filtering-contract.md
  - ../discovery/restaurant-discovery-api.md
---

# 페이지네이션 계약

이 문서의 기본값과 허용값은 일반 페이지네이션의 공통 기준이다. 단, 3열 맛집 카드의 마지막 행을 채우기 위한 맛집 탐색 API(`GET /api/restaurants`, `POST /api/restaurants/natural-language-search`)는 endpoint 계약에서 기본값을 `21`로 정하고, 기존 호출 호환을 위해 `10`, `20`, `21`, `50`을 허용한다. 다른 페이지네이션 API는 아래 공통 기준을 따른다.

## 1. 요청

| 이름 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---:|---|---|
| `page` | integer | 아니요 | `1` | 1 이상. 첫 페이지는 1 |
| `size` | integer | 아니요 | `20` | `10`, `20`, `50` 중 하나 |

페이지 번호는 1부터 시작한다. 0 이하의 `page`와 허용되지 않은 `size`는 `400 INVALID_FIELD_VALUE`다.

## 2. 정렬

맛집 목록은 클라이언트 정렬 파라미터를 받지 않는다. 맛집 이름 오름차순, 이름이 같으면 전체 도로명주소 오름차순을 적용한다. 같은 조건에서 페이지 이동 중 누락·중복이 없어야 한다.

## 3. 응답

```json
{
  "items": [],
  "page": {
    "number": 1,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false
  }
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `items` | array | 예 | 해당 페이지 항목. 없으면 `[]` |
| `page.number` | integer | 예 | 요청에 적용된 현재 페이지 |
| `page.size` | integer | 예 | 요청에 적용된 페이지 크기 |
| `page.totalElements` | integer | 예 | 전체 조건 일치 맛집 수 |
| `page.totalPages` | integer | 예 | 전체 페이지 수. 결과가 없으면 `0` |
| `page.hasNext` | boolean | 예 | 다음 페이지 존재 여부 |

유효하지만 결과 범위를 벗어난 페이지는 `200`과 빈 `items`를 반환한다. 이때 전체 개수·페이지 수는 실제 조건 결과를 유지하고 `hasNext`는 `false`다. 검색·필터가 변경되면 클라이언트는 첫 페이지를 요청한다.
