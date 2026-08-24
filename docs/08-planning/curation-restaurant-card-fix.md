---
related_documents:
  - ../04-product/prd/curation/admin-curation.md
  - ../04-product/wireframes/second-expansion-wireframes.md
  - ../05-specs/api/curation/curation-api.md
  - ../05-specs/api/common/identifier-contract.md
  - ../troubleshooting/pr-142-public-curation-review.md
---

# 공개 큐레이션 구성 맛집 카드 보완

## 1. 문제

공개 큐레이션 상세의 `구성 맛집`이 제목과 도로명 주소만 있는 공통 `Card`로 표시되어, 맛집 탐색 화면의 카드와 시각적 계층과 주요 행동이 달랐다. 이미지·찜·상세 보기 진입점이 없어 큐레이션에서 맛집을 고르는 흐름이 끊겼다.

## 2. 적용 범위

큐레이션 상세의 구성 맛집 카드를 기존 공개 맛집 카드의 핵심 구조에 맞춘다.

- `restaurantId`와 이름을 기준으로 안정적으로 선택한 placeholder 이미지를 표시한다.
- 맛집 이름, `roadAddress`, 찜 상태/로그인 진입점, 맛집 상세 보기 링크를 표시한다.
- 큐레이션 상세의 저장된 순서를 유지하고, 기존 `1-base` 번호 표시를 유지한다.
- 모바일 단일 열과 데스크톱 2·3열 반응형 그리드를 유지한다.

## 3. 계약 경계

공개 큐레이션 API의 구성 맛집 응답은 `restaurantId`, `name`, `roadAddress`만 제공한다. 따라서 이 변경에서는 카테고리나 방문 유튜버를 추정해 표시하지 않는다. 해당 정보가 필요하면 API 소유자 합의와 공개 조회 성능 검토를 거쳐 API 계약을 별도로 변경해야 한다.

이미지는 외부 원본 URL을 추가하지 않고 저장소의 기존 placeholder 자산을 사용한다. 카테고리가 계약에 없으므로 맛집 이름을 이미지 세트 선택의 fallback 입력으로 사용하며, 같은 ID와 이름은 같은 이미지 변형을 선택한다.

## 4. 완료 기준과 검증

- 구성 맛집마다 이미지·이름·주소·찜·상세 보기 요소가 렌더링된다.
- 이름과 상세 보기 링크는 불투명한 `restaurantId`를 URL 인코딩해 기존 상세 경계로 이동한다.
- 비공개·빈 큐레이션 상태와 공개 조회 오류 처리는 기존 로직을 유지한다.
- 프런트 테스트, TypeScript 타입 검사, Next.js 운영 빌드와 `git diff --check`를 통과한다.
