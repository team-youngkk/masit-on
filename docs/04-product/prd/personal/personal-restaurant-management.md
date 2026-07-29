---
id: PRD-PERSONAL-001
title: 개인 맛집 관리
status: draft
workstream: WS-06
owner: 박진영
reviewers:
  - 김인안
  - 양성훈
related_requirements:
  - FR-FAVORITE-001
  - FR-FAVORITE-002
  - FR-FAVORITE-003
  - FR-FAVORITE-004
  - FR-RECENT-001
  - FR-RECENT-002
  - FR-RECENT-003
  - FR-MEMBER-004
related_business_rules:
  - BR-FAVORITE-001
  - BR-FAVORITE-002
  - BR-FAVORITE-003
  - BR-FAVORITE-004
  - BR-RECENT-001
  - BR-RECENT-002
  - BR-RECENT-003
  - BR-RECENT-004
  - BR-RECENT-005
  - BR-MEMBER-004
related_nfr:
  - NFR-PERFORMANCE-004
  - NFR-PERFORMANCE-005
  - NFR-SECURITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-COMPATIBILITY-001
  - NFR-TEST-004
  - NFR-PRIVACY-003
  - NFR-PRIVACY-004
related_documents:
  - ../00-product-overview.md
  - ../../../00-overview/scope.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../02-analysis/first-expansion-workstreams.md
  - ../../../03-team/ownership.md
  - ../detail/restaurant-detail.md
  - ../discovery/restaurant-discovery.md
  - ../../user-flows/first-expansion-user-flows.md
  - ../../wireframes/first-expansion-wireframes.md
  - ../../traceability.md
---

# 개인 맛집 관리 PRD

## 1. 문서 정보

이 문서는 로그인 회원이 다시 찾고 싶은 맛집을 직접 찜하고, 이전에 본 맛집으로 되돌아가는 하나의 개인 재탐색 목표를 정의한다. 주 Workstream은 `WS-06 개인 맛집 관리`이며 최종 책임자는 박진영이다. API 경로·메서드·필드는 [개인 맛집 관리 API](../../../05-specs/api/personal/personal-restaurant-api.md), 화면 Route와 물리 데이터 구조는 후속 계약에서 확정한다.

## 2. 해결할 문제

사용자는 탐색 중 발견한 맛집을 나중에 다시 찾기 위해 별도로 기억하거나 기록해야 한다. 직접 보관한 맛집과 최근 확인한 맛집을 계정 단위로 제공하면 여러 기기에서도 탐색 맥락을 이어갈 수 있다. 동시에 다른 회원의 기록, 비공개·삭제 자원과 불필요한 행동 데이터가 노출되지 않아야 한다.

## 3. 목표

- 회원이 공개 맛집을 찜하거나 해제하고 목록·상세에서 같은 상태를 확인한다.
- 회원이 자신의 찜 목록을 최신 찜 순으로 다시 탐색한다.
- 공개 맛집 상세를 정상 조회한 이력을 중복 없이 갱신하고 최근 순으로 확인한다.
- 빈 목록, 페이지 경계와 비공개·삭제 맛집 상태를 예측 가능한 방식으로 처리한다.
- 최소한의 개인화 데이터만 저장하고 탈퇴 시 모두 정리한다.

## 4. 대상 사용자

- 탐색 중 발견한 맛집을 나중에 다시 확인하려는 로그인 회원
- 과거에 본 맛집을 이름을 기억하지 못해 최근 기록에서 찾으려는 로그인 회원

비로그인 사용자는 공개 목록과 상세를 계속 이용할 수 있지만 로컬 찜이나 최근 기록은 제공받지 않는다.

## 5. 사용자 여정

### 5.1 맛집을 찜하고 다시 찾기

