# 맛잇온 ADR 추적성

## 1. 문서 목적

확정 기술 스펙의 모든 항목을 ADR, 기술 정책, Backlog 또는 운영 설정에 연결하고 1차 MVP 적용 여부를 명확히 한다.

## 2. 기술 스펙 → ADR 매핑

| 기술 항목 | 현재 문서 상태 | 분류 | 관련 ADR | 분류 근거 |
|---|---|---|---|---|
| JDK 21.0.12 LTS | 고정 | Accepted ADR | ADR-LANG-001 | 백엔드 런타임 기준선 |
| Gradle 8.14.3 + Groovy DSL | 고정 | Accepted ADR | ADR-BUILD-001 | 재현 가능한 빌드 체계 |
| Spring Boot 4.1.0 | 고정 | Accepted ADR | ADR-FRAME-001 | 백엔드 프레임워크 기준선 |
| Spring Security 7.1.0 | BOM 파생·고정 | Duplicate or Derived Rule | ADR-FRAME-001, ADR-AUTH-001 | 버전은 Boot BOM 파생, 사용 방식은 인증 결정에 종속 |
| Node.js 24.18.0 LTS | 고정 | Accepted ADR | ADR-WEB-001 | 프론트엔드 런타임 기준선 |
| Next.js 16.2.11 + TypeScript 7.0.2 | 고정 | Accepted ADR | ADR-WEB-001 | 웹 프레임워크·언어 기준선 |
| Server Components `fetch` + TanStack Query | 확정, TanStack 정확한 버전 미기재 | Accepted ADR | ADR-WEB-002 | 초기·상호작용 데이터 책임 분리, 버전 확정 전 설치 금지 |
| URL Query Parameter | 확정 | Accepted ADR | ADR-WEB-002 | 검색 상태의 공유·재현 |
| React `useState` | 확정 | Duplicate or Derived Rule | ADR-WEB-002 | 화면 지역 상태 구현 규칙 |
| MVP 단일 모듈 | 확정 | Accepted ADR | ADR-ARCH-001 | 초기 배포·테스트 단순화 |
| 도메인 중심 계층형 모놀리스 | 확정 | Accepted ADR | ADR-ARCH-001 | 단일 모듈과 같은 구조 결정 문제 |
| Port/Adapter | 확정 | Accepted ADR | ADR-ARCH-002 | 외부 서비스 변동 격리 |
| PostgreSQL 17.10 | 고정 | Accepted ADR | ADR-DATA-001 | 주 관계형 데이터베이스 |
| 개발 Docker PostgreSQL / 운영 RDS | 확정 | Accepted ADR | ADR-DATA-002 | 환경 분리와 운영 배치 |
| Spring Data JPA | 확정 | Accepted ADR | ADR-DATA-003 | 기본 ORM·Repository 전략 |
| Flyway 12.4.0 | 고정 | Accepted ADR | ADR-DATA-004 | 스키마 변경 단일 경로 |
| QueryDSL | 조건부 | Conditional ADR | ADR-SEARCH-001 | 복합 조회 필요성 확인 후 도입 |
| PostGIS | 기술 스펙 확정, 범위 제외 | Post-MVP ADR | ADR-MAP-001 | 지도·좌표·거리 검색 제외 |
| pgvector | Post-MVP | Post-MVP ADR | ADR-SEARCH-002 | 자연어 검색·RAG 제외 |
| Redis 8.8 전용 인스턴스 | 고정·관리자 Token 역할 확정 | Accepted ADR | ADR-DATA-005 | 관리자 Refresh Token 저장, 캐시·락은 별도 조건부 |
| Redis AOF `everysec` + RDB | 확정 설정 | Operational Configuration | Redis 역할 결정 후 운영 문서 | 아키텍처보다 영속화 설정값 |
| Redis 캐시 | 확정 기술 용도 | Conditional ADR | ADR-CACHE-001 | 성능 병목·무효화 근거 없음 |
| Redis 관리자 Refresh Token | 사용자 확정 | Accepted ADR | ADR-AUTH-001, ADR-DATA-005 | 관리자 JWT 재발급·폐기 |
| Redis 일반 사용자 Refresh Token | 기술 스펙 확정, 범위 제외 | Post-MVP ADR | ADR-AUTH-002 | 일반 사용자 로그인 제외 |
| Redis 분산 락 | 확정 기술 용도 | Conditional ADR | ADR-LOCK-001 | 자동 배치·다중 실행 미확정 |
| 관리자 Spring Security 7.1.0 + JWT | 사용자 확정 | Accepted ADR | ADR-AUTH-001 | 관리자 인증·인가 기준 |
| 관리자 Refresh Token 보안 쿠키 | 사용자 확정 | Accepted ADR | ADR-AUTH-001, ADR-DATA-005 | Redis 저장·회전, HttpOnly·Secure 전달 |
| 일반 사용자 JWT·Refresh Token | 기술 스펙 확정, 범위 제외 | Post-MVP ADR | ADR-AUTH-002 | 회원가입·로그인 제외 |
| springdoc-openapi 3.0.3 + Swagger UI | 고정 | Technology Policy | ADR-FRAME-001 | 구현과 명세 대조 도구, 외부 계약 원문은 `docs/05-specs` |
| Kakao Local REST API V2 | 확정·MVP 필요 | Accepted ADR | ADR-EXT-001 | 관리자 맛집 장소 확인 |
| YouTube Data API v3 | 확정·MVP 필요 | Accepted ADR | ADR-EXT-001 | 관리자 채널·영상 확인 |
| Kakao Maps JavaScript API V3 | 확정이나 범위 제외 | Post-MVP ADR | ADR-MAP-001 | 지도 SDK·표시 제외 |
| Kakao Mobility Directions API V1 | 확정이나 범위 제외 | Post-MVP ADR | ADR-ROUTE-001 | 동선·코스 추천 제외 |
| Java + Jsoup | 확정이나 자동화 제외 | Post-MVP ADR | ADR-AUTO-001 | 자동 수집 제외 |
| Playwright | 필요 시 | Conditional ADR | ADR-CRAWL-001 | JS 렌더링 필요 검증 후 도입 |
| n8n | 확정이나 자동화 제외 | Post-MVP ADR | ADR-AUTO-001 | 자동 수집·동기화 제외 |
| Spring Scheduler | 확정이나 자동화 제외 | Post-MVP ADR | ADR-AUTO-001 | 자동 주기 실행 제외 |
| Spring Batch 6.0.4 | 고정이나 자동화 제외 | Post-MVP ADR | ADR-AUTO-001 | 이력·재시작 배치 범위 없음 |
| 하루 1회 새벽 배치 | 확정 설정이나 기능 제외 | Operational Configuration | ADR-AUTO-001 | 활성화되지 않은 실행 주기 |
| Spring AI 2.0.0 | 고정이나 범위 제외 | Post-MVP ADR | ADR-AI-001 | AI 영상 추출 제외 |
| `gemini-3-flash-preview` | 승인된 Preview이나 범위 제외 | Post-MVP ADR | ADR-AI-001 | AI 기능 활성화 전 사용 금지 |
| JSON Schema + Prompt Template | 확정이나 범위 제외 | Post-MVP ADR | ADR-AI-001 | AI 추출 계약은 기능과 함께 활성화 |
| JUnit 5 + Mockito | 확정 | Accepted ADR | ADR-TEST-001 | 단위 테스트 기준 |
| Spring Boot Test + Testcontainers 2.0.5 | 고정 | Accepted ADR | ADR-TEST-001 | 실제 저장소 통합 검증 |
| WireMock | 확정 | Accepted ADR | ADR-TEST-001 | 외부 API 장애·계약 격리 |
| Spring Batch Test 6.0.4 | 파생·기능 제외 | Duplicate or Derived Rule | ADR-AUTO-001 | Spring Batch 활성화에 종속 |
| k6 | 확정 도구, 환경 미결정 | Conditional ADR | ADR-PERF-001 | 성능 테스트 환경·부하 모델 팀 결정 필요 |
| SLF4J + Logback | 확정 | Accepted ADR | ADR-OBS-001 | 애플리케이션 로그 기준 |
| Actuator + CloudWatch | 확정, 세부 지표 미결정 | Accepted ADR | ADR-OBS-001 | 관측 도구는 확정, 임계값은 운영 설정 |
| 로그 보관 14일 | 결정 완료 (2026-07-24) | Operational Configuration | ADR-OBS-001 | RV-NFR-009 결정 완료, 14일 유지 |
| Parameter Store SecureString + KMS | 확정 | Accepted ADR | ADR-SEC-001 | 운영 비밀정보 보호 |
| EC2 IAM Role | 확정 | Accepted ADR | ADR-SEC-001 | 장기 AWS 키 제거 |
| GitHub Actions OIDC | 확정 | Accepted ADR | ADR-SEC-001 | CI의 단기 AWS 자격 증명 |
| Docker | 확정 | Accepted ADR | ADR-RUNTIME-001 | 재현 가능한 실행·배포 산출물 |
| GitHub Actions 빌드·테스트 | 확정 | Accepted ADR | ADR-CI-001 | 배포 후보 품질 게이트 |
| Nginx | 결정 완료 (2026-07-24) | Scope Conflict Review | 배포 토폴로지 Backlog | 단일 EC2 인스턴스의 리버스 프록시로 확정 |
| Amazon ECR·EC2 | 결정 완료 (2026-07-24) | Scope Conflict Review | 배포 토폴로지 Backlog | 단일 EC2 인스턴스 배포로 확정, 비용 대조는 운영 중 재확인 |
| ALB·ASG·Blue-Green | 결정 완료 (2026-07-24) | Scope Conflict Review | 배포 토폴로지 Backlog | MVP 미도입. 단일 인스턴스 수동 복구로 시작하고 ALB는 확장 단계 검토 경로로 보류 |
| GitHub Actions → ECR → Green → ALB | 부분 결정 | Scope Conflict Review | ADR-CI-001, 배포 토폴로지 Backlog | GitHub Actions → ECR → EC2까지 대상 확정, ALB·Blue-Green 전환 자동화는 배포 토폴로지 확장 시 재설계 (RV-NFR-012 미결정 유지) |
| Amazon S3 이미지 저장 | 확정이나 기능 없음 | Post-MVP ADR | ADR-MEDIA-001 | 이미지 업로드·사용자 이미지 요구사항 없음 |
| FCM HTTP v1 | 확정이나 범위 제외 | Post-MVP ADR | ADR-NOTIFY-001 | 사용자 알림 제외 |
| 초기 월 인프라 예산 15만 원 | 목표 | Operational Configuration | 배포 토폴로지 Backlog | 운영 제약·조정 가능한 수치 |

