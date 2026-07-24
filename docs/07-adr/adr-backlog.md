---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../06-architecture/technology-policy.md
  - README.md
  - adr-index.md
  - adr-traceability.md
  - security/auth-001-spring-security-jwt.md
  - data/data-005-redis-refresh-token.md
---

# 맛잇온 ADR Backlog

## 1. 문서 목적

현재 1차 MVP에서 바로 구현 기준으로 사용할 수 없는 조건부, Post-MVP와 범위 충돌 결정을 관리한다. 여기 있는 기술은 고정 버전이 있더라도 활성화 전 의존성·설정·스키마를 추가하지 않는다.

## 2. 조건부 ADR

### ADR-SEARCH-001 QueryDSL 도입

- 현재 상태: Conditional
- 현재 결정: 기본 조회는 Spring Data JPA의 단순 Repository 메서드 또는 명시적 쿼리로 구현한다.
- 활성화 조건: 필터 조합이 해당 방식으로 유지하기 어렵고 관련 API의 복잡도·성능 테스트에서 필요성이 확인된다.
- 도입 전 확인: 정확한 QueryDSL 버전, 생성 코드 경로, 빌드 영향, 쿼리·회귀 테스트, 팀 합의
- 영향: 빌드 의존성, Repository 구현, 테스트

### ADR-CRAWL-001 Playwright 도입

- 현재 상태: Conditional
- 현재 결정: 1차 MVP에는 자동 크롤링을 구현하지 않는다.
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

