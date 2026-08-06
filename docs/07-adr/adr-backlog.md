---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../06-architecture/technology-policy.md
  - ../06-architecture/transaction-boundaries.md
  - ../06-architecture/query-composition.md
  - ../06-architecture/external-integration.md
  - ../06-architecture/module-boundaries.md
  - ../06-architecture/security-boundary.md
  - README.md
  - adr-index.md
  - adr-traceability.md
  - security/auth-001-spring-security-jwt.md
  - security/auth-003-confirmation-token.md
  - data/data-005-redis-refresh-token.md
  - data/data-007-uuid-v4-identifiers.md
  - data/data-008-publication-lifecycle-soft-delete.md
  - data/data-011-popular-restaurant-request-time-aggregation.md
  - data/data-012-second-expansion-retention-cleanup.md
  - integration/notify-002-in-app-notification-reliability.md
---

# 맛잇온 ADR Backlog

## 1. 문서 목적

현재 확정되지 않은 구현 전 필수 결정, 조건부 결정, Post-MVP와 범위 충돌 결정을 관리한다. `Proposed` 항목은 명시된 결정 시점 전에 Accepted ADR로 확정하고, `Conditional`·`Post-MVP` 기술은 활성화 전 의존성·설정·스키마를 추가하지 않는다.

## 2. 구현 전 필수 ADR

현재 미결정 항목은 없다. 확인 Token은 [ADR-AUTH-003](security/auth-003-confirmation-token.md), 내부 식별자는 [ADR-DATA-007](data/data-007-uuid-v4-identifiers.md), 공개·논리 삭제 생명주기는 [ADR-DATA-008](data/data-008-publication-lifecycle-soft-delete.md)로 2026-07-27 확정했다.

## 3. 조건부 ADR

### ADR-DATA-006 동시 쓰기 충돌 제어

