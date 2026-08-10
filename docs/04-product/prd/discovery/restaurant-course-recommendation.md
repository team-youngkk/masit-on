---
id: PRD-DISCOVERY-006
title: 맛집 코스 추천
status: approved
workstream: WS-16
owner: 이우람
reviewers:
  - 양성훈
related_requirements:
  - FR-COURSE-001
  - FR-COURSE-002
  - FR-COURSE-003
related_business_rules:
  - BR-COURSE-001
  - BR-COURSE-002
  - BR-COURSE-003
  - BR-COURSE-004
related_nfr:
  - NFR-PRIVACY-006
  - NFR-COST-001
  - NFR-EXTERNAL-005
  - NFR-PERFORMANCE-007
  - NFR-AVAILABILITY-003
  - NFR-OBSERVABILITY-005
  - NFR-TEST-006
related_documents:
  - ../../../00-overview/scope.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/business-rules.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../../01-requirements/requirements-review.md
  - ../../../08-planning/third-expansion-scope-and-terminology.md
  - ../../../08-planning/third-expansion-evaluation-strategy.md
  - ../../../02-analysis/third-expansion-workstreams.md
  - restaurant-discovery.md
  - ../../user-flows/third-expansion-user-flows.md
  - ../../wireframes/third-expansion-wireframes.md
---

# 맛집 코스 추천 PRD

## 1. 목적과 사용자 문제

사용자가 방문할 맛집을 이미 골랐을 때 직접 여러 지도에서 순서를 비교하지 않고 자동차 이동 순서와 구간별 경로를 확인하게 한다. `추천`은 맛집을 대신 고르는 의미가 아니라 선택한 장소의 이동 순서를 제안하는 의미다.

## 2. 대상 사용자와 선행 조건

- 대상: 로그인 여부와 관계없이 공개 맛집을 탐색하는 사용자
- 선행 조건: 공개·활성 맛집 조회, 유효한 위도·경도, 2~5개 선택 UI, Kakao Mobility `/v1/directions` REST API Key 설정과 quota 연결 확인
- 현재 위치·위치 권한·회원 행동·개인 컬렉션은 입력으로 사용하지 않는다.
- 구현은 [WS-16](../../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천)이 담당하며 최종 책임자는 이우람, 기본 리뷰어는 양성훈이다.

## 3. 목표와 성공 기준

- 사용자는 공개·좌표 보유 맛집 2~5개를 직접 선택한다.
- 첫 맛집을 출발점으로 유지하면서 나머지 맛집의 자동차 방문 순서를 확인한다.
- 실제 경로 거리·예상 소요 시간과 30km 상한을 신뢰할 수 있게 표시한다.
- 외부 장애·좌표 누락·부분 경로 실패 때 추정값을 정상 결과처럼 표시하지 않는다.

## 4. 범위

### 포함

- 서로 다른 공개·활성·좌표 보유 맛집 2~5개 선택
- 첫 선택 맛집 출발점 고정, 마지막 제안 맛집 도착점
- 자동차 이동 순서 제안
- 구간별 실제 경로 거리·예상 소요 시간과 전체 거리
- 실제 경로 거리 합계 30km 이하 상한
- 생성·만료 시각과 만료 뒤 재조회
- 좌표·입력·외부 API·부분 경로 실패 상태

### 제외

- 자동 맛집 선정·개인화·찜·최근 본·컬렉션 활용
- 현재 위치·별도 출발지·도착지와 위치 이력 저장
- 도보·대중교통·자전거, 실시간 교통 판단과 도착 보장
- 영업시간·대기·주차·예약·결제
- 코스 저장·공유·공동 편집

## 5. 핵심 사용자 흐름

1. 사용자가 탐색·상세 화면에서 코스 후보 맛집을 2~5개 선택한다.
2. 시스템은 중복·공개 상태·좌표·개수와 첫 출발점을 검증한다.
3. 사용자가 순서 계산을 요청하면 코스 1건당 외부 경로 계산을 최대 1회 수행한다.
4. 성공하면 제안 순서, 구간별 거리·시간, 전체 거리와 생성·만료 시각을 표시한다.
5. 전체 실제 경로가 30km를 넘으면 재선택을 안내하고 코스를 제공하지 않는다.
6. 외부 실패·부분 성공이면 입력 목록과 실패 이유만 보존하고 거리·시간을 추정하지 않는다.

