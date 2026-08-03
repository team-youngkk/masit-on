---
id: PRD-DISCOVERY-003
title: 지도 기반 탐색
status: draft
workstream: WS-07
owner: 양성훈
reviewers:
  - 박진영
  - 이우람
related_requirements:
  - FR-MAP-001
  - FR-MAP-002
  - FR-RESTAURANT-005
related_business_rules:
  - BR-MAP-001
  - BR-MAP-002
  - BR-MAP-003
  - BR-MAP-004
  - BR-MAP-005
related_nfr:
  - NFR-PERFORMANCE-005
  - NFR-RELIABILITY-001
  - NFR-RELIABILITY-003
  - NFR-EXTERNAL-004
  - NFR-COMPATIBILITY-004
  - NFR-TEST-004
  - NFR-PRIVACY-002
  - NFR-PRIVACY-004
related_documents:
  - ../00-product-overview.md
  - README.md
  - restaurant-discovery.md
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

# 지도 기반 탐색 PRD

## 1. 문서 정보

공개 맛집의 위치와 분포를 지도 영역에서 탐색하는 1차 확장 제품 계약이다. 주 Workstream은 WS-07이며 양성훈이 최종 책임을 맡는다. 맛집 좌표·공개 상태·영역 조건은 Restaurant 책임을 따르고, 지도 SDK와 마커 상호작용은 화면 경계에서 처리한다.

API 경로·요청 및 응답 필드는 [지도 탐색 API](../../../05-specs/api/discovery/map-discovery-api.md)에서 확정한다. 좌표의 물리 저장 구조는 후속 데이터 계약에서 확정하며 본 PRD와 [기능 요구사항](../../../01-requirements/functional-requirements.md), [비즈니스 규칙](../../../01-requirements/business-rules.md)을 역추적할 수 있어야 한다.

## 2. 해결할 문제

목록의 주소만으로는 맛집이 어느 구역에 모여 있는지, 사용자가 보고 있는 지역 안에 어떤 후보가 있는지 빠르게 파악하기 어렵다. 사용자는 개인 위치를 제공하지 않고도 원하는 지도 영역을 이동하면서 기존 탐색 조건에 맞는 공개 맛집의 위치와 요약을 확인할 수 있어야 한다.

## 3. 목표

- 사용자가 지도 화면 영역 안의 공개 맛집 위치와 분포를 파악한다.
- 지도와 대체 목록이 같은 맛집 집합과 선택 상태를 표현한다.
- 이름·자치구·음식 카테고리·유튜버 조건을 지도 영역과 함께 적용한다.
- 외부 지도 장애와 좌표 누락이 지도 밖 공개 목록·상세·유튜버 상세로 확산되지 않는다.
- 사용자 위치 권한이나 현재 좌표를 요청·수집·저장하지 않는다.

## 4. 대상 사용자

- 로그인 여부와 관계없이 서울특별시 안의 맛집을 위치 기준으로 둘러보려는 일반 사용자
- 포인터뿐 아니라 키보드·보조 기술·터치로 탐색하는 사용자

## 5. 사용자 여정

### 5.1 지도 열기와 초기 탐색

1. 사용자가 지도 탐색 화면을 연다.
2. 시스템은 초기 지도 영역과 같은 범위의 공개 맛집을 조회한다.
3. 유효 좌표가 있는 결과가 200개 이하이면 지도 마커와 접근 가능한 대체 목록에 모두 표시한다.
4. 사용자가 마커 또는 목록 항목을 선택하면 동일한 맛집이 선택되고 이름, 대표 음식 카테고리와 주소 요약을 확인한다.
5. 사용자가 요약의 상세 이동 수단을 선택하면 해당 맛집 상세로 이동한다.

### 5.2 지도 이동과 조건 조합

1. 사용자가 지도를 이동·확대·축소하거나 이름·자치구·음식 카테고리·유튜버 조건을 변경한다.
2. 지도 이동이 끝난 뒤 300ms 동안 추가 이동이 없으면 현재 화면 경계를 포함한 영역 조회를 시작한다.
3. 시스템은 현재 영역과 지정된 모든 탐색 조건을 AND로 적용하고 맛집 중복을 제거한다.
4. 지도와 대체 목록은 가장 최근의 유효한 결과로 함께 갱신된다.
5. 빈 결과면 현재 영역에 조건을 만족하는 맛집이 없음을 알리고 영역 이동 또는 조건 변경을 안내한다.

