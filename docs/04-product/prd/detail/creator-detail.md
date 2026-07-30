---
id: PRD-DETAIL-002
title: 유튜버 상세
status: draft
workstream: WS-08
owner: 이우람
reviewers:
  - 박진영
  - 양성훈
related_requirements:
  - FR-CREATOR-004
  - FR-CREATOR-005
  - FR-CREATOR-006
related_business_rules:
  - BR-CREATOR-004
  - BR-CREATOR-007
  - BR-CREATOR-008
  - BR-CREATOR-009
  - BR-CREATOR-010
  - BR-CREATOR-011
  - BR-CREATOR-012
  - BR-VISIT-005
  - BR-PUBLICATION-004
  - BR-PUBLICATION-005
related_nfr:
  - NFR-PERFORMANCE-005
  - NFR-INTEGRITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-EXTERNAL-001
  - NFR-EXTERNAL-002
  - NFR-COMPATIBILITY-001
  - NFR-COMPATIBILITY-002
  - NFR-COMPATIBILITY-003
  - NFR-TEST-004
related_documents:
  - ../00-product-overview.md
  - restaurant-detail.md
  - ../discovery/creator-discovery.md
  - ../../../00-overview/scope.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../02-analysis/domain-boundaries.md
  - ../../../02-analysis/first-expansion-workstreams.md
  - ../../user-flows/first-expansion-user-flows.md
  - ../../wireframes/first-expansion-wireframes.md
  - ../../traceability.md
---

# 유튜버 상세 PRD

## 1. 문서 정보

특정 유튜버의 공개 채널 정보, 실제 방문이 확인된 맛집과 근거 영상을 한 화면에서 탐색하는 1차 확장 제품 계약이다. 화면 Route는 `/creators/{creatorId}`이며, 주 Workstream은 WS-08이고 이우람이 최종 책임을 맡는다.

API 경로·응답 필드, 선택 표시 정보의 누락 표현과 동일 등록 시각의 안정적인 보조 정렬은 [유튜버 상세 API](../../../05-specs/api/detail/creator-detail-api.md)에서 확정한다. 물리 데이터 구조는 후속 데이터 계약에서 정한다.

## 2. 해결할 문제

사용자는 특정 유튜버가 실제로 방문한 맛집과 그 근거 영상을 확인하려면 맛집별 상세와 외부 채널을 오가야 한다. 채널의 현재 표시 정보, 검증된 방문 맛집과 근거 영상을 한곳에서 제공하면 사용자는 유튜버를 출발점으로 신뢰할 수 있는 맛집 후보를 빠르게 탐색할 수 있다.

## 3. 목표

- 공개·이용 가능한 유튜버의 저장된 채널 표시 정보를 제공한다.
- 유효·공개 방문 관계에 근거한 방문 맛집과 근거 영상을 각각 중복 없이 제공한다.
- 두 연결 목록이 독립된 페이지 상태를 가지며 다른 목록의 이동에 영향받지 않는다.
- 비공개·삭제·외부 이용 불가 상태를 일반 사용자에게 노출하지 않는다.
- 사용자 조회 중 YouTube API를 호출하지 않고 외부 장애와 저장된 상세 조회를 분리한다.

## 4. 대상 사용자

- 특정 유튜버가 실제로 방문한 맛집을 찾으려는 일반 사용자
- 방문 근거가 되는 원본 영상을 확인하려는 일반 사용자
- 로그인 여부와 관계없이 공개 유튜버 정보를 탐색하는 사용자

## 5. 사용자 여정

### 5.1 채널 정보 확인

1. 사용자가 유튜버 탐색 결과나 맛집의 방문 채널 정보에서 유튜버 상세로 이동한다.
2. 시스템은 유튜버가 공개 상태이고 외부 채널을 이용할 수 있는지 확인한다.
3. 조건을 충족하면 마지막으로 관리자가 확인해 저장한 현재 채널명, 프로필 이미지, 채널 소개, handle과 YouTube 채널 링크를 표시한다.
4. 사용자는 원본 채널로 이동하거나 방문 맛집·근거 영상 탐색을 이어 간다.

### 5.2 방문 맛집 탐색

1. 사용자가 방문 맛집 영역을 확인한다.
2. 시스템은 유효·공개 방문 관계에 연결된 공개 맛집을 맛집별로 중복 제거한다.
3. 가장 최근 유효 관계의 등록 시각을 기준으로 최신순 정렬하고 1-base, 기본 20개 페이지로 제공한다.
4. 사용자는 맛집 항목을 선택해 맛집 상세로 이동한다.
5. 방문 맛집 페이지 이동은 근거 영상 페이지에 영향을 주지 않는다.

### 5.3 근거 영상 탐색