상세 흐름은 [3차 확장 사용자 흐름](../../user-flows/third-expansion-user-flows.md#4-맛집-코스-추천)을 따른다.

## 6. 제품 요구사항

| 제품 요구사항 | 제품 동작 | 근거 |
|---|---|---|
| PR-COURSE-001 | 공개·활성·좌표 보유 맛집 2~5개를 중복 없이 입력받는다. | FR-COURSE-001, BR-COURSE-001~002 |
| PR-COURSE-002 | 첫 맛집을 출발점으로 고정하고 나머지 자동차 방문 순서를 제안한다. | FR-COURSE-002, BR-COURSE-001 |
| PR-COURSE-003 | 실제 구간 거리·예상 시간·전체 거리와 생성·만료 시각을 제공한다. | FR-COURSE-002, BR-COURSE-003 |
| PR-COURSE-004 | 실제 경로 거리 합계 30km를 초과하면 코스를 제공하지 않는다. | FR-COURSE-002 |
| PR-COURSE-005 | 좌표 누락 맛집을 조용히 제외하지 않고 전체 요청을 거부해 재선택을 안내한다. | BR-COURSE-002 |
| PR-COURSE-006 | 외부·부분 경로 실패 시 선택 목록과 실패 범주만 반환하고 거리·시간을 추정하지 않는다. | FR-COURSE-003, BR-COURSE-004 |

## 7. 화면과 상태

| 상태 | 사용자에게 보여줄 내용 | 다음 행동 |
|---|---|---|
| 후보 0~1개 | 선택 목록과 최소 2개 안내 | 맛집 추가 |
| 후보 2~5개 | 순서, 첫 출발점, 좌표 가능 상태 | 계산 요청·제거·순서 입력 변경 |
| 5개 도달 | 상한 안내와 추가 차단 | 제거 후 추가 |
| 좌표 없음·비공개 | 문제 맛집과 이유 | 해당 맛집 교체 |
| 계산 중 | 선택 목록 유지와 진행 상태 | 중복 제출 방지 |
| 정상 결과 | 제안 순서, 구간·전체 거리/시간, 생성·만료 시각 | 상세 이동·재조회 |
| 30km 초과 | 초과 안내, 선택 목록 | 맛집 제거·교체 |
| 외부·부분 실패 | 거리·시간 없는 실패, 입력 순서, 재시도 안내 | 재조회·기존 탐색 |
| 만료 | 오래된 결과 안내 | 새 경로 조회 |

화면 구조는 [3차 확장 와이어프레임](../../wireframes/third-expansion-wireframes.md#5-맛집-코스-추천)을 따른다.

## 8. 외부 장애·비용·개인정보

- 코스 1건당 외부 경로 계산은 최대 1회이며 무료 quota 안에서만 동작한다.
- Mobility 유료 호출은 금지하고 Free Tier quota 초과 전 hard stop한다. 무료 quota·계약을 확인할 수 없으면 호출하지 않는다.
- timeout·429·5xx·일부 구간 실패 시 완전한 코스가 없는 것으로 처리한다.
- 현재 위치·선택 이력·경로 결과를 저장하지 않고 공개 사업장 좌표만 전송한다.
- Mobility 장애는 코스 기능에만 격리하고 기존 탐색과 자연어 조건 해석은 유지한다.

## 9. 성능과 지표

- 내부 입력 검증·응답 조합 p95 500ms 이하
- 외부 호출 포함 5초 안에 정상 또는 명시적 실패 반환
- 계산 성공률, 좌표 누락률, 30km 초과율, 외부 오류율과 재조회율
- 코스당 외부 호출 수, 캐시 적중률, quota 잔여량과 차단 건수
- 결과 TTL은 5분이며 [RV-BR-016](../../../01-requirements/requirements-review.md#rv-br-016-경로-결과-만료-시간)의 확정값을 따른다.

## 10. 완료 조건

- [ ] FR-COURSE-001~003과 BR-COURSE-001~004의 개수·좌표·출발점·거리·만료·부분 실패가 검증된다.
- [ ] 현재 위치·선택 이력·경로 결과 저장 0건과 외부 전송 필드 최소화를 검증한다.
- [ ] 비용·quota hard stop과 코스당 외부 호출 최대 1회를 검증한다.
- [ ] 정상 50명·20 RPS와 최대 200명·80 RPS에서 응답 시간·오류 격리를 검증한다.
- [ ] API·데이터·Kakao Mobility ADR·Workstream·담당자와 운영 절차가 승인된다.

## 11. 리스크와 실행 검증

- 운영 계정 quota·인증 연결이 확인되지 않으면 외부 호출을 hard stop한다.
- 순서는 첫 장소 출발·좌표 직선거리 최근접 이웃·Restaurant ID 동률 오름차순으로 고정한다.
- 결과 TTL은 5분이고 서버 캐시는 사용하지 않는다. 코스당 외부 호출은 최대 1회다.
- 운영 맛집 좌표 보강률이 낮아 코스 후보가 부족할 가능성