- 현재 상태: Conditional
- 현재 결정: 일반 쓰기는 애플리케이션 선조회와 PostgreSQL `UNIQUE` 제약을 함께 사용하고 기본 격리 수준을 임의로 강화하지 않는다. 제약 위반은 도메인 중복 오류로 변환한다. 확인 Token 생성 확정의 제한된 `ON CONFLICT DO NOTHING RETURNING`은 [ADR-AUTH-003](security/auth-003-confirmation-token.md)이 소유하며 이 일반 전환 후보를 활성화하지 않는다.
- 활성화 조건: 동시성 통합 테스트에서 `UNIQUE`만으로 업무 불변 조건·응답 계약을 보장하지 못하거나 중복 충돌률·재시도 비용이 허용 기준을 넘는다.
- 도입 전 확인: 격리 수준, 낙관적·비관적·분산 락, `INSERT ... ON CONFLICT` 기반 upsert, 멱등성 키 중 최소 복잡도 대안 비교, 교착·기아·재시도 상한, 오류 매핑
- 영향: 트랜잭션 경계, Repository·SQL 구현, DB 부하, API 멱등성, 동시성 테스트
- 관련 후보: Redis 분산 락이 선택지에 포함될 때만 [ADR-LOCK-001](#adr-lock-001-redis-분산-락-도입)을 함께 활성화한다.

### ADR-ARCH-003 조회 확장 패턴

- 현재 상태: Conditional
- 현재 결정: 동일 PostgreSQL의 Projection을 사용하는 코드 수준 CQRS를 유지하고, 서버 조회 캐시·읽기 복제본·별도 읽기 저장소·물리적 CQRS는 도입하지 않는다.
- 활성화 조건: 성능·부하 테스트에서 읽기 병목이 확인되고 쿼리·인덱스 최적화만으로 NFR을 충족하지 못하거나, 쓰기 모델과 다른 조회 모델·독립 확장 요구가 승인된다.
- 도입 전 확인: 캐시·읽기 복제본·전용 읽기 모델·물리적 CQRS 대안 비교, 허용 staleness, 동기화·재구축, 캐시 무효화, 장애 fallback, 운영 비용과 관측성
- 영향: 조회 API, 데이터 일관성, 배포·운영 구성요소, 스키마·이벤트, 성능·복구 테스트
- 관련 후보: Redis 캐시를 선택할 때만 [ADR-CACHE-001](#adr-cache-001-redis-캐시-도입)을 함께 활성화한다.

### ADR-EXT-002 자동 복원력과 신뢰성 이벤트 전달

- 현재 상태: Conditional (회원 Action 메일 발송 한 가지 사례만 부분 활성화, 아래 참고)
- 현재 결정: 외부 호출은 timeout과 오류 분류만 적용하고 관리자가 수동 재시도한다. 도메인 이벤트·메시지 브로커·비동기 Worker·범용 Circuit Breaker는 도입하지 않는다.
- 부분 활성화: 회원 가입 인증·비밀번호 재설정 메일은 "DB 커밋 뒤 유실되어서는 안 되는 후속 작업"에 해당해 [ADR-AUTH-005](security/auth-005-member-action-mail-outbox.md)(Accepted, 2026-07-31)로 Transactional Outbox를 좁게 승인했다. Kakao·YouTube 등 다른 외부 Adapter와 Circuit Breaker·메시지 브로커·도메인 이벤트는 이 활성화에 포함되지 않으며 아래 활성화 조건을 그대로 따른다.
- 분리된 결정: 2차 확장 사용자 알림은 외부 전달이 아니라 같은 DB 트랜잭션의 최종 기록이므로 [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md)에서 Outbox·Worker 없이 확정했다. 이는 외부 전달 활성화가 아니다.
- 활성화 조건: 측정된 외부 실패율·호출량 때문에 수동 재시도로 운영 목표를 지킬 수 없거나, DB 커밋 뒤 유실되어서는 안 되는 알림·외부 동기화·후속 작업이 승인된다.
- 도입 전 확인: 자동 재시도 대상과 최대 횟수·backoff·jitter·전체 시간 예산·429 처리·멱등성, Circuit Breaker 상태·임계값·fallback, 이벤트 전달 보장·순서·중복 소비, Outbox 스키마·발행기·정리·재처리·DLQ(회원 Action 메일 Outbox는 ADR-AUTH-005가 이미 확정)
- 영향: 외부 Adapter, 트랜잭션·이벤트 경계, 저장소·Queue, 장애 복구, 관측성·통합 테스트

### ADR-SEARCH-001 QueryDSL 도입

- 현재 상태: Conditional
- 현재 결정: 기본 조회는 Spring Data JPA의 단순 Repository 메서드 또는 명시적 쿼리로 구현한다.
- 활성화 조건: 필터 조합이 해당 방식으로 유지하기 어렵고 관련 API의 복잡도·성능 테스트에서 필요성이 확인된다.
- 도입 전 확인: 정확한 QueryDSL 버전, 생성 코드 경로, 빌드 영향, 쿼리·회귀 테스트, 팀 합의
- 영향: 빌드 의존성, Repository 구현, 테스트

### ADR-CRAWL-001 Playwright 도입

- 현재 상태: Conditional
- 현재 결정: MVP에는 자동 크롤링을 구현하지 않는다.
- 활성화 조건: 승인된 수집 기능에서 JavaScript 렌더링이 필수이고 정적 HTTP·Jsoup으로 요구사항을 충족할 수 없음이 확인된다.
- 도입 전 확인: 기능 범위, 정확한 버전·브라우저 배치, 실행 비용, 실패·보안 테스트
- 영향: 런타임 이미지, 배치 운영, 테스트 시간

### ADR-LOCK-001 Redis 분산 락 도입

- 현재 상태: Conditional
- 현재 결정: 단일 실행 흐름에서는 DB 고유 제약과 트랜잭션으로 중복을 방지한다.
- 활성화 조건: 동일 작업이 여러 인스턴스에서 동시에 실행될 수 있고 DB 제약만으로 피해를 통제할 수 없음이 검증된다.
- 도입 전 확인: Redis 역할 활성화, 키·TTL·소유권·해제 규칙, 장애 시 안전성, 동시성 테스트
- 영향: Redis 운영, 배치·등록 동시성, 장애 처리

### ADR-CACHE-001 Redis 캐시 도입

- 현재 상태: Conditional
- 현재 결정: 성능 측정 전 캐시를 선제 도입하지 않는다.
- 활성화 조건: NFR 성능 테스트에서 병목이 확인되고 캐시 적중률·무효화 전략이 정의된다.
- 도입 전 확인: 대상 데이터, TTL, 직렬화, 무효화, 장애 시 원본 조회, 비용
- 영향: 데이터 일관성, Redis 운영, 관측성

### ADR-PERF-001 k6 성능 테스트 체계

- 현재 상태: Accepted로 이동 (2026-08-06)
- 현재 결정: [ADR-PERF-001](quality/perf-001-k6-load-testing.md)에 따라 k6 v2.1.0을 고정하고, 시나리오는 `perf/k6/`, 기준 데이터 시드는 `perf/seed/`에 둔다. 실행은 `workflow_dispatch` 전용 워크플로로만 하며 정기 CI 비용은 늘지 않는다. 측정은 운영 동급 사양의 측정 전용 임시 EC2에서 수행한다.
- 활성화 조건 충족: 정확한 버전(v2.1.0)과 CI 비용(정기 실행 없음, 측정 시간분 임시 인스턴스 비용)이 2026-08-06 승인됐다.
- 남은 공백: 정상 부하 실측은 팀이 2026-08-06에 3차 확장 이후로 연기했고, 최대 부하 200명·80 RPS 시나리오는 아직 만들지 않았다. 둘 다 `RV-NFR-011`이 이미 요구하는 항목이며 범위 밖이 아니다([ADR-PERF-001](quality/perf-001-k6-load-testing.md) 8.1절).
- 재검토 조건: 기준 데이터 규모·목표 부하가 바뀌거나, 배포 토폴로지가 단일 인스턴스에서 바뀌거나, 정기 자동 실행이 필요해진다.
- 영향: CI 시간(정기 증가 없음), 성능 품질 게이트, 측정 시점 인프라 비용

## 4. Post-MVP ADR

### ADR-MAP-001 지도 표시와 공간 검색

- 현재 상태: Accepted로 이동
- 현재 결정: [ADR-MAP-001](integration/map-001-map-bounds-search.md)에 따라 1차 확장은 Kakao Maps JavaScript API V3와 nullable WGS84 좌표의 필터 기반 마커 조회를 사용한다. 지도 뷰포트 서버 조건, PostGIS, 현재 위치, 거리·반경 검색은 도입하지 않는다.
- 재검토 조건: 현재 위치·거리·반경·다각형 검색 또는 PostGIS가 범위에 들어온다.
- 영향: 프론트엔드, Restaurant 좌표 모델, 필터 기반 마커 조회 API, 외부 지도 SDK

### ADR-ROUTE-001 Kakao Mobility와 동선 추천

- 현재 상태: Post-MVP
- 현재 결정: Kakao Mobility Directions API V1과 코스 모델을 도입하지 않는다.
- 활성화 조건: 3차 확장 범위의 동선·코스 추천이 승인된다.
- 도입 전 확인: 이동수단·추천 규칙, API 비용·제한, 위치 데이터, 실패 대체
- 영향: 외부 API, 추천 도메인, 테스트

### ADR-AI-001 Spring AI와 Gemini 영상 정보 추출

- 현재 상태: Post-MVP
- 현재 결정: Spring AI 2.0.0, `gemini-3-flash-preview`, JSON Schema·Prompt Template 런타임을 추가하지 않는다.
- 활성화 조건: AI 기반 영상 정보 추출이 범위 변경으로 승인되고 관리자 검수·품질 기준이 정의된다.
- 도입 전 확인: 고정 모델 유효성, 비용, 입력·출력 개인정보, 평가 데이터, 오류·재시도, 통합 테스트, 사용자 승인
- 영향: AI BOM, 외부 호출, 데이터 모델, 관리자 검수

### ADR-SEARCH-002 pgvector 자연어 검색·RAG

- 현재 상태: Post-MVP
- 현재 결정: pgvector 확장, 임베딩 저장과 RAG 흐름을 도입하지 않는다.
- 활성화 조건: 3차 확장 자연어 검색 또는 챗봇 범위가 승인된다.
- 도입 전 확인: 검색 품질 기준, 임베딩 모델, RDS 확장 지원, 색인·재생성·비용
- 영향: DB 확장·스키마, AI 연동, 검색 API

### ADR-AUTO-001 자동 수집과 배치 처리

- 현재 상태: Post-MVP
- 현재 결정: Jsoup, n8n, Spring Scheduler, Spring Batch 6.0.4와 자동 주기 수집·동기화를 도입하지 않는다.
- 분리된 결정: 최근 본 맛집 30일 보존은 [ADR-DATA-010](data/data-010-recent-view-retention-cleanup.md), 2차 확장 보존 정리는 [ADR-DATA-012](data/data-012-second-expansion-retention-cleanup.md)에서 제한적 Scheduler로 관리한다. 둘 다 자동 수집·집계·동기화를 허용하지 않는다.
- 활성화 조건: 관리자 확인 없는 자동 등록과 구분되는 승인된 수집·검수 흐름이 범위에 포함된다.
- 도입 전 확인: n8n·Scheduler·Batch 책임 경계, 정확한 n8n·Jsoup 버전, 실행 이력·재시작·중복 방지, 외부 API 비용
- 영향: 운영 구성요소, Redis 락, 테스트, 관리자 흐름

### ADR-NOTIFY-001 FCM 푸시 알림

- 현재 상태: Post-MVP
- 현재 결정: [ADR-NOTIFY-002](integration/notify-002-in-app-notification-reliability.md)에 따라 2차 확장은 서비스 내 DB 저장 알림만 제공하고 Firebase Cloud Messaging HTTP v1을 도입하지 않는다.
- 활성화 조건: 외부 푸시 채널의 사용자 가치와 전달 SLA가 범위로 승인되고, 사용자 식별·채널별 동의·해지·기기 Token 수명주기와 비용 책임이 확정된다.
- 도입 전 확인: 알림 이벤트와 민감정보 최소화, Token 등록·회전·폐기·탈퇴, 플랫폼별 동의, 전달 의미, 실패·재시도·backoff·중복, Outbox·DLQ·재처리, FCM 비밀정보와 비용
- 영향: 사용자 데이터, API·스키마, 외부 API, 비동기 처리, 개인정보·운영

### ADR-MEDIA-001 S3 사용자 이미지 저장

- 현재 상태: Post-MVP
- 현재 결정: Amazon S3 이미지 저장을 도입하지 않는다. 현재 MVP API·데이터 모델에는 사용자 이미지 업로드가 없다.
- 활성화 조건: 이미지 업로드·보관 기능과 소유권·삭제 정책이 승인된다.
- 도입 전 확인: 파일 제한, 악성 파일 검사, 접근 정책, 수명주기, 비용, CDN 필요성
- 영향: API·데이터 모델, S3 권한, 개인정보

### ADR-ARCH-004 멀티모듈·독립 배포 전환

- 현재 상태: Post-MVP
- 현재 결정: 단일 Gradle 모듈의 도메인 중심 모놀리스와 단일 애플리케이션 배포를 유지한다.
- 활성화 조건: 도메인별 독립 배포·확장·장애 격리 요구가 측정 가능한 병목으로 확인되거나 팀 소유권·예산·배포 토폴로지 변경이 승인된다.
- 도입 전 확인: 단일 모듈 유지·Gradle 멀티모듈·모듈러 모놀리스·독립 서비스 대안, 모듈 API와 의존 방향, 데이터 소유권, 동기·비동기 계약, 배포·관측·장애 대응 책임, 단계적 이전·rollback
- 영향: 빌드, 패키지·모듈 경계, 데이터·트랜잭션, CI/CD, 인프라 비용, 팀 소유권

### ADR-AUTH-004 관리자 권한 세분화

- 현재 상태: Post-MVP
- 현재 결정: 사전 발급 관리자에게 단일 `ADMIN` 역할과 동일 등록 권한을 적용하고 관리자 등급·기능별 권한은 제공하지 않는다.
- 활성화 조건: 관리자 수가 늘거나 자원·기능별 최소 권한, 승인 분리, 감사 요구가 범위 변경으로 승인된다.
- 도입 전 확인: 역할·권한 모델(RBAC/permission), 자원·행위 매트릭스, JWT claim과 서버 조회 책임, 권한 변경 반영·Token 폐기, 기본 거부·승격 승인·감사 로그, 기존 `ADMIN` 이전
- 영향: 인증·인가, 관리자 API, 계정·권한 데이터 모델, 운영 절차, 보안 테스트

## 5. 범위 충돌 검토

| 검토 항목 | 분류 | 근거 | 필요한 결정 |
|---|---|---|---|
| 관리자 JWT·Refresh Token | 결정 완료 (2026-07-24) | Spring Security JWT와 Redis Refresh Token 사용으로 사용자 결정 | Access Token 30분, Refresh Token 14일(재발급마다 회전+재사용 탐지), Redis 장애 시 fail-closed(강제 재로그인) |
| 일반 사용자 JWT·Refresh Token | Post-MVP | 일반 사용자 로그인 제외 | 회원 기능 승인 시 별도 인증 ADR |
| Kakao Maps·PostGIS | Post-MVP | 지도·좌표·거리 검색 제외 | 지도 기능 범위 변경 |
| Kakao Local REST API | 범위 일치 | 관리자 맛집 등록 시 카카오 장소 확인 필요 | Port/Adapter와 장애 처리 구현 |
| Kakao Mobility | Post-MVP | 동선·코스 추천 3차 확장 | 추천 범위 변경 |
| Spring AI·Gemini | Post-MVP | AI 영상 추출 3차 확장 | 검수·품질·비용 기준 승인 |
| pgvector | Post-MVP | 자연어 검색·RAG 제외 | 검색 범위 변경 |
| FCM | Post-MVP | 서비스 내 알림만 승인, 외부 푸시·동의·DeviceToken 제외 | 채널·동의·Token·전달 SLA 승인 |
| S3 이미지 저장 | Post-MVP | 현재 이미지 업로드·사용자 이미지 요구사항 없음 | 이미지 기능 범위 변경 |
| Redis 캐시 | 조건부 도입 | 캐시 필요성을 입증한 성능 측정 없음 | 병목과 무효화 전략 확인 |
| Redis 관리자 Refresh Token | 범위 일치 | 관리자 JWT 재발급·폐기에 사용 | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md)·[ADR-DATA-005](data/data-005-redis-refresh-token.md) 적용 |
| Redis 일반 사용자 Token | Post-MVP | 일반 사용자 로그인 없음 | 회원 인증 범위 변경 |
| Redis 분산 락 | 조건부 도입 | 자동 배치와 다중 실행이 MVP에서 제외·미확정 | 실행 토폴로지와 중복 피해 확인 |
| 확인 Token 저장·서명·단일 사용 | 결정 완료 (2026-07-27) | PostgreSQL 저장형 불투명 Token, 해시·후보 Snapshot, 원자적 소비와 결과 재현 | [ADR-AUTH-003](security/auth-003-confirmation-token.md) 적용 |
| `UNIQUE` 이후 일반 동시성 제어 | 조건부 도입 | 기본 격리+고유 제약으로 시작하며 확인 Token의 제한된 conflict 처리는 별도 확정 | [ADR-DATA-006](#adr-data-006-동시-쓰기-충돌-제어)의 격리·락·일반 upsert 대안 검토 |
| 캐시·읽기 저장소·물리적 CQRS | 조건부 도입 | 현재 동일 PostgreSQL Projection으로 NFR 미검증 | [ADR-ARCH-003](#adr-arch-003-조회-확장-패턴)의 일관성·동기화·복구 결정 |
| 자동 재시도·Circuit Breaker·비동기 이벤트·Outbox | 조건부 도입 | 회원 Action 메일만 ADR-AUTH-005 Outbox, 서비스 내 알림은 ADR-NOTIFY-002 직접 저장 | [ADR-EXT-002](#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달)의 외부 실패·전달 보장 결정 |
| n8n·Batch·크롤링 | Post-MVP | 관리자 수동 확인·등록, 자동 수집 제외 | 승인된 자동화 범위 정의 |
| 멀티모듈·독립 배포 | Post-MVP | 단일 모듈·단일 애플리케이션 배포로 MVP 복잡도 제한 | [ADR-ARCH-004](#adr-arch-004-멀티모듈독립-배포-전환)의 경계·이전 전략 결정 |
| 세분화된 관리자 권한 | Post-MVP | 사전 발급 단일 `ADMIN` 역할만 범위에 포함 | [ADR-AUTH-004](#adr-auth-004-관리자-권한-세분화)의 권한 모델·이전 결정 |
| Nginx·EC2·ECR | 기술 선택 완료, M2 적용 (2026-07-28) | [ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md)에서 단계 순서 변경 | M2부터 단일 EC2 인스턴스에 적용 |
| ALB·ASG·Blue-Green | 3차 확장 이후 배포 고도화 단계에서 검토 (2026-07-28 착수 시점 합의, 비용·일정 영향 검토 미완) | 초기 운영 배포는 단일 인스턴스 수동 복구 | 영향 검토 통과 후 착수, 토폴로지·비용은 별도 ADR |
| 전체 CI/CD 배포 흐름 | M2 적용 (2026-07-28) | 전 단계 CI는 빌드·테스트 수행 | M2부터 ECR push·EC2 승인 배포·Smoke Test 활성화 |
| 로그 14일 보관 | M2 적용 (2026-07-28) | 로컬 단계에는 CloudWatch 미사용 | M2부터 로그·백업·알림 정책 활성화 |

## 6. 활성화 조건

Conditional·Post-MVP Backlog 항목은 다음을 모두 충족해야 활성화된다. Proposed 항목은 각 항목에 적힌 결정 시점과 검증 기준을 따른다.

1. 상위 범위 변경 또는 명시된 조건 충족
2. 관련 요구사항·NFR·API·데이터 영향 확정
3. 정확한 버전과 공식 호환성 근거 확인
4. 비용·보안·운영·복구 영향 검토
5. 개별 ADR 작성과 팀 리뷰
6. 테스트 계획과 되돌리기 방법 승인

## 7. 작성 우선순위

1. [결정 완료 2026-07-27] 관리자 검증 미리보기의 확인 Token 저장·단일 사용·재시도 정책 결정
2. [결정 완료 2026-07-24] 배포 토폴로지와 ALB·ASG·Blue-Green 충돌 해결
3. [결정 완료 2026-07-24] 관리자 JWT 만료·서명 키와 Redis Refresh Token 키·회전·장애 정책 결정
4. [결정 완료 2026-07-24] 로그 보관·운영 지표·알림 기준 결정
5. 동시성·조회 확장·자동 복원력은 테스트·운영 근거 발생 시 검토
6. 지도·회원·AI·RAG·알림·이미지·자동화·독립 배포·권한 세분화는 해당 확장 단계 승인 후 검토

## 8. 결정 기록

| 항목 | 결정 |
|---|---|
| 관리자 등록 확인 Token | PostgreSQL 저장형 불투명 Token, SHA-256 해시·후보 JSONB Snapshot, 10분 만료, Entity 생성과 원자적 소비 |
| 확인 Token 재시도·보관 | 최초 생성 `201`, 생성 완료 재시도 `200`, 동시 중복은 결정적 `409`, 완료·만료 기록 24시간 보관 후 발급 시 지연 정리 |
| 배포 순서 | MVP 구현은 로컬 통합 검증, M2에서 다음 확장 단계보다 먼저 최초 AWS 운영 배포 |
| 배포 토폴로지 | 초기 운영 배포는 단일 EC2 인스턴스(Nginx+App), 장애 시 수동 복구로 시작 |
| 관리자 JWT | Access Token 만료 30분 |
| 관리자 Refresh Token(Redis) | TTL 14일, 재발급마다 회전 + 재사용 탐지·즉시 폐기 |
| Redis 장애 처리 | Fail-closed(재발급 차단, Access Token 만료 후 강제 재로그인) |
| 로그 보관 | 14일 (기존 기술 스펙 값 유지) |
| 백업 | PostgreSQL 일 1회 자동 스냅샷, 7일 보관, RPO 최대 24시간 |
| 운영 알림 | CloudWatch 알람 → Slack, 담당자 1명 |
| 부하 테스트 도구 | k6 v2.1.0 고정, `perf/k6/` 시나리오와 `perf/seed/` 기준 데이터, `workflow_dispatch` 전용 실행으로 정기 CI 비용 없음 |
| 성능 측정 기준 데이터 | `RV-NFR-002` 4종에 더해 회원 1,000명·찜 20,000건(상위권 편차 분포)을 `ADR-PERF-001`이 확정 |
| 정상 부하 실측 시점 | 2026-08-06 팀 결정으로 3차 확장 이후로 연기. 측정 수단은 준비 완료, 결과는 미측정 |

현재 MVP 구현 전 필수 미결정 항목은 없다. AWS 운영 세부는 M2 초기 운영 배포 문서에서 확정한다. ALB·Blue-Green 전환은 3차 확장 이후 배포 고도화 단계에서 검토한다. 착수 시점은 2026-07-28 팀 4인 전원이 합의했으나 비용·일정 영향 검토가 남아 있다([ADR-DEPLOY-002](platform/deploy-002-validation-deployment-before-expansion.md) 3.1절). 영향 검토가 미결정 항목으로 남으며, 토폴로지·전환 절차·비용은 착수 시점의 별도 ADR에서 확정한다.
