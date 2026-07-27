---
related_documents:
  - architecture-overview.md
  - module-boundaries.md
  - package-structure.md
  - ../07-adr/architecture/arch-001-domain-monolith.md
  - ../07-adr/data/data-003-spring-data-jpa.md
---

# 의존성 규칙

## 1. 기본 방향

각 도메인 내부의 컴파일 타임 의존 방향은 다음과 같다.

```text
presentation ──> application ──> domain
infrastructure ──> application ──> domain
infrastructure ─────────────────> domain
```

Application이 Persistence나 외부 API 기능을 호출할 때는 자신이 소유한 `port.out` 인터페이스를 호출한다. Infrastructure Adapter가 이 인터페이스를 구현한다.

## 2. 계층별 허용 의존성

| From \ To | Presentation | Application | Domain | Infrastructure |
|---|---:|---:|---:|---:|
| Presentation | 같은 입력 Adapter 내부만 | 허용 | 값 타입에 한해 제한 허용 | 금지 |
| Application | 금지 | 같은 유스케이스·공개 Port | 허용 | 금지 |
| Domain | 금지 | 금지 | 같은 도메인 내부 | 금지 |
| Infrastructure | 금지 | Port 구현을 위해 허용 | 매핑을 위해 허용 | 같은 Adapter 내부만 |

추가 규칙:

- Controller는 Application 입력 Port만 호출한다.
- Application은 Spring Data Repository, `EntityManager`, HTTP Client를 직접 import하지 않는다.
- Domain은 `org.springframework..`, `jakarta.persistence..`, 제공자 SDK 패키지를 import하지 않는다.
- Infrastructure가 다른 Infrastructure 구현체를 호출해 업무 흐름을 만들지 않는다.
- API DTO와 외부 DTO는 Domain 타입으로 사용하지 않는다.

## 3. 도메인 간 의존 규칙

### 허용

- `orchestration.application` → 각 도메인의 `application.port.in`
- `orchestration`의 읽기 Adapter → 승인된 읽기 전용 DB Projection
- 각 도메인의 Infrastructure Adapter → 자신이 구현하는 Port
- Presentation → 동일 도메인 Application
- 테스트 → 대상 공개 계약과 필요한 Fixture

### 금지

- `restaurant.domain` → `visit.*`
- `visit.*` → `restaurant.infrastructure.persistence.*`
- `creator.application` → `video.domain.model.Video`
- 한 도메인의 JPA Entity에 다른 도메인의 JPA Entity 객체 연관 선언
- 다른 도메인의 Spring Data Repository 직접 주입
- 도메인 A와 B가 서로의 Application을 양방향 호출

교차 도메인 협력이 양방향으로 보이면 호출을 양쪽에 추가하지 말고 `orchestration`으로 끌어올린다.

## 4. 컴파일 타임과 런타임 의존

예를 들어 `RegisterRestaurantService`는 컴파일 타임에 `RestaurantRepositoryPort`만 안다. 런타임에는 Spring이 `RestaurantPersistenceAdapter`를 주입하고 실제 호출은 PostgreSQL로 이어진다.

```text
컴파일 타임:
RegisterRestaurantService ──> RestaurantRepositoryPort
RestaurantPersistenceAdapter ──> RestaurantRepositoryPort

런타임:
RegisterRestaurantService ──call──> RestaurantPersistenceAdapter ──> PostgreSQL
```

런타임 호출 방향을 이유로 Application이 Adapter 구현 클래스를 import하면 안 된다.

## 5. 순환 의존 방지

- 최상위 도메인 패키지 간 직접 의존을 기본 금지한다.
- 교차 흐름은 `orchestration`이 모든 도메인을 향하는 단방향 구조로 만든다.
- `common`을 순환 의존 우회로로 사용하지 않는다.
- 이벤트 Listener가 원래 도메인을 다시 동기 호출하는 구조도 순환으로 본다.
- Spring의 `@Lazy`, Bean 이름 조회, ApplicationContext 직접 조회로 순환 의존을 숨기지 않는다.

## 6. 전환 의존 규칙

도메인 간 전달 타입은 다음 중 하나여야 한다.

1. 불투명 식별자
2. 공개 입력 Port가 반환한 최소 Snapshot
3. Query 전용 Projection
4. 도메인 의미가 없는 공통 값 타입

다음은 금지한다.

- 다른 도메인의 Aggregate 또는 JPA Entity
- 가변 컬렉션과 Lazy Proxy
- 외부 제공자 응답 DTO
- Controller 요청·응답 DTO
- “나중에 쓸 수 있어서” 추가한 미사용 필드

## 7. 읽기 모델 예외

맛집 목록·상세처럼 여러 테이블을 조인해야 하는 Query Adapter는 읽기 전용으로 다른 도메인 소유 테이블을 참조할 수 있다.

- Application이 소유한 Query Port를 구현한다.
- Projection 전용 SQL/JPQL만 수행하고 Entity 상태를 변경하지 않는다.
- 소유 도메인의 Repository를 재사용하거나 반환하지 않는다.
- 스키마 변경 시 영향받는 도메인 담당자의 리뷰를 받는다.
- Command 경로에서는 이 예외를 사용할 수 없다.

## 8. ArchUnit 적용 예시

아래는 목표 규칙의 예시이며 실제 루트 패키지 확정 후 테스트 코드로 옮긴다.

```java
@AnalyzeClasses(packages = "{basePackage}")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "com.google.api..");

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule presentation_must_not_access_persistence =
            noClasses().that().resideInAPackage("..presentation..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..");
}
```

추가로 다음 규칙을 구현한다.

- 네 도메인의 `domain`·`infrastructure` 상호 import 금지
- `orchestration.domain`·`orchestration.infrastructure.persistence` 패키지 생성 금지
- `common`에서 네 도메인 패키지 의존 금지
- 패키지 Slice 순환 의존 금지
- Controller 이름 클래스의 Repository 의존 금지

`{basePackage}` placeholder는 그대로 실행할 수 없으므로 프로젝트 초기 설정에서 실제 값으로 치환해야 한다.

## 9. 리뷰 위반 사례

| 위반 | 문제 | 수정 방향 |
|---|---|---|
| Controller에서 네 Repository 주입 | 조합·트랜잭션이 입력 계층에 분산 | Application Query/Command로 이동 |
| Domain에 `@Entity` 사용 | 프레임워크 독립성 위반 | Persistence Entity와 매퍼 분리 |
| `CommonVisitService` 생성 | 소유권 은폐 | Visit 또는 Orchestration으로 이동 |
| 상세 조회에서 Entity graph 직렬화 | Lazy/N+1·계약 누출 | Projection을 응답 DTO로 변환 |
| Application에서 Kakao DTO 사용 | 제공자 변경 전파 | 내부 확인 결과로 Adapter에서 변환 |
