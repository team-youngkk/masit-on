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
  - platform/lang-001-java-21-runtime.md
  - platform/build-001-gradle-groovy.md
  - platform/frame-001-spring-boot.md
  - security/auth-001-spring-security-jwt.md
  - security/auth-003-confirmation-token.md
  - platform/web-001-frontend-platform.md
  - platform/web-002-data-state.md
  - platform/web-003-routing-boundary.md
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
  - quality/test-001-automation-strategy.md
  - quality/obs-001-logging-observability.md
  - security/sec-001-secrets-workload-identity.md
  - platform/runtime-001-docker.md
  - platform/ci-001-github-actions-quality-gate.md
---

# 맛잇온 ADR 추적성

## 1. 문서 목적

확정 기술 스펙의 모든 항목을 ADR, 기술 정책, Backlog 또는 운영 설정에 연결하고 1차 MVP 적용 여부를 명확히 한다.

## 2. 기술 스펙 → ADR 매핑

| 기술 항목 | 현재 문서 상태 | 분류 | 관련 ADR | 분류 근거 |
|---|---|---|---|---|
| JDK 21.0.12 LTS | 고정 | Accepted ADR | [ADR-LANG-001](platform/lang-001-java-21-runtime.md) | 백엔드 런타임 기준선 |
| Gradle 8.14.3 + Groovy DSL | 고정 | Accepted ADR | [ADR-BUILD-001](platform/build-001-gradle-groovy.md) | 재현 가능한 빌드 체계 |
| Spring Boot 4.1.0 | 고정 | Accepted ADR | [ADR-FRAME-001](platform/frame-001-spring-boot.md) | 백엔드 프레임워크 기준선 |
| Spring Security 7.1.0 | BOM 파생·고정 | Duplicate or Derived Rule | [ADR-FRAME-001](platform/frame-001-spring-boot.md), [ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 버전은 Boot BOM 파생, 사용 방식은 인증 결정에 종속 |
| Node.js 24.18.0 LTS | 고정 | Accepted ADR | [ADR-WEB-001](platform/web-001-frontend-platform.md) | 프론트엔드 런타임 기준선 |
| Next.js 16.2.11 + TypeScript 7.0.2 | 고정 | Accepted ADR | [ADR-WEB-001](platform/web-001-frontend-platform.md) | 웹 프레임워크·언어 기준선 |
| Server Components `fetch` + TanStack Query | 확정, TanStack 정확한 버전 미기재 | Accepted ADR | [ADR-WEB-002](platform/web-002-data-state.md) | 초기·상호작용 데이터 책임 분리, 버전 확정 전 설치 금지 |
| URL Query Parameter | 확정 | Accepted ADR | [ADR-WEB-002](platform/web-002-data-state.md) | 검색 상태의 공유·재현 |
| React `useState` | 확정 | Duplicate or Derived Rule | [ADR-WEB-002](platform/web-002-data-state.md) | 화면 지역 상태 구현 규칙 |
| MVP 단일 모듈 | 확정 | Accepted ADR | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 초기 배포·테스트 단순화 |
| 도메인 중심 계층형 모놀리스 | 확정 | Accepted ADR | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md) | 단일 모듈과 같은 구조 결정 문제 |
| Gradle 멀티모듈·독립 배포 | 범위 제외 | Post-MVP ADR | [ADR-ARCH-004](adr-backlog.md#adr-arch-004-멀티모듈독립-배포-전환) | 독립 확장·배포·소유권 근거가 생길 때 전환 |
| Port/Adapter | 확정 | Accepted ADR | [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md) | 외부 서비스 변동 격리 |
| 서버 캐시·읽기 저장소·물리적 CQRS | 조건부 | Conditional ADR | [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴) | 동일 PostgreSQL Projection의 실측 한계 확인 전 도입 금지 |
| PostgreSQL 17.10 | 고정 | Accepted ADR | [ADR-DATA-001](data/data-001-postgresql.md) | 주 관계형 데이터베이스 |
| 개발 Docker PostgreSQL / 운영 RDS | 확정 | Accepted ADR | [ADR-DATA-002](data/data-002-database-placement.md) | 환경 분리와 운영 배치 |
| Spring Data JPA | 확정 | Accepted ADR | [ADR-DATA-003](data/data-003-spring-data-jpa.md) | 기본 ORM·Repository 전략 |
| Flyway 12.4.0 | 고정 | Accepted ADR | [ADR-DATA-004](data/data-004-flyway.md) | 스키마 변경 단일 경로 |
| QueryDSL | 조건부 | Conditional ADR | [ADR-SEARCH-001](adr-backlog.md#adr-search-001-querydsl-도입) | 복합 조회 필요성 확인 후 도입 |
| PostGIS | 기술 스펙 확정, 범위 제외 | Post-MVP ADR | [ADR-MAP-001](adr-backlog.md#adr-map-001-지도-표시와-공간-검색) | 지도·좌표·거리 검색 제외 |
| pgvector | Post-MVP | Post-MVP ADR | [ADR-SEARCH-002](adr-backlog.md#adr-search-002-pgvector-자연어-검색rag) | 자연어 검색·RAG 제외 |
| Redis 8.8 전용 인스턴스 | 고정·관리자 Token 역할 확정 | Accepted ADR | [ADR-DATA-005](data/data-005-redis-refresh-token.md) | 관리자 Refresh Token 저장, 캐시·락은 별도 조건부 |
| Redis AOF `everysec` + RDB | 확정 설정 | Operational Configuration | Redis 역할 결정 후 운영 문서 | 아키텍처보다 영속화 설정값 |
| Redis 캐시 | 확정 기술 용도 | Conditional ADR | [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입) | 성능 병목·무효화 근거 없음 |
| Redis 관리자 Refresh Token | 사용자 확정 | Accepted ADR | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-DATA-005](data/data-005-redis-refresh-token.md) | 관리자 JWT 재발급·폐기 |
| Redis 일반 사용자 Refresh Token | 기술 스펙 확정, 범위 제외 | Post-MVP ADR | [ADR-AUTH-002](adr-backlog.md#adr-auth-002-일반-사용자-jwt와-refresh-token) | 일반 사용자 로그인 제외 |
| Redis 분산 락 | 확정 기술 용도 | Conditional ADR | [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입) | 자동 배치·다중 실행 미확정 |
| 격리 수준·락·upsert 동시성 제어 | 조건부 | Conditional ADR | [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어) | 기본 격리와 `UNIQUE`로 불충분하다는 동시성 근거 필요 |
| 관리자 Spring Security 7.1.0 + JWT | 사용자 확정 | Accepted ADR | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md) | 관리자 인증·인가 기준 |
| 관리자 Refresh Token 보안 쿠키 | 사용자 확정 | Accepted ADR | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-DATA-005](data/data-005-redis-refresh-token.md) | Redis 저장·회전, HttpOnly·Secure 전달 |
| 확인 Token 저장·소비·재시도 | 결정 완료 (2026-07-27) | Accepted ADR | [ADR-AUTH-003](security/auth-003-confirmation-token.md) | PostgreSQL 해시·후보 Snapshot, 생성과 원자적 소비, 완료 결과 재현 |
| 관리자 등급·기능별 권한 | 범위 제외 | Post-MVP ADR | [ADR-AUTH-004](adr-backlog.md#adr-auth-004-관리자-권한-세분화) | MVP는 사전 발급 단일 `ADMIN` 역할 |
| 일반 사용자 JWT·Refresh Token | 기술 스펙 확정, 범위 제외 | Post-MVP ADR | [ADR-AUTH-002](adr-backlog.md#adr-auth-002-일반-사용자-jwt와-refresh-token) | 회원가입·로그인 제외 |
| springdoc-openapi 3.0.3 + Swagger UI | 고정 | Technology Policy | [ADR-FRAME-001](platform/frame-001-spring-boot.md) | 구현과 명세 대조 도구, 외부 계약 원문은 `docs/05-specs` |
| Kakao Local REST API V2 | 확정·MVP 필요 | Accepted ADR | [ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 맛집 장소 확인 |
| YouTube Data API v3 | 확정·MVP 필요 | Accepted ADR | [ADR-EXT-001](integration/ext-001-reference-verification.md) | 관리자 채널·영상 확인 |
| 자동 재시도·Circuit Breaker·비동기 이벤트·Outbox | 조건부 | Conditional ADR | [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달) | 수동 재시도·동기 처리로 운영 목표를 지킬 수 없다는 근거 필요 |
| Kakao Maps JavaScript API V3 | 확정이나 범위 제외 | Post-MVP ADR | [ADR-MAP-001](adr-backlog.md#adr-map-001-지도-표시와-공간-검색) | 지도 SDK·표시 제외 |
| Kakao Mobility Directions API V1 | 확정이나 범위 제외 | Post-MVP ADR | [ADR-ROUTE-001](adr-backlog.md#adr-route-001-kakao-mobility와-동선-추천) | 동선·코스 추천 제외 |
| Java + Jsoup | 확정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 자동 수집 제외 |
| Playwright | 필요 시 | Conditional ADR | [ADR-CRAWL-001](adr-backlog.md#adr-crawl-001-playwright-도입) | JS 렌더링 필요 검증 후 도입 |
| n8n | 확정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 자동 수집·동기화 제외 |
| Spring Scheduler | 확정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 자동 주기 실행 제외 |
| Spring Batch 6.0.4 | 고정이나 자동화 제외 | Post-MVP ADR | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 이력·재시작 배치 범위 없음 |
| 하루 1회 새벽 배치 | 확정 설정이나 기능 제외 | Operational Configuration | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | 활성화되지 않은 실행 주기 |
| Spring AI 2.0.0 | 고정이나 범위 제외 | Post-MVP ADR | [ADR-AI-001](adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출) | AI 영상 추출 제외 |
| `gemini-3-flash-preview` | 승인된 Preview이나 범위 제외 | Post-MVP ADR | [ADR-AI-001](adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출) | AI 기능 활성화 전 사용 금지 |
| JSON Schema + Prompt Template | 확정이나 범위 제외 | Post-MVP ADR | [ADR-AI-001](adr-backlog.md#adr-ai-001-spring-ai와-gemini-영상-정보-추출) | AI 추출 계약은 기능과 함께 활성화 |
| JUnit 5 + Mockito | 확정 | Accepted ADR | [ADR-TEST-001](quality/test-001-automation-strategy.md) | 단위 테스트 기준 |
| Spring Boot Test + Testcontainers 2.0.5 | 고정 | Accepted ADR | [ADR-TEST-001](quality/test-001-automation-strategy.md) | 실제 저장소 통합 검증 |
| WireMock | 확정 | Accepted ADR | [ADR-TEST-001](quality/test-001-automation-strategy.md) | 외부 API 장애·계약 격리 |
| Spring Batch Test 6.0.4 | 파생·기능 제외 | Duplicate or Derived Rule | [ADR-AUTO-001](adr-backlog.md#adr-auto-001-자동-수집과-배치-처리) | Spring Batch 활성화에 종속 |
| k6 | 도구 도입 조건부, 환경 결정 완료 | Conditional ADR | [ADR-PERF-001](adr-backlog.md#adr-perf-001-k6-성능-테스트-체계) | 정확한 버전·CI 실행 비용 승인 전 설치 금지 |
| SLF4J + Logback | 확정 | Accepted ADR | [ADR-OBS-001](quality/obs-001-logging-observability.md) | 애플리케이션 로그 기준 |
| Actuator + CloudWatch | 확정 | Accepted ADR | [ADR-OBS-001](quality/obs-001-logging-observability.md) | 오류율·응답 지연·상태·저장소 장애 임계값 확정 |
| 로그 보관 14일 | 결정 완료 (2026-07-24) | Operational Configuration | [ADR-OBS-001](quality/obs-001-logging-observability.md) | [RV-NFR-009](../01-requirements/non-functional-requirements.md#rv-nfr-009-로그-보관-기간) 결정 완료, 14일 유지 |
| Parameter Store SecureString + KMS | 확정 | Accepted ADR | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 운영 비밀정보 보호 |
| EC2 IAM Role | 확정 | Accepted ADR | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 장기 AWS 키 제거 |
| GitHub Actions OIDC | 확정 | Accepted ADR | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | CI의 단기 AWS 자격 증명 |
| Docker | 확정 | Accepted ADR | [ADR-RUNTIME-001](platform/runtime-001-docker.md) | 재현 가능한 실행·배포 산출물 |
| GitHub Actions 빌드·테스트 | 확정 | Accepted ADR | [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 배포 후보 품질 게이트 |
| Nginx | 경로 경계 결정 완료 (2026-07-27) | Accepted ADR | [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md) | `/api/**`는 Spring Boot, 나머지 외부 경로는 Next.js, `/internal/**`은 외부 차단 |
| Amazon ECR·EC2 | 결정 완료 (2026-07-24) | Scope Conflict Review | 배포 토폴로지 Backlog | 단일 EC2 인스턴스 배포로 확정, 비용 대조는 운영 중 재확인 |
| ALB·ASG·Blue-Green | 결정 완료 (2026-07-24) | Scope Conflict Review | 배포 토폴로지 Backlog | MVP 미도입. 단일 인스턴스 수동 복구로 시작하고 ALB는 확장 단계 검토 경로로 보류 |
| GitHub Actions → ECR → EC2 | 결정 완료 (2026-07-27) | Scope Conflict Review | [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 빌드·테스트·이미지 생성·ECR push 자동, 운영 EC2 배포 수동 승인, Smoke Test 자동, 복구 수동. ALB·Blue-Green은 확장 시 재설계 |
| Amazon S3 이미지 저장 | 확정이나 기능 없음 | Post-MVP ADR | [ADR-MEDIA-001](adr-backlog.md#adr-media-001-s3-사용자-이미지-저장) | 이미지 업로드·사용자 이미지 요구사항 없음 |
| FCM HTTP v1 | 확정이나 범위 제외 | Post-MVP ADR | [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림) | 사용자 알림 제외 |
| 초기 월 인프라 예산 15만 원 | 목표 | Operational Configuration | 배포 토폴로지 Backlog | 운영 제약·조정 가능한 수치 |

## 3. NFR → ADR 매핑

| NFR | 관련 ADR | 적용 |
|---|---|---|
| [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제)~[NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-AUTH-004](adr-backlog.md#adr-auth-004-관리자-권한-세분화), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 관리자 접근, 인증 matcher, 확인 Token 무결성, 권한 확장, 입력·비밀 보호 |
| [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리) | [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-DATA-004](data/data-004-flyway.md), [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 참조·원자성·동시 등록·확인 Token 재사용·외부 실패 격리 |
| [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책)~[NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리) | [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 오류 경계, 재시도·회로 차단·이벤트 전달, 장애 검증 |
| [NFR-AVAILABILITY-001](../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분)~[NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-mvp-가용성과-수동-복구) | [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), 배포 토폴로지 Backlog | 내부 상태 확인 경로와 단일 인스턴스 수동 복구 |
| [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리)~[NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보) | [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-EXT-001](integration/ext-001-reference-verification.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 원본 미저장, 외부 호출 격리, 키 보호 |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류)~[NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단) | [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 요청 추적·지표·민감정보 차단 |
| [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층)~[NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트) | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 테스트 계층과 배포 품질 게이트 |
| [NFR-DEPLOYMENT-001](../01-requirements/non-functional-requirements.md#nfr-deployment-001-재현-가능한-빌드와-환경-분리)~[NFR-DEPLOYMENT-002](../01-requirements/non-functional-requirements.md#nfr-deployment-002-배포-전후-검증) | [ADR-BUILD-001](platform/build-001-gradle-groovy.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 재현 빌드, 경로 전달·내부 차단, 환경 분리, 배포 전후 검증 |
| [NFR-DEPLOYMENT-003](../01-requirements/non-functional-requirements.md#nfr-deployment-003-버전-추적과-복구-절차)~[NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-mvp-배포-복잡도-제한) | [ADR-DATA-004](data/data-004-flyway.md), 배포 토폴로지 Backlog | 복구·자동화·복잡도는 후속 결정 |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계)~[NFR-MAINTAINABILITY-003](../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도) | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴), [ADR-ARCH-004](adr-backlog.md#adr-arch-004-멀티모듈독립-배포-전환) | 책임 경계, 조회 확장, 배포 경계와 운영 복잡도 제한 |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화)~[NFR-PRIVACY-003](../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-기능-도입-시-재검토) | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-AUTH-002](adr-backlog.md#adr-auth-002-일반-사용자-jwt와-refresh-token), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 일반 사용자 계정 제외와 비밀 보호 |

## 4. API → ADR 매핑

| API 영역 | 관련 ADR | 경계 |
|---|---|---|
| 공개 탐색·상세 API | [ADR-WEB-002](platform/web-002-data-state.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴), [ADR-DATA-003](data/data-003-spring-data-jpa.md) | `/api` 경로와 계약은 `docs/05-specs/api/`가 소유 |
| 관리자 인증 API | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-DATA-005](data/data-005-redis-refresh-token.md) | 세부 matcher, JWT Bearer, Redis Refresh Token 보안 쿠키 사용 |
| 관리자 기준정보 등록 API | [ADR-EXT-001](integration/ext-001-reference-verification.md), [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | Kakao·YouTube 확인, 확인 Token, 실패·키 격리 |
| 전체 API | [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-OBS-001](quality/obs-001-logging-observability.md) | `/api` 전달, 계약·장애 테스트와 요청 추적 |

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
| 공간·벡터 데이터 | [ADR-MAP-001](adr-backlog.md#adr-map-001-지도-표시와-공간-검색), [ADR-SEARCH-002](adr-backlog.md#adr-search-002-pgvector-자연어-검색rag) | 현재 모델·확장 설치 금지 |
| 사용자·토큰·기기 데이터 | [ADR-AUTH-002](adr-backlog.md#adr-auth-002-일반-사용자-jwt와-refresh-token), [ADR-NOTIFY-001](adr-backlog.md#adr-notify-001-fcm-푸시-알림) | 현재 MVP 모델에 추가 금지 |

## 6. Workstream → ADR 매핑

| Workstream | 필수 ADR | 추가 책임 |
|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | [ADR-WEB-001](platform/web-001-frontend-platform.md)~[ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 화면·API 경로, 검색 상태·최종 조회 조합 |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md)~[ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | 외부 링크 실패 격리·상세 조합 |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | [ADR-ARCH-001](architecture/arch-001-domain-monolith.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md), [ADR-TEST-001](quality/test-001-automation-strategy.md) | Visit 관계 판정 경계 |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md), [ADR-AUTH-003](security/auth-003-confirmation-token.md), [ADR-WEB-003](platform/web-003-routing-boundary.md), [ADR-EXT-001](integration/ext-001-reference-verification.md), [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달), [ADR-ARCH-002](architecture/arch-002-external-ports-adapters.md), [ADR-DATA-003](data/data-003-spring-data-jpa.md)~[ADR-DATA-004](data/data-004-flyway.md), [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) | 관리자 화면·API 경로, 인증·확인 Token·외부 확인·등록 정합성 |
| 전체 | [ADR-LANG-001](platform/lang-001-java-21-runtime.md), [ADR-BUILD-001](platform/build-001-gradle-groovy.md), [ADR-FRAME-001](platform/frame-001-spring-boot.md), [ADR-OBS-001](quality/obs-001-logging-observability.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) | 공통 구현·운영 기준 |

## 7. 기술 정책 → ADR 매핑

| 기술 정책 | 근거 ADR |
|---|---|
| 고정 버전·BOM·범위 버전 금지 | [ADR-LANG-001](platform/lang-001-java-21-runtime.md), [ADR-BUILD-001](platform/build-001-gradle-groovy.md), [ADR-FRAME-001](platform/frame-001-spring-boot.md), [ADR-WEB-001](platform/web-001-frontend-platform.md), [ADR-DATA-004](data/data-004-flyway.md) |
| 개발·테스트·운영 분리 | [ADR-DATA-002](data/data-002-database-placement.md), [ADR-RUNTIME-001](platform/runtime-001-docker.md), [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) |
| DB 스키마 변경 | [ADR-DATA-004](data/data-004-flyway.md) |
| Redis 역할 선확정 | [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입), [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입), [ADR-AUTH-002](adr-backlog.md#adr-auth-002-일반-사용자-jwt와-refresh-token) |
| 동시성 강화 선확정 | [ADR-DATA-006](adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어), [ADR-LOCK-001](adr-backlog.md#adr-lock-001-redis-분산-락-도입) |
| 조회 저장소·물리적 CQRS 선확정 | [ADR-ARCH-003](adr-backlog.md#adr-arch-003-조회-확장-패턴), [ADR-CACHE-001](adr-backlog.md#adr-cache-001-redis-캐시-도입) |
| 자동 복원력·비동기 전달 선확정 | [ADR-EXT-002](adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달) |
| 비밀정보·워크로드 인증 | [ADR-SEC-001](security/sec-001-secrets-workload-identity.md) |
| 조건부·Post-MVP 선제 도입 금지 | 모든 Backlog 항목 |
| AI 생성 코드 검증 | [ADR-TEST-001](quality/test-001-automation-strategy.md), [ADR-CI-001](platform/ci-001-github-actions-quality-gate.md) |

## 8. 미매핑 기술 검토

모든 기술 스펙 항목은 위 표에서 ADR, 정책, Backlog 또는 운영 설정으로 분류됐다. 다음은 2026-07-24 결정 완료 항목이다.

- 관리자 JWT 만료(30분)·Redis Refresh Token TTL(14일, 회전+재사용 탐지)·Redis 장애 시 fail-closed 정책
- Nginx·ECR·EC2를 포함한 최소 MVP 배포 토폴로지(단일 EC2 인스턴스)
- `/api` 화면·백엔드 분리, 관리자 인증 matcher와 `/internal` 상태 확인 경계
- ALB·ASG·Blue-Green: MVP 미도입, ALB는 확장 경로로 보류
- 로그 14일 보관, 백업(일 1회 자동 스냅샷·7일 보관), 운영 알림(CloudWatch→이메일/Slack, 담당자 1명)

다음은 여전히 팀 결정이 필요한 미결정 항목이다.

- `UNIQUE` 이후 격리 수준·락·upsert 도입 기준
- 캐시·별도 읽기 저장소·물리적 CQRS 전환 기준
- 자동 재시도·Circuit Breaker·비동기 이벤트·Transactional Outbox 도입 기준
- 멀티모듈·독립 배포와 세분화된 관리자 권한의 전환 기준
- TanStack Query, Jsoup, n8n, k6 등 정확한 버전이 없는 의존성
- 현재 구현 전 필수 팀 결정은 없다. ALB·Blue-Green 전환 자동화는 토폴로지 확장 시 새 ADR로 결정한다.