1. 사용자가 근거 영상 영역을 확인한다.
2. 시스템은 유효·공개 방문 관계에 연결된 공개·이용 가능 영상을 영상별로 중복 제거한다.
3. 가장 최근 유효 관계의 등록 시각을 기준으로 최신순 정렬하고 1-base, 기본 20개 페이지로 제공한다.
4. 사용자는 저장된 영상 표시 정보에서 YouTube 원본 링크로 이동한다.
5. 근거 영상 페이지 이동은 방문 맛집 페이지에 영향을 주지 않는다.

## 6. 포함 범위

- `/creators/{creatorId}` 상세 화면
- 마지막으로 관리자가 확인해 저장한 현재 채널명, 프로필 이미지, 채널 소개, handle과 YouTube 채널 링크
- 유효·공개 Visit 관계에 연결된 공개 방문 맛집 목록
- 유효·공개 Visit 관계에 연결된 공개·이용 가능 근거 영상 목록
- 맛집별·영상별 중복 제거와 관계 등록 최신순 정렬
- 방문 맛집과 근거 영상의 서로 독립된 1-base 페이지 및 기본 페이지 크기 20개
- 방문 맛집 상세와 YouTube 원본 채널·영상 링크 이동
- 두 목록 각각의 정상·빈·페이지 경계 상태
- 공개·비공개·삭제·외부 이용 불가 상태에 따른 노출 제어
- YouTube 장애 중 저장된 공개 상세와 연결 목록 조회

## 7. 제외 범위

- 구독자 수, 조회 수, 인기 순위와 통계
- YouTube 채널의 전체 영상 목록
- 유튜버 팔로우·구독·알림과 사용자별 유튜버 조회 기록
- 사용자 조회 시 실시간 YouTube API 호출
- 표시 정보의 자동 주기 동기화
- 방문 여부의 자동 판정과 관리자 확인 없는 관계 생성
- 별도 영상 상세·내부 영상 재생·영상 원본 저장 또는 재배포
- 방문 맛집과 근거 영상을 하나의 공통 페이지 상태로 결합

## 8. 전제 조건과 의존성

- 관리자가 유튜버의 공개 채널 표시 정보를 확인해 저장해야 한다.
- Creator의 공개·삭제·외부 이용 가능 상태를 판정할 수 있어야 한다.
- Visit가 관계의 실제 방문 근거·유효성과 중복 제거 기준을 제공해야 한다.
- Restaurant와 Video가 각 대상의 공개·삭제·외부 이용 가능 상태와 표시 정보를 제공해야 한다.
- 표시 정보의 저장 항목, 선택 필드 표현, 안정적인 보조 정렬과 목록별 페이지 계약은 후속 API·데이터 계약에서 PRD와 일치하도록 확정한다.

## 9. 화면 및 상태

| 상태 | 채널 정보 | 방문 맛집 영역 | 근거 영상 영역 | 사용자 행동 |
|---|---|---|---|---|
| 초기 로딩 | 채널 정보 자리 표시 | 목록 로딩 | 목록 로딩 | 완료 대기 |
| 정상 | 저장된 공개 표시 정보 | 최신순 맛집 목록 | 최신순 영상 목록 | 채널·맛집·영상 링크 이동, 독립 페이지 이동 |
| 방문 맛집 없음 | 정상 표시 | 방문 맛집이 없다는 빈 상태 | 정상 또는 빈 상태 유지 | 근거 영상 탐색, 다른 유튜버 이동 |
| 근거 영상 없음 | 정상 표시 | 정상 또는 빈 상태 유지 | 근거 영상이 없다는 빈 상태 | 방문 맛집 탐색, 채널 이동 |
| 두 목록 모두 비어 있음 | 정상 표시 | 독립 빈 상태 | 독립 빈 상태 | 원본 채널 이동, 다른 유튜버 탐색 |
| 범위 밖 페이지 | 정상 표시 | 해당 목록의 빈 페이지 | 다른 목록 상태 유지 | 유효 페이지로 이동 |
| 관계 대상 비공개·삭제·무효 | 정상 표시 | 해당 맛집만 제외 | 해당 영상만 제외 | 남은 유효 결과 탐색 |
| 유튜버 없음·비공개·삭제·외부 이용 불가 | 상세 미표시 | 미표시 | 미표시 | 동일한 찾을 수 없음 안내 후 다른 탐색으로 이동 |
| YouTube 장애 | 저장된 정보 유지 | 저장된 목록 유지 | 저장된 목록과 원본 링크 유지 | 내부 탐색 계속, 외부 링크 실패 가능성 인지 |
| 내부 일시적 조회 오류 | 오류 원인을 노출하지 않는 안내 | 재시도 상태 | 재시도 상태 | 재시도, 이전 탐색으로 이동 |

없는 유튜버와 비공개·삭제·외부 이용 불가 유튜버는 모두 같은 찾을 수 없음 결과로 처리한다. 연결 목록에서 제외된 자원의 과거 존재나 상태는 사용자에게 따로 노출하지 않는다.

