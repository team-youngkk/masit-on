---
id: PRD-DISCOVERY-004
title: 인기 맛집
status: draft
workstream: WS-10
owner: 양성훈
reviewers:
  - 박진영
related_requirements:
  - FR-POPULAR-001
related_business_rules:
  - BR-POPULAR-001
  - BR-POPULAR-002
  - BR-POPULAR-003
related_nfr:
  - NFR-PERFORMANCE-006
  - NFR-RELIABILITY-004
  - NFR-TEST-005
related_documents:
  - ../../../02-analysis/second-expansion-domain-boundaries.md
  - ../../../02-analysis/second-expansion-workstreams.md
  - ../../user-flows/second-expansion-user-flows.md
  - ../../wireframes/second-expansion-wireframes.md
  - ../../../05-specs/api/discovery/popular-restaurant-api.md
  - ../../../05-specs/data/second-expansion-data-contract.md
  - ../../../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md
---

# 인기 맛집 PRD

## 1. 목적과 선행 조건

사용자가 검색어 없이도 현재 회원들이 많이 저장한 공개 맛집을 발견하게 한다. WS-06의 찜 행동 데이터와 Restaurant 공개 상태 계약이 선행되어야 한다.

## 2. 목표와 성공 기준

- 비로그인 사용자가 현재 찜 수 기준 상위 20개 공개 맛집을 안정된 순서로 본다.
- 찜 추가·해제와 맛집 공개 상태 변경이 다음 조회부터 반영된다.
- 성공 지표 후보는 조회 성공률, 상세 전환율과 집계 불일치 0건이며 수치 목표는 계측 계약에서 정한다.

## 3. 범위

### 포함

- 현재 찜 수가 1개 이상인 공개 맛집
- 전체 기간 상위 20개
- 찜 수 내림차순, 동점은 맛집 ID 오름차순
- 실시간 PostgreSQL 조회

### 제외

- 상세·최근 조회 신호, 기간별 점수와 추천 알고리즘
- 페이지네이션, 배치·캐시·재계산 상태와 조작 탐지 모델

## 4. 제품 요구사항과 정책

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-DISCOVERY-POPULAR-001 | 공개 사용자는 현재 찜 수 기준 상위 20개 공개 맛집을 조회한다. | FR-POPULAR-001 |
| PR-DISCOVERY-POPULAR-002 | 현재 찜 수 1개 이상만 포함하고 안정된 동점 순서를 적용한다. | BR-POPULAR-001~002 |
| PR-DISCOVERY-POPULAR-003 | 찜과 공개 상태 변경을 다음 조회부터 반영한다. | BR-POPULAR-003 |

- Favorite는 회원별 찜 원본을, Restaurant는 공개 상태를 소유하며 Popularity 읽기 책임은 둘을 변경하지 않는다.
- 별도 popularity Aggregate나 최상위 패키지는 만들지 않는다. 필요성은 후속 성능 설계에서 재검토한다.

## 5. 화면과 예외

- 메인 인기 영역 또는 인기 목록: 로딩, 정상, 결과 없음, 조회 실패
- 결과 없음은 정상 빈 상태이며 조회 실패와 구분한다.
- 맛집 카드는 기존 공개 Restaurant 표시 계약을 사용하고 상세로 이동한다.
- 기간 선택, 조회수와 추천 점수는 표시하지 않는다.

## 6. 개인정보·운영·비용

- 회원 식별자와 개별 찜 이력은 공개하지 않고 집계 수만 사용한다.
- 별도 운영자 편집이나 외부 연동은 없다.
- 실시간 쿼리의 인덱스·부하 검증 비용이 발생한다.

## 7. 완료 조건

- [ ] FR-POPULAR-001, BR-POPULAR-001~003의 정확성·공개 상태·동점 테스트가 통과한다.
- [ ] NFR-PERFORMANCE-006 측정 환경에서 공개 조회 성능을 충족한다.
- [ ] 찜 동시 변경에도 집계가 음수·중복되지 않는다.
- [ ] WS-10 양성훈 구현과 박진영 기본 리뷰가 완료된다.
- [ ] API·데이터 계약과 일정이 승인된다.
