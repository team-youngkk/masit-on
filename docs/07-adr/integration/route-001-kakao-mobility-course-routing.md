---
id: ADR-ROUTE-001
title: Kakao Mobility 자동차 경로와 코스 결과 경계
status: Accepted
decision_date: 2026-08-10
owners:
  - 이우람
related_requirements:
  - FR-COURSE-001
  - FR-COURSE-002
  - FR-COURSE-003
  - BR-COURSE-001
  - BR-COURSE-002
  - BR-COURSE-003
  - BR-COURSE-004
  - NFR-EXTERNAL-005
  - NFR-PERFORMANCE-007
  - NFR-PRIVACY-006
  - NFR-COST-001
related_documents:
  - ../../02-analysis/third-expansion-domain-boundaries.md
  - ../../02-analysis/third-expansion-workstreams.md
  - ../../04-product/prd/discovery/restaurant-course-recommendation.md
  - ../../04-product/user-flows/third-expansion-user-flows.md
  - ../../08-planning/third-expansion-baseline-review.md
  - ../architecture/arch-002-external-ports-adapters.md
  - ../integration/map-001-map-bounds-search.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../quality/obs-001-logging-observability.md
  - ../adr-backlog.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-ROUTE-001 Kakao Mobility 자동차 경로와 코스 결과 경계

## 1. 상태

Accepted. 요청 시 경로 계산, 초기 비저장·비캐시, Port/Adapter 경계와 Kakao Mobility 자동차 길찾기 `/v1/directions` 사용을 2026-08-10 확정했다. 실제 운영 계정 quota·인증 연결은 활성화 전 검증한다.

## 2. 결정 요약

코스 추천은 새 `Route` 또는 `Course` 영속 도메인을 만들지 않고, [WS-16](../../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천)의 요청·조회 애플리케이션에서 사용자가 선택한 2~5개 공개 맛집의 Restaurant 좌표를 Kakao Mobility 자동차 경로 API에 전달한다.

외부 경로 호출은 Port/Adapter로 격리한다. 결과는 초기에는 일회성 응답으로 제공하고 TTL은 5분으로 고정하며 서버 캐시는 사용하지 않는다.

## 3. 배경

3차 범위에서 추천은 시스템이 맛집을 선택하는 의미가 아니라 사용자가 고른 맛집의 이동 순서를 제안하는 의미다. 현재 위치·도착지·이동 이력·영업시간은 사용하지 않고, 코스 결과도 저장·공유하지 않는다.

Restaurant에는 지도 표시를 위한 nullable WGS84 좌표가 이미 존재할 수 있지만, 좌표 보강률과 품질은 아직 검증해야 한다. WS-16이 좌표를 직접 보정하면 Restaurant 생명주기와 장소 동일성 규칙이 중복되므로 허용하지 않는다.

## 4. 결정 문제

외부 자동차 경로 제공자를 어떤 경계에서 호출하고, 좌표 누락·부분 실패·quota·비용·응답 만료를 어떻게 처리할 것인가.

## 5. 결정

### 5.1. 입력과 좌표

- 사용자가 선택한 공개·활성 맛집 2~5개만 입력으로 받는다.
- 모든 후보는 Restaurant가 소유한 유효 좌표를 가져야 한다.
- 좌표가 없거나 공개 상태가 아니거나 총 이동 거리 상한을 초과하면 경로 호출 전에 실패한다.
- 첫 번째 선택 맛집을 출발점으로 고정하고 별도 출발·도착지나 현재 위치를 받지 않는다.
- WS-16은 Restaurant 좌표·주소·공개 상태를 생성·수정하지 않는다.

### 5.2. 외부 호출

- Application은 Mobility SDK/HTTP client를 직접 호출하지 않고 Route Provider Port를 사용한다.
- Adapter는 좌표·자동차 이동 조건을 외부 요청으로 변환하고 거리·시간·구간·제공자 오류를 내부 계약으로 변환한다.
- 외부 키·원문 응답·민감한 요청 정보를 로그·응답에 노출하지 않는다.
- 연결 timeout 1초·응답 timeout 4초·전체 5초를 적용하고 재시도하지 않는다. 429·5xx·quota 오류와 호출 상한은 실패 상태로 기록한다.

