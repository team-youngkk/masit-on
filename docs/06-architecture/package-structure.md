---
related_documents:
  - architecture-overview.md
  - module-boundaries.md
  - dependency-rules.md
  - query-composition.md
  - security-boundary.md
  - external-integration.md
  - ../07-adr/architecture/arch-001-domain-monolith.md
---

# 패키지 구조

## 1. 루트 패키지

Gradle `group`과 Java 루트 패키지는 `com.masiton`으로 확정한다. Spring Boot 진입점은 `com.masiton.MasitOnApplication`이다.

## 2. 권장 패키지 트리

```text
com/masiton/
├─ restaurant/
│  ├─ presentation/
│  │  ├─ rest/
│  │  └─ admin/
│  ├─ application/
│  │  ├─ command/
│  │  ├─ query/
│  │  └─ port/
│  │     ├─ in/
│  │     └─ out/
│  ├─ domain/
│  │  ├─ model/
│  │  ├─ service/
│  │  └─ exception/
│  └─ infrastructure/
│     ├─ persistence/
│     └─ external/kakao/
├─ creator/
│  ├─ presentation/{rest,admin}/
│  ├─ application/{command,query,port/in,port/out}/
│  ├─ domain/{model,service,exception}/
│  └─ infrastructure/{persistence,external/youtube}/
├─ video/
│  ├─ presentation/{rest,admin}/
│  ├─ application/{command,query,port/in,port/out}/
│  ├─ domain/{model,service,exception}/
│  └─ infrastructure/{persistence,external/youtube}/
├─ visit/
│  ├─ presentation/
│  │  └─ rest/
│  ├─ application/{command,query,port/in,port/out}/
│  ├─ domain/{model,service,exception}/
│  └─ infrastructure/persistence/
├─ orchestration/
│  ├─ presentation/
│  │  ├─ detail/
│  │  └─ visit/
│  ├─ application/
│  │  ├─ command/
│  │  ├─ query/
│  │  └─ port/out/
│  └─ infrastructure/
│     └─ query/
├─ security/
│  ├─ presentation/
│  ├─ application/
│  └─ infrastructure/
└─ common/
   ├─ web/
   ├─ observability/
   ├─ security/
   ├─ time/
   ├─ id/
   └─ config/
```

`presentation`, `application`, `domain`, `infrastructure`를 전체 최상위로 두지 않는다. 같은 이름은 각 도메인 안에서만 반복한다.

Visit는 현재 MVP에서 독립 공개 HTTP 자원이 없으므로 `visit.presentation`에 구현할 Controller가 없을 수 있다. 교차 도메인 Visit 등록 Controller는 `orchestration.presentation.visit`에 둔다. 패키지는 빈 디렉터리를 미리 만들지 않고 실제 컴포넌트가 생길 때 생성한다.

## 3. 패키지별 책임

| 패키지 | 책임 | 둘 수 없는 것 |
|---|---|---|
| `*.presentation` | HTTP 요청 파싱, Bean Validation, 입력·출력 DTO 변환, HTTP 상태 | 업무 규칙, 트랜잭션, JPA 조회 조합 |
| `*.application.command` | 한 Command 유스케이스의 순서와 결과 | 외부 제공자 DTO, HTTP 응답 |
| `*.application.query` | 조회 유스케이스와 읽기 DTO 조합 | Entity 상태 변경 |
| `*.application.port.in` | 외부가 호출하는 유스케이스 계약 | Spring MVC/JPA 타입 |
| `*.application.port.out` | 저장소·외부 시스템에 요구하는 계약 | 제공자 SDK 타입 |
| `*.domain.model` | Entity, Aggregate, 값 객체, 불변 조건 | Spring/JPA/HTTP 의존 |
| `*.domain.service` | 한 도메인 안의 여러 객체에 걸친 순수 규칙 | Repository 구현, 트랜잭션 |
| `*.infrastructure.persistence` | JPA 매핑, Spring Data Repository, Port 구현, Projection | 업무 흐름 조정 |
| `*.infrastructure.external` | HTTP 호출, 인증 헤더, 제공자 DTO와 내부 결과 변환 | 관리자 확인·저장 결정 |
| `orchestration.application` | 교차 도메인 호출 순서, 트랜잭션, 부분 실패와 최종 DTO | Entity, 도메인 규칙, 자체 Repository |
| `orchestration.infrastructure.query` | 교차 도메인 읽기 Port 구현과 읽기 전용 Projection | 쓰기, Entity 소유, Domain 규칙 |
| `security` | 관리자 인증, JWT, Refresh Token, Principal 전달 | Restaurant·Creator·Video·Visit 규칙 |
| `common` | 소유 도메인이 없는 작은 기술 기반 | 다중 도메인 서비스, 편의 DTO, 업무 상수 |

## 4. 대표 컴포넌트 배치

