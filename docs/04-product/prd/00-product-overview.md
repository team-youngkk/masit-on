---
id: PRD-PRODUCT-001
title: 맛잇온 제품 개요
status: draft
owner: 이우람
reviewers:
  - 양성훈
  - 박진영
  - 김인안
related_documents:
  1: ../../00-overview/service-overview.md
  2: ../../00-overview/scope.md
  3: ../../00-overview/glossary.md
  4: ../../01-requirements/functional-requirements.md
  5: ../../01-requirements/business-rules.md
  6: ../../01-requirements/non-functional-requirements.md
  7: ../../02-analysis/mvp-workstreams.md
  8: ../traceability.md
  9: discovery/restaurant-discovery.md
  10: discovery/creator-discovery.md
  11: detail/restaurant-detail.md
  12: admin/admin-data-management.md
  13: ../../01-requirements/requirements-review.md
  14: ../../03-team/ownership.md
---

# 맛잇온 제품 개요 PRD

## 1. 문서 목적

맛잇온 1차 MVP의 제품 방향, 공통 범위·원칙·품질 목표와 기능 PRD 경계를 정의한다. 기능별 상세 동작은 각 기능 PRD가 소유한다.

## 2. 제품 한 줄 정의

맛잇온은 유튜버가 실제로 방문한 맛집을 지역, 음식 카테고리와 유튜버 기준으로 탐색하고 방문 근거 원본 영상까지 확인할 수 있는 웹 서비스다.

## 3. 문제 정의

YouTube 맛집 정보는 영상과 채널에 흩어져 있어 사용자가 식당, 위치, 음식 종류와 방문 근거를 직접 모아야 한다. 지역·음식 종류별 비교나 특정 유튜버의 방문 맛집 확인도 채널 영상을 하나씩 찾아야 해 어렵다.

## 4. 목표 사용자

- 계정 없이 방문할 맛집을 찾는 일반 사용자
- 특정 지역·음식 종류·유튜버 기준으로 후보를 좁히려는 사용자
- 맛집·채널·영상과 방문 관계를 검증해 등록하는 관리자

## 5. 사용자 문제

- 여러 원본 콘텐츠에 흩어진 맛집 정보를 한 흐름에서 탐색하기 어렵다.
- 실제 방문 근거가 있는 정보와 단순 언급을 구분하기 어렵다.
- 맛집 기본 정보와 방문 유튜버·관련 영상을 함께 확인하기 어렵다.
- 관리자는 서로 참조하는 데이터를 일관된 순서와 검증 기준으로 등록해야 한다.

## 6. 제품 목표

- 공개 맛집을 이름·서울 자치구·대표 음식 카테고리·유튜버 조건으로 찾을 수 있게 한다.
- 맛집 기본 정보와 검증된 방문 유튜버·근거 영상을 한 상세 흐름에서 제공한다.
- 관리자가 검증한 데이터와 관계만 사용자 조회에 반영한다.
- PC와 모바일 브라우저에서 핵심 탐색 흐름을 사용할 수 있게 한다.

## 7. 비목표

