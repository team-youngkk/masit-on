---
related_documents:
  - implementation-conventions.md
  - technology-policy.md
  - architecture-overview.md
  - module-boundaries.md
  - package-structure.md
  - dependency-rules.md
  - application-flow.md
  - transaction-boundaries.md
  - query-composition.md
  - security-boundary.md
  - external-integration.md
  - ../07-adr/architecture/arch-001-domain-monolith.md
  - ../07-adr/architecture/arch-002-external-ports-adapters.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/security/auth-003-confirmation-token.md
---

# 아키텍처 상세 설계

## 1. 목적

이 디렉터리는 Accepted ADR과 확정된 API·논리 데이터 계약을 구현 가능한 패키지, 의존성, 흐름과 경계로 구체화한다. 결정 이유와 대안은 ADR이 권위 있는 원문이며, 이 문서는 결정된 원칙을 실제 코드 구조에 적용하는 기준이다.

현재 저장소에는 `src/`, Gradle 빌드 파일과 애플리케이션 클래스가 없다. 따라서 이 문서의 클래스 이름은 모두 **목표 구조의 제안 이름**이며 현재 구현을 설명하지 않는다. 다만 Gradle `group`과 루트 Java 패키지 `com.masiton`, 진입점 `com.masiton.MasitOnApplication`은 [패키지 구조](package-structure.md) 1절에서 확정됐다.

## 2. 문서 읽기 순서

1. [구현 컨벤션 및 공통 정책](implementation-conventions.md): Java, Spring·JPA, 테스트, Git과 AI 구현 규칙
2. [기술 정책](technology-policy.md): 고정 기술, 버전과 도입 금지 기준
3. [아키텍처 개요](architecture-overview.md): 전체 스타일과 현재/목표 상태
4. [모듈 경계](module-boundaries.md): 네 도메인의 소유권과 공개 계약
5. [패키지 구조](package-structure.md): 목표 Java 패키지 트리와 이전 방법
6. [의존성 규칙](dependency-rules.md): 허용·금지 의존과 자동 검증
7. [애플리케이션 흐름](application-flow.md): 주요 Command·Query 실행 순서
8. [트랜잭션 경계](transaction-boundaries.md): 원자성, 동시성과 외부 호출 경계
9. [조회 조합](query-composition.md): 맛집 상세 전용 읽기 모델과 성능 원칙
10. [보안 경계](security-boundary.md): 인증, 인가와 관리자 유스케이스
11. [외부 연동](external-integration.md): Port/Adapter와 실패 변환

## 3. 문서별 역할

| 문서 | 답하는 질문 | 다루지 않는 내용 |
|---|---|---|
| `implementation-conventions.md` | 모든 구현과 협업에 공통으로 적용할 규칙은 무엇인가? | 개별 기능의 상세 계약 |
| `architecture-overview.md` | 시스템을 어떤 큰 구조로 나누는가? | 세부 패키지와 클래스 |
| `module-boundaries.md` | 각 도메인이 무엇을 소유하고 무엇만 공개하는가? | 계층별 디렉터리 배치 |
| `package-structure.md` | 코드를 어느 패키지에 두는가? | 결정의 역사와 대안 |
| `dependency-rules.md` | 어떤 import와 호출이 허용되는가? | 기능별 실행 순서 |
| `application-flow.md` | 요청이 어떤 컴포넌트를 거치는가? | 물리 DB 스키마 |
| `transaction-boundaries.md` | 어디에서 트랜잭션을 시작·종료하는가? | 테이블·인덱스 명 |
| `query-composition.md` | 상세 응답을 누가, 어떻게 조합하는가? | 외부 API 등록 검증 |
| `security-boundary.md` | 신원·권한·업무 규칙 검증을 누가 하는가? | JWT 암호 알고리즘·키 운영 수치 |
| `external-integration.md` | 외부 제공자 변경과 실패를 어디에서 차단하는가? | 제공자별 실제 요청 JSON 전체 |

## 4. 관련 ADR

- [ADR-ARCH-001 단일 모듈 도메인 중심 모놀리스](../07-adr/architecture/arch-001-domain-monolith.md)
- [ADR-ARCH-002 외부 연동 Port/Adapter 경계](../07-adr/architecture/arch-002-external-ports-adapters.md)
- [ADR-DATA-003 Spring Data JPA](../07-adr/data/data-003-spring-data-jpa.md)
- [ADR-DATA-004 Flyway](../07-adr/data/data-004-flyway.md)
- [ADR-AUTH-001 관리자 Spring Security JWT](../07-adr/security/auth-001-spring-security-jwt.md)
- [ADR-AUTH-003 관리자 등록 확인 Token](../07-adr/security/auth-003-confirmation-token.md)
- [ADR-WEB-003 웹 화면·API·운영 경로 경계](../07-adr/platform/web-003-routing-boundary.md)
- [ADR-EXT-001 관리자 외부 기준정보 확인](../07-adr/integration/ext-001-reference-verification.md)
- [ADR-TEST-001 계층별 자동화 테스트](../07-adr/quality/test-001-automation-strategy.md)

## 5. 구현 시 사용 방법

- 새 클래스는 먼저 소유 도메인과 계층을 결정한 뒤 [패키지 구조](package-structure.md)에 배치한다.
- 다른 도메인 코드가 필요하면 내부 클래스를 import하지 말고 [모듈 경계](module-boundaries.md)의 공개 계약 또는 교차 도메인 `orchestration` 유스케이스를 사용한다.
- Command 유스케이스는 [트랜잭션 경계](transaction-boundaries.md), Query는 [조회 조합](query-composition.md)을 확인한다.
- 외부 HTTP 클라이언트, JPA 타입이나 Spring Security 타입이 도메인 계층에 들어오면 [의존성 규칙](dependency-rules.md) 위반이다.
- 구현 PR은 관련 요구사항·API·ADR, 변경한 경계, 트랜잭션과 테스트 근거를 함께 적는다.

## 6. 리뷰 체크리스트

- [ ] 네 도메인 중 소유자가 명확한가?
- [ ] 교차 도메인 흐름이 `orchestration`에 있고 도메인 규칙을 소유하지 않는가?
- [ ] 도메인 내부 엔티티·Repository를 다른 도메인이 import하지 않는가?
- [ ] Controller나 Repository 구현체가 트랜잭션을 소유하지 않는가?
- [ ] 공개 조회가 Kakao·YouTube를 실시간 호출하지 않는가?
- [ ] 외부 DTO·JPA Entity가 API 응답 또는 도메인 계약으로 노출되지 않는가?
- [ ] `common` 이동 기준과 회수 기준을 통과하는가?
- [ ] ArchUnit, 단위·통합·계약 테스트가 변경 위험에 맞게 추가됐는가?

## 7. 상태 표기

- **확정**: Accepted ADR 또는 확정 계약에서 직접 파생된 규칙
- **상세 설계 결정**: ADR 범위 안에서 이 문서가 구체화한 구현 구조
- **제안**: 구현 전 팀 확인이 필요한 이름·배치
- **확인 필요**: 저장소에서 근거를 찾지 못했거나 상위 문서끼리 불일치
- **추가 ADR 필요**: 장기 영향이나 대안의 비용이 커 별도 결정 기록이 필요한 항목
