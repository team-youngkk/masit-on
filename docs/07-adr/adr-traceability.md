---
related_documents:
  - ../01-requirements/non-functional-requirements.md
  - ../02-analysis/mvp-workstreams.md
  - ../03-team/ownership.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../06-architecture/technology-policy.md
  - adr-index.md
  - adr-backlog.md
  - ../08-planning/second-expansion-test-matrix.md
  - ../08-planning/expansion-2-implementation-plan.md
  - ../08-planning/expansion-2-task-breakdown.md
  - platform/lang-001-java-21-runtime.md
  - platform/build-001-gradle-groovy.md
  - platform/frame-001-spring-boot.md
  - security/auth-001-spring-security-jwt.md
  - security/auth-003-confirmation-token.md
  - platform/web-001-frontend-platform.md
  - platform/web-002-data-state.md
  - platform/web-003-routing-boundary.md
  - platform/web-004-supported-browser-matrix.md
  - platform/deploy-003-validation-cookie-session.md
  - architecture/arch-001-domain-monolith.md
  - architecture/arch-002-external-ports-adapters.md
  - data/data-001-postgresql.md
  - data/data-002-database-placement.md
  - data/data-003-spring-data-jpa.md
  - data/data-004-flyway.md
  - data/data-005-redis-refresh-token.md
  - data/data-007-uuid-v4-identifiers.md
  - data/data-008-publication-lifecycle-soft-delete.md
  - integration/ext-001-reference-verification.md
  - integration/map-001-map-bounds-search.md
  - quality/test-001-automation-strategy.md
  - quality/perf-001-k6-load-testing.md
  - quality/obs-001-logging-observability.md
  - security/sec-001-secrets-workload-identity.md
  - platform/runtime-001-docker.md
  - platform/ci-001-github-actions-quality-gate.md
  - ../02-analysis/second-expansion-workstreams.md
  - ../02-analysis/third-expansion-domain-boundaries.md
  - ../02-analysis/third-expansion-workstreams.md
  - ../08-planning/third-expansion-evaluation-strategy.md
  - ../08-planning/third-expansion-test-matrix.md
  - ../08-planning/third-expansion-task-breakdown.md
  - data/data-011-popular-restaurant-request-time-aggregation.md
  - data/data-012-second-expansion-retention-cleanup.md
  - integration/notify-002-in-app-notification-reliability.md
---

# 맛잇온 ADR 추적성

## 1. 문서 목적

확정 기술 스펙의 모든 항목을 ADR, 기술 정책, Backlog 또는 운영 설정에 연결하고 MVP·확장 단계 적용 여부를 명확히 한다.

## 2. 기술 스펙 → ADR 매핑

