---
id: ADR-TEST-001
title: 계층별 자동화 테스트 전략
status: Accepted
decision_date: 2026-07-27
owners:
  - 박진영
related_requirements:
  - NFR-TEST-001
  - NFR-TEST-002
  - NFR-TEST-003
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../05-specs/api/README.md
  - ../../05-specs/data/README.md
  - ../platform/ci-001-github-actions-quality-gate.md
  - ../integration/ext-001-reference-verification.md
  - ../../00-overview/scope.md
  - ../../03-team/roles.md
  - ../adr-traceability.md
  - ../adr-backlog.md
  - perf-001-k6-load-testing.md
supersedes: []
superseded_by: null
---

# ADR-TEST-001 계층별 자동화 테스트 전략

## 1. 상태

Accepted

## 2. 결정 요약

단위 테스트는 JUnit 5·Mockito, 통합 테스트는 Spring Boot Test·Testcontainers 2.0.5, 외부 API 대체는 WireMock을 사용한다.

## 3. 배경

맛잇온의 핵심 도메인은 Restaurant·Visit·Creator·Video 네 엔티티가 맺는 관계로 구성되고, 관리자 등록은 필수값·중복·참조·원자성 규칙을 동시에 지켜야 한다([NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-003](../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성)). 또한 [scope.md](../../00-overview/scope.md)는 "영상 연결 정보가 없는 맛집도 목록과 상세에서 기본 정보를 조회할 수 있어야 한다"고 명시하므로, Kakao·YouTube 같은 외부 의존성의 장애가 이미 저장된 데이터의 조회 경로를 막아서는 안 된다([NFR-EXTERNAL-001](../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)). 이 두 가지—관계형 정합성 규칙과 외부 의존성 실패 격리—는 목 객체만으로는 실제로 지켜지는지 확인하기 어렵고, 실제 저장소·실제 실패 시나리오를 재현하는 테스트가 필요하다.

동시에 이 프로젝트는 4명의 백엔드 개발자가 각자 하나의 워크스트림([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))을 처음부터 끝까지 책임지는 구조다([roles.md](../../03-team/roles.md) 3장). [roles.md](../../03-team/roles.md)는 "개인별 기술 역량과 선호도는 확인되지 않았다"고 명시하므로, 특정 팀원이 Testcontainers나 WireMock에 이미 능숙하다는 전제로 전략을 세울 수 없다. [roles.md](../../03-team/roles.md) 8장의 공통 책임은 "정상·예외·경계 조건을 포함한 자동화 테스트를 작성한다"를 모든 팀원의 의무로 규정하므로, 이 ADR이 정하는 계층 구분은 특정 담당자가 아니라 4명 각자가 자신의 워크스트림에 적용할 수 있어야 한다.

이 ADR의 문서 소유자는 박진영이지만, 이는 [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)(맛집 상세)가 Restaurant·Visit·Creator·Video 네 영역을 모두 조합하는 워크스트림이라 관계 정합성·부분 실패 테스트 위험이 가장 크기 때문에 초안 작성 책임을 맡은 것이지, 다른 워크스트림의 테스트를 대신 작성한다는 뜻은 아니다([roles.md](../../03-team/roles.md) 6장).

## 4. 결정 문제

변경 위험과 외부 의존성을 어떤 테스트 계층과 도구로 검증할 것인가. 제약 조건은 다음과 같다: [scope.md](../../00-overview/scope.md) 6번 경계 규칙에 따라 4명이 MVP 기간 내 서로 독립적으로 개발·검증할 수 있어야 하고, 초기 월 인프라 예산 목표 150,000원([adr-traceability.md](../adr-traceability.md)) 안에서 상시 운영되는 공유 스테이징 환경을 추가로 유지할 여유가 없으며, [NFR-MAINTAINABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도)은 4명이 이해·운영할 수 없는 불필요한 분산 구성요소를 금지한다.

## 5. 고려한 선택지

- **단위·통합·외부 계약 계층 분리**: 빠른 단위 테스트, 실제 Postgres·Redis를 사용하는 통합 테스트, 외부 API를 대체하는 계약 테스트를 각각 둔다.
- **Mock 중심 단일 계층**: 모든 저장소·외부 호출을 목 객체로 대체하고 단위 테스트만으로 검증한다. 이 방식은 실행은 빠르지만, [NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-003](../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성)이 요구하는 실제 유니크 제약·트랜잭션 원자성이 Postgres에서 실제로 지켜지는지는 검증하지 못한다. 단계별 로컬 통합 전에 실제 저장소 동작을 확인하지 못하는 전략은 정합성 문제를 뒤로 미루므로 허용하지 않는다.
- **공유 개발·운영 서비스에 연결한 테스트**: 팀이 공유하는 상시 운영 Postgres·Redis 환경에 테스트를 연결한다. 이는 초기 월 150,000원 예산 목표([adr-traceability.md](../adr-traceability.md))를 넘어서는 상시 운영 인프라를 추가로 요구하고, 4명이 각자 독립적으로 개발해야 한다는 [scope.md](../../00-overview/scope.md) 6번 제약과 충돌한다. 공유 가변 환경에서는 한 팀원의 테스트 데이터가 다른 팀원의 테스트 결과에 영향을 줄 수 있어 독립적 개발·검증이 어려워진다.