회원·개인화, 지도·위치 탐색, 유튜버 상세, 사용자 제보, 추천·큐레이션, 자연어·AI 검색, 영상 원본 저장, 예약·결제는 1차 MVP의 목표가 아니다. 상세 목록은 [#2 프로젝트 범위](../../00-overview/scope.md)를 따른다.

## 8. 핵심 가치 제안

- 흩어진 맛집·방문 정보를 맛집 중심으로 구조화한다.
- 검증된 영상 근거를 통해 방문 정보의 신뢰 근거를 제공한다.
- 탐색 조건과 상세 콘텐츠를 연결해 후보 발견부터 원본 확인까지 이어 준다.

## 9. 상위 사용자 흐름

### 일반 사용자

1. 공개 맛집 목록을 연다.
2. 이름 또는 지역·음식 카테고리·유튜버 조건으로 결과를 좁힌다.
3. 맛집을 선택해 기본 정보, 방문 유튜버와 관련 영상을 확인한다.
4. 필요하면 외부 원본 영상으로 이동한다.

### 관리자

1. 사전 발급 계정으로 등록 기능에 접근한다.
2. 검증한 맛집·유튜버·영상을 등록한다.
3. 실제 방문 영상을 근거로 방문 관계를 등록한다.
4. 등록 결과가 사용자 탐색과 상세에 반영됐는지 확인한다.

## 10. 1차 MVP 범위

| 기능 | 목적 | 담당 PRD |
|---|---|---|
| 맛집 탐색 | 공개 맛집을 검색·필터·페이지 단위로 탐색 | [#9 맛집 탐색](discovery/restaurant-discovery.md) |
| 유튜버 기반 탐색 | 유효 방문 관계를 기준으로 특정 유튜버의 맛집 탐색 | [#10 유튜버 기반 탐색](discovery/creator-discovery.md) |
| 맛집 상세 및 콘텐츠 | 기본 정보와 방문 유튜버·영상을 한 흐름에서 확인 | [#11 맛집 상세](detail/restaurant-detail.md) |
| 관리자 데이터 등록 | 기본 데이터와 방문 관계를 검증·등록하고 조회에 반영 | [#12 관리자 데이터 등록](admin/admin-data-management.md) |

## 11. 공통 제품 요구사항

- 일반 사용자 공개 조회는 로그인 없이 제공한다.
- 공개 조건을 충족한 데이터와 유효 관계만 노출한다.
- 영상 연결이 없는 공개 맛집도 목록과 상세 기본 조회를 제공한다.
- 빈 조회 결과는 정상 결과로 처리한다.
- 기능별 제품 동작은 [#4 기능 요구사항](../../01-requirements/functional-requirements.md)을 따른다.

## 12. 공통 비즈니스 원칙

- 방문 관계는 실제 방문을 확인할 수 있는 등록 영상에 근거한다.
- 사용자는 데이터를 직접 등록하지 않으며 관리자가 사실과 참조를 검증한다.
- YouTube 원본은 저장·재배포하지 않고 링크와 필요한 메타데이터만 관리한다.
- 관리자 등록 시 YouTube API로 채널·영상 정보를 조회하고 관리자가 확인한 결과를 저장한다. 일반 사용자 조회는 앱 서버의 저장 정보를 사용한다.
- 비공개·삭제·무효 대상의 노출은 [#5 BR-PUBLICATION-001](../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위)~[#5 BR-PUBLICATION-008](../../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성)을 따른다.
- 공통 규칙의 원문과 우선순위는 [#5 비즈니스 규칙](../../01-requirements/business-rules.md)이 소유한다.

## 13. 공통 품질 목표

- 일반 조회·검색·등록의 목표 시간은 [#6 NFR-PERFORMANCE-001](../../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간)~[#6 NFR-PERFORMANCE-004](../../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한)에 따라 측정하며 미확정 수치는 확정 전까지 팀 결정 필요로 둔다.
- 공개 조회와 관리자 접근을 분리하고 입력·비밀정보를 보호한다([#6 NFR-SECURITY-001](../../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제)~[#6 NFR-SECURITY-003](../../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호)).
- 참조·중복·등록 원자성과 공개 상태 일관성을 보장한다([#6 NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[#6 NFR-INTEGRITY-004](../../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리)).
- 외부 링크 장애를 내부 기본 정보 조회와 격리한다([#6 NFR-RELIABILITY-001](../../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책), [#6 NFR-EXTERNAL-001](../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)~[#6 NFR-EXTERNAL-003](../../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보)).
- 웹·모바일 브라우저의 핵심 흐름과 자동화 테스트·배포 품질 게이트를 검증한다.

## 14. 기능 PRD 구성

기능 PRD는 [#7 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[#7 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 사용자 가치와 완료 흐름에 맞춰 4개로 구성한다. 탐색 영역은 두 독립 Workstream이므로 별도 PRD를 두고, 상세 콘텐츠는 [#7 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)로, 관리자 등록 전 과정은 [#7 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)로 각각 통합한다.

## 15. Workstream 및 책임 구조

| Workstream | PRD | 최종 책임자 | 기본 리뷰어 |
|---|---|---|---|
| [#7 WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | [#9 PRD-DISCOVERY-001](discovery/restaurant-discovery.md) | 양성훈 | 이우람 |
| [#7 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세 및 콘텐츠 조회 | [#11 PRD-DETAIL-001](detail/restaurant-detail.md) | 박진영 | 김인안 |
| [#7 WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | [#10 PRD-DISCOVERY-002](discovery/creator-discovery.md) | 이우람 | 양성훈 |
| [#7 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 데이터 등록 | [#12 PRD-ADMIN-001](admin/admin-data-management.md) | 김인안 | 박진영 |

공통 범위 변경은 이우람이, PRD 갱신은 김인안이 조율하되 팀 또는 영향받는 담당자가 리뷰한다.

## 16. 제품 성공 기준

- 사용자가 네 탐색 조건을 단독·조합해 원하는 공개 맛집을 찾을 수 있다.
- 사용자가 맛집 기본 정보와 유효한 방문 유튜버·영상 근거를 확인할 수 있다.
- 콘텐츠가 없는 맛집도 기본 탐색 가치가 유지된다.
- 관리자의 검증 등록 결과가 세 조회 흐름에 일관되게 반영된다.

## 17. MVP 완료 기준

- 기능 요구사항 20개의 인수 조건과 적용 NFR 검증이 완료된다.
- 네 기능 PRD의 완료 기준과 Workstream 간 실제 계약 통합이 충족된다.
- PC·모바일 브라우저 핵심 흐름, 접근 통제, 공개 상태, 빈 결과와 외부 장애 시나리오가 검증된다.
- API 계약, 테스트, PRD와 추적성 문서가 실제 동작과 일치한다.

## 18. 제약 조건

- 서비스 지역은 서울특별시이며 음식 카테고리는 확정된 10개 중 대표 1개를 사용한다.
- 일반 사용자 계정과 개인화 데이터는 다루지 않는다.
- 관리자 계정은 사전 발급하며 Spring Security 7.1.0과 JWT Access Token, Redis 8.8 Refresh Token으로 인증·인가한다.
- API 표현, 데이터 모델과 기술 선택은 PRD에서 결정하지 않는다.

## 19. 공통 리스크

- 목표 데이터 규모·응답 시간·지원 브라우저와 가용성 수치가 미확정이다.
- 공개 상태와 관계 판정 계약 변경은 여러 Workstream에 동시에 영향을 준다.
- [#7 WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 등록 통합 범위와 [#7 WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)의 다중 영역 조합이 일정·테스트 복잡도를 높인다.
- 외부 YouTube 링크·상태 변화가 콘텐츠 노출에 영향을 줄 수 있다.

## 20. 관련 문서

- [#1 서비스 개요](../../00-overview/service-overview.md)
- [#2 프로젝트 범위](../../00-overview/scope.md)
- [#3 용어집](../../00-overview/glossary.md)
- [#4 기능 요구사항](../../01-requirements/functional-requirements.md)
- [#13 요구사항 검토 결과](../../01-requirements/requirements-review.md)
- [#5 비즈니스 규칙](../../01-requirements/business-rules.md)
- [#6 비기능 요구사항](../../01-requirements/non-functional-requirements.md)
- [#7 MVP Workstream](../../02-analysis/mvp-workstreams.md)
- [#14 소유권](../../03-team/ownership.md)
- [#8 PRD 추적성](../traceability.md)

## 21. 검토 필요 항목

- 목표 동시 사용자·데이터 규모·응답 시간·가용성·지원 브라우저 수치
- JWT 만료·서명 키 교체와 Redis Refresh Token 운영, 계정 발급·회수·복구의 세부 절차
- YouTube API 시간 제한·할당량 초과·재시도 세부 정책
- 공통 데이터 모델 변경 승인 방식과 Workstream 부담 재조정 시점
