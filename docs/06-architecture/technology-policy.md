---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../05-specs/api-review.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../05-specs/data/README.md
  - ../07-adr/README.md
  - ../07-adr/adr-index.md
  - ../07-adr/adr-backlog.md
  - ../07-adr/adr-traceability.md
---

# 맛잇온 기술 정책

## 1. 문서 목적

이 문서는 Accepted ADR에서 파생되어 모든 Workstream과 AI 에이전트가 반복 적용해야 하는 기술 규칙을 정의한다. 기술 선택의 이유와 대안은 `docs/07-adr/`의 개별 ADR에서 관리하고, 기능 요구사항·API 계약·데이터 모델은 각 원문을 참조한다.

## 2. 적용 우선순위

1. [docs/00-overview/scope.md](../00-overview/scope.md)의 1차 MVP 범위
2. 확정 요구사항, 비즈니스 규칙, API·데이터 명세
3. Accepted ADR
4. 이 기술 정책
5. Conditional·Post-MVP ADR Backlog

하위 문서가 상위 범위를 확장할 수 없다. 충돌은 임의로 해소하지 않고 [docs/07-adr/adr-backlog.md](../07-adr/adr-backlog.md)의 범위 충돌 검토에 기록한다.

## 3. 고정 버전 정책

다음 기준은 승인 전까지 변경하지 않는다.

| 영역 | 고정 기준 | 현재 적용 상태 |
|---|---|---|
| Java | JDK 21.0.12 LTS | MVP Accepted |
| 백엔드 프레임워크 | Spring Boot 4.1.0 | MVP Accepted |
| Spring Security | 7.1.0, Spring Boot BOM 관리 | 관리자 JWT 인증·인가에 적용 |
| 빌드 | Gradle 8.14.3 + Groovy DSL | MVP Accepted |
| DB 마이그레이션 | Flyway 12.4.0 | MVP Accepted |
| Node.js | 24.18.0 LTS | MVP Accepted |
| 프론트엔드 | Next.js 16.2.11 + TypeScript 7.0.2 | MVP Accepted |
| React | 19.2.0 (`react`, `react-dom`) | MVP Accepted. Next.js 16.2.11의 peer 범위가 `^19.0.0`으로 넓어 Next 고정만으로는 패치가 고정되지 않으므로 별도로 명시한다 |
| 프론트엔드 타입 정의 | `@types/node` 24.10.1, `@types/react` 19.2.2, `@types/react-dom` 19.2.1 | MVP Accepted |
| PostgreSQL | 17.10 | MVP Accepted |
| Redis | Redis Open Source 8.8 | 관리자 Refresh Token 저장에 적용, 캐시·락은 조건부 |
| Spring AI | 2.0.0 | Post-MVP |
| Gemini | `gemini-3-flash-preview` | Post-MVP, 승인된 유일한 Preview 예외 |
| springdoc-openapi | 3.0.3 | MVP Accepted |
| Testcontainers | 2.0.5 | MVP Accepted |
| ArchUnit | archunit-junit5 1.4.1 | MVP Accepted, `T-01`에서 고정 (Spring Boot BOM 관리 대상 아님) |
| Gradle 의존성 관리 플러그인 | io.spring.dependency-management 1.1.7 | MVP Accepted, `T-01`에서 고정 |
| WireMock 로컬 컨테이너 | `wiremock/wiremock:3.13.2-alpine` | MVP Accepted, `T-01`에서 고정. Compose와 통합 테스트가 같은 태그를 사용한다 |
| Spring Batch | 6.0.4, Spring Boot BOM 관리 | Post-MVP |

- `latest`, `+`, `x`, `^`, `~` 또는 동등한 범위 버전을 사용하지 않는다.
- 고정 버전을 다른 패치·메이저 버전으로 바꾸지 않는다.
- RC, Snapshot, 미승인 Preview 의존성을 사용하지 않는다.
- 컨테이너 이미지는 검증된 명시 태그를 사용하고 운영 확정 시 digest도 고정한다.

## 4. 의존성 관리 정책

- Spring 생태계 의존성은 Spring Boot 4.1.0 BOM을 우선 사용한다.
- AI 기능이 정식 활성화된 뒤에만 Spring AI 2.0.0 BOM을 사용한다.
- BOM 관리 대상의 개별 버전을 중복 선언하지 않는다.
- API 문서화는 springdoc-openapi 3.0.3과 Swagger UI를 사용하되 `docs/05-specs/`의 확정 외부 계약을 권위 있는 원문으로 유지하고 생성 문서와 대조한다.
- 정확한 버전이 확정되지 않은 라이브러리는 이름이 기술 스펙에 있어도 의존성에 추가하지 않는다.
- 패키지 매니저, IDE, 클라우드 콘솔의 자동 업그레이드 제안은 변경 승인이 아니다.
- 잠금 파일과 Wrapper 등 재현 가능한 빌드에 필요한 파일을 저장소에서 관리한다.