| 컴포넌트 예시 | 목표 위치 | 상태 |
|---|---|---|
| `RestaurantSearchController` | `restaurant.presentation.rest` | 제안 이름 |
| `RegisterRestaurantService` | `restaurant.application.command` | 제안 이름 |
| `Restaurant` | `restaurant.domain.model` | 제안 이름 |
| `RestaurantPersistenceAdapter` | `restaurant.infrastructure.persistence` | 제안 이름 |
| `CreatorRegistrationController` | `creator.presentation.admin` | 제안 이름 |
| `VideoVerificationPort` | `video.application.port.out` | 제안 이름 |
| `YoutubeVideoVerificationAdapter` | `video.infrastructure.external.youtube` | 제안 이름 |
| `RestaurantDetailController` | `orchestration.presentation.detail` | 상세 설계 결정 |
| `RestaurantDetailQueryService` | `orchestration.application.query` | 상세 설계 결정 |
| `VisitRegistrationController` | `orchestration.presentation.visit` | 상세 설계 결정 |
| `RegisterVisitService` | `orchestration.application.command` | 상세 설계 결정 |
| `SecurityConfig`, JWT Filter | `security.infrastructure` | 제안 이름 |
| `GlobalExceptionHandler` | `common.web` | `T-01`에서 구현 완료 |
| `TraceIdFilter` | `common.observability` | `T-01`에서 구현 완료 |

## 5. Persistence 내부 구조

도메인 모델과 JPA 매핑을 분리하는 것을 기본 목표로 한다.

```text
restaurant/infrastructure/persistence/
├─ RestaurantJpaEntity
├─ SpringDataRestaurantRepository
├─ RestaurantPersistenceAdapter
├─ RestaurantMapper
└─ RestaurantQueryAdapter
```

- Application은 `RestaurantRepositoryPort` 같은 출력 Port에만 의존한다.
- `SpringDataRestaurantRepository`는 Infrastructure 내부 타입이다.
- JPA Entity를 Controller 응답이나 다른 도메인의 공개 계약으로 반환하지 않는다.
- 단순 CRUD에서 도메인 모델과 JPA Entity 분리가 과도하다고 판단해 동일 클래스로 합치려면, 프레임워크 독립 Domain 원칙과 충돌하므로 **추가 ADR 필요**다.

## 6. 교차 도메인 Orchestration

`orchestration`은 `common`이 아니다. 다음 두 종류만 허용한다.

- 여러 도메인의 공개 계약을 호출해야 완결되는 Command
- 여러 도메인 원천 데이터를 최종 API DTO로 조합하는 Query

`orchestration.infrastructure.query`는 Query Port를 구현하기 위해 여러 소유 테이블을 읽을 수 있지만 쓰거나 Entity를 소유할 수 없다. `orchestration`에 데이터 소유 Entity, 범용 Repository, “재사용 가능한” 비즈니스 규칙을 두지 않는다. 한 도메인만 참조하게 된 클래스는 해당 도메인으로 이동한다.

## 7. 공통 코드 허용 체크리스트

다음 질문에 모두 `예`여야 `common`으로 이동할 수 있다.

- [ ] 특정 도메인 용어가 클래스명·필드·분기에 없는가?
- [ ] Restaurant·Creator·Video·Visit 타입을 import하지 않는가?
- [ ] 독립적인 기술 이유로 변경되는가?
- [ ] 소유할 도메인을 합리적으로 지정할 수 없는가?
- [ ] 숨은 호출 순서나 트랜잭션을 만들지 않는가?
- [ ] 공개 API 계약을 편의상 복제한 DTO가 아닌가?

다음 중 하나면 도메인 또는 `orchestration`으로 되돌린다.

- 업무 분기나 상태값이 추가됐다.
- 두 개 이상의 도메인 모델·Repository를 참조한다.
- 특정 Workstream만 변경·리뷰한다.
- 이름이 `CommonService`, `SharedManager`, `Utils`처럼 책임을 설명하지 못한다.
- 공통 코드를 통해 도메인 내부 구현에 우회 접근한다.

## 8. 현재 경로에서 목표 경로로의 매핑

`T-01`이 소스 경로와 진입점, 제한된 `common`을 만들었다. 네 도메인과 `orchestration`, `security`는 아직 클래스가 없으므로 클래스별 매핑도 없다.

| 현재 | 목표 | 전환 |
|---|---|---|
| `src/main/java/com/masiton/MasitOnApplication.java` | 동일 | `T-01`에서 생성 완료 |
| `src/main/java/com/masiton/common/{web,observability}` | 동일 | 공통 오류·traceId 기반만 포함 |
| 도메인 패키지 없음 | `src/main/java/com/masiton/{restaurant,creator,video,visit,orchestration,security}/...` | 각 Workstream의 첫 실제 클래스와 함께 생성 |
| `src/test/java/com/masiton/...` | 동일 | 운영 패키지 구조를 반영 |
| `src/main/resources/db/migration/` (비어 있음) | 동일 | `T-03`이 V1~V5를 추가 |
| `src/test/java/com/masiton/architecture` | 동일 | `T-01`에서 ArchUnit 규칙 적용 완료 |

## 9. 단계적 이전 방법

1. Gradle `group=com.masiton`, 루트 패키지 `com.masiton`, 진입점 `MasitOnApplication`으로 프로젝트를 생성한다.
2. 네 도메인과 `orchestration`, `security`, 제한된 `common` 빈 패키지 대신 첫 실제 클래스와 함께 생성한다.
3. 입력·출력 Port와 Domain을 먼저 만들고 Infrastructure 구현을 연결한다.
4. 상세 조회와 방문 등록처럼 교차 도메인 흐름만 `orchestration`에 둔다.
5. ArchUnit 규칙을 첫 구현 PR부터 적용한다.
6. 기능 구현 뒤 패키지를 재배치하는 대규모 이전은 피하고, 경계 변경은 문서와 테스트를 먼저 갱신한다.
