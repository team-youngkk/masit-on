---
id: PRD-DETAIL-001
title: 맛집 상세 및 콘텐츠 조회
status: draft
workstream: WS-02
owner: 박진영
reviewers:
  - 김인안
related_requirements:
  1: FR-RESTAURANT-008
  2: FR-RESTAURANT-009
  3: FR-RESTAURANT-010
  4: FR-RESTAURANT-011
  5: FR-CREATOR-002
  6: FR-VIDEO-001
related_business_rules:
  - BR-RESTAURANT-002
  - BR-RESTAURANT-004
  - BR-RESTAURANT-005
  - BR-RESTAURANT-008
  - BR-CREATOR-004
  - BR-CREATOR-007
  - BR-VIDEO-001
  - BR-VIDEO-004
  - BR-VIDEO-007
  - BR-VIDEO-008
  - BR-VIDEO-009
  - BR-VISIT-004
  - BR-VISIT-005
related_nfr:
  - NFR-PERFORMANCE-001
  - NFR-INTEGRITY-004
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-EXTERNAL-001
  - NFR-EXTERNAL-002
  - NFR-COMPATIBILITY-002
  - NFR-COMPATIBILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
related_documents:
  1: ../00-product-overview.md
  2: ../discovery/creator-discovery.md
  3: ../../../01-requirements/functional-requirements.md
  4: ../../../01-requirements/business-rules.md
  5: ../../../02-analysis/mvp-workstreams.md
  6: ../../../05-specs/api/detail/restaurant-detail-api.md
  7: ../../../05-specs/data/relationship-rules.md
  8: ../../../05-specs/data/lifecycle-rules.md
  9: ../../../01-requirements/non-functional-requirements.md
  10: ../../traceability.md
---

# 맛집 상세 및 콘텐츠 조회 PRD

## 1. 문서 정보