## 3. NFR → ADR 매핑

| NFR | 관련 ADR | 적용 |
|---|---|---|
| NFR-SECURITY-001~003 | ADR-AUTH-001, ADR-SEC-001 | 관리자 접근, 입력·비밀 보호 |
| NFR-INTEGRITY-001~004 | ADR-DATA-003, ADR-DATA-004, ADR-ARCH-002, ADR-TEST-001 | 참조·원자성·외부 실패 격리 |
| NFR-RELIABILITY-001~003 | ADR-ARCH-002, ADR-TEST-001 | 오류 경계와 장애 검증 |
| NFR-AVAILABILITY-001~002 | ADR-OBS-001, 배포 토폴로지 Backlog | 상태 확인과 단일 인스턴스 수동 복구 |
| NFR-EXTERNAL-001~003 | ADR-ARCH-002, ADR-EXT-001, ADR-SEC-001 | 원본 미저장, 외부 호출 격리, 키 보호 |
| NFR-OBSERVABILITY-001~003 | ADR-OBS-001, ADR-SEC-001 | 요청 추적·지표·민감정보 차단 |
| NFR-TEST-001~003 | ADR-TEST-001, ADR-CI-001 | 테스트 계층과 배포 품질 게이트 |
| NFR-DEPLOYMENT-001~002 | ADR-BUILD-001, ADR-RUNTIME-001, ADR-CI-001, ADR-SEC-001 | 재현 빌드, 환경 분리, 배포 전후 검증 |
| NFR-DEPLOYMENT-003~004 | ADR-DATA-004, 배포 토폴로지 Backlog | 복구·자동화·복잡도는 후속 결정 |
| NFR-MAINTAINABILITY-001~003 | ADR-ARCH-001, ADR-ARCH-002 | 책임 경계와 운영 복잡도 제한 |
| NFR-PRIVACY-001~003 | ADR-AUTH-001, ADR-AUTH-002, ADR-SEC-001 | 일반 사용자 계정 제외와 비밀 보호 |