## 5. 개발·테스트·운영 환경 분리

- 개발은 로컬 Docker의 PostgreSQL을 사용하며 운영 RDS에 연결하지 않는다.
- Redis 역할이 활성화되면 개발은 로컬 Docker Redis만 사용하며 운영 Redis에 연결하지 않는다.
- 테스트는 Testcontainers 또는 격리된 대체 구현을 사용하고 운영 리소스를 사용하지 않는다.
- 운영 접속 정보는 환경별 프로파일과 비밀값으로 주입한다.
- 운영 설정에 `localhost` 또는 Docker 서비스명을 넣지 않는다.
- 개발 설정에 RDS·운영 Redis 엔드포인트 또는 운영 자격 증명을 넣지 않는다.

## 6. 데이터베이스 연결 정책

- 개발 Docker PostgreSQL과 운영 Amazon RDS for PostgreSQL은 17.10을 사용한다.
- 모든 스키마 변경은 Flyway 마이그레이션으로 수행한다.
- 운영 DB를 개발·수동 테스트 데이터베이스로 사용하지 않는다.
- 애플리케이션 버전과 적용된 Flyway 버전의 대응 관계를 추적한다.
- PostGIS와 pgvector는 관련 Post-MVP 기능이 활성화되기 전 확장 설치·의존성·스키마를 추가하지 않는다.

## 7. Redis 연결 및 역할 분리 정책

- Redis 버전 기준과 실제 사용 역할을 별개 결정으로 취급한다.
- 관리자 Refresh Token은 Redis 8.8에 저장한다. Access Token 만료는 30분, Refresh Token TTL은 14일이며 재발급마다 회전하고 재사용을 탐지해 즉시 폐기한다. Redis 장애 시에는 재발급을 차단하는 fail-closed로 처리하여 Access Token 만료 후 재로그인을 요구한다 (2026-07-24 결정).
- 캐시, 일반 사용자 Refresh Token과 분산 락은 관리자 Token 역할과 분리하고 각각 활성화 조건을 충족한 뒤 도입한다.
- 1차 MVP에서 일반 사용자 Refresh Token 저장은 금지한다.
- 자동 배치와 다중 인스턴스 실행이 확정되기 전 분산 락을 도입하지 않는다.
- Redis를 활성화할 경우 개발·운영의 키 형식, TTL, 직렬화와 락 해제 규칙을 일치시킨다.
- AOF `everysec`, RDB 스냅샷과 구체적 TTL은 운영 설정이며 배포 설계에서 검증한다.

## 8. 비밀정보 관리 정책

- 비밀번호, 세션 식별자, 토큰, API 키, AWS 장기 키를 코드·설정 기본값·로그·API 응답·Git에 저장하지 않는다.
- 운영 비밀값은 AWS Systems Manager Parameter Store SecureString과 KMS로 보호한다.
- EC2는 IAM Role, GitHub Actions는 OIDC 기반 단기 자격 증명을 사용한다.
- 최소 권한을 적용하고 개발 환경에 운영 권한을 배포하지 않는다.
- 비밀값 예시는 실제 값과 구분되는 명시적 placeholder만 사용한다.

## 9. 조건부 기술 도입 정책

- Conditional 항목은 [adr-backlog.md](../07-adr/adr-backlog.md)의 활성화 조건과 도입 전 확인 항목을 모두 충족하기 전 의존성·설정·스키마를 추가하지 않는다.
- QueryDSL은 단순 Repository 메서드나 명시적 JPQL로 유지하기 어려운 복합 조회가 검증된 뒤 도입한다.
- Playwright는 JavaScript 렌더링이 필수인 승인된 수집 기능이 생긴 뒤 도입한다.
- Redis 분산 락은 동일 작업의 다중 실행 가능성과 중복 피해가 확인된 뒤 도입한다.
- 조건 충족 여부와 테스트 결과를 기록하고 담당 Workstream과 ADR 소유자의 리뷰를 받는다.

## 10. Preview 및 실험 기술 정책

- `gemini-3-flash-preview`는 사용자가 승인한 유일한 Preview 예외지만 AI 영상 추출이 Post-MVP이므로 현재 의존성이나 운영 설정에 추가하지 않는다.
- 모델 ID를 정식·신규·다른 Preview 모델로 자동 교체하지 않는다.
- 실험 기술은 운영 경로와 분리하고 삭제 조건, 검증 범위와 비용 한도를 기록한다.
- 실험 성공만으로 Accepted 상태로 승격하지 않으며 범위 변경과 ADR 승인이 필요하다.

## 11. AI 에이전트 구현 규칙