1. 로그인 회원이 공개 맛집 목록 또는 상세에서 찜을 추가한다.
2. 시스템은 회원 식별자, 맛집 식별자와 찜 생성 시각만 저장한다.
3. 같은 맛집의 목록·상세 표시가 현재 회원에게 찜 상태로 일관되게 바뀐다.
4. 회원은 개인 찜 목록에서 최신 찜 순으로 맛집을 확인하고 상세로 이동한다.
5. 더 이상 보관하지 않을 맛집을 해제하면 목록·상세 상태와 찜 목록에 일관되게 반영된다.

이미 찜한 맛집의 추가와 이미 해제한 맛집의 해제는 현재 상태를 유지하는 성공이다. 중복·동시 추가에도 같은 회원·맛집의 논리 찜은 한 건만 존재한다.

### 5.2 최근 본 맛집으로 돌아가기

1. 로그인 회원이 공개 맛집 상세를 정상 조회한다.
2. 시스템은 회원 식별자, 맛집 식별자와 마지막 조회 시각만 기록한다.
3. 같은 맛집을 다시 조회하면 새 기록을 만들지 않고 마지막 조회 시각을 갱신한다.
4. 회원은 최근 본 목록에서 마지막 조회 시각 최신순으로 맛집을 확인하고 상세로 이동한다.
5. 시스템은 회원별 최신 50개만 유지하고 마지막 조회 후 30일이 지난 기록을 정리한다.
6. 회원은 원하지 않는 맛집의 최근 기록을 개별 삭제할 수 있다.

없는 맛집, 비공개·삭제 맛집 또는 실패한 상세 조회는 최근 기록을 만들지 않는다. 기록 저장 실패는 공개 맛집 상세 자체를 실패시키지 않는다.

### 5.3 목록에서 사라진 자원 처리

- 맛집이 비공개가 되면 기존 찜 관계는 보존하되 찜 목록에서 숨긴다. 재공개되면 보존된 관계를 다시 표시한다.
- 맛집이 삭제되면 해당 찜 관계를 삭제한다.
- 비공개·삭제 맛집은 최근 본 목록에서 숨기며 만료·상한 정리 대상에 포함한다.
- 회원 탈퇴 시 해당 회원의 모든 찜과 최근 본 기록을 삭제한다.
- 목록에 표시할 수 있는 자원이 없으면 오류 대신 빈 목록과 다음 탐색 행동을 안내한다.

## 6. 기능 범위

### 포함 범위

- 로그인 회원의 공개 맛집 찜 추가·해제
- 맛집 목록·상세의 현재 회원 찜 상태
- 최신 찜 순 찜 목록
- 공개 맛집 상세 정상 조회 시 최근 본 기록 생성·갱신
- 최신 조회 순 최근 본 목록
- 최근 본 맛집 기록의 개별 멱등 삭제
- 두 목록의 1-base 페이지네이션과 기본 페이지 크기 20개
- 최근 기록의 최신 50개 상한과 마지막 조회 후 30일 보존
- 다른 회원 접근 차단, 비공개·삭제 자원과 탈퇴 연계 처리

### 제외 범위

- 찜 폴더·컬렉션·메모·태그
- 찜 목록 공유, 사용자 지정 정렬과 일괄 편집
- 최근 본 맛집 전체 일괄 삭제
- 비로그인 브라우저의 로컬 찜과 최근 기록
- 기기별 최근 기록 구분
- 검색어·필터 조건과 조회 횟수 저장
- 개인화 추천, 인기 순위와 행동 분석

## 7. 개인정보와 소유권

- 찜에는 회원 식별자, 맛집 식별자와 찜 생성 시각만 저장한다.
- 최근 기록에는 회원 식별자, 맛집 식별자와 마지막 조회 시각만 저장한다.
- 찜 대상과 최근 조회 대상의 원문 조합을 로그나 분석 데이터에 기록하지 않는다.
- 회원은 자신의 찜과 최근 기록만 추가·해제·확인·조회·개별 삭제할 수 있다.
- 다른 회원의 식별자를 지정하거나 추측해 접근할 수 없으며, 다른 회원의 기록 존재 여부도 노출하지 않는다.
- 회원 탈퇴가 완료되면 찜과 최근 기록의 잔존 데이터는 0건이어야 한다.

