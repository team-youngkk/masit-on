---
related_documents:
  - README.md
  - module-boundaries.md
  - package-structure.md
  - dependency-rules.md
  - diagrams/component-overview.md
  - ../07-adr/architecture/arch-001-domain-monolith.md
  - ../07-adr/architecture/arch-002-external-ports-adapters.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - ../07-adr/integration/route-001-kakao-mobility-course-routing.md
  - ../08-planning/third-expansion-task-breakdown.md
  - ../07-adr/platform/web-003-routing-boundary.md
---

# 아키텍처 개요

## 1. 전체 스타일

맛잇온 백엔드는 **단일 Gradle 빌드·단일 Spring Boot 실행 산출물·단일 PostgreSQL을 사용하는 도메인 중심 계층형 모놀리스**다. Restaurant, Creator, Video, Visit를 비즈니스 도메인 경계로 두고, 각 경계 안에 `presentation`, `application`, `domain`, `infrastructure`를 둔다.

여러 도메인의 공개 계약을 한 유스케이스에서 조정해야 할 때는 비즈니스 도메인이 아닌 `orchestration` 애플리케이션 경계를 사용한다. `orchestration`은 상세 조회 조합과 방문 관계 등록처럼 교차 도메인 순서·실패·트랜잭션을 관리하지만, 엔티티·Repository·도메인 규칙은 소유하지 않는다.

이 구조는 [ADR-ARCH-001](../07-adr/architecture/arch-001-domain-monolith.md)의 단일 모듈·도메인 중심 패키지 결정을 구체화한다. 외부 Kakao·YouTube 호출은 [ADR-ARCH-002](../07-adr/architecture/arch-002-external-ports-adapters.md)에 따라 애플리케이션 Port와 인프라 Adapter로 격리한다.

3차 확장에서는 자연어 검색을 기존 공개 목록 Query 조합으로 제한하고, AI 영상 추출을 후보·검수 경계에 격리하며, 코스 추천은 요청 범위의 Route Provider 응답으로 조합한다. AI 후보 저장·Worker·검수는 정식 Restaurant·Creator·Video·Visit Entity의 소유권을 가져가지 않으며, 코스 결과도 영속 캐시를 만들지 않는다. 세 경계의 실행 순서는 [3차 확장 E3 Task 분해](../08-planning/third-expansion-task-breakdown.md)와 각 Accepted ADR을 따른다.

## 2. 기술 계층 중심 구조와의 비교

| 기준 | 기술 계층 최상위 (`controller/`, `service/`, `repository/`) | 도메인 최상위 (`restaurant/`, `creator/` 등) |
|---|---|---|
| 변경 범위 | 한 기능 변경이 여러 최상위 패키지에 흩어짐 | 한 도메인 패키지 안에서 대부분 완결 |
| Workstream 소유권 | 계층별 충돌과 공동 파일 증가 | 도메인·기능 담당자의 세로 소유권과 일치 |
| 규칙 위치 | 공용 Service로 모이기 쉬움 | 도메인 내부에 규칙을 유지하기 쉬움 |
| 도메인 간 결합 | import만으로 소유권을 구분하기 어려움 | 공개 계약과 내부 구현을 구분 가능 |
| ADR 부합성 | ADR-ARCH-001의 금지 대안 | **선택** |

## 3. 핵심 원칙

1. 비즈니스 데이터와 불변 조건은 네 도메인 중 하나가 소유한다.
2. 의존성은 바깥 계층에서 안쪽 계층으로 향한다.
3. 도메인 계층은 Spring, JPA, HTTP, 외부 제공자 DTO를 알지 못한다.
4. 다른 도메인의 엔티티·Repository·내부 서비스에 직접 접근하지 않는다.
5. 여러 도메인의 변경 순서와 결과 조합은 `orchestration.application`이 담당한다.
6. 관리자 기능은 독립 비즈니스 도메인이 아니며 각 도메인의 관리자용 입력 Adapter와 교차 도메인 조정 유스케이스로 구성한다.
7. 공개 조회는 저장된 내부 데이터만 사용하고 실시간 외부 API 호출을 하지 않는다.
8. 쓰기 원자성은 유스케이스 단위 트랜잭션과 DB 제약을 함께 사용한다.
9. 조회 전용 Projection은 도메인 Entity와 분리하고 필요한 테이블을 읽을 수 있지만 쓰기는 금지한다.
10. `common`은 기술적으로 범용이고 소유 도메인이 없는 코드만 허용한다.
11. 자연어 해석은 통제 태그·기존 필터 조건으로만 구조화하고, 원문 검색 이력을 저장하지 않는다.
12. AI Provider 호출은 `ai.application.port.out` 뒤에 두고 후보·시도·검수 상태만 저장하며, 정식 Entity 연결은 검증된 Orchestration Command로 한정한다.
13. 코스 경로는 `route` Port/Adapter와 요청 단위 응답으로 처리하고, 현재 위치 수집·경로 영속 캐시를 도입하지 않는다.

## 4. 상위 컴포넌트 관계

