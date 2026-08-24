---
related_documents:
  - ../04-product/prd/curation/admin-curation.md
  - ../04-product/wireframes/second-expansion-wireframes.md
  - ../05-specs/api/curation/curation-api.md
  - ../05-specs/api/common/identifier-contract.md
  - ../troubleshooting/pr-142-public-curation-review.md
  - ../troubleshooting/pr-301-curation-restaurant-card-review.md
---

# 공개 큐레이션 구성 맛집 카드 보완

## 1. 문제

공개 큐레이션 상세의 `구성 맛집`이 제목과 도로명 주소만 있는 공통 `Card`로 표시되어, 상세 화면으로 이동하는 주요 행동이 눈에 잘 띄지 않았다.

## 2. 적용 범위

큐레이션 상세의 구성 맛집 카드를 기존 공개 화면의 텍스트·상세 이동 흐름에 맞춘다.

- 맛집 이름, `roadAddress`, 맛집 상세 보기 링크를 표시한다.
- 큐레이션 상세의 저장된 순서를 유지하고, 기존 `1-base` 번호 표시를 유지한다.
- 모바일 단일 열과 데스크톱 2·3열 반응형 그리드를 유지한다.

## 3. 계약 경계

공개 큐레이션 API의 구성 맛집 응답은 `restaurantId`, `name`, `roadAddress`만 제공한다. 따라서 이 변경에서는 이미지·카테고리·방문 유튜버·찜 상태를 추정하거나 추가하지 않는다. 이미지와 개인화 기능은 WS-11 큐레이션 확정 범위에서 제외된 항목이다. 해당 정보가 필요하면 WS-11·WS-06 소유자 합의와 공개 조회 성능 검토를 거쳐 제품 추적표·PRD·API 계약을 먼저 변경해야 한다.

## 4. 완료 기준과 검증

- 구성 맛집마다 이름·주소·상세 보기 요소가 렌더링된다.
- 이름과 상세 보기 링크는 불투명한 `restaurantId`를 URL 인코딩해 기존 상세 경계로 이동한다.
- 비공개·빈 큐레이션 상태와 공개 조회 오류 처리는 기존 로직을 유지한다.
- 프런트 테스트, TypeScript 타입 검사, Next.js 운영 빌드와 `git diff --check`를 통과한다.