## 4. API → ADR 매핑

| API 영역 | 관련 ADR | 경계 |
|---|---|---|
| 공개 탐색·상세 API | ADR-WEB-002, ADR-ARCH-001, ADR-DATA-003 | 계약은 `docs/05-specs/api/`가 소유 |
| 관리자 인증 API | ADR-AUTH-001, ADR-DATA-005 | JWT Bearer, Redis Refresh Token 보안 쿠키 사용 |
| 관리자 기준정보 등록 API | ADR-EXT-001, ADR-ARCH-002, ADR-SEC-001 | Kakao·YouTube 확인, 실패·키 격리 |
| 전체 API | ADR-TEST-001, ADR-OBS-001 | 계약·장애 테스트와 요청 추적 |

## 5. 데이터 모델 → ADR 매핑

| 데이터 범위 | 관련 ADR | 비고 |
|---|---|---|
| MVP 관계형 데이터 | ADR-DATA-001, ADR-DATA-003 | 엔티티·관계 원문은 `docs/05-specs/data/` |
| 스키마 변경 | ADR-DATA-004 | Flyway만 사용 |
| 환경별 DB | ADR-DATA-002 | 개발 Docker / 운영 RDS, 버전 17.10 일치 |
| 공간·벡터 데이터 | ADR-MAP-001, ADR-SEARCH-002 | 현재 모델·확장 설치 금지 |
| 사용자·토큰·기기 데이터 | ADR-AUTH-002, ADR-NOTIFY-001 | 현재 MVP 모델에 추가 금지 |