1. 고정 기술·버전을 최신, 다른 메이저, RC·Snapshot으로 바꾸지 않는다.
2. 미확정 기술·버전·엔진의 빈칸을 추측하거나 기본값으로 채우지 않는다.
3. Conditional·Post-MVP 기술을 선제 도입하지 않는다.
4. API 계약, 데이터 모델과 기능 범위를 ADR에서 재정의하지 않는다.
5. AI 생성 코드도 동일한 테스트, 정적 검사와 사람 리뷰를 통과해야 한다.
6. 범위나 문서 충돌을 발견하면 구현을 확장하지 않고 Backlog에 기록하거나 사용자·팀 결정을 요청한다.
7. 공식 호환성 근거 없이 버전 호환을 단정하지 않는다.

## 12. 기술 변경 절차

1. 변경 대상 ADR과 관련 요구사항·API·데이터·운영 영향을 식별한다.
2. 대안, 변경 이유, 호환성 공식 근거와 마이그레이션·복구 방법을 작성한다.
3. 기존 ADR을 직접 지우지 않고 새 ADR로 대체하거나 `Superseded` 관계를 기록한다.
4. 관련 Workstream과 필수 리뷰어의 승인을 받는다.
5. 단위·통합·계약·배포 검증 중 영향받는 테스트를 실행한다.
6. ADR 인덱스, 추적성, 정책과 구현을 함께 갱신한다.

## 13. 실행 및 운영 배포 토폴로지 정책 (2026-07-28 변경)

- 1차 MVP 구현은 로컬 Docker 환경에서 Next.js, Spring Boot, PostgreSQL과 Redis를 통합 실행하고 검증한다.
- M2에서 다음 확장 단계보다 먼저 최초 운영 환경을 제한 공개로 배포하고, 검증을 통과한 같은 환경을 계속 운영한다.
- 최초 운영 배포는 단일 EC2 인스턴스(Nginx 리버스 프록시 + Next.js 프론트엔드 + Spring Boot 백엔드)를 사용하며 다중 리전·다중 인스턴스 고가용성 구성을 필수로 하지 않는다.
- Nginx는 `/api/**`를 Spring Boot, 나머지 외부 경로를 Next.js로 전달하며 `/internal/**`은 인터넷에서 차단한다. 세부 경로와 인증 matcher는 [ADR-WEB-003](../07-adr/platform/web-003-routing-boundary.md)을 따른다.
- 장애 발생 시 운영자가 인스턴스를 수동으로 재기동·교체하는 절차를 사용하며, ASG 기반 자동 복구는 도입하지 않는다.
- ALB·Blue-Green·ASG 다중 인스턴스 자동화는 운영 근거가 생기는 확장 단계에서 재검토한다. 전환 시 Nginx의 경로 라우팅 책임을 ALB가 대체할지도 함께 결정한다.
- GitHub Actions 빌드·테스트 품질 게이트는 전 단계에 적용하고, ECR push·EC2 승인 배포·Smoke Test는 M2부터 활성화한다.
- M2부터 GitHub Actions → ECR → EC2 경로를 사용한다. ALB·Blue-Green 전환 자동화 범위는 배포 토폴로지가 확장될 때 별도로 설계한다.
- M2부터 로그는 14일 보관하고, DB 백업은 일 1회 자동 스냅샷 후 7일 보관(RPO 최대 24시간)하며, 운영 알림은 CloudWatch 알람을 Slack으로 담당자 1명에게 통지한다.
- 관련: [ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md), [ADR-WEB-003](../07-adr/platform/web-003-routing-boundary.md), [docs/07-adr/adr-backlog.md](../07-adr/adr-backlog.md) 범위 충돌 검토, [RV-NFR-005](../01-requirements/non-functional-requirements.md#rv-nfr-005-목표-가용성과-복구-시간)·[RV-NFR-009](../01-requirements/non-functional-requirements.md#rv-nfr-009-로그-보관-기간)·[RV-NFR-010](../01-requirements/non-functional-requirements.md#rv-nfr-010-백업-주기와-복구-범위)·[RV-NFR-013](../01-requirements/non-functional-requirements.md#rv-nfr-013-운영-알림-기준).

## 14. 위반 검증 방법

- Gradle·npm 의존성에서 범위 버전, 직접 버전 중복과 미승인 의존성을 검사한다.
- Wrapper, toolchain, Node·컨테이너 이미지와 CI 런타임 버전을 대조한다.
- 설정 파일과 Git 이력에서 운영 엔드포인트·`localhost` 오배치와 비밀정보를 검사한다.
- Flyway 외 스키마 변경과 Post-MVP 확장 설치 여부를 검사한다.
- 테스트가 운영 네트워크 없이 실행되는지 확인한다.
- ADR 인덱스와 Backlog 상태가 실제 의존성·설정과 일치하는지 리뷰한다.
