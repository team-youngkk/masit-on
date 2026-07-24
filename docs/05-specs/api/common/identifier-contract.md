---
related_documents:
  1: ../README.md
  2: response-contract.md
  3: error-contract.md
  4: ../../data/entity-definitions.md
---

# 식별자 계약

## 1. 적용 범위

외부 API의 맛집, 유튜버, 영상과 방문 관계 식별자에 공통 적용한다. 각 식별자는 자원 종류 안에서 하나의 논리 대상을 안정적으로 가리키는 불투명 값이다.

## 2. 식별자 종류

| 논리 타입 | 사용 필드 | 의미 |
|---|---|---|
| `RestaurantIdentifier` | `restaurantId`, 맛집의 `id` | 맛집 식별자 |
| `CreatorIdentifier` | `creatorId`, 유튜버의 `id` | YouTube 채널 단위 유튜버 식별자 |
| `VideoIdentifier` | `videoId`, 영상의 `id` | 등록된 YouTube 영상 식별자 |
| `VisitRelationshipIdentifier` | 방문 관계의 `id` | 맛집·유튜버·영상 조합의 등록 관계 식별자 |

## 3. 표현 규칙

- 모든 외부 자원 식별자는 JSON `string`으로 표현한다.
- 값은 비어 있지 않은 불투명 문자열이며, 클라이언트는 UUID 여부나 내부 생성 규칙을 검증하지 않는다.
- 클라이언트는 식별자를 연속 번호, 생성 순서 또는 다른 자원의 식별자로 해석하지 않는다.
- 식별자를 계산·증감·부분 분석하지 않고 받은 값을 그대로 경로와 요청 필드에 전달한다.
- 식별자 생성 기술과 내부 저장 방식은 데이터 모델 또는 ADR에서 결정한다.
- 기능 문서의 `Identifier`는 이 계약의 불투명 JSON 문자열을 뜻한다. 예시의 `*-id`는 값의 구조나 생성 기술을 뜻하지 않는다.

## 4. 유효하지 않거나 존재하지 않는 식별자

- 확정 타입·형식에 맞지 않는 값은 `400 INVALID_IDENTIFIER`다.
- 형식은 유효하지만 공개 단일 조회 대상이 없거나 비공개·삭제 상태이면 존재 여부를 구분하지 않고 `404 *_NOT_FOUND`다.
- 관리자 방문 관계 요청의 참조가 없으면 대상별 `404 RESTAURANT_NOT_FOUND`, `CREATOR_NOT_FOUND`, `VIDEO_NOT_FOUND`를 사용하며 관계를 만들지 않는다.

## 5. 확정 범위와 후속 설계

외부 JSON 타입은 문자열로 확정한다. 실제 생성 알고리즘, 길이 상한, 데이터베이스 저장 타입과 UUID 사용 여부는 외부 계약을 바꾸지 않는 후속 설계다.