[컴포넌트 다이어그램](diagrams/component-overview.md)은 다음 관계를 표현한다.

- 공개·관리자 HTTP 요청은 Presentation Adapter로 들어온다.
- 단일 도메인 유스케이스는 해당 도메인 Application을 호출한다.
- 교차 도메인 유스케이스는 Orchestration Application이 각 도메인의 공개 입력 Port를 호출한다.
- 도메인 Application은 Domain 모델과 자신이 소유한 출력 Port에 의존한다.
- Persistence와 외부 HTTP Adapter가 출력 Port를 구현한다.
- Spring Security Filter Chain은 관리자 요청이 애플리케이션에 도달하기 전에 인증과 역할을 검증한다.

## 5. 컴파일 타임과 런타임 구조

컴파일 타임에는 `presentation → application → domain` 방향의 Java 의존성이 존재하고, `infrastructure → application/domain`이 Port를 구현한다. `domain → infrastructure` import는 없다.

런타임에는 Spring이 Application이 요구하는 Port에 Infrastructure Adapter를 주입하므로 호출은 `application → port 구현체`로 보인다. 이는 의존성 역전이며 컴파일 타임 방향을 뒤집는 것이 아니다. 상세 기준은 [의존성 규칙](dependency-rules.md)을 따른다.

## 6. 현재 상태와 목표 상태

| 항목 | 현재 저장소에서 확인한 상태 | 목표 상태 |
|---|---|---|
| 애플리케이션 소스 | `src/main/java/com/masiton`에 MVP·1/2차와 AI 추출 일부 구현 확인 | Java 21/Spring Boot 단일 모듈과 3차 WS-14·WS-16 구현 |
| 빌드 설정 | Gradle Wrapper·단일 모듈 확인 | ADR에 고정된 Gradle Groovy DSL |
| 도메인 클래스 | Restaurant·Creator·Video·Visit·orchestration 및 `ai` 기술 경계 일부 존재 | 3차 계약에 맞는 자연어·코스 Port/Adapter와 경계 검증 |
| 패키지명 | `com.masiton` | [패키지 구조](package-structure.md)의 패턴 적용 |
| 트랜잭션 코드 | 기존 등록·조회·AI Job 일부 구현 | 모든 3차 Command·외부 실패 원자성 |
| 외부 Adapter | YouTube·Gemini 일부 확인, 자연어·Mobility 구현 미확인 | Kakao·YouTube·Gemini·Mobility Port/Adapter |
| 아키텍처 검증 | 기존 ArchUnit·SQL 경계 테스트 확인 | 3차 AI·검색·코스 경계와 회귀 증거 |

문서 승인과 구현 완료는 구분한다. 현재 확인 가능한 공백은 자연어 검색·코스 추천의 구현·계약 테스트·브라우저·성능 증거이며, AI 추출은 소스와 Flyway가 일부 존재하더라도 전체 E3 완료 게이트를 통과한 것으로 보지 않는다.

## 7. 확정, 제안과 추가 결정

### 확정

- 단일 배포 모듈과 도메인 중심 패키지
- Spring Data JPA, PostgreSQL, Flyway
- 관리자 Spring Security JWT와 Redis Refresh Token
- 관리자 등록 시 Kakao·YouTube Port/Adapter
- `/api` 화면·백엔드 분리와 내부 상태 확인 경로

### 상세 설계 결정

- 교차 도메인 흐름을 위한 비도메인 `orchestration` 경계
- 맛집 상세는 `orchestration.application.query`의 전용 Query Service가 조합
- 방문 등록은 `orchestration.application.command`의 유스케이스가 트랜잭션 소유
- 교차 도메인 조회 Projection은 읽기만 허용

### 3차 확장 경계

- 자연어 검색: `arch-005`에 따라 Parser가 `QueryCondition`을 만들고 기존 Restaurant·Creator·Visit 공개 Query를 조합한다.
- AI 영상 추출: `ai-001`, `ext-003`에 따라 Provider·Worker·후보 Snapshot·검수 이력을 핵심 Entity와 분리한다.
- 코스 추천: `route-001`에 따라 선택된 공개 좌표를 결정론적으로 정렬한 뒤 Kakao Mobility Port를 코스당 한 번 호출한다.
- `ai`·`route`를 최상위 기술 경계로 유지할지 `orchestration` 하위로 재배치할지는 구현 경계 검토에서 확정하며, 그 전까지 새 도메인 Entity를 추가하지 않는다.

### 확정된 초기 구조

- Gradle `group`과 Java 루트 패키지는 `com.masiton`이다.
- Spring Boot 진입점은 `com.masiton.MasitOnApplication`이다.
- 운영 소스는 `src/main/java/com/masiton`, 테스트는 `src/test/java/com/masiton`에서 시작한다.

### 추가 ADR 필요

- 모놀리스 내부 교차 도메인 이벤트를 실제로 도입하거나 비동기화할 때
- 도메인별 Gradle 멀티모듈 또는 독립 배포로 전환할 때
- 상세 조회에 별도 읽기 저장소, CQRS 동기화 또는 캐시를 도입할 때
