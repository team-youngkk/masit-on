---
id: PRD-CURATION-001
title: 관리자 큐레이션
status: approved
workstream: WS-11
owner: 김인안
reviewers:
  - 양성훈
related_requirements:
  - FR-CURATION-001
  - FR-CURATION-002
  - FR-CURATION-003
  - FR-CURATION-004
related_business_rules:
  - BR-CURATION-001
  - BR-CURATION-002
  - BR-CURATION-003
  - BR-CURATION-004
related_nfr:
  - NFR-PERFORMANCE-006
  - NFR-OBSERVABILITY-004
  - NFR-TEST-005
related_documents:
  - ../../../02-analysis/second-expansion-domain-boundaries.md
  - ../../../02-analysis/second-expansion-workstreams.md
  - ../../user-flows/second-expansion-user-flows.md
  - ../../wireframes/second-expansion-wireframes.md
  - ../../../05-specs/api/curation/curation-api.md
  - ../../../05-specs/data/second-expansion-data-contract.md
---

# 관리자 큐레이션 PRD

## 1. 목적과 선행 조건

관리자가 특정 주제로 공개 맛집을 직접 구성해 사용자에게 탐색 맥락을 제공한다. 관리자 인증과 Restaurant 공개 상태 계약이 선행되어야 한다.

## 2. 목표와 성공 기준

- 관리자가 큐레이션을 작성·수정·게시/비게시하고 구성 순서를 통제한다.
- 사용자는 게시된 큐레이션과 공개 상태인 구성 맛집만 본다.
- 성공 지표 후보는 공개 조회 성공률, 상세 전환율, 게시 오류와 비공개 맛집 노출 0건이다.

## 3. 범위

### 포함

- `DRAFT`와 `PUBLISHED` 상태
- 제목·설명, 공개 맛집 최대 20개와 관리자 수동 순서
- 메인 게시 큐레이션 최대 5개
- 게시 중 수정의 즉시 공개 반영
- 비공개·삭제 맛집 자동 숨김과 관리자 경고

### 제외

- 예약 게시, 자동 추천, 개인화와 공동 편집
- 개인 컬렉션 Aggregate·저장소·상태 모델 재사용

## 4. 제품 요구사항

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-CURATION-001 | 관리자는 큐레이션 초안을 등록하고 제목·설명·구성·순서를 수정한다. | FR-CURATION-001~002 |
| PR-CURATION-002 | 관리자는 `DRAFT`와 `PUBLISHED`를 전환한다. | FR-CURATION-003, BR-CURATION-001 |
| PR-CURATION-003 | 공개 사용자는 게시 큐레이션과 공개 맛집만 조회한다. | FR-CURATION-004, BR-CURATION-003 |
| PR-CURATION-004 | 게시 중 수정은 저장 성공 직후 공개 조회에 반영한다. | BR-CURATION-004 |

## 5. 정책과 예외

- 같은 큐레이션에 같은 맛집을 중복 배치할 수 없고 20개를 초과할 수 없다.
- 비공개·삭제 맛집은 공개 큐레이션에서 자동 숨기고 관리자 편집 화면에 경고한다.
- 메인 노출 최대 5개의 선택과 순서는 관리자 편집 계약에서 확정한다.
- Curation은 독립 게시 생명주기를 소유하며 Collection과 직접 의존하지 않는다.

## 6. 화면과 상태

- 공개 목록·상세: 로딩, 정상, 게시 없음, 구성 없음, 게시 종료
- 관리자 목록: 초안/게시 필터, 비공개 맛집 경고, 최근 수정
- 관리자 편집: 저장 중, 검증 실패, 구성 순서 변경, 게시·비게시 확인
- 모든 맛집 카드는 기존 공개 Restaurant 표시 계약을 재사용한다.

## 7. 개인정보·운영·비용

- 회원 개인정보는 처리하지 않는다.
- 편집·게시·비게시와 구성 변경은 관리자 감사 이력으로 남긴다.
- 운영자는 콘텐츠 품질과 비공개 맛집 경고를 지속적으로 처리한다.
- 외부 연동은 없고 관리자 UI와 운영 작업 비용이 발생한다.

## 8. 완료 조건

- [ ] FR-CURATION-001~004와 BR-CURATION-001~004의 권한·상태·상한 테스트가 통과한다.
- [ ] 게시·비게시와 게시 중 수정 결과가 공개 조회에 정확히 반영된다.
- [ ] 비공개·삭제 맛집 숨김, 관리자 경고와 감사 이력이 검증된다.
- [ ] WS-11 김인안 구현과 양성훈 기본 리뷰가 완료된다.
- [ ] API·데이터 계약, 메인 노출 순서와 일정이 승인된다.
