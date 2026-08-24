---
related_documents:
  - README.md
  - ../06-architecture/technology-policy.md
  - adr-backlog.md
  - adr-traceability.md
  - platform/lang-001-java-21-runtime.md
  - platform/build-001-gradle-groovy.md
  - platform/frame-001-spring-boot.md
  - platform/web-001-frontend-platform.md
  - platform/web-002-data-state.md
  - platform/web-003-routing-boundary.md
  - platform/web-006-unified-login-rbac-route.md
  - platform/web-004-supported-browser-matrix.md
  - platform/web-005-application-port-binding.md
  - ../02-analysis/mvp-workstreams.md
  - architecture/arch-001-domain-monolith.md
  - architecture/arch-002-external-ports-adapters.md
  - data/data-001-postgresql.md
  - data/data-002-database-placement.md
  - data/data-003-spring-data-jpa.md
  - data/data-004-flyway.md
  - security/auth-001-spring-security-jwt.md
  - security/auth-002-member-jwt-refresh-token.md
  - security/auth-003-confirmation-token.md
  - security/auth-006-cookie-origin-defense.md
  - security/auth-007-unified-account-rbac-session.md
  - data/data-005-redis-refresh-token.md
  - data/data-007-uuid-v4-identifiers.md
  - data/data-008-publication-lifecycle-soft-delete.md
  - data/data-010-recent-view-retention-cleanup.md
  - data/data-011-popular-restaurant-request-time-aggregation.md
  - data/data-012-second-expansion-retention-cleanup.md
  - integration/notify-002-in-app-notification-reliability.md
  - integration/ext-001-reference-verification.md
  - integration/ai-001-video-extraction-candidate-boundary.md
  - integration/ext-003-ai-extraction-async-reliability.md
  - integration/route-001-kakao-mobility-course-routing.md
  - architecture/arch-005-natural-language-filter-interpretation.md
  - integration/map-001-map-bounds-search.md
  - quality/test-001-automation-strategy.md
  - quality/obs-001-logging-observability.md
  - quality/perf-001-k6-load-testing.md
  - quality/perf-002-operational-participant-load-testing.md
  - quality/perf-003-isolated-performance-terraform.md
  - security/sec-001-secrets-workload-identity.md
  - platform/runtime-001-docker.md
  - platform/ci-001-github-actions-quality-gate.md
  - platform/deploy-001-release-sequencing.md
  - platform/deploy-003-validation-cookie-session.md
  - platform/deploy-004-public-api-validation-gate-boundary.md
  - platform/deploy-006-public-release-without-validation-gate.md
  - platform/git-001-branch-merge-strategy.md
---

# 맛잇온 ADR 인덱스