### 5.3 과다 결과와 외부 장애

1. 결과가 200개를 초과하면 시스템은 임의의 일부 마커를 표시하지 않는다.
2. 사용자가 영역을 확대하거나 조건을 좁히도록 안내한다.
3. 지도 SDK를 불러오지 못하면 지도 오류 상태와 재시도 수단을 제공한다.
4. 사용자는 지도 밖의 공개 맛집 목록·상세와 유튜버 상세를 계속 사용할 수 있다.

## 6. 포함 범위

- Kakao 지도 표시
- 유효 장소 좌표가 있는 공개 맛집의 마커
- 지도 마커와 같은 결과를 제공하는 접근 가능한 대체 목록
- 마커 또는 목록 항목 선택과 선택 상태 연동
- 맛집 이름, 대표 음식 카테고리, 주소 요약과 맛집 상세 이동
- 현재 지도 화면 영역의 경계를 포함한 영역 조회
- 이름·자치구·음식 카테고리·유튜버 조건과 지도 영역의 AND 조합
- 지도 이동 종료 뒤 300ms debounce와 클라이언트당 초당 최대 4회 조회
- 결과 200개 이하 전체 표시와 200개 초과 시 영역 축소 안내
- 신규 맛집의 유효 좌표 확보와 기존 공개 맛집 좌표 backfill 결과 반영
- PC·모바일·키보드·스크린 리더를 고려한 핵심 탐색 흐름

## 7. 제외 범위

- 사용자 현재 위치 권한 요청, 현재 좌표 수집·저장과 실시간 위치 추적
- 현재 위치 기준 거리 계산과 반경 검색
- 길찾기, 경로 표시와 코스 추천
- 서울특별시 외 지역 확장
- 마커 클러스터링을 이용한 200개 초과 결과의 부분 표시
- 추천·인기·개인화 지도 정렬
- 지도 안에서의 맛집 등록·수정

## 8. 전제 조건과 의존성

- 기존 공개 맛집에 검증된 좌표를 단계적으로 보강할 수 있는 backfill 절차가 준비되어야 한다. 미보강 맛집은 지도에서만 제외하고 일반 목록·상세에는 유지한다.
- 신규 맛집은 유효 좌표를 확보한 경우에만 지도 탐색 대상이 된다.
- 지도 영역과 기존 필터의 판정은 [맛집 탐색 PRD](restaurant-discovery.md)의 검색·필터 의미와 일치해야 한다.
- 유튜버 조건은 공개·유효 Visit 관계의 판정 결과를 사용한다.
- Kakao Maps SDK의 브라우저용 식별 키와 서버용 외부 API 비밀정보를 분리하고 허용 출처를 제한해야 한다.
- 구체적인 좌표 저장, backfill, 지도 영역 조회와 외부 연동 계약은 후속 데이터·API·아키텍처 문서에서 확정한다.

## 9. 화면 및 상태

| 상태 | 사용자에게 보이는 내용 | 가능한 행동 |
|---|---|---|
| 초기 로딩 | 지도와 결과 목록의 로딩 표시 | 로딩 완료 대기 |
| 정상 | 200개 이하의 마커·동일 결과 목록·적용 조건 | 지도 이동·확대·축소, 조건 변경, 맛집 선택 |
| 맛집 선택 | 선택 마커와 목록 항목, 이름·카테고리·주소 요약 | 맛집 상세 이동, 선택 해제 |
| 빈 영역 | 현재 영역과 조건에 맞는 맛집이 없다는 설명 | 영역 이동, 조건 초기화·변경 |
| 결과 200개 초과 | 일부 마커 없이 영역을 좁히라는 안내 | 확대, 조건 추가 |
| 좌표 없는 맛집 | 지도에는 나타나지 않음 | 일반 목록·상세에서 계속 탐색 |
| 잘못된 영역 조건 | 요청을 처리할 수 없다는 오류 | 마지막 유효 상태 유지, 재시도 |
| 호출 제한 | 너무 잦은 조작을 합치거나 제한한다는 피드백 | 조작을 멈춘 뒤 최신 영역 재조회 |
| 지도 SDK 오류 | 지도를 불러올 수 없음과 재시도 수단 | 재시도, 지도 밖 목록·상세 이용 |