맛집 기본 정보와 유효한 방문 유튜버·관련 영상을 한 사용자 흐름으로 조합한다. [#5 WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 여러 영역의 최종 표시 결과를 책임한다.

## 2. 기능 개요

사용자는 선택한 공개 맛집의 위치·연락·카테고리 정보와 실제 방문이 검증된 채널·원본 영상 링크를 같은 상세에서 확인한다.

## 3. 문제 및 사용자 요구

맛집 기본 정보와 방문 근거가 여러 출처에 나뉘어 있어 장소를 판단하기 어렵다. 사용자는 콘텐츠가 없거나 외부 링크에 문제가 있어도 맛집 자체의 유효한 기본 정보는 확인하고 싶다.

## 4. 목표

- 한 맛집의 기본 정보와 검증된 방문 콘텐츠를 한 흐름에서 제공한다.
- 관계·영상이 없어도 기본 상세 가치를 유지한다.
- 비공개·삭제·중복 콘텐츠를 제외하고 외부 링크 장애를 내부 조회와 격리한다.

## 5. 비목표

지도·길찾기·거리, 지번주소 검색, 예약·결제, 사용자 리뷰·개인화, 유튜버·영상 별도 상세, 영상 재생·저장·재배포와 자동 메타데이터 동기화는 목표가 아니다.

## 6. 대상 사용자

- 탐색 결과에서 한 맛집의 방문 판단 정보를 확인하는 일반 사용자

## 7. 전제 조건

- 요청 맛집이 존재하고 일반 사용자에게 공개돼야 한다.
- 연결 콘텐츠는 공개·유효 방문 관계와 공개 유튜버·영상을 모두 충족해야 한다.
- 외부 원본 링크와 내부 기본 정보의 가용성을 분리한다.

## 8. 핵심 사용자 흐름

- 시작 조건: 사용자가 탐색 결과에서 맛집을 선택한다.
- 사용자 행동: 맛집 상세를 열고 기본 정보와 방문 콘텐츠를 확인한다.
- 시스템 동작: 공개 기본 정보를 조회하고 유효 관계의 유튜버·영상을 중복 제거해 조합한다.
- 성공 결과: 이름, 주소·상세 위치, 전화번호, 대표 카테고리, 카카오 장소 링크와 방문 채널·관련 영상 링크를 확인한다.
- 빈 결과 또는 실패 처리: 관계나 영상이 없으면 빈 콘텐츠와 기본 정보를 제공한다. 맛집 기본 정보 제공자가 실패하면 상세 전체를 실패 처리한다. 관계·유튜버·영상 제공자만 실패하면 기본 정보를 제공하고 콘텐츠 영역을 정상 빈 결과와 구분되는 일시적 조회 실패로 처리한다.

## 9. 기능 범위

### 포함 범위

- 맛집 이름, 전체 도로명주소·상세 위치, 전화번호, 대표 음식 카테고리와 카카오 장소 링크
- 영상 연결 없는 맛집 기본 상세
- 중복 제거한 방문 채널명·채널 링크와 영상 제목·썸네일·게시 채널명·원본 링크
- Restaurant·Visit·Creator·Video 표시 결과 조합과 비공개·삭제 대상 제외

### 제외 범위

- 별도 방문 콘텐츠 PRD 또는 화면, 지도·예약·사용자별 기능
- 외부 원본 저장, 자동 복구·동기화와 기술적 조합 위치 결정

### 후속 확장

- 지도 기반 탐색과 유튜버 상세는 2차 확장 범위에서 검토한다.

## 10. 제품 요구사항

| PRD 요구사항 | 제품 동작 | 관련 기능 요구사항 | 중요도 | 상태 |
|---|---|---|---|---|
| PR-DETAIL-001 | 공개 맛집의 기본 정보를 조회한다. | [REQ#1 FR-RESTAURANT-008](../../../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) | Must | 확정 |
| PR-DETAIL-002 | 전체 도로명주소와 필요한 상세 위치를 확인한다. | [REQ#2 FR-RESTAURANT-009](../../../01-requirements/functional-requirements.md#fr-restaurant-009-지역-정보-확인) | Must | 확정 |
| PR-DETAIL-003 | 대표 음식 카테고리를 확인한다. | [REQ#3 FR-RESTAURANT-010](../../../01-requirements/functional-requirements.md#fr-restaurant-010-음식-카테고리-확인) | Must | 확정 |
| PR-DETAIL-004 | 영상 연결이 없어도 맛집 기본 정보를 조회한다. | [REQ#4 FR-RESTAURANT-011](../../../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회) | Must | 확정 |
| PR-DETAIL-005 | 유효 관계의 방문 유튜버 표시 정보와 채널 링크를 확인한다. | [REQ#5 FR-CREATOR-002](../../../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인) | Must | 확정 |
| PR-DETAIL-006 | 유효 관계의 영상 표시 정보와 원본 링크를 확인한다. | [REQ#6 FR-VIDEO-001](../../../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인) | Must | 확정 |

## 11. 비즈니스 규칙

- 맛집의 영상 독립성, 카테고리·지역·공개 조건은 [#4 BR-RESTAURANT-002](../../../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집), [#4 BR-RESTAURANT-004](../../../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리), [#4 BR-RESTAURANT-005](../../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속), [#4 BR-RESTAURANT-008](../../../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건)을 따른다.
- 유튜버·영상 표시와 이용 불가·외부 장애는 [#4 BR-CREATOR-004](../../../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보), [#4 BR-CREATOR-007](../../../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리), [#4 BR-VIDEO-001](../../../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위), [#4 BR-VIDEO-004](../../../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결), [#4 BR-VIDEO-007](../../../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리)~[#4 BR-VIDEO-009](../../../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리)를 따른다.
- 관계 연결 범위·조회 유효성은 [#4 BR-VISIT-004](../../../01-requirements/business-rules.md#br-visit-004-방문-관계의-연결-범위), [#4 BR-VISIT-005](../../../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성), 공개 노출은 [#4 BR-PUBLICATION-001](../../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [#4 BR-PUBLICATION-003](../../../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출)~[#4 BR-PUBLICATION-007](../../../01-requirements/business-rules.md#br-publication-007-외부-영상-삭제의-영향-범위)을 따른다.

## 12. 예외 및 경계 상황

| 상황 | 기대 결과 |
|---|---|
| 맛집이 없거나 비공개 | 공개 데이터 존재 여부를 누설하지 않는 찾을 수 없음으로 처리한다. |
| 관계·유튜버·영상 없음 | 기본 상세와 빈 콘텐츠 목록을 제공한다. |
| 동일 유튜버·영상의 복수 관계 | 각 표시 대상을 한 번만 제공한다. |
| 연결 대상 또는 관계가 비공개·삭제·무효 | 해당 콘텐츠만 제외한다. |
| 외부 영상 링크 오류 | 내부 기본 상세를 유지하고 링크 오류를 격리한다. |
| 관계·유튜버·영상 제공자 실패 | 기본 정보를 제공하고 콘텐츠를 `일시적으로 불러올 수 없음`으로 구분한다. |
| 하나의 영상이 여러 맛집과 연결 | 요청 맛집의 유효 관계에 해당하는 콘텐츠만 표시한다. |

## 13. 품질 요구사항

조회 성능은 [#9 NFR-PERFORMANCE-001](../../../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간), 외부 링크 격리는 [#9 NFR-INTEGRITY-004](../../../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리), [#9 NFR-EXTERNAL-001](../../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리), [#9 NFR-EXTERNAL-002](../../../01-requirements/non-functional-requirements.md#nfr-external-002-외부-호출-실패와-변경-격리)를 따른다. 공통 오류·응답·모바일 크기와 다중 제공자 실패 테스트는 메타데이터의 관련 NFR로 검증한다.

## 14. 의존성

- 선행 정책: 공개 상태 우선순위와 관계 유효성·중복 제거
- 데이터 의존성: [#5 WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 등록한 기본 데이터와 관계
- 다른 기능 PRD: [#2 PRD-DISCOVERY-002](../discovery/creator-discovery.md)와 동일 Visit 판정 정책 공유
- 다른 Workstream: [#5 WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 관계 계약, [#5 WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 결과
- 외부 서비스: 카카오 장소 링크와 YouTube 원본 링크
- 공통 API 계약: 식별자, 표시 정보, 오류와 부분 실패 계약

## 15. Workstream 및 책임자

- 주 Workstream: [#5 WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세 및 콘텐츠 조회
- 최종 책임자: 박진영
- 기본 리뷰어: 김인안
- 협업: 이우람(Visit 판정), 양성훈(목록·상세 식별자), 김인안(등록 반영)

## 16. 성공 기준

- 사용자가 한 상세 흐름에서 장소 판단 정보와 유효한 방문 근거를 확인한다.
- 콘텐츠가 없거나 외부 링크가 실패해도 공개 기본 정보는 유지된다.
- 중복·비공개·무효 콘텐츠가 노출되지 않는다.

## 17. 완료 기준

- 6개 요구사항과 관련 규칙의 구현·자동화 테스트가 완료된다.
- 네 영역 표시 계약, [#5 WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 관계 정책과 [#5 WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터를 통합 검증한다.
- 외부 링크·빈 콘텐츠·중복·부분 실패 시나리오와 PC·모바일 흐름이 통과한다.
- API 계약, 문서와 추적성이 실제 동작과 일치한다.

## 18. 리스크

- 여러 영역 조합과 상태 확인이 성능·통합 테스트 복잡도를 높인다.
- 상세 조합의 실제 애플리케이션 책임 위치가 미확정이다.
- 외부 링크 상태 변화가 표시 품질에 영향을 준다.

## 19. 관련 문서

- [#1 전체 제품 PRD](../00-product-overview.md)
- [#2 유튜버 기반 탐색 PRD](../discovery/creator-discovery.md)
- [#3 기능 요구사항](../../../01-requirements/functional-requirements.md)
- [#4 비즈니스 규칙](../../../01-requirements/business-rules.md)
- [#5 MVP Workstream](../../../02-analysis/mvp-workstreams.md)
- [#10 추적성](../../traceability.md)

## 20. 검토 필요 항목

- 상세 조합의 애플리케이션 책임 위치
- 일반 조회 목표 응답 시간과 외부 링크 확인 정책