| ADR ID | 제목 | 상태 | 우선순위 | 관련 기술 | 관련 범위 | 문서 경로 |
|---|---|---|---|---|---|---|
| [ADR-LANG-001](platform/lang-001-java-21-runtime.md) | Java 21 런타임 기준 | Accepted | Critical | JDK 21.0.12 LTS | 전체 백엔드 | [문서](platform/lang-001-java-21-runtime.md) |
| [ADR-BUILD-001](platform/build-001-gradle-groovy.md) | Gradle과 Groovy DSL 빌드 체계 | Accepted | Critical | Gradle 8.14.3, Groovy DSL | 전체 백엔드·CI | [문서](platform/build-001-gradle-groovy.md) |
| [ADR-FRAME-001](platform/frame-001-spring-boot.md) | Spring Boot 애플리케이션 기준 | Accepted | Critical | Spring Boot 4.1.0, Spring Security 7.1.0 BOM | 전체 백엔드 | [문서](platform/frame-001-spring-boot.md) |
| [ADR-WEB-001](platform/web-001-frontend-platform.md) | 프론트엔드 런타임과 프레임워크 기준 | Accepted | High | Node.js 24.18.0, Next.js 16.2.11, TypeScript 7.0.2 | 전체 웹 UI | [문서](platform/web-001-frontend-platform.md) |
| [ADR-WEB-002](platform/web-002-data-state.md) | 프론트엔드 데이터와 상태 책임 분리 | Accepted | Medium | Server Components `fetch`, TanStack Query, URL Query Parameter, `useState` | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 웹 UI | [문서](platform/web-002-data-state.md) |
| [ADR-WEB-003](platform/web-003-routing-boundary.md) | 웹 화면·API·운영 경로 경계 | Superseded | Critical | Next.js App Router, Nginx, Spring Security, `/api`, `/internal` | ADR-WEB-006이 전체 경계를 대체 | [문서](platform/web-003-routing-boundary.md) |
| [ADR-WEB-004](platform/web-004-supported-browser-matrix.md) | 지원 브라우저 매트릭스와 iPhone Safari 지원 수준 | Accepted | High | PC Chrome·Edge, Android Chrome, 화면 폭 5종 | 전체 웹 UI 인수 판정 | [문서](platform/web-004-supported-browser-matrix.md) |
| [ADR-WEB-005](platform/web-005-application-port-binding.md) | 운영 애플리케이션 포트 loopback 바인딩 | Accepted | Critical | Spring Boot, Next.js, Docker host network, Nginx | 운영 진입점·애플리케이션 포트 | [문서](platform/web-005-application-port-binding.md) |
| [ADR-WEB-006](platform/web-006-unified-login-rbac-route.md) | 통합 로그인과 역할 기반 관리자 화면 진입 | Accepted | Critical | Next.js App Router, TanStack Query, Spring Security RBAC | 통합 로그인·메인 관리자 링크·`/admin` 경계 | [문서](platform/web-006-unified-login-rbac-route.md) |
| [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 단일 모듈 도메인 중심 모놀리스 | Accepted | Critical | 단일 모듈, 계층형 모놀리스 | 전체 Workstream | [문서](architecture/arch-001-domain-monolith.md) |
| [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 외부 연동 Port/Adapter 경계 | Accepted | High | Port/Adapter | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)~[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 외부 연동 | [문서](architecture/arch-002-external-ports-adapters.md) |
| [ADR-DATA-001](data/data-001-postgresql.md) | PostgreSQL 17.10 주 데이터베이스 | Accepted | Critical | PostgreSQL 17.10 | 전체 영속 데이터 | [문서](data/data-001-postgresql.md) |
| [ADR-DATA-002](data/data-002-database-placement.md) | 개발 Docker와 운영 RDS 데이터베이스 분리 | Accepted | High | Docker PostgreSQL, Amazon RDS | 개발·테스트·운영 | [문서](data/data-002-database-placement.md) |
| [ADR-DATA-003](data/data-003-spring-data-jpa.md) | Spring Data JPA 기본 데이터 접근 | Accepted | High | Spring Data JPA | 전체 Repository | [문서](data/data-003-spring-data-jpa.md) |
| [ADR-DATA-004](data/data-004-flyway.md) | Flyway 스키마 마이그레이션 | Accepted | Critical | Flyway 12.4.0 | 전체 스키마 변경 | [문서](data/data-004-flyway.md) |
| [ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 관리자 Spring Security JWT 인증·인가 | Superseded | Critical | Spring Security 7.1.0, JWT, Redis 8.8 Refresh Token | ADR-AUTH-007이 통합 계정·세션으로 대체 | [문서](security/auth-001-spring-security-jwt.md) |
| [ADR-AUTH-002](security/auth-002-member-jwt-refresh-token.md) | 회원 JWT와 Refresh Token | Superseded | Critical | Spring Security 7.1.0, JWT, Redis 8.8 Refresh Token | ADR-AUTH-007이 통합 계정·세션으로 대체 | [문서](security/auth-002-member-jwt-refresh-token.md) |
| [ADR-AUTH-003](security/auth-003-confirmation-token.md) | 관리자 등록 확인 Token의 저장·소비·재시도 | Accepted | Critical | PostgreSQL, SHA-256, 불투명 Token, JSONB Snapshot | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 기준정보 등록 | [문서](security/auth-003-confirmation-token.md) |
| [ADR-AUTH-005](security/auth-005-member-action-mail-outbox.md) | 회원 Action 메일의 신뢰성 있는 전달 (Outbox) | Accepted | High | PostgreSQL, AES-GCM, `@Scheduled` Worker | 회원 가입 인증·비밀번호 재설정 | [문서](security/auth-005-member-action-mail-outbox.md) |
| [ADR-AUTH-006](security/auth-006-cookie-origin-defense.md) | 쿠키 기반 Refresh·Logout Origin 방어 | Accepted | High | 단일 Origin 헤더, 통합 `AUTH_ALLOWED_ORIGINS` | 역할 공통 Refresh·Logout | [문서](security/auth-006-cookie-origin-defense.md) |
| [ADR-AUTH-007](security/auth-007-unified-account-rbac-session.md) | 통합 계정 RBAC와 세션 | Accepted | Critical | Spring Security 7.1.0, JWT, Redis 8.8, `member_account.role` | 회원·관리자 통합 인증과 `/api/admin/**` 인가 | [문서](security/auth-007-unified-account-rbac-session.md) |
| [ADR-DATA-005](data/data-005-redis-refresh-token.md) | Redis 8.8 관리자 Refresh Token 저장소 | Superseded | Critical | Redis Open Source 8.8 | ADR-AUTH-007이 통합 세션 저장으로 대체 | [문서](data/data-005-redis-refresh-token.md) |
| [ADR-DATA-007](data/data-007-uuid-v4-identifiers.md) | 애플리케이션 생성 UUID v4 내부 식별자 | Accepted | High | Java UUID, PostgreSQL uuid | 전체 영속 데이터 | [문서](data/data-007-uuid-v4-identifiers.md) |
| [ADR-DATA-008](data/data-008-publication-lifecycle-soft-delete.md) | 공개 상태와 논리 삭제 생명주기 분리 | Accepted | Critical | PostgreSQL CHECK, partial index | 핵심 공개 데이터 | [문서](data/data-008-publication-lifecycle-soft-delete.md) |
| [ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 외부 기준정보 확인 서비스 | Accepted | High | Kakao Local REST API V2, YouTube Data API v3 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 | [문서](integration/ext-001-reference-verification.md) |
| [ADR-ARCH-005](architecture/arch-005-natural-language-filter-interpretation.md) | 자연어 조건 해석과 기존 필터 조회 경계 | Accepted | High | P1 규칙·사전, TagDefinition·VisitTag | [WS-14](../02-analysis/third-expansion-workstreams.md#5-ws-14-자연어-맛집-탐색) | [문서](architecture/arch-005-natural-language-filter-interpretation.md) |
| [ADR-AI-001](integration/ai-001-video-extraction-candidate-boundary.md) | AI 영상 추출 후보 경계와 제공자 선택 기준 | Accepted | Critical | Gemini Free Tier 전용, `gemini-3.5-flash-lite`, 현재 P8/S2·기존 P1·P2·P3·P4·P5·P6·P7 이력 보존 | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | [문서](integration/ai-001-video-extraction-candidate-boundary.md) |
| [ADR-EXT-003](integration/ext-003-ai-extraction-async-reliability.md) | AI 추출 비동기 작업과 단일 EC2 복구 경계 | Accepted | Critical | PostgreSQL lease, 내부 Worker, Gemini retry | [WS-15](../02-analysis/third-expansion-workstreams.md#6-ws-15-ai-영상-정보-추출) | [문서](integration/ext-003-ai-extraction-async-reliability.md) |
| [ADR-ROUTE-001](integration/route-001-kakao-mobility-course-routing.md) | Kakao Mobility 자동차 경로와 코스 결과 경계 | Accepted | High | Kakao Mobility `/v1/directions`, 자동차 경로 | [WS-16](../02-analysis/third-expansion-workstreams.md#7-ws-16-맛집-코스-추천) | [문서](integration/route-001-kakao-mobility-course-routing.md) |
| [ADR-MAP-001](integration/map-001-map-bounds-search.md) | Kakao 지도 표시와 뷰포트 비종속 마커 조회 | Accepted | High | Kakao Maps JavaScript API V3, WGS84 좌표, 필터 기반 마커 조회 | [WS-07](../02-analysis/first-expansion-workstreams.md#6-ws-07-지도-탐색) 지도 탐색 | [문서](integration/map-001-map-bounds-search.md) |
| [ADR-TEST-001](quality/test-001-automation-strategy.md) | 계층별 자동화 테스트 전략 | Accepted | Critical | JUnit 5, Mockito, Spring Boot Test, Testcontainers 2.0.5, WireMock | 전체 Workstream | [문서](quality/test-001-automation-strategy.md) |
| [ADR-OBS-001](quality/obs-001-logging-observability.md) | 애플리케이션 로그와 운영 관측 기준 | Accepted | High | SLF4J, Logback, Actuator, CloudWatch | 전체 운영 | [문서](quality/obs-001-logging-observability.md) |
| [ADR-PERF-001](quality/perf-001-k6-load-testing.md) | k6 부하 테스트 도구와 실행 체계 | Accepted | High | k6 v2.1.0, GitHub Actions `workflow_dispatch` | 2차 확장 공개 조회 성능 검증 | [문서](quality/perf-001-k6-load-testing.md) |
| [ADR-PERF-002](quality/perf-002-operational-participant-load-testing.md) | 검증 참여자 전용 운영 직접 부하 검증 예외 | Accepted | High | k6 v2.1.0, AWS SSM, 운영 PostgreSQL·Redis | 이슈 #190 일회성 제한 공개 검증 | [문서](quality/perf-002-operational-participant-load-testing.md) |
| [ADR-PERF-003](quality/perf-003-isolated-performance-terraform.md) | 격리 성능 검증 환경 Terraform과 상태 저장소 | Accepted | High | Terraform, AWS provider, S3 backend, DynamoDB locking, 제한된 egress | 이슈 #207 격리 성능 검증 | [문서](quality/perf-003-isolated-performance-terraform.md) |
| [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 비밀정보와 AWS 워크로드 인증 | Accepted | Critical | Parameter Store, KMS, IAM Role, GitHub OIDC | 운영·CI·외부 연동 | [문서](security/sec-001-secrets-workload-identity.md) |
| [ADR-DATA-009](data/data-009-pre-release-migration-consolidation.md) | 운영 배포 전 마이그레이션 통합 범위 | Accepted | High | Flyway | 마이그레이션 파일과 모든 환경 스키마 | [문서](data/data-009-pre-release-migration-consolidation.md) |
| [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md) | 최근 본 맛집 보존 기간 정리 실행 | Accepted | High | Spring Scheduler, PostgreSQL | [WS-06](../02-analysis/first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) 최근 기록 생명주기 | [문서](data/data-010-recent-view-retention-cleanup.md) |
| [ADR-DATA-011](data/data-011-popular-restaurant-request-time-aggregation.md) | 인기 맛집 요청 시점 실시간 집계 | Accepted | High | PostgreSQL 집계·인덱스 | [WS-10](../02-analysis/second-expansion-workstreams.md#5-ws-10-인기-맛집) 인기 맛집 | [문서](data/data-011-popular-restaurant-request-time-aggregation.md) |
| [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md) | 2차 확장 보존 정책 정리 실행 | Accepted | High | Spring Scheduler, PostgreSQL | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토)·[WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) 보존 | [문서](data/data-012-second-expansion-retention-cleanup.md) |
| [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md) | 서비스 내 사용자 알림의 저장 신뢰성 경계 | Accepted | Critical | PostgreSQL 단일 트랜잭션 | [WS-12](../02-analysis/second-expansion-workstreams.md#7-ws-12-제보신고-검토)·[WS-13](../02-analysis/second-expansion-workstreams.md#8-ws-13-사용자-알림) | [문서](integration/notify-002-in-app-notification-reliability.md) |
| [ADR-RUNTIME-001](platform/runtime-001-docker.md) | Docker 기반 실행 환경 | Accepted | High | Docker | 개발·테스트·배포 산출물 | [문서](platform/runtime-001-docker.md) |
| [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | GitHub Actions 빌드·테스트 품질 게이트 | Accepted | Critical | GitHub Actions | 전체 배포 후보 | [문서](platform/ci-001-github-actions-quality-gate.md) |
| [ADR-GIT-001](platform/git-001-branch-merge-strategy.md) | 브랜치 병합 방식과 역동기화 정책 | Accepted | High | GitHub ruleset(Squash/Merge Commit) | 전체 PR 병합 | [문서](platform/git-001-branch-merge-strategy.md) |
| [ADR-DEPLOY-001](platform/deploy-001-release-sequencing.md) | 단계별 로컬 검증과 최종 AWS 배포 순서 | Superseded | Critical | Docker, AWS | 전체 단계 및 최종 배포 | [문서](platform/deploy-001-release-sequencing.md) |
| [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) | 초기 운영 배포 선행과 확장 단계별 인프라 반영 | Accepted | Critical | Docker, AWS | M2 제한 공개 단계 완료 및 이후 확장 | [문서](platform/deploy-002-validation-deployment-before-expansion.md) |
| [ADR-DEPLOY-003](platform/deploy-003-validation-cookie-session.md) | 검증 참여자 제한 공개 쿠키 세션 | Superseded | Critical | Nginx `auth_request`, Spring Boot, Redis, HttpOnly Cookie | ADR-DEPLOY-004를 거친 M2 역사 ADR; 현재 최종 결정은 ADR-DEPLOY-006 | [문서](platform/deploy-003-validation-cookie-session.md) |
| [ADR-DEPLOY-004](platform/deploy-004-public-api-validation-gate-boundary.md) | 비관리자 공개 API 검증 세션 gate 경계 | Superseded | Critical | Nginx `auth_request`, Spring Security, 운영 smoke | M2 역사 gate; ADR-DEPLOY-006이 정식 공개 전환으로 대체 | [문서](platform/deploy-004-public-api-validation-gate-boundary.md) |
| [ADR-DEPLOY-005](platform/deploy-005-asg-blue-green-rollout.md) | ASG 기반 Blue-Green 운영 배포 | Accepted | Critical | ALB, ASG, CodeDeploy, Redis, GitHub Actions | 배포 고도화 기준 확정; 실제 운영 전환은 별도 runbook·승인·리허설 | [문서](platform/deploy-005-asg-blue-green-rollout.md) |
| [ADR-DEPLOY-006](platform/deploy-006-public-release-without-validation-gate.md) | 검증 참여자 gate 없는 정식 공개 전환과 운영 경계 유지 | Accepted | Critical | Nginx, Spring Security, Webhook HMAC, loopback | 정식 공개 접근 경계; v1.0.0 tag·운영 전환은 별도 승인 | [문서](platform/deploy-006-public-release-without-validation-gate.md) |
