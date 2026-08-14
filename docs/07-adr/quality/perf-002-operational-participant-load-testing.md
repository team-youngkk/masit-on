---
id: ADR-PERF-002
title: 검증 참여자 전용 운영 직접 부하 검증 예외
status: Accepted
decision_date: 2026-08-14
owners:
  - 이우람
reviewers:
  - 양성훈
  - 박진영
  - 김인안
related_requirements:
  - NFR-PERFORMANCE-006
  - NFR-PERFORMANCE-007
  - NFR-COST-001
  - RV-NFR-011
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../08-planning/second-expansion-performance-verification.md
  - ../../08-planning/third-expansion-final-gate-result.md
  - ../../08-planning/third-expansion-test-matrix.md
  - perf-001-k6-load-testing.md
  - ../platform/deploy-002-validation-deployment-before-expansion.md
  - ../platform/deploy-003-validation-cookie-session.md
  - ../integration/route-001-kakao-mobility-course-routing.md
  - ../../troubleshooting/pr-185-e3-t13-final-gate-review.md
  - ../../../perf/operational-fixture/README.md
  - https://github.com/team-youngkk/masit-on/issues/190
supersedes: []
superseded_by: null
---

# ADR-PERF-002 검증 참여자 전용 운영 직접 부하 검증 예외

## 1. 상태

Accepted. 이 결정은 이슈 [#190](https://github.com/team-youngkk/masit-on/issues/190)의 일회성 검증 범위에만 적용한다.

## 2. 문맥

[ADR-PERF-001](perf-001-k6-load-testing.md)의 기본 경로는 운영과 동급인 임시 EC2·RDS와 WireMock을 사용한다. 현재 운영은 정식 공개 전 검증 참여자만 접근할 수 있고, 별도 EC2를 만들지 않고 배포 완료된 운영 인스턴스에서 실제 배포 산출물과 운영 PostgreSQL·Redis를 확인하기로 했다.

운영 직접 측정은 임시 환경과 같은 의미가 아니다. 운영의 기존 데이터 분포와 검증 참여자 트래픽이 섞이고, 같은 EC2에서 k6를 실행하면 부하 생성기 자원도 서버 지표에 포함된다. 따라서 이 예외의 결과는 운영 동급 기준 데이터에 대한 독립적인 성능 인증이 아니라, 제한 공개 중인 실제 배포의 회귀·용량 관찰 증거로 기록한다.

## 3. 결정

- 측정은 운영 EC2 내부에서 SSM으로 실행하고 대상은 애플리케이션의 내부 `127.0.0.1:8080` 경로로 제한한다. k6는 v2.1.0을 사용한다.
- 기존 `perf/seed/` 전체 시드는 운영에 사용하지 않는다. 운영에는 `perf/operational-fixture/`의 RUN_ID 기반 최소 합성 데이터만 사용한다.
- fixture는 합성 공개·활성 맛집 25건, `example.invalid` 회원 25건, 찜 500건, 좌표 보유 맛집 5건, 합성 공개 큐레이션 1건과 그 맛집 연결 20건만 추가한다. 기존 활성 관리자 계정은 작성자로 재사용하며 관리자 계정 자체·크리에이터·영상·Visit와 기존 DRAFT 큐레이션은 변경하지 않는다.
- fixture 적재 전 RDS 스냅샷, DB명·중복 RUN_ID·활성 관리자 계정 사전 점검, 검증 참여자 공지를 완료한다. 참여자 참조가 발견되면 cleanup은 중단한다.
- 정상 부하 `50 VU / 20 RPS`와 최대 부하 `200 VU / 80 RPS`를 각각 실행하되, 운영 상태 악화·오류율 증가·자원 임계 초과 시 즉시 중단한다. threshold는 [NFR-PERFORMANCE-006](../../01-requirements/non-functional-requirements.md#nfr-performance-006-2차-확장-공개-조회와-인기-집계-성능)을 낮추지 않는다.
- 실제 Kakao·YouTube·Gemini 유료 호출과 비밀정보 출력을 측정에 포함하지 않는다. 코스 측정은 운영 Mobility free-tier 검증과 Redis 월별 quota 잔여량을 사전 확인하고, 명시한 작은 요청 예산을 넘기지 않을 때만 별도로 실행한다. quota가 확인되지 않으면 코스 측정은 보류한다.
- 결과에는 실행 커밋, 운영 인스턴스·DB 식별자, fixture RUN_ID, 시작·종료 시각, 부하 프로필, k6 결과, 오류율, 컨테이너·DB·Redis 지표를 기록한다. 이 항목이 일부라도 누락되면 결과는 부분 운영 관찰 증거로만 분류하며 용량 승인이나 `ADR-PERF-001` 성능 인증으로 승격하지 않는다. 운영 데이터 규모가 `RV-NFR-002`와 다르므로 이 결과만으로 전체 기준 데이터 규모 충족을 주장하지 않는다.

## 4. 정리와 복구

`99-cleanup.sql`은 `PRODUCTION_PERF_CLEANUP_APPROVED=true`가 없으면 실행되지 않는다. 정리 전에 fixture 맛집을 참조하는 `favorite`, `recent_restaurant_view`, `collection_restaurant`, `curation_restaurant`, `visit`, `ai_candidate_snapshot` 및 fixture 회원의 action token·collection·notification·submission·report·idempotency record·deletion job을 확인한다. 실제 참여자 참조가 하나라도 있으면 전체 정리를 중단하고 운영자 판단과 잔존 데이터를 이슈에 기록한다. cleanup은 참여자 요청과 회원 작업 Worker를 중지한 쓰기 공백 구간에 실행하며, 비-FK 테이블 잠금은 삭제 트랜잭션 동안만 유지하고 `COMMIT` 후 `ANALYZE`한다.

RDS 스냅샷은 정리 성공과 행 수 복원을 확인한 뒤에도 보존 정책에 따라 유지·삭제를 별도로 결정한다. 이 ADR은 데이터 복원이나 운영 중단을 자동화하지 않는다.

## 5. 적용 범위와 재검토

이 예외는 검증 참여자 전용 제한 공개 기간과 이슈 #190의 측정에만 유효하다. 정식 공개, 정기 회귀 측정, 기준 데이터 규모의 성능 인증, 외부 제공자 포함 코스 부하에는 적용하지 않으며 [ADR-PERF-001](perf-001-k6-load-testing.md)의 임시 환경 경로를 따른다. 운영 직접 부하가 사용자 영향 또는 quota·비용 위험을 만들면 즉시 폐기하고 임시 측정 환경으로 되돌린다.