## 10. 제품 요구사항

| PRD 요구사항 | 제품 동작 | 관련 기능 요구사항 | 중요도 | 상태 |
|---|---|---|---|---|
| PR-CREATOR-DETAIL-001 | 공개·이용 가능한 유튜버의 저장된 채널 정보를 제공한다. | [FR-CREATOR-004](../../../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회) | Must | 확정 |
| PR-CREATOR-DETAIL-002 | 없는·비공개·삭제·외부 이용 불가 유튜버를 같은 찾을 수 없음으로 처리한다. | [FR-CREATOR-004](../../../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회) | Must | 확정 |
| PR-CREATOR-DETAIL-003 | 방문 맛집을 유효 관계 기준 최신순으로 중복 없이 제공한다. | [FR-CREATOR-005](../../../01-requirements/functional-requirements.md#fr-creator-005-유튜버의-방문-맛집-목록-조회) | Must | 확정 |
| PR-CREATOR-DETAIL-004 | 근거 영상을 유효 관계 기준 최신순으로 중복 없이 제공한다. | [FR-CREATOR-006](../../../01-requirements/functional-requirements.md#fr-creator-006-유튜버의-근거-영상-목록-조회) | Must | 확정 |
| PR-CREATOR-DETAIL-005 | 두 목록에 독립된 1-base, 기본 20개 페이지를 적용한다. | [FR-CREATOR-005](../../../01-requirements/functional-requirements.md#fr-creator-005-유튜버의-방문-맛집-목록-조회), [FR-CREATOR-006](../../../01-requirements/functional-requirements.md#fr-creator-006-유튜버의-근거-영상-목록-조회) | Must | 확정 |
| PR-CREATOR-DETAIL-006 | 사용자 조회 중 YouTube API를 호출하지 않고 저장된 상세와 목록을 제공한다. | [FR-CREATOR-004](../../../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회), [FR-CREATOR-006](../../../01-requirements/functional-requirements.md#fr-creator-006-유튜버의-근거-영상-목록-조회) | Must | 확정 |

## 11. 비즈니스 규칙

- 기존 유튜버 표시와 외부 이용 불가 처리는 [BR-CREATOR-004](../../../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보), [BR-CREATOR-007](../../../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리)을 따른다.
- 상세 공개·찾을 수 없음과 저장된 표시 정보는 [BR-CREATOR-008](../../../01-requirements/business-rules.md#br-creator-008-유튜버-상세-공개와-찾을-수-없음-처리), [BR-CREATOR-009](../../../01-requirements/business-rules.md#br-creator-009-유튜버-상세-표시-정보)를 따른다.
- 방문 맛집과 근거 영상의 고유성·정렬은 [BR-CREATOR-010](../../../01-requirements/business-rules.md#br-creator-010-유튜버-방문-맛집의-고유성과-정렬), [BR-CREATOR-011](../../../01-requirements/business-rules.md#br-creator-011-유튜버-근거-영상의-고유성과-정렬)을 따른다.
- 독립 페이지는 [BR-CREATOR-012](../../../01-requirements/business-rules.md#br-creator-012-유튜버-상세-연결-목록의-페이지)을 따른다.
- 관계와 대상의 공개 유효성은 [BR-VISIT-005](../../../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성), [BR-PUBLICATION-004](../../../01-requirements/business-rules.md#br-publication-004-유튜버-상태와-관계-노출), [BR-PUBLICATION-005](../../../01-requirements/business-rules.md#br-publication-005-영상-상태와-관계-노출)을 따른다.

## 12. 품질 요구사항

- 상세 기본 정보와 각 연결 목록 조회는 [NFR-PERFORMANCE-005](../../../01-requirements/non-functional-requirements.md#nfr-performance-005-개인화지도유튜버-상세-조회-응답-시간)의 정상 부하와 각 조회 p95 1.5초 이하 기준으로 검증한다.
- 외부 링크와 저장된 내부 정보의 분리는 [NFR-INTEGRITY-004](../../../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리), [NFR-EXTERNAL-001](../../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)을 따른다.
- YouTube 장애와 외부 변경 격리는 [NFR-EXTERNAL-002](../../../01-requirements/non-functional-requirements.md#nfr-external-002-외부-호출-실패와-변경-격리), [NFR-RELIABILITY-001](../../../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책)을 따른다.
- 찾을 수 없음·빈 상태·내부 오류의 사용자 표현은 [NFR-RELIABILITY-003](../../../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리)을 따른다.
- PC·모바일, 문자·응답 형식과 페이지별 응답 크기는 [NFR-COMPATIBILITY-001](../../../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성)~[NFR-COMPATIBILITY-003](../../../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기)을 따른다.
- 정상·빈·오류·페이지·상태 조합과 외부 장애는 [NFR-TEST-004](../../../01-requirements/non-functional-requirements.md#nfr-test-004-1차-확장-보안통합브라우저-검증)에 연결해 검증한다.

## 13. 제품 성공 기준

- 대표 사용자 검증에서 특정 유튜버의 저장된 채널 정보, 방문 맛집과 근거 영상 중 원하는 정보를 한 상세 흐름에서 찾을 수 있다.
- 방문 맛집과 근거 영상이 유효 관계 기준으로 중복 없이 최신순 제공된다.
- 한 목록의 페이지 이동이 다른 목록의 페이지·결과·선택 상태에 영향을 주지 않는다.
- 빈 목록과 찾을 수 없음 상태에서 사용자가 다른 탐색 또는 원본 채널 이동 같은 다음 행동을 이해한다.
- YouTube 장애 중에도 저장된 공개 상세와 연결 목록 조회 성공률이 내부 서비스 정상 범위를 유지한다.

제품 가치 지표의 표본 크기와 목표 성공률은 출시 전 측정 계획에서 확정하며, 시스템 품질 수치는 관련 NFR을 그대로 적용한다.

## 14. Workstream 및 책임

- 주 Workstream: WS-08 유튜버 상세
- 최종 책임자: 이우람
- 기본 리뷰어: 박진영
- 협업: 박진영(맛집 상세 표시 정보), 양성훈(탐색 진입과 목록 표시), 김인안(관리자 채널 정보 등록·갱신)

Creator는 상세 진입 여부와 채널 표시 정보를 소유한다. Visit는 유효 관계와 관계 기반 목록의 중복 제거·정렬을 판정하며, Restaurant와 Video는 각 목록의 공개 표시 정보를 제공한다.

## 15. 완료 기준

- [FR-CREATOR-004](../../../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회)~[FR-CREATOR-006](../../../01-requirements/functional-requirements.md#fr-creator-006-유튜버의-근거-영상-목록-조회)과 관련 BR·NFR이 구현 및 테스트 결과로 추적된다.
- 저장된 채널명, 프로필 이미지, 소개, handle과 YouTube 채널 링크가 공개 상세에 표시된다.
- 없는·비공개·삭제·외부 이용 불가 유튜버가 동일한 찾을 수 없음으로 처리된다.
- 방문 맛집과 근거 영상의 유효성, 공개 상태, 중복 제거와 관계 등록 최신순을 검증한다.
- 두 목록의 독립 페이지, 기본 크기 20개, 빈·첫·마지막·범위 밖 페이지를 검증한다.
- 관계 대상 하나가 무효일 때 해당 맛집 또는 영상만 제외되고 나머지 상세가 유지된다.
- 사용자 조회 중 YouTube API가 호출되지 않으며 YouTube 장애에도 저장된 상세와 목록을 조회한다.
- 지원 PC·모바일 브라우저에서 채널·맛집·영상 탐색 흐름이 동작한다.
- 후속 API·데이터·와이어프레임과 제품 추적성 문서가 실제 동작과 일치한다.

## 16. 리스크

- 저장된 채널 표시 정보가 외부 채널 변경과 시차를 가질 수 있다.
- 관계가 많은 유튜버는 중복 제거·정렬·독립 페이지 조회 비용이 커질 수 있다.
- Creator·Visit·Restaurant·Video 상태 조합이 누락되면 비공개 또는 이용 불가 정보가 노출될 수 있다.
- 선택 표시 정보와 안정적 보조 정렬의 API 계약이 늦어지면 화면 상태와 테스트가 흔들릴 수 있다.

## 17. 관련 문서

- [유튜버 상세 API](../../../05-specs/api/detail/creator-detail-api.md)

- [1차 확장 범위](../../../00-overview/scope.md#51-1차-확장)
- [기능 요구사항](../../../01-requirements/functional-requirements.md#fr-creator-004-유튜버-상세-정보-조회)
- [비즈니스 규칙](../../../01-requirements/business-rules.md#br-creator-008-유튜버-상세-공개와-찾을-수-없음-처리)
- [비기능 요구사항](../../../01-requirements/non-functional-requirements.md)
- [도메인 경계](../../../02-analysis/domain-boundaries.md)
- [맛집 상세 PRD](restaurant-detail.md)
- [유튜버 기반 탐색 PRD](../discovery/creator-discovery.md)
- [1차 확장 Workstream](../../../02-analysis/first-expansion-workstreams.md#7-ws-08-유튜버-상세)
- [1차 확장 사용자 흐름](../../user-flows/first-expansion-user-flows.md#8-유튜버-상세)
- [1차 확장 와이어프레임](../../wireframes/first-expansion-wireframes.md#7-유튜버-상세)
- [제품 추적성](../../traceability.md)