지도 조작에 의존하지 않는 대체 목록은 단순 오류 대체물이 아니라 정상 화면의 필수 요소다. 마커와 목록의 선택 상태 및 상세 이동 대상은 일치해야 한다.

## 10. 제품 요구사항

| PRD 요구사항 | 제품 동작 | 관련 기능 요구사항 | 중요도 | 상태 |
|---|---|---|---|---|
| PR-MAP-001 | 유효 좌표가 있는 공개 맛집을 지도와 대체 목록에 표시한다. | [FR-MAP-001](../../../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시) | Must | 확정 |
| PR-MAP-002 | 마커 또는 목록 선택 시 맛집 요약을 제공하고 상세로 이동한다. | [FR-MAP-001](../../../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시) | Must | 확정 |
| PR-MAP-003 | 현재 지도 영역과 기존 탐색 조건을 AND로 조합한다. | [FR-MAP-002](../../../01-requirements/functional-requirements.md#fr-map-002-지도-영역과-탐색-조건-조합-조회), [FR-RESTAURANT-005](../../../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합) | Must | 확정 |
| PR-MAP-004 | 이동 종료 뒤 300ms 지연하고 클라이언트당 초당 최대 4회 조회한다. | [FR-MAP-002](../../../01-requirements/functional-requirements.md#fr-map-002-지도-영역과-탐색-조건-조합-조회) | Must | 확정 |
| PR-MAP-005 | 결과가 200개를 초과하면 일부 마커 대신 영역 축소를 안내한다. | [FR-MAP-001](../../../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시), [FR-MAP-002](../../../01-requirements/functional-requirements.md#fr-map-002-지도-영역과-탐색-조건-조합-조회) | Must | 확정 |
| PR-MAP-006 | 사용자 위치를 사용하지 않고 지도 장애를 다른 공개 기능과 격리한다. | [FR-MAP-001](../../../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시) | Must | 확정 |

## 11. 비즈니스 규칙

- 표시 대상과 좌표 누락 처리는 [BR-MAP-001](../../../01-requirements/business-rules.md#br-map-001-지도-표시-대상과-좌표)을 따른다.
- 영역 경계 포함, 기존 조건 AND와 중복 제거는 [BR-MAP-002](../../../01-requirements/business-rules.md#br-map-002-지도-영역과-필터-조합)를 따른다.
- 200개 제한은 [BR-MAP-003](../../../01-requirements/business-rules.md#br-map-003-지도-마커-수-제한)을 따른다.
- debounce와 호출 제한은 [BR-MAP-004](../../../01-requirements/business-rules.md#br-map-004-지도-조회-호출-제한)를 따른다.
- 마커 요약과 위치 비수집은 [BR-MAP-005](../../../01-requirements/business-rules.md#br-map-005-마커-요약과-사용자-위치-비수집)를 따른다.

## 12. 품질 요구사항

- 영역 조회는 [NFR-PERFORMANCE-005](../../../01-requirements/non-functional-requirements.md#nfr-performance-005-개인화지도유튜버-상세-조회-응답-시간)의 정상 부하와 p95 1.5초 이하 기준으로 검증한다.
- 지도 장애와 오류 표현은 [NFR-RELIABILITY-001](../../../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책), [NFR-RELIABILITY-003](../../../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리)을 따른다.
- 키 보호와 SDK 장애 격리는 [NFR-EXTERNAL-004](../../../01-requirements/non-functional-requirements.md#nfr-external-004-지도-api-키와-외부-sdk-경계), [NFR-PRIVACY-002](../../../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호)을 따른다.
- 대체 목록, 키보드·보조 기술·터치와 360px 이상 화면은 [NFR-COMPATIBILITY-004](../../../01-requirements/non-functional-requirements.md#nfr-compatibility-004-지도-접근성과-모바일-조작)을 따른다.
- 위치 비수집은 [NFR-PRIVACY-004](../../../01-requirements/non-functional-requirements.md#nfr-privacy-004-위치와-행동-데이터-최소화)를 따른다.
- 정상·빈·경계·외부 장애와 지원 브라우저 흐름은 [NFR-TEST-004](../../../01-requirements/non-functional-requirements.md#nfr-test-004-1차-확장-보안통합브라우저-검증)에 연결해 검증한다.

## 13. 제품 성공 기준

- 대표 사용자 검증에서 주소 목록만 제공할 때보다 지도 영역 안의 후보 위치를 파악하고 상세로 이동하는 흐름의 성공률이 개선된다.
- 유효 좌표가 있는 공개 맛집의 마커·대체 목록과 동일 조건의 영역 조회 결과가 일치한다.
- 빈 영역, 200개 초과와 SDK 오류에서 사용자가 다음 행동을 선택할 수 있다.
- 지원 PC·모바일 환경에서 지도 조작과 키보드 대체 목록을 통해 같은 핵심 탐색을 완료한다.
- 브라우저 권한·네트워크·저장·로그 검사에서 사용자 현재 위치의 요청·수집·저장이 0건이다.

제품 가치 지표의 표본 크기와 목표 개선 폭은 출시 전 측정 계획에서 확정하며, 시스템 품질 수치는 관련 NFR을 그대로 적용한다.

## 14. Workstream 및 책임

- 주 Workstream: WS-07 지도 탐색
- 최종 책임자: 양성훈
- 기본 리뷰어: 박진영
- 협업: 이우람(유튜버 조건과 Visit 판정), 박진영(맛집 상세 연결), 김인안(맛집 좌표 등록·갱신 흐름)

WS-07은 지도·목록 연결과 최종 사용자 상태를 책임진다. Restaurant 소유자는 좌표·공개 상태·영역 조건을, Visit 소유자는 유튜버 조건의 관계 유효성을 제공한다.

## 15. 완료 기준

- [FR-MAP-001](../../../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시), [FR-MAP-002](../../../01-requirements/functional-requirements.md#fr-map-002-지도-영역과-탐색-조건-조합-조회)와 관련 BR·NFR이 구현 및 테스트 결과로 추적된다.
- 신규 맛집의 유효 좌표 확보와 기존 공개 맛집 좌표 backfill 결과를 검증한다.
- 영역 밖 제외, 경계 좌표 포함, 기존 필터 AND, 중복 제거와 빈 영역을 검증한다.
- 지도·대체 목록 선택 연동, 요약과 상세 이동이 PC·모바일·키보드·스크린 리더 표본에서 동작한다.
- 300ms debounce, 초당 4회 제한, 200개 경계와 초과 안내를 검증한다.
- 좌표 없는 맛집이 지도에서만 제외되고 일반 목록·상세에 유지된다.
- SDK 오류와 호출 제한이 지도 밖 공개 조회를 실패시키지 않는다.
- 사용자 위치 권한 요청·좌표 저장·로그·분석 전송이 없고 브라우저용 키와 서버 비밀정보가 분리된다.
- 후속 API·데이터·와이어프레임과 제품 추적성 문서가 실제 동작과 일치한다.

## 16. 리스크

- 좌표 backfill 누락이나 부정확한 좌표는 지도 신뢰도를 낮춘다.
- 지도 영역과 다중 조건 조합은 조회 비용을 높일 수 있다.
- 외부 SDK 정책·호출 제한·허용 도메인 설정 변경이 지도 가용성에 영향을 줄 수 있다.
- 지도와 대체 목록의 결과 또는 선택 상태가 어긋나면 접근성과 제품 일관성이 깨진다.

## 17. 관련 문서

- [지도 탐색 API](../../../05-specs/api/discovery/map-discovery-api.md)

- [1차 확장 범위](../../../00-overview/scope.md#51-1차-확장)
- [기능 요구사항](../../../01-requirements/functional-requirements.md#fr-map-001-kakao-지도와-맛집-마커-표시)
- [비즈니스 규칙](../../../01-requirements/business-rules.md#br-map-001-지도-표시-대상과-좌표)
- [비기능 요구사항](../../../01-requirements/non-functional-requirements.md)
- [도메인 경계](../../../02-analysis/domain-boundaries.md)
- [맛집 탐색 PRD](restaurant-discovery.md)
- [1차 확장 Workstream](../../../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색)
- [1차 확장 사용자 흐름](../../user-flows/first-expansion-user-flows.md#7-지도-탐색)
- [1차 확장 와이어프레임](../../wireframes/first-expansion-wireframes.md#6-지도-탐색)
- [제품 추적성](../../traceability.md)