- 현재 상태: Conditional
- 현재 결정: 성능 요구사항과 테스트 환경은 유지하되 도구 의존성은 추가하지 않는다.
- 활성화 조건: [RV-NFR-011](../01-requirements/non-functional-requirements.md#rv-nfr-011-성능-테스트-환경)의 환경·데이터 규모·부하 모델을 팀이 결정한다.
- 도입 전 확인: 정확한 버전, CI 실행 위치, 임계값, 결과 보관
- 영향: CI 시간, 성능 품질 게이트

## 3. Post-MVP ADR

### ADR-MAP-001 지도 표시와 공간 검색

- 현재 상태: Post-MVP
- 현재 결정: Kakao Maps JavaScript API V3와 PostGIS를 도입하지 않는다. MVP는 도로명주소와 카카오 장소 링크만 제공한다.
- 활성화 조건: 지도 기반 탐색·현재 위치·거리 또는 반경 검색이 범위 변경으로 승인된다.
- 도입 전 확인: 위치 개인정보, 좌표 모델, RDS 확장 지원, 지도 API 계약·비용, 공간 쿼리 성능
- 영향: 프론트엔드, 데이터 모델·DB 확장, 외부 연동

### ADR-ROUTE-001 Kakao Mobility와 동선 추천

- 현재 상태: Post-MVP
- 현재 결정: Kakao Mobility Directions API V1과 코스 모델을 도입하지 않는다.
- 활성화 조건: 4차 확장 범위의 동선·코스 추천이 승인된다.
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
- 활성화 조건: 4차 확장 자연어 검색 또는 챗봇 범위가 승인된다.
- 도입 전 확인: 검색 품질 기준, 임베딩 모델, RDS 확장 지원, 색인·재생성·비용
- 영향: DB 확장·스키마, AI 연동, 검색 API

### ADR-AUTH-002 일반 사용자 JWT와 Refresh Token

- 현재 상태: Post-MVP
- 현재 결정: 일반 사용자는 로그인하지 않는다. JWT, Refresh Token과 Redis 토큰 저장을 구현하지 않는다.
- 활성화 조건: 2차 확장 회원가입·로그인이 승인되고 개인정보·세션 요구사항이 확정된다.
- 도입 전 확인: 토큰 수명·폐기·회전, 브라우저 전달, Redis 필요성, 개인정보·보안 위협 모델
- 영향: API 인증, Redis, 프론트엔드, 개인정보

### ADR-AUTO-001 자동 수집과 배치 처리

- 현재 상태: Post-MVP
- 현재 결정: Jsoup, n8n, Spring Scheduler, Spring Batch 6.0.4와 자동 주기 동기화를 도입하지 않는다.
- 활성화 조건: 관리자 확인 없는 자동 등록과 구분되는 승인된 수집·검수 흐름이 범위에 포함된다.
- 도입 전 확인: n8n·Scheduler·Batch 책임 경계, 정확한 n8n·Jsoup 버전, 실행 이력·재시작·중복 방지, 외부 API 비용
- 영향: 운영 구성요소, Redis 락, 테스트, 관리자 흐름

### ADR-NOTIFY-001 FCM 푸시 알림

- 현재 상태: Post-MVP
- 현재 결정: Firebase Cloud Messaging HTTP v1을 도입하지 않는다.
- 활성화 조건: 3차 확장 알림과 사용자 식별·동의·토큰 수명주기가 승인된다.
- 도입 전 확인: 알림 이벤트, 기기 토큰, 동의·해지, 실패·재시도, 비밀정보
- 영향: 사용자 데이터, 외부 API, 비동기 처리

### ADR-MEDIA-001 S3 사용자 이미지 저장

- 현재 상태: Post-MVP
- 현재 결정: Amazon S3 이미지 저장을 도입하지 않는다. 현재 MVP API·데이터 모델에는 사용자 이미지 업로드가 없다.
- 활성화 조건: 이미지 업로드·보관 기능과 소유권·삭제 정책이 승인된다.
- 도입 전 확인: 파일 제한, 악성 파일 검사, 접근 정책, 수명주기, 비용, CDN 필요성
- 영향: API·데이터 모델, S3 권한, 개인정보

## 4. 범위 충돌 검토

| 검토 항목 | 분류 | 근거 | 필요한 결정 |
|---|---|---|---|
| 관리자 JWT·Refresh Token | 결정 완료 (2026-07-24) | Spring Security JWT와 Redis Refresh Token 사용으로 사용자 결정 | Access Token 30분, Refresh Token 14일(재발급마다 회전+재사용 탐지), Redis 장애 시 fail-closed(강제 재로그인) |
| 일반 사용자 JWT·Refresh Token | Post-MVP | 일반 사용자 로그인 제외 | 회원 기능 승인 시 별도 인증 ADR |
| Kakao Maps·PostGIS | Post-MVP | 지도·좌표·거리 검색 제외 | 지도 기능 범위 변경 |
| Kakao Local REST API | 범위 일치 | 관리자 맛집 등록 시 카카오 장소 확인 필요 | Port/Adapter와 장애 처리 구현 |
| Kakao Mobility | Post-MVP | 동선·코스 추천 4차 확장 | 추천 범위 변경 |
| Spring AI·Gemini | Post-MVP | AI 영상 추출 4차 확장 | 검수·품질·비용 기준 승인 |
| pgvector | Post-MVP | 자연어 검색·RAG 제외 | 검색 범위 변경 |
| FCM | Post-MVP | 사용자 알림 제외 | 계정·동의·알림 범위 변경 |
| S3 이미지 저장 | Post-MVP | 현재 이미지 업로드·사용자 이미지 요구사항 없음 | 이미지 기능 범위 변경 |
| Redis 캐시 | 조건부 도입 | 캐시 필요성을 입증한 성능 측정 없음 | 병목과 무효화 전략 확인 |
| Redis 관리자 Refresh Token | 범위 일치 | 관리자 JWT 재발급·폐기에 사용 | [ADR-AUTH-001](security/auth-001-spring-security-jwt.md)·[ADR-DATA-005](data/data-005-redis-refresh-token.md) 적용 |
| Redis 일반 사용자 Token | Post-MVP | 일반 사용자 로그인 없음 | 회원 인증 범위 변경 |
| Redis 분산 락 | 조건부 도입 | 자동 배치와 다중 실행이 MVP에서 제외·미확정 | 실행 토폴로지와 중복 피해 확인 |
| n8n·Batch·크롤링 | Post-MVP | 관리자 수동 확인·등록, 자동 수집 제외 | 승인된 자동화 범위 정의 |
| Nginx·EC2·ECR | 결정 완료 (2026-07-24) | 기술 스펙에는 확정, NFR은 배포 상세를 후속 설계로 둠 | 단일 EC2 인스턴스(Nginx 리버스 프록시+App), 장애 시 수동 복구 |
| ALB·ASG·Blue-Green | 결정 완료 (2026-07-24) | 기술 스펙의 다중 인스턴스 구조와 NFR의 단일 인스턴스 수동 복구·복잡도 제한이 충돌 | MVP는 도입하지 않음. ALB는 확장 단계 우선 검토 대상으로 남기고, ASG·Blue-Green은 Post-MVP로 보류 |
| 전체 CI/CD 배포 흐름 | 팀 결정 필요 | 빌드·테스트 게이트는 확정, 배포 자동화·수동 승인 지점은 미확정 | [RV-NFR-012](../01-requirements/non-functional-requirements.md#rv-nfr-012-배포-자동화-범위) 결정 |
| 로그 14일 보관 | 결정 완료 (2026-07-24) | 기술 스펙 값과 [RV-NFR-009](../01-requirements/non-functional-requirements.md#rv-nfr-009-로그-보관-기간)의 미결정 상태가 충돌 | 14일 보관(기술 스펙 값) 확정. 백업은 일 1회 자동 스냅샷+7일 보관, 알림은 CloudWatch 알람→이메일/Slack, 담당자 1명 |

## 5. 활성화 조건

Backlog 항목은 다음을 모두 충족해야 활성화된다.

1. 상위 범위 변경 또는 명시된 조건 충족
2. 관련 요구사항·NFR·API·데이터 영향 확정
3. 정확한 버전과 공식 호환성 근거 확인
4. 비용·보안·운영·복구 영향 검토
5. 개별 ADR 작성과 팀 리뷰
6. 테스트 계획과 되돌리기 방법 승인

## 6. 작성 우선순위

1. [결정 완료 2026-07-24] 배포 토폴로지와 ALB·ASG·Blue-Green 충돌 해결
2. [결정 완료 2026-07-24] 관리자 JWT 만료·서명 키와 Redis Refresh Token 키·회전·장애 정책 결정
3. [결정 완료 2026-07-24] 로그 보관·운영 지표·알림 기준 결정
4. QueryDSL과 성능 테스트 도구는 구현 복잡도·성능 근거 발생 시 검토
5. 지도·회원·AI·RAG·알림·이미지·자동화는 해당 확장 단계 승인 후 검토

## 7. 결정 기록 (2026-07-24)

| 항목 | 결정 |
|---|---|
| 배포 토폴로지 | 단일 EC2 인스턴스(Nginx+App), 장애 시 수동 복구로 시작. ALB는 확장 단계에서 우선 검토할 확장 경로로 남기고 ASG·Blue-Green은 Post-MVP 보류 |
| 관리자 JWT | Access Token 만료 30분 |
| 관리자 Refresh Token(Redis) | TTL 14일, 재발급마다 회전 + 재사용 탐지·즉시 폐기 |
| Redis 장애 처리 | Fail-closed(재발급 차단, Access Token 만료 후 강제 재로그인) |
| 로그 보관 | 14일 (기존 기술 스펙 값 유지) |
| 백업 | PostgreSQL 일 1회 자동 스냅샷, 7일 보관, RPO 최대 24시간 |
| 운영 알림 | CloudWatch 알람 → 이메일/Slack, 담당자 1명 |

남은 미결정 항목: 배포 자동화 범위([RV-NFR-012](../01-requirements/non-functional-requirements.md#rv-nfr-012-배포-자동화-범위), ALB·Blue-Green 전환 자동화 포함)는 배포 토폴로지가 확장될 때 별도 결정한다.