### 5.3. 결과와 실패

- 결과는 방문 순서, 구간별 거리·예상 시간, 전체 거리·시간, 생성 시각과 만료 정보를 제공한다.
- 첫 장소를 출발지로 고정하고 좌표 직선거리 최근접 이웃 순서로 정렬하며, 동률은 Restaurant ID 오름차순으로 안정 정렬한다. 정렬된 마지막 장소를 도착지로, 중간 장소를 waypoints로 전송한다.
- 외부 호출이 실패하면 실패 상태와 재시도 안내를 반환한다.
- 일부 구간만 성공한 경우 성공하지 않은 구간을 추정해 정상 코스로 표시하지 않는다.
- 초기에는 코스 결과를 사용자별로 저장·공유하지 않는다.

### 5.4. 캐시

3차 확장에서는 캐시를 사용하지 않는다. 결과는 5분 뒤 만료하고 새 요청으로 재조회한다. 캐시 도입은 별도 범위 변경과 [ADR-CACHE-001](../adr-backlog.md#adr-cache-001-redis-캐시-도입) 활성화가 필요한 후속 결정이다.

## 6. 고려한 선택지

- **Kakao Mobility 직접 호출**: 초기에는 단순하지만 제공자 변경·오류·테스트 격리가 어렵다.
- **Port/Adapter를 통한 Kakao Mobility 호출**: 제공자 계약을 격리하고 WireMock으로 실패·quota·부분 결과를 재현할 수 있다.
- **자체 거리 계산·직선거리 fallback**: 실제 자동차 경로가 아닌 결과를 정상 경로처럼 제공할 위험이 있어 초기에는 사용하지 않는다.
- **모든 결과를 DB에 저장**: 초기 범위의 저장·공유를 넘어 보존·개인정보·무효화 책임을 만들므로 사용하지 않는다.

## 7. 트레이드오프

실시간 외부 경로 결과에 의존하므로 제공자 장애 때 코스 기능은 일시적으로 사용할 수 없고, 캐시를 도입하지 않으면 반복 호출 비용이 발생할 수 있다. 대신 잘못된 경로 추정과 stale 결과 노출을 줄이고, 기존 맛집 탐색 기능과 외부 장애를 격리할 수 있다.

## 8. 검증 방법과 실행 게이트

- 좌표 누락·비공개·삭제·2개 미만·6개 초과·30km 초과 입력을 검증한다.
- 정상 경로, timeout, 429, 5xx, 부분 구간 실패, 잘못된 응답 Schema를 WireMock으로 검증한다.
- 외부 호출 실패가 기존 맛집 목록·상세 기능을 중단시키지 않는지 확인한다.
- 요청별 호출 수·응답 시간·무료 quota 사용량을 기록하고 quota hard stop을 검증한다.
- 좌표 보강률과 단일 EC2 용량을 확인한다.
- [NFR-EXTERNAL-005](../../01-requirements/non-functional-requirements.md#nfr-external-005-ai와-mobility-timeoutrate-limit재시도), [NFR-PERFORMANCE-007](../../01-requirements/non-functional-requirements.md#nfr-performance-007-자연어-검색과-경로-응답-시간), [NFR-COST-001](../../01-requirements/non-functional-requirements.md#nfr-cost-001-ai임베딩mobility-호출-비용-상한)의 목표와 수치를 계약 기준으로 사용한다. 실제 quota 연결과 부하 결과는 활성화·최종 완료 증거로 추가한다.

## 9. 확정 운영 규칙

- Kakao Mobility `/v1/directions`, REST API Key, 자동차 경로, 첫 장소 출발·최근접 이웃·ID 동률 정렬을 사용한다.
- 코스당 외부 호출은 최대 1회, 결과 TTL은 5분, 서버 캐시는 없고 유료 비용은 0원·앱 월 1,000건 상한이다.
- Mobility timeout은 1초·4초·5초, 재시도 0회다. 실패·429·부분 결과는 추정값 없는 실패 상태로 반환한다.
- API 자격 증명과 quota가 확인되지 않으면 코스 외부 호출을 hard stop하고 기존 탐색 기능은 계속 제공한다.