## 8. 화면과 상태

화면의 명칭과 정보 구조만 이 PRD에서 확정하며 실제 Route는 프론트 계약에서 확정한다.

| 화면·영역 | 정상 상태 | 빈 상태 | 오류·비공개·삭제 상태 |
|---|---|---|---|
| 맛집 목록의 찜 | 로그인 회원에게 각 맛집의 찜·미찜 상태와 변경 동작 표시 | 공개 맛집 결과가 없으면 기존 탐색 빈 상태 사용 | 인증 만료 시 재인증 안내, 저장 실패 시 이전 확정 상태를 유지하고 재시도 제공 |
| 맛집 상세의 찜 | 현재 상태를 표시하고 추가·해제 후 즉시 일관되게 반영 | 해당 없음 | 맛집이 비공개·삭제되면 기존 상세 계약에 따라 찾을 수 없음으로 처리 |
| 내 찜 목록 | 공개 맛집을 최신 찜 순으로 표시하고 상세 이동 제공 | 찜이 없거나 페이지가 비면 정상 빈 목록과 맛집 탐색 이동 제공 | 비공개 맛집은 숨기고 삭제 맛집 관계는 제거, 다른 회원 접근은 거부 |
| 최근 본 맛집 | 30일 이내 최신 50개를 최근 조회 순으로 표시하고 상세 이동·개별 삭제 제공 | 기록이 없거나 페이지가 비면 정상 빈 목록과 맛집 탐색 이동 제공 | 비공개·삭제·만료 자원은 숨기고 다른 회원 접근은 거부 |
| 인증 만료 | 재발급 성공 후 현재 목록·찜 동작을 이어감 | 재인증 진행 중 | 재발급 불가 시 로그인 안내, 공개 탐색으로 이동 가능 |

찜과 최근 목록의 빈 상태는 실패로 표현하지 않는다. 페이지 범위 밖 결과도 계약에 따른 빈 목록으로 제공하며, 잘못된 페이지 형식·허용되지 않은 크기는 수정 가능한 요청 오류로 구분한다.

## 9. 제품 요구사항