| 기술 항목 | 현재 문서 상태 | 분류 | 관련 ADR | 분류 근거 |
|---|---|---|---|---|
| JDK 21.0.12 LTS | 고정 | Accepted ADR | [ADR-LANG-001](platform/lang-001-java-21-runtime.md) | 백엔드 런타임 기준선 |
| Gradle 8.14.3 + Groovy DSL | 고정 | Accepted ADR | [ADR-BUILD-001](platform/build-001-gradle-groovy.md) | 재현 가능한 빌드 체계 |
| Spring Boot 4.1.0 | 고정 | Accepted ADR | [ADR-FRAME-001](platform/frame-001-spring-boot.md) | 백엔드 프레임워크 기준선 |
| Spring Security 7.1.0 | BOM 파생·고정 | Duplicate or Derived Rule | [ADR-FRAME-001](platform/frame-001-spring-boot.md), [ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 버전은 Boot BOM 파생, 사용 방식은 인증 결정에 종속 |
| Node.js 24.18.0 LTS | 고정 | Accepted ADR | [ADR-WEB-001](platform/web-001-frontend-platform.md) | 프론트엔드 런타임 기준선 |
| Next.js 16.2.11 + TypeScript 7.0.2 | 고정 | Accepted ADR | [ADR-WEB-001](platform/web-001-frontend-platform.md) | 웹 프레임워크·언어 기준선 |
| Server Components `fetch` + TanStack Query 5.101.4 | 확정 | Accepted ADR | [ADR-WEB-002](platform/web-002-data-state.md) | 초기·상호작용 데이터 책임 분리, 정확한 버전 고정 |
| URL Query Parameter | 확정 | Accepted ADR | [ADR-WEB-002](platform/web-002-data-state.md) | 검색 상태의 공유·재현 |
| React `useState` | 확정 | Duplicate or Derived Rule | [ADR-WEB-002](platform/web-002-data-state.md) | 화면 지역 상태 구현 규칙 |
| 지원 브라우저 PC Chrome·Edge, Android Chrome | 확정 | Accepted ADR | [ADR-WEB-004](platform/web-004-supported-browser-matrix.md) | 인수 판정 대상 브라우저 매트릭스 |
| iPhone Safari | 검증 없이 지원 표방하지 않음 | Accepted ADR | [ADR-WEB-004](platform/web-004-supported-browser-matrix.md) | 실단말 검증 수단이 없어 지원 표방을 낮추고 해제 조건을 남김 |
| MVP 단일 모듈 | 확정 | Accepted ADR | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 초기 배포·테스트 단순화 |
| 도메인 중심 계층형 모놀리스 | 확정 | Accepted ADR | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 단일 모듈과 같은 구조 결정 문제 |
| Gradle 멀티모듈·독립 배포 | 범위 제외 | Post-MVP ADR | [ADR-ARCH-004](adr-backlog.md#adr-arch-004-멀티모듈독립-배포-전환) | 독립 확장·배포·소유권 근거가 생길 때 전환 |
| Port/Adapter | 확정 | Accepted ADR | [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 외부 서비스 변동 격리 |
| 서버 캐시·읽기 저장소·물리적 CQRS | 조건부 | Conditional ADR | [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴) | 동일 PostgreSQL Projection의 실측 한계 확인 전 도입 금지 |
| PostgreSQL 17.10 | 고정 | Accepted ADR | [ADR-DATA-001](data/data-001-postgresql.md) | 주 관계형 데이터베이스 |
| 개발 Docker PostgreSQL / 운영 RDS | 확정 | Accepted ADR | [ADR-DATA-002](data/data-002-database-placement.md) | 환경 분리와 운영 배치 |
| Spring Data JPA | 확정 | Accepted ADR | [ADR-DATA-003](data/data-003-spring-data-jpa.md) | 기본 ORM·Repository 전략 |
| Flyway 12.4.0 | 고정 | Accepted ADR | [ADR-DATA-004](data/data-004-flyway.md), [ADR-DATA-009](data/data-009-pre-release-migration-consolidation.md) | 스키마 변경 단일 경로, 적용된 마이그레이션의 환경 범위와 운영 배포 전 통합 예외 |
| QueryDSL | 조건부 | Conditional ADR | [ADR-SEARCH-001](adr-backlog.md#adr-search-001-querydsl-도입) | 복합 조회 필요성 확인 후 도입 |
| PostGIS | 기술 스펙 확정, 현재 범위 제외 | Accepted ADR | [ADR-MAP-001](integration/map-001-map-bounds-search.md) | WGS84 좌표 응답만 사용하고 뷰포트·거리·공간 검색은 제외 |
| pgvector | Post-MVP | Post-MVP ADR | [ADR-SEARCH-002](adr-backlog.md#adr-search-002-pgvector-자연어-검색rag) | 자연어 검색·RAG 제외 |
| Redis 8.8 전용 인스턴스 | 고정·관리자 Token 역할 확정 | Accepted ADR | [ADR-DATA-005](data/data-005-redis-refresh-token.md) | 관리자 Refresh Token 저장, 캐시·락은 별도 조건부 |
| Redis AOF `everysec` + RDB | 확정 설정 | Operational Configuration | Redis 역할 결정 후 운영 문서 | 아키텍처보다 영속화 설정값 |
| Redis 캐시 | 확정 기술 용도 | Conditional ADR | [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입) | 성능 병목·무효화 근거 없음 |
| Redis 관리자 Refresh Token | 사용자 확정 | Accepted ADR | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-DATA-005](data/data-005-redis-refresh-token.md) | 관리자 JWT 재발급·폐기 |
| Redis 회원 Refresh Token | 구현 범위 확정 | Accepted ADR | [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md) | 최대 3세션·원자 회전·재사용 탐지 |
| Redis 분산 락 | 확정 기술 용도 | Conditional ADR | [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입) | 자동 배치·다중 실행 미확정 |
| 격리 수준·락·upsert 동시성 제어 | 조건부 | Conditional ADR | [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어) | 기본 격리와 `UNIQUE`로 불충분하다는 동시성 근거 필요 |
| 관리자 Spring Security 7.1.0 + JWT | 사용자 확정 | Accepted ADR | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 관리자 인증·인가 기준 |
| 관리자 Refresh Token 보안 쿠키 | 사용자 확정 | Accepted ADR | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-DATA-005](data/data-005-redis-refresh-token.md) | Redis 저장·회전, HttpOnly·Secure 전달 |
| 확인 Token 저장·소비·재시도 | 결정 완료 (2026-07-27) | Accepted ADR | [ADR-AUTH-003](security/auth-003-confirmation-token.md) | PostgreSQL 해시·후보 Snapshot, 생성과 원자적 소비, 완료 결과 재현 |
| 관리자 등급·기능별 권한 | 범위 제외 | Post-MVP ADR | [ADR-AUTH-004](adr-backlog.md#adr-auth-004-관리자-권한-세분화) | MVP는 사전 발급 단일 `ADMIN` 역할 |
| 회원 JWT·Refresh Token | 구현 범위 확정 | Accepted ADR | [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md) | 관리자 audience·principal·쿠키와 분리 |
| springdoc-openapi 3.0.3 + Swagger UI | 고정 | Technology Policy | [ADR-FRAME-001](platform/frame-001-spring-boot.md) | 구현과 명세 대조 도구, 외부 계약 원문은 `docs/05-specs` |
| Kakao Local REST API V2 | 확정·MVP 필요 | Accepted ADR | [ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 맛집 장소 확인 |
| YouTube Data API v3 | 확정·MVP 필요 | Accepted ADR | [ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 채널·영상 확인 |
| 자동 재시도·Circuit Breaker·비동기 이벤트·Outbox | 조건부(회원 Action 메일만 Outbox) | Conditional ADR | [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), 메일은 [ADR-AUTH-005](security/auth-005-member-action-mail-outbox.md), 서비스 내 알림은 [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) | 사용자 알림은 DB 직접 저장, 그 밖의 외부 전달은 측정·SLA 승인 전 금지 |
| Kakao Maps JavaScript API V3 | 구현 범위 확정 | Accepted ADR | [ADR-MAP-001](integration/map-001-map-bounds-search.md) | 지도 SDK와 필터 기반 마커 조회를 포함하고 뷰포트 서버 조회·현재 위치·길찾기는 제외 |
| Kakao Mobility 자동차 경로 API | `/v1/directions`, 자동차 경로, 5분 TTL·캐시 없음·코스당 1회 호출 | Accepted ADR | [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md) | WS-16 코스 추천의 외부 경계 |
| Java + Jsoup | 확정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 자동 수집 제외 |
| Playwright | 필요 시 | Conditional ADR | [ADR-CRAWL-001](adr-backlog.md#adr-crawl-001-playwright-도입) | JS 렌더링 필요 검증 후 도입 |
| n8n | 확정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 자동 수집·동기화 제외 |
| Spring Scheduler | 보존 cleanup에 한해 채택 | Accepted ADR | [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md), [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) | 최근 기록과 2차 확장 보존 Command만 실행; 자동 수집·집계·동기화 제외 |
| Spring Batch 6.0.4 | 고정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 이력·재시작 배치 범위 없음 |
| 하루 1회 이상 최근 기록 cleanup | 1차 확장 적용 | Operational Configuration | [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md) | 신규 조회와 독립된 30일 경과 `recent_restaurant_view` 물리 삭제; 실패 관측·재시도 |
| AI 제공자·모델 | Gemini Free Tier global endpoint, `gemini-3-flash-preview` | Accepted ADR | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md) | 공개 YouTube URL 입력, 후보·검수·무료 quota·보존 기준 |
| JSON Schema + Prompt Template | Prompt `P1`, 결과 Schema `S1` | Accepted ADR | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md) | AI 후보 계약과 평가 기준에 연결 |
| 자연어 조건 해석 | P1 규칙 기반·태그 18종·태그 AND·`UNRESOLVED` | Accepted ADR | [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md) | 임베딩·RAG 없이 WS-14 조회 애플리케이션에서 처리 |
| AI 추출 비동기 Worker | Worker 1개/인스턴스·lease 120초·polling 5초·재시도 고정, 용량 실측은 최종 게이트 | Accepted ADR | [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md) | 작업 상태·복구·비용 격리 |
| JUnit 5 + Mockito | 확정 | Accepted ADR | [ADR-TEST-001](quality/test-001-automation-strategy.md) | 단위 테스트 기준 |
| Spring Boot Test + Testcontainers 2.0.5 | 고정 | Accepted ADR | [ADR-TEST-001](quality/test-001-automation-strategy.md) | 실제 저장소 통합 검증 |
| WireMock | 확정 | Accepted ADR | [ADR-TEST-001](quality/test-001-automation-strategy.md) | 외부 API 장애·계약 격리 |
| Spring Batch Test 6.0.4 | 파생·기능 제외 | Duplicate or Derived Rule | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | Spring Batch 활성화에 종속 |
| k6 v2.1.0 | 도구·버전·실행 비용 확정 (2026-08-06) | Accepted ADR | [ADR-PERF-001](quality/perf-001-k6-load-testing.md) | `perf/k6/` 시나리오, `workflow_dispatch` 전용 실행, 정기 CI 비용 증가 없음 |
| SLF4J + Logback | 확정 | Accepted ADR | [ADR-OBS-001](quality/obs-001-logging-observability.md) | 애플리케이션 로그 기준 |
| Actuator + CloudWatch | 기술 선택 확정, 적용 시점 이관 | Accepted ADR | [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | Actuator는 전 단계, CloudWatch는 초기 운영 배포부터 적용 |
| 로그 보관 14일 | M2부터 적용 | Operational Configuration | [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | AWS 운영 시작 후 14일 유지 |
| Parameter Store SecureString + KMS | M2부터 적용 | Accepted ADR | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 운영 비밀정보 보호 |
| EC2 IAM Role | M2부터 적용 | Accepted ADR | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 장기 AWS 키 제거 |
| GitHub Actions OIDC | M2부터 적용 | Accepted ADR | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | CI의 단기 AWS 자격 증명 |
| Docker | 확정 | Accepted ADR | [ADR-RUNTIME-001](platform/runtime-001-docker.md) | 재현 가능한 실행·배포 산출물 |
| GitHub Actions 빌드·테스트 | 확정 | Accepted ADR | [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 배포 후보 품질 게이트 |
| Nginx | 경로 경계 결정 완료 (2026-07-27) | Accepted ADR | [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md) | `/api/**`는 Spring Boot, 나머지 외부 경로는 Next.js, `/internal/**`은 외부 차단 |
| 검증 참여자 제한 공개 | 쿠키 세션 전환 확정 (2026-08-03) | Accepted ADR | [ADR-DEPLOY-003](platform/deploy-003-validation-cookie-session.md) | Basic Auth를 제거하고 7일 HttpOnly 쿠키·Redis 세션·Nginx `auth_request` 사용; 정식 공개 시 전체 제거 |
| Amazon ECR·EC2 | 기술 선택 확정, 초기 운영 배포부터 적용 | Accepted ADR | [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | M2부터 단일 EC2에 배포하고 확장 단계별 변경 반영 |
| ALB·ASG·Blue-Green | 착수 시점 합의 (2026-07-28), 비용·일정 영향 검토 미완 | Scope Conflict Review | [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 초기 운영 배포는 단일 인스턴스 수동 복구, 영향 검토 통과 후 착수 |
| GitHub Actions → ECR → EC2 | 초기 운영 배포부터 적용 | Accepted ADR | [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 빌드·테스트 품질 게이트를 유지하고 M2부터 AWS 배포 경로 활성화 |
| Amazon S3 이미지 저장 | 확정이나 기능 없음 | Post-MVP ADR | [ADR-MEDIA-001](adr-backlog.md#adr-media-001-s3-사용자-이미지-저장) | 이미지 업로드·사용자 이미지 요구사항 없음 |
| FCM HTTP v1 | 외부 채널 범위 제외 | Post-MVP ADR | [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림), 현재 서비스 내 저장은 [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) | 채널·동의·DeviceToken·전달 SLA 미승인 |
| 초기 월 인프라 예산 15만 원 | 목표 | Operational Configuration | 배포 토폴로지 Backlog | 운영 제약·조정 가능한 수치 |

## 3. NFR → ADR 매핑

| NFR | 관련 ADR | 적용 |
|---|---|---|
| [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제)~[NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-AUTH-004](adr-backlog.md#adr-auth-004-관리자-권한-세분화), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DEPLOY-003](platform/deploy-003-validation-cookie-session.md) | 관리자 접근, 인증 matcher, 확인 Token 무결성, 권한 확장, 입력·비밀 보호, 제한 공개 세션 분리 |
| [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리) | [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-DATA-004](data/data-004-flyway.md), [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 참조·원자성·동시 등록·확인 Token 재사용·외부 실패 격리 |
| [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책)~[NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리) | [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 오류 경계, 재시도·회로 차단·이벤트 전달, 장애 검증 |
| [NFR-AVAILABILITY-001](../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)~[NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-초기-운영-배포-가용성과-수동-복구) | [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 로컬 상태 확인과 초기 운영 배포 수동 복구 |
| [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)~[NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보) | [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-EXT-001](integration/ext-001-reference-verification.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 원본 미저장, 외부 호출 격리, 키 보호 |
| [NFR-ACCURACY-001](../01-requirements/non-functional-requirements.md#nfr-accuracy-001-자연어-검색-정확도와-평가-데이터)·[NFR-PERFORMANCE-007](../01-requirements/non-functional-requirements.md#nfr-performance-007-자연어-검색과-경로-응답-시간) | [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md), [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md) | 자연어 구조화 조건과 코스 외부 호출의 품질·응답 경계 |
| [NFR-ACCURACY-002](../01-requirements/non-functional-requirements.md#nfr-accuracy-002-ai-추출-정확도재현율자동-등록-정밀도)·[NFR-INTEGRITY-006](../01-requirements/non-functional-requirements.md#nfr-integrity-006-ai-환각과-잘못된-장소-연결-방지) | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md), [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md) | 후보 격리·자동 등록·예외 보정·정식 등록 0건·비동기 재현성 |
| [NFR-RELIABILITY-005](../01-requirements/non-functional-requirements.md#nfr-reliability-005-ai-비동기-작업-복구)·[NFR-EXTERNAL-005](../01-requirements/non-functional-requirements.md#nfr-external-005-ai와-mobility-timeoutrate-limit재시도)·[NFR-AVAILABILITY-003](../01-requirements/non-functional-requirements.md#nfr-availability-003-ai-모델외부-api-장애-격리) | [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md), [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | timeout·rate limit·복구·기존 기능 격리 |
| [NFR-SECURITY-007](../01-requirements/non-functional-requirements.md#nfr-security-007-prompt-injection과-악성-ai-입력-방어)·[NFR-PRIVACY-006](../01-requirements/non-functional-requirements.md#nfr-privacy-006-ai-입력저작권자막-보존-경계) | [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md), [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 악성 입력·원문·자막·외부 키·보존·접근 경계 |
| [NFR-COST-001](../01-requirements/non-functional-requirements.md#nfr-cost-001-ai임베딩mobility-호출-비용-상한) | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md), [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md), [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md) | AI·Mobility quota·retry·비용 hard stop |
| [NFR-TEST-006](../01-requirements/non-functional-requirements.md#nfr-test-006-3차-확장-품질과-완료-게이트) | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-PERF-001](quality/perf-001-k6-load-testing.md), 3차 Accepted ADR 전체 | 기능 평가·장애·복구·2차 부하 승계 게이트 |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)~[NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단) | [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 요청 추적·지표·민감정보 차단 |
| [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층)~[NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트) | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 테스트 계층과 배포 품질 게이트 |
| [NFR-DEPLOYMENT-001](../01-requirements/non-functional-requirements.md#nfr-deployment-001-재현-가능한-빌드와-환경-분리)~[NFR-DEPLOYMENT-002](../01-requirements/non-functional-requirements.md#nfr-deployment-002-배포-전후-검증) | [ADR-BUILD-001](platform/build-001-gradle-groovy.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 재현 빌드, 로컬 통합과 초기 운영 배포 검증 |
| [NFR-DEPLOYMENT-003](../01-requirements/non-functional-requirements.md#nfr-deployment-003-버전-추적과-복구-절차)~[NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-단계별-실행-및-초기-운영-배포-복잡도-제한) | [ADR-DATA-004](data/data-004-flyway.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md), [ADR-DEPLOY-003](platform/deploy-003-validation-cookie-session.md) | 단계별 실행, 초기 운영 배포 복구·복잡도와 제한 공개 쿠키 세션 제거 가능성 |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계)~[NFR-MAINTAINABILITY-003](../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도) | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴), [ADR-ARCH-004](adr-backlog.md#adr-arch-004-멀티모듈독립-배포-전환) | 책임 경계, 조회 확장, 배포 경계와 운영 복잡도 제한 |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화)~[NFR-PRIVACY-004](../01-requirements/non-functional-requirements.md#nfr-privacy-004-위치와-행동-데이터-최소화) | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md) | 회원 데이터 최소화·탈퇴 파기·최근 기록 30일 cleanup과 비밀 보호 |
| [NFR-PERFORMANCE-006](../01-requirements/non-functional-requirements.md#nfr-performance-006-2차-확장-공개-조회와-인기-집계-성능) | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md), [ADR-PERF-001](quality/perf-001-k6-load-testing.md) | 실시간 PostgreSQL 집계·실행계획·부하 기준, 선제 캐시 금지, k6 v2.1.0 정상 부하 판정 |
| [NFR-INTEGRITY-005](../01-requirements/non-functional-requirements.md#nfr-integrity-005-처리-상태와-알림-원자성)·[NFR-RELIABILITY-004](../01-requirements/non-functional-requirements.md#nfr-reliability-004-실시간-집계와-서비스-내-알림-복구-경계) | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md), [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) | 과거 순위 fallback 없음, 상태·이력·알림 같은 트랜잭션, Outbox 없음 |
| [NFR-PRIVACY-005](../01-requirements/non-functional-requirements.md#nfr-privacy-005-2차-확장-개인정보-보존과-회원-탈퇴) | [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md), [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) | 식별 제거·알림 보존·탈퇴, Preference·DeviceToken 미저장 |
| [NFR-COMPATIBILITY-001](../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성) | [ADR-WEB-001](platform/web-001-frontend-platform.md), [ADR-WEB-004](platform/web-004-supported-browser-matrix.md) | 지원 표방 브라우저 3종과 화면 폭 5종, iPhone Safari 지원 표방 보류 |

## 4. API → ADR 매핑

| API 영역 | 관련 ADR | 경계 |
|---|---|---|
| 공개 탐색·상세 API | [ADR-WEB-002](platform/web-002-data-state.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴), [ADR-DATA-003](data/data-003-spring-data-jpa.md) | `/api` 경로와 계약은 `docs/05-specs/api/`가 소유 |
| 관리자 인증 API | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-DATA-005](data/data-005-redis-refresh-token.md) | 세부 matcher, JWT Bearer, Redis Refresh Token 보안 쿠키 사용 |
| 관리자 기준정보 등록 API | [ADR-EXT-001](integration/ext-001-reference-verification.md), [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | Kakao·YouTube 확인, 확인 Token, 실패·키 격리 |
| 검증 참여자 세션 API | [ADR-DEPLOY-003](platform/deploy-003-validation-cookie-session.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙)이 정식 공개 전 쿠키·Redis·Nginx 진입 경계를 소유하고 공개 전환 시 제거 |
| 전체 API | [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-OBS-001](quality/obs-001-logging-observability.md) | `/api` 전달, 계약·장애 테스트와 요청 추적 |
| 인기 맛집 API | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md) | 현재 Favorite 요청 시 집계, Snapshot·Batch·Redis 캐시 없음 |
| 제보·신고 상태와 사용자 알림 API | [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md), [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) | 상태·이력·알림 원자 저장, 외부 전달 없음, 독립 보존 cleanup |
| 자연어 검색 API | [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md), [ADR-WEB-002](platform/web-002-data-state.md), [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 구조화 조건·직접 필터 우선·기존 목록 계약 재사용 |
| AI 추출 작업·자동 등록 API | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md), [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 후보 상태·비동기 작업·자동 검증·예외 보정·정식 등록 |
| 맛집 코스·경로 API | [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 선택 맛집 좌표·자동차 경로·외부 실패·초기 비저장 |

## 5. 데이터 모델 → ADR 매핑

| 데이터 범위 | 관련 ADR | 비고 |
|---|---|---|
| MVP 관계형 데이터 | [ADR-DATA-001](data/data-001-postgresql.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md) | 엔티티·관계 원문은 `docs/05-specs/data/` |
| 스키마 변경 | [ADR-DATA-004](data/data-004-flyway.md) | Flyway만 사용 |
| 환경별 DB | [ADR-DATA-002](data/data-002-database-placement.md) | 개발 Docker / 운영 RDS, 버전 17.10 일치 |
| 확인 Token 단기 상태 | [ADR-AUTH-003](security/auth-003-confirmation-token.md) | PostgreSQL 해시·후보 JSONB, 10분 만료, 완료·만료 결과 24시간 보관 |
| 동시 쓰기 충돌 | [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입) | 기본 `UNIQUE` 이후 강화는 통합 테스트 근거 필요 |
| 내부 식별자 | [ADR-DATA-007](data/data-007-uuid-v4-identifiers.md) | Accepted; UUID v4와 PostgreSQL `uuid` |
| 공개·삭제 생명주기 | [ADR-DATA-008](data/data-008-publication-lifecycle-soft-delete.md) | Accepted; 상태 분리·논리 삭제·FK RESTRICT |
| 공간·벡터 데이터 | [ADR-MAP-001](integration/map-001-map-bounds-search.md), [ADR-SEARCH-002](adr-backlog.md#adr-search-002-pgvector-자연어-검색rag) | 1차 확장은 nullable WGS84 좌표와 필터 기반 마커 조회만 허용, 뷰포트 서버 조건·PostGIS·pgvector는 금지 |
| 사용자·토큰·기기 데이터 | [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md), [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md), [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림) | 서비스 내 Notification만 저장; Preference·DeviceToken은 Post-MVP |
| 최근 본 맛집 보존 | [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md) | 30일 경과 행의 독립 cleanup과 GET 읽기 전용 경계 |
| 2차 확장 집계·보존 | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md), [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) | 인기 결과 비저장, 제보·신고·알림·멱등 기록 독립 cleanup |
| AI 작업·후보·자동 등록 상태 | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md), [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md), [ADR-DATA-001](data/data-001-postgresql.md), [ADR-DATA-004](data/data-004-flyway.md) | Snapshot·작업 상태·보존·중복·claim·예외 보정 데이터 계약은 Accepted 논리 계약 |
| 코스 경로 결과·캐시 | [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md), [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입) | 초기 비저장·캐시 조건부, TTL·무효화는 후속 계약 |

## 6. Workstream → ADR 매핑

| Workstream | 필수 ADR | 추가 책임 |
|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | [ADR-WEB-001](platform/web-001-frontend-platform.md)~[ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 화면·API 경로, 검색 상태·최종 조회 조합 |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md)~[ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 외부 링크 실패 격리·상세 조합 |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | Visit 관계 판정 경계 |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-EXT-001](integration/ext-001-reference-verification.md), [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md)~[ADR-DATA-004](data/data-004-flyway.md), [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 관리자 화면·API 경로, 인증·확인 Token·외부 확인·등록 정합성 |
| [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙) | [ADR-DEPLOY-003](platform/deploy-003-validation-cookie-session.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md), [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 검증 참여자 쿠키·Redis·Nginx 진입 경계, Basic Auth 제거, 정식 공개 시 전체 제거 |
| [WS-09](../02-analysis/second-expansion-workstreams.md#4-ws-09-개인-컬렉션) | 기존 데이터·인증 ADR | 회원 소유·동시성은 API·데이터 계약으로 충분하며 새 구조 ADR 없음 |
| [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md) | 실시간 집계와 성능 측정 소유 |
| [WS-11](../02-analysis/second-expansion-workstreams.md#6-ws-11-관리자-큐레이션) | 기존 인증·PostgreSQL ADR | 관리자 편집형 저장 모델, 추천·이미지·캐시 제외 |
| [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토) | [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md), [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) | 상태 트랜잭션과 식별 제거 소유 |
| [WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md), [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md), [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림) | DB 알림·읽음·보존 소유, FCM 비활성 |
| [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md), [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 자연어 조건 해석·기존 검색 계약·평가 회귀 |
| [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md), [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | AI 후보·자동 등록·예외 보정·비동기·제공자·비용·복구 |
| [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | Mobility 자동차 경로·좌표·부분 실패·비용 |
| [QUALITY-EVAL](../02-analysis/third-expansion-workstreams.md#8-quality-eval-교차-품질-트랙) | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-PERF-001](quality/perf-001-k6-load-testing.md), 3차 Accepted ADR 전체 | 골든 Dataset·Evaluator·2차 부하 승계·출시 게이트 |
| 전체 | [ADR-LANG-001](platform/lang-001-java-21-runtime.md), [ADR-BUILD-001](platform/build-001-gradle-groovy.md), [ADR-FRAME-001](platform/frame-001-spring-boot.md), [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 공통 구현·운영 기준 |

## 7. 기술 정책 → ADR 매핑

| 기술 정책 | 근거 ADR |
|---|---|
| 고정 버전·BOM·범위 버전 금지 | [ADR-LANG-001](platform/lang-001-java-21-runtime.md), [ADR-BUILD-001](platform/build-001-gradle-groovy.md), [ADR-FRAME-001](platform/frame-001-spring-boot.md), [ADR-WEB-001](platform/web-001-frontend-platform.md), [ADR-DATA-004](data/data-004-flyway.md) |
| 개발·테스트·운영 분리 | [ADR-DATA-002](data/data-002-database-placement.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) |
| DB 스키마 변경 | [ADR-DATA-004](data/data-004-flyway.md) |
| Redis 역할 선확정 | [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입), [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입), [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md) |
| 동시성 강화 선확정 | [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입) |
| 조회 저장소·물리적 CQRS 선확정 | [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴), [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입) |
| 자동 복원력·비동기 전달 선확정 | [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달) |
| 비밀정보·워크로드 인증 | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) |
| 조건부·Post-MVP 선제 도입 금지 | 모든 Backlog 항목 |
| AI 생성 코드 검증 | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) |

## 8. 미매핑 기술 검토

모든 기술 스펙 항목은 위 표에서 ADR, 정책, Backlog 또는 운영 설정으로 분류됐다. 다음은 2026-07-24 결정 완료 항목이다.

- 관리자 JWT 만료(30분)·Redis Refresh Token TTL(14일, 회전+재사용 탐지)·Redis 장애 시 fail-closed 정책
- Nginx·ECR·EC2를 포함한 초기 운영 배포 토폴로지(단일 EC2 인스턴스)
- `/api` 화면·백엔드 분리, 관리자 인증 matcher와 `/internal` 상태 확인 경계
- ALB·ASG·Blue-Green: 초기 운영 배포에는 미도입, 3차 확장 이후 배포 고도화 단계에서 검토 (2026-07-28 착수 시점 합의, 비용·일정 영향 검토 미완)
- 로그 14일 보관, 백업(일 1회 자동 스냅샷·7일 보관), 운영 알림(CloudWatch→Slack, 담당자 1명. 팀 상시 채널이 Slack뿐이라는 근거는 [RV-NFR-013](../01-requirements/non-functional-requirements.md#rv-nfr-013-운영-알림-기준))

다음은 여전히 팀 결정이 필요한 미결정 항목이다.

- `UNIQUE` 이후 격리 수준·락·upsert 도입 기준
- 캐시·별도 읽기 저장소·물리적 CQRS 전환 기준
- 외부 전달의 자동 재시도·Circuit Breaker·비동기 이벤트 도입 기준 (Transactional Outbox는 회원 Action 메일에 한해 [ADR-AUTH-005](security/auth-005-member-action-mail-outbox.md)로 확정, 서비스 내 알림은 [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md)로 비동기 전달 불필요 결정)
- 멀티모듈·독립 배포와 세분화된 관리자 권한의 전환 기준
- Jsoup, n8n 등 정확한 버전이 없는 의존성 (k6는 [ADR-PERF-001](quality/perf-001-k6-load-testing.md)이 v2.1.0으로 고정해 2026-08-06 해소)
- 현재 구현 전 필수 팀 결정은 없다. ALB·Blue-Green 전환 자동화는 토폴로지 확장 시 새 ADR로 결정한다.

## 9. 2차 확장 ADR 검토 결과

| 검토 대상 | 결과 | 근거 |
|---|---|---|
| 인기 집계 | 요청 시점 PostgreSQL 집계 Accepted | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md) |
| 사용자 알림 저장 신뢰성 | 상태·이력·알림 단일 트랜잭션 Accepted | [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) |
| 보존 정리 실행 | 제한적 Spring Scheduler Accepted | [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) |
| FCM | Post-MVP 유지 | [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림) |
| Redis 인기 캐시 | Conditional 비활성 | [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입) |
| Snapshot·자동 집계·Spring Batch | 비활성 | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) |
| 외부 알림 Outbox·재시도·DLQ | Conditional 비활성 | [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달) |
| 관리자 권한 세분화 | Post-MVP 유지 | [ADR-AUTH-004](adr-backlog.md#adr-auth-004-관리자-권한-세분화) |
| 사용자 이미지·S3 | Post-MVP 유지 | [ADR-MEDIA-001](adr-backlog.md#adr-media-001-s3-사용자-이미지-저장) |

Accepted 세 건은 현재 요구사항을 구현하는 최소 구조만 승인한다. 조건부·Post-MVP 항목은 활성화 조건을 충족하고 새 범위·비용·운영 책임을 승인하기 전 의존성·설정·스키마를 추가하지 않는다.

## 10. 2차 확장 ADR·보류 → 테스트·Task 검증

| 기능·결정 | 요구사항·데이터 | ADR 또는 명시적 보류 | Workstream | 테스트 | E2 Task |
|---|---|---|---|---|---|
| 개인 컬렉션 | `FR-COLLECTION-001~006`, 컬렉션 두 테이블 | 기존 인증·PostgreSQL·Flyway ADR로 충분; 공유·직접 정렬·이미지 제외 | WS-09 | [`TST-E2-COL-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md)~`E2-T03`, `E2-T15` |
| 인기 맛집 실시간 집계 | `FR-POPULAR-001`, 기존 `favorite`, 결과 비저장 | [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md) Accepted; Snapshot·Batch·Redis 비활성 | WS-10 | [`TST-E2-POP-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T04`, `E2-T05`, `E2-T15` |
| 관리자 큐레이션 | `FR-CURATION-001~004`, 큐레이션 두 테이블 | 기존 인증·PostgreSQL ADR로 충분; 예약·추천·이미지 제외 | WS-11 | [`TST-E2-CUR-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-PERF-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T06`, `E2-T07`, `E2-T15` |
| 제보·신고 보존 | `FR-SUBMISSION-001~003`, `FR-REPORT-001~003`, 요청·이력·멱등 데이터 | [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) Accepted | WS-12 | [`TST-E2-SUB-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-REP-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T08`, `E2-T09`, `E2-T11`, `E2-T15` |
| 상태 전이·서비스 내 알림 | `FR-NOTIFICATION-001~004`, `notification` | [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) Accepted; FCM·Outbox·DLQ 비활성 | WS-12·WS-13 | [`TST-E2-ATOMIC-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-NOT-001`, `TST-E2-LIFE-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T02`, `E2-T10`, `E2-T11`, `E2-T14`, `E2-T15` |
| 외부 알림 채널 | 현재 FR 없음, Preference·DeviceToken 비저장 | [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림) Post-MVP, [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달) Conditional | WS-13 향후 재승인 | 현재 테스트·Task 없음 | 현재 E2 Task 없음 |
| 2차 확장 전체 품질 | `NFR-TEST-005` | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md), [ADR-WEB-004](platform/web-004-supported-browser-matrix.md) | WS-09~WS-13 | [`TST-E2-SEC-001`](../08-planning/second-expansion-test-matrix.md), `TST-E2-E2E-001` | [`E2-T01`](../08-planning/expansion-2-task-breakdown.md), `E2-T13`, `E2-T14`, `E2-T15` |

`ADR 또는 명시적 보류` 칸이 비어 있는 2차 확장 기능은 없다. 외부 푸시용 `E2-T12`는 현재 생성하지 않는다. 새 캐시·Batch·외부 채널·저장 개념이 필요해지면 이 표와 상위 범위를 먼저 변경하고 별도 Task를 승인한다.

## 11. 3차 확장 ADR → 테스트·Task 추적

| ADR | 결정 경계 | 테스트·평가 | E3 Task |
|---|---|---|---|
| [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md) | P1 규칙·사전·기존 목록 Query·태그 AND·해석 실패 | `TST-E3-NL-001~002`, `EVAL-NL-001~007` | `E3-T01~02` |
| [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md) | Gemini P1/S1·후보 Snapshot·근거·자동 검증·무료 quota | `TST-E3-AI-001~003`, `TST-E3-SEC-001`, [`EVAL-AI-001~010` 계약 자산·dry-run·HOLD 기록](../08-planning/third-expansion-ai-evaluation-result.md) | `E3-T03~08` |
| [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md) | PostgreSQL claim·lease·heartbeat·retry·재기동·단일 EC2 | `TST-E3-AI-004`, `TST-E3-DATA-001`, `E3-T13` 증거 | `E3-T04~05`, `E3-T13` |
| [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md) | Mobility `/v1/directions`·순서·TTL·캐시 없음·호출/비용 | `TST-E3-COURSE-001~003`, `EVAL-COURSE-001~005`, `E3-T13` 증거 | `E3-T09~10`, `E3-T13` |
| [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-PERF-001](quality/perf-001-k6-load-testing.md) | 테스트 계층·WireMock·Testcontainers·부하 실행 | `TST-E3-DATA-001`, `TST-E3-E2E-001`, `TST-E3-PERF-001` | `E3-T11~13` |

3차 확장 ADR은 Accepted 정책이지만, 각 행의 테스트·평가·운영 증거가 없으면 해당 ADR을 근거로 기능 완료를 선언하지 않는다. 조건부·Post-MVP ADR은 이 추적표의 3차 완료 Task에 포함하지 않는다.

### 11.1 E3-T13 최종 게이트 증거

Accepted ADR의 자동화 검증 결과와 실제 운영·평가·부하 증거의 보류 상태는 [E3-T13 최종 게이트 판정](../08-planning/third-expansion-final-gate-result.md)에 기록한다. ADR 승인 자체는 AI·Mobility 호출 활성화나 3차 확장 출시 승인을 의미하지 않는다.