## 6. Workstream → ADR 매핑

| Workstream | 필수 ADR | 추가 책임 |
|---|---|---|
| WS-01 | ADR-WEB-001~002, ADR-ARCH-001, ADR-DATA-003, ADR-TEST-001 | 검색 상태·최종 조회 조합 |
| WS-02 | ADR-ARCH-001~002, ADR-DATA-003, ADR-TEST-001 | 외부 링크 실패 격리·상세 조합 |
| WS-03 | ADR-ARCH-001, ADR-DATA-003, ADR-TEST-001 | Visit 관계 판정 경계 |
| WS-04 | ADR-AUTH-001, ADR-EXT-001, ADR-ARCH-002, ADR-DATA-003~004, ADR-SEC-001 | 인증·외부 확인·등록 정합성 |
| 전체 | ADR-LANG-001, ADR-BUILD-001, ADR-FRAME-001, ADR-OBS-001, ADR-RUNTIME-001, ADR-CI-001 | 공통 구현·운영 기준 |

## 7. 기술 정책 → ADR 매핑

| 기술 정책 | 근거 ADR |
|---|---|
| 고정 버전·BOM·범위 버전 금지 | ADR-LANG-001, ADR-BUILD-001, ADR-FRAME-001, ADR-WEB-001, ADR-DATA-004 |
| 개발·테스트·운영 분리 | ADR-DATA-002, ADR-RUNTIME-001, ADR-SEC-001 |
| DB 스키마 변경 | ADR-DATA-004 |
| Redis 역할 선확정 | ADR-CACHE-001, ADR-LOCK-001, ADR-AUTH-002 |
| 비밀정보·워크로드 인증 | ADR-SEC-001 |
| 조건부·Post-MVP 선제 도입 금지 | 모든 Backlog 항목 |
| AI 생성 코드 검증 | ADR-TEST-001, ADR-CI-001 |

## 8. 미매핑 기술 검토

모든 기술 스펙 항목은 위 표에서 ADR, 정책, Backlog 또는 운영 설정으로 분류됐다. 다음은 2026-07-24 결정 완료 항목이다.

- 관리자 JWT 만료(30분)·Redis Refresh Token TTL(14일, 회전+재사용 탐지)·Redis 장애 시 fail-closed 정책
- Nginx·ECR·EC2를 포함한 최소 MVP 배포 토폴로지(단일 EC2 인스턴스)
- ALB·ASG·Blue-Green: MVP 미도입, ALB는 확장 경로로 보류
- 로그 14일 보관, 백업(일 1회 자동 스냅샷·7일 보관), 운영 알림(CloudWatch→이메일/Slack, 담당자 1명)

다음은 여전히 팀 결정이 필요한 미결정 항목이다.

- TanStack Query, Jsoup, n8n, k6 등 정확한 버전이 없는 의존성
- 배포 자동화 범위(RV-NFR-012): ALB·Blue-Green 전환 자동화를 포함한 수동 승인 지점