| PRD 요구사항 | 제품 동작 | 관련 기능 요구사항 | 중요도 | 상태 |
|---|---|---|---|---|
| PR-PERSONAL-001 | 공개 맛집을 회원별로 중복 없이 찜한다. | [FR-FAVORITE-001](../../../01-requirements/functional-requirements.md#fr-favorite-001-맛집-찜-추가) | Must | 확정 |
| PR-PERSONAL-002 | 현재 회원의 찜을 멱등하게 해제한다. | [FR-FAVORITE-002](../../../01-requirements/functional-requirements.md#fr-favorite-002-맛집-찜-해제) | Must | 확정 |
| PR-PERSONAL-003 | 맛집 목록과 상세에서 현재 회원의 찜 상태를 일관되게 표시한다. | [FR-FAVORITE-003](../../../01-requirements/functional-requirements.md#fr-favorite-003-맛집별-현재-회원-찜-상태-확인) | Must | 확정 |
| PR-PERSONAL-004 | 자신의 공개 맛집 찜을 최신순 페이지 목록으로 조회한다. | [FR-FAVORITE-004](../../../01-requirements/functional-requirements.md#fr-favorite-004-찜-목록-조회) | Must | 확정 |
| PR-PERSONAL-005 | 공개 맛집 상세의 정상 조회를 중복 없는 최근 기록으로 갱신한다. | [FR-RECENT-001](../../../01-requirements/functional-requirements.md#fr-recent-001-최근-본-맛집-기록) | Must | 확정 |
| PR-PERSONAL-006 | 자신의 유효한 최근 기록을 최신순 페이지 목록으로 조회한다. | [FR-RECENT-002](../../../01-requirements/functional-requirements.md#fr-recent-002-최근-본-맛집-목록-조회) | Must | 확정 |
| PR-PERSONAL-007 | 회원 탈퇴 시 모든 찜과 최근 기록을 삭제한다. | [FR-MEMBER-004](../../../01-requirements/functional-requirements.md#fr-member-004-회원-탈퇴) | Must | 확정 |
| PR-PERSONAL-008 | 자신의 최근 기록을 맛집별로 멱등하게 개별 삭제한다. | [FR-RECENT-003](../../../01-requirements/functional-requirements.md#fr-recent-003-최근-본-맛집-개별-삭제) | Must | 확정 |

## 10. 비즈니스 규칙

- 찜의 고유성·멱등성·회원 소유권·정렬·페이지와 맛집 상태 처리는 [BR-FAVORITE-001](../../../01-requirements/business-rules.md#br-favorite-001-회원별-찜의-고유성과-멱등성)~[BR-FAVORITE-004](../../../01-requirements/business-rules.md#br-favorite-004-맛집-상태에-따른-찜-보존과-정리)를 따른다.
- 최근 기록의 생성·갱신·상한·보존·정렬·페이지·공개 범위와 개별 삭제는 [BR-RECENT-001](../../../01-requirements/business-rules.md#br-recent-001-최근-본-맛집의-기록과-갱신)~[BR-RECENT-005](../../../01-requirements/business-rules.md#br-recent-005-최근-기록-개별-삭제의-멱등성과-소유권)를 따른다.
- 탈퇴 연계 삭제와 재가입 조건은 [BR-MEMBER-004](../../../01-requirements/business-rules.md#br-member-004-회원-탈퇴와-재가입)를 따른다.
- 찜 또는 최근 기록의 주 정렬 시각이 같은 항목에 적용할 안정적인 보조 순서는 API 계약에서 확정한다.

## 11. 비기능 요구사항

- 페이지 크기와 조회량은 [NFR-PERFORMANCE-004](../../../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한)을 따른다.
- 찜·최근 목록의 애플리케이션 서버 내부 처리 시간은 정상 부하에서 p95 1초 이하이며 [NFR-PERFORMANCE-005](../../../01-requirements/non-functional-requirements.md#nfr-performance-005-개인화지도유튜버-상세-조회-응답-시간)로 검증한다.
- 인증 저장소 장애 중 개인화 기능은 [NFR-SECURITY-004](../../../01-requirements/non-functional-requirements.md#nfr-security-004-회원-자격-증명과-token-보호)에 따라 fail-closed로 처리하며 공개 목록·상세는 계속 제공한다.
- 오류 격리와 사용자 메시지는 [NFR-RELIABILITY-001](../../../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책), [NFR-RELIABILITY-003](../../../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리)을 따른다.
- 개인정보 생명주기와 행동 데이터 최소화는 [NFR-PRIVACY-003](../../../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-개인정보-최소-수집과-생명주기), [NFR-PRIVACY-004](../../../01-requirements/non-functional-requirements.md#nfr-privacy-004-위치와-행동-데이터-최소화)를 따른다.
- 지원 브라우저와 인수 검증은 [NFR-COMPATIBILITY-001](../../../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성), [NFR-TEST-004](../../../01-requirements/non-functional-requirements.md#nfr-test-004-1차-확장-보안통합브라우저-검증)를 따른다.

## 12. 제품 성공 기준

- 로그인 회원이 목록과 상세 어느 곳에서든 찜 상태를 같은 결과로 확인하고 변경할 수 있다.
- 반복·동시 찜 요청에도 회원·맛집별 논리 찜이 한 건만 존재한다.
- 회원이 찜 목록과 최근 본 목록에서 목적 맛집의 상세로 다시 이동할 수 있다.
- 최근 본 맛집은 재조회 시 맨 앞으로 이동하고 중복 표시되지 않으며 최신 50개·30일 규칙을 지킨다.
- 최근 기록 개별 삭제는 반복·동시 요청에도 같은 완료 상태가 되고 다른 회원 기록에 영향을 주지 않는다.
- 비공개·삭제 맛집과 다른 회원의 데이터가 개인 목록에 노출되지 않는다.
- 빈 목록과 페이지 경계가 오류 없이 명확한 다음 행동과 함께 표시된다.
- 탈퇴 회원의 찜과 최근 기록이 남지 않는다.

초기 제품 기준은 이용량 목표가 아니라 위 인수 시나리오 통과율 100%와 정상 부하 성능 기준 충족으로 판단한다. 찜 재방문율 같은 행동 지표는 개인정보 최소화 원칙을 훼손하지 않는 측정 계약을 별도로 합의한 뒤 사용한다.

## 13. Workstream 및 협업

- 주 Workstream: `WS-06 개인 맛집 관리`
- 최종 책임자: 박진영
- 기본 리뷰어: 김인안
- 선행 Workstream: `WS-05 사용자 계정·인증`
- 협업: 기존 맛집 목록·상세 Workstream과 찜 상태·최근 기록 생성 시점 통합
- 선행 계약: 일반 회원 인증, 페이지 응답, 공개·비공개·삭제 맛집 생명주기, API·데이터 계약

## 14. 완료 기준

- 8개 관련 FR과 10개 관련 BR이 구현 및 추적성 문서에 연결된다.
- 목록·상세의 찜 추가·해제·상태와 내 찜 목록의 최신순·페이지·빈 상태가 브라우저에서 통과한다.
- 중복·동시 찜 요청의 고유성과 다른 회원 접근 차단이 통합·보안 테스트로 검증된다.
- 최근 기록의 정상 상세 조회 생성, 재조회 갱신, 실패 조회 미생성, 최신 50개와 30일 만료가 검증된다.
- 최근 기록 개별 삭제의 본인 소유권과 반복·동시 요청 멱등성이 검증된다.
- 비공개·삭제 맛집의 표시·보존·삭제 정책과 탈퇴 연계 삭제가 검증된다.
- PostgreSQL·Redis 장애 중 개인화 기능의 안전한 실패와 공개 조회 격리가 검증된다.
- API·데이터 계약, 사용자 흐름과 목록·상세 와이어프레임이 이 PRD의 범위 및 상태 계약과 일치한다.

## 15. 리스크와 후속 결정

- 찜 상태를 기존 공개 목록·상세에 결합할 때 비로그인 성능과 캐시 경계를 훼손하지 않아야 한다.
- 최근 기록 저장 실패는 상세 조회를 막지 않아야 하므로 저장 실패 관측과 재시도 여부를 후속 설계에서 명확히 해야 한다.
- 비공개에서 재공개된 맛집의 찜 복원과 삭제 맛집 정리는 생명주기 이벤트의 일관성 검증이 필요하다.
- 화면 Route와 물리 데이터 구조는 후속 명세에서 확정한다. API 경로·필드·오류 코드와 같은 시각의 보조 정렬은 개인 맛집 관리 API를 따른다.

## 16. 관련 문서

- [개인 맛집 관리 API](../../../05-specs/api/personal/personal-restaurant-api.md)

- [1차 확장 범위](../../../00-overview/scope.md#51-1차-확장-확정-범위)
- [기능 요구사항](../../../01-requirements/functional-requirements.md)
- [비즈니스 규칙](../../../01-requirements/business-rules.md)
- [비기능 요구사항](../../../01-requirements/non-functional-requirements.md)
- [맛집 탐색 PRD](../discovery/restaurant-discovery.md)
- [맛집 상세 PRD](../detail/restaurant-detail.md)
- [1차 확장 Workstream](../../../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리)
- [1차 확장 사용자 흐름](../../user-flows/first-expansion-user-flows.md#5-내-찜-목록)
- [1차 확장 와이어프레임](../../wireframes/first-expansion-wireframes.md#4-내-찜-목록)
- [제품 추적표](../../traceability.md)