## 6. 결정

빠른 단위 테스트(JUnit 5·Mockito)로 도메인 규칙을 검증하고, 컨테이너 기반 통합 테스트(Spring Boot Test·Testcontainers 2.0.5)로 실제 PostgreSQL·Redis 동작을 검증하며, 외부 HTTP 호출은 WireMock으로 대체해 계약과 장애 시나리오를 검증한다. 통합 테스트용 컨테이너는 테스트 실행 시점에만 기동되고 상시 운영되지 않으므로 별도의 고정 비용 인프라를 추가하지 않는다.

세 계층의 역할은 겹치지 않게 나눈다. 단위 테스트는 저장소·외부 호출 없이 순수 도메인 규칙(중복 판정 조건, 카테고리 검증 등)을 검증하고, 통합 테스트는 그 규칙이 실제 스키마 제약·트랜잭션 위에서도 유지되는지 검증하며, WireMock 기반 계약 테스트는 Kakao·YouTube 응답에 대한 어댑터의 동작만 검증한다. 한 계층에서 이미 검증한 것을 다른 계층에서 중복 검증하지 않도록 각 워크스트림 담당자가 계층별 테스트 목록을 요구사항 ID와 함께 관리한다.

## 7. 선택 근거

- 단위 테스트의 빠른 실행 속도는 4명이 각자 독립적으로 개발·검증해야 한다는 [scope.md](../../00-overview/scope.md) 6번 제약을 직접 지원한다. 컨테이너를 기동하지 않고도 도메인 규칙(예: 중복 판정, 등록 원자성)을 빠르게 반복 검증할 수 있어 각 워크스트림 담당자의 개발 루프를 막지 않는다.
- Testcontainers는 실제 Postgres·Redis 위에서 유니크 제약, 트랜잭션 원자성, JPA 매핑 같은 [NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-003](../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성) 요구를 목 객체 없이 검증하면서도, 컨테이너가 테스트 실행 시에만 뜨고 꺼지므로 상시 운영 환경을 추가로 유지할 필요가 없다. 이는 초기 월 150,000원 예산 목표에 맞는 방식이다.
- WireMock은 [WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자(김인안)가 Kakao·YouTube 실패 시나리오([ADR-EXT-001](../integration/ext-001-reference-verification.md))를 실제 외부 서비스 없이 재현하게 하고, [WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 담당자(박진영)가 영상·외부 링크 장애 중에도 이미 저장된 맛집 기본 정보 조회가 성공하는지([scope.md](../../00-overview/scope.md)의 "영상 연결 정보가 없는 맛집도..." 요구, [NFR-EXTERNAL-001](../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)) 검증하게 한다.

## 8. 트레이드오프

컨테이너 기반 통합 테스트는 단위 테스트보다 실행 시간이 길고, 각 워크스트림 담당자가 자신의 Fixture와 테스트 데이터를 관리해야 하는 부담이 생긴다. 이 비용은 단위 테스트를 기본 개발 루프로 삼고, 컨테이너 통합 테스트를 실제 제약·관계 동작으로 좁혀 제한한다. 단계별 완료 전에 실제 정합성 문제를 탐지하고 향후 운영 배포의 수동 복구 위험을 줄이기 위해 필요한 비용으로 본다.

## 9. 적용 범위

모든 워크스트림([WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)), 공통 인증·데이터·외부 연동과 배포 후보에 적용한다. 이 ADR의 문서 소유자는 박진영이지만, [roles.md](../../03-team/roles.md) 8장의 공통 책임에 따라 실제 테스트 작성은 각 워크스트림 최종 책임자(양성훈=[WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색), 박진영=[WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), 이우람=[WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), 김인안=[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록))가 자신의 기능에 대해 수행한다.

## 10. 강제 규칙

- 요구사항 ID와 테스트를 추적한다([NFR-MAINTAINABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도), Critical·High 요구사항마다 검증 근거 연결).
- 운영 엔드포인트 없이 정상·경계·오류·동시성 시나리오를 실행한다. 특히 동시성 시나리오는 [NFR-INTEGRITY-002](../../01-requirements/non-functional-requirements.md#nfr-integrity-002-중복-및-동시-등록-방지)(동일 대상 동시 등록 시 하나만 생성)를 검증하는 데 필수적이다.
- 관리자 등록의 부분 실패 시나리오는 반드시 부분 저장 0건을 확인한다([NFR-INTEGRITY-003](../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성)).

## 11. 금지 사항

- 운영 DB·Redis·외부 API 직접 사용: 단일 EC2·150,000원 예산 목표 안에서는 테스트 전용 별도 스테이징 환경이 없으므로, 운영 자원을 직접 사용하는 테스트는 운영 데이터를 오염시킬 위험이 있다.
- 구현 세부만 검증하는 취약한 Mock 남용: 개인별 기술 역량이 확인되지 않은 상태([roles.md](../../03-team/roles.md))에서 과도한 목 객체 사용은 실제 관계형 동작의 오류를 가려 배포 후에야 드러나게 할 수 있다.
- AI 생성 코드의 테스트 면제: [roles.md](../../03-team/roles.md)의 AI 활용 책임은 "AI 생성 코드도 동일한 코드 리뷰, 자동화 테스트와 보안 검증을 거친다"고 명시하므로 예외를 두지 않는다.

## 12. 구현 및 운영 영향

- CI에서 컨테이너를 실행하는 시간을 관리한다. 컨테이너는 테스트 실행 시에만 기동되므로 상시 비용은 없지만 CI 파이프라인 소요 시간에는 반영된다.
- 4명이 동시에 작업할 때 테스트 데이터가 충돌하지 않도록 격리한다(워크스트림별 독립 Fixture, 공유 상태 최소화).
- WireMock 계약 파일의 위치와 버전 관리 기준을 정하고, 실패 진단 산출물(로그, 리포트)을 CI 아티팩트로 보관한다.
- 외부 계약(Kakao·YouTube 응답 스키마)이 실제로 바뀌었을 때 WireMock 스텁이 낡은 계약을 그대로 통과시키는 위험이 있으므로, 스텁 갱신 시점과 책임자([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당 김인안)를 문서화한다.
- 동시성 테스트(중복·동시 등록)는 컨테이너 통합 테스트 계층에서만 수행하고, 단위 테스트에서는 흉내 내지 않는다. 목 객체로 동시성을 흉내 내면 실제 DB 락·유니크 제약 동작과 다르게 통과할 위험이 있기 때문이다.

## 13. 검증 방법

테스트 목록과 요구사항 추적성을 확인하고 CI에서 반복 실행하되, 다음을 구체적 통과 기준으로 삼는다.

- 도메인 정합성: [NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-004](../../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리)에 대응하는 통합 테스트(필수값·참조 무결성, 중복·동시 등록, 등록 원자성, 외부 링크 실패와 내부 데이터 분리)가 존재하고 통과해야 한다. 구체적으로 [scope.md](../../00-overview/scope.md) 3.4의 중복 판정 기준(동일 place id, 동일 채널 id, 동일 영상 id, 동일 (맛집, 유튜버, 영상) 조합)을 각각 재현하는 테스트가 있어야 한다.
- 외부 장애 격리: WireMock으로 Kakao·YouTube 응답 지연·실패·계약 변경을 재현했을 때, 이미 저장된 맛집의 목록·상세 기본 정보 조회가 계속 성공하는지 확인한다([scope.md](../../00-overview/scope.md), [NFR-EXTERNAL-001](../../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)).
- 성능·오류율 기준: 계약·통합 테스트는 일반 조회 p95 500ms, 검색·필터 조합 p95 800ms, 관리자 등록(외부 대기 제외) p95 1초, 정상 부하 서버 오류율 1% 미만이라는 기준을 위반하는 회귀를 탐지해야 한다. 운영 동급 단일 EC2, 기준 데이터 100%, WireMock 외부 Stub, 정상 부하 50명·20 RPS와 최대 부하 200명·80 RPS를 사용한다. 부하 생성 도구와 실행 체계는 [ADR-PERF-001](perf-001-k6-load-testing.md)이 소유한다(k6 v2.1.0, `perf/k6/` 시나리오, `workflow_dispatch` 전용 실행).
- 배포 게이트: 필수 빌드·테스트가 실패한 변경은 운영 배포 후보로 승인되지 않는다([NFR-TEST-003](../../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트)).

## 14. 재검토 조건

테스트 실행 시간이 각 워크스트림 담당자의 독립적 개발 속도를 지속적으로 저해할 때, 초기 기준 데이터 규모나 목표 부하가 변경될 때, 또는 [ADR-PERF-001](perf-001-k6-load-testing.md)이 정한 도구·버전·부하 모델이 바뀔 때 재검토한다.

## 15. 관련 문서

- [NFR](../../01-requirements/non-functional-requirements.md)
- [CI ADR](../platform/ci-001-github-actions-quality-gate.md)
- [외부 기준정보 확인 ADR](../integration/ext-001-reference-verification.md)
- [ADR-PERF-001 k6 부하 테스트 도구와 실행 체계](perf-001-k6-load-testing.md)
