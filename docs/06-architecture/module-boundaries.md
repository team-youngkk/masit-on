---
related_documents:
  - architecture-overview.md
  - package-structure.md
  - dependency-rules.md
  - application-flow.md
  - ../02-analysis/domain-boundaries.md
  - ../05-specs/data/data-model.md
  - ../07-adr/architecture/arch-001-domain-monolith.md
---

# 모듈 경계

## 1. 경계 원칙

Restaurant, Creator, Video, Visit는 각각 데이터, 불변 조건과 변경 이유를 소유한다. 한 화면이나 한 SQL에서 함께 사용된다는 이유로 소유권을 합치지 않는다.

도메인 간 참조는 다음 순서로 제한한다.

1. Domain Entity는 자기 도메인의 값 객체와 식별자만 객체로 참조한다.
2. 다른 도메인은 식별자 값 또는 최소 Snapshot 계약으로 참조한다.
3. 교차 도메인 변경은 `orchestration.application`이 각 도메인의 공개 입력 Port를 호출해 조정한다.
4. 교차 도메인 조회는 공개 Query Port 또는 읽기 전용 Projection을 사용한다.
5. 다른 도메인의 JPA Entity, Repository, 내부 Domain Service를 직접 import하지 않는다.

## 2. 도메인별 소유권

| 경계 | 소유 데이터·규칙 | 외부에 공개하는 기능 | 공개하지 않는 내부 요소 | 변경 이유 |
|---|---|---|---|---|
| Restaurant | 맛집, Region, FoodCategory, 장소 동일성, 공개 상태, 이름·지역·카테고리 탐색 | 기본 정보 조회, 존재·공개 상태 Snapshot, 맛집 등록, 단일 도메인 검색 | JPA Entity, Repository, 카테고리 검증 구현, 장소 중복 판정 구현 | 맛집 정보·지역·카테고리·탐색 정책 변경 |
| Creator | YouTube 채널 단위 Creator, 외부 채널 ID, 표시 정보, 공개·외부 가용 상태 | Creator Snapshot 조회, 선택 목록 조회, Creator 등록 | 외부 DTO, Repository, 채널 동일성 구현 | 채널 동일성·표시·상태 정책 변경 |
| Video | 외부 영상 ID, 제목·썸네일·원본 링크, 게시 채널 ID, 공개·외부 가용 상태 | Video Snapshot 조회, Video 등록 | 외부 DTO, Repository, 원본 영상 중복 판정 구현 | 영상 메타데이터·동일성·가용성 정책 변경 |
| Visit | Restaurant·Creator·Video 식별자의 검증된 삼항 관계, 관계 공개·유효성·복합 중복 | Visit 생성 판정·저장, 관계 기반 식별자 조회 | 다른 도메인의 Entity, 관계 Repository, 중복·근거 판정 구현 | 방문 근거·채널 일치·중복·노출 정책 변경 |

## 3. 경계별 공개 계약 예시

아래 이름은 목표 구조의 예시다. 실제 루트 패키지는 미정이지만 계약의 형태와 정보 최소화 원칙은 고정한다.

```java
// restaurant.application.port.in
public interface GetRestaurantReferenceUseCase {
    RestaurantReference findPublic(RestaurantId id);
}

public record RestaurantReference(
        RestaurantId id,
        RestaurantPublicationStatus publicationStatus
) {}
```

```java
// creator.application.port.in
public interface GetCreatorReferenceUseCase {
    CreatorReference findPublic(CreatorId id);
}

public record CreatorReference(
        CreatorId id,
        ExternalChannelId externalChannelId,
        CreatorPublicationStatus publicationStatus
) {}
```

```java
// video.application.port.in
public interface GetVideoReferenceUseCase {
    VideoReference findPublic(VideoId id);
}

public record VideoReference(
        VideoId id,
        ExternalChannelId publisherExternalChannelId,
        VideoPublicationStatus publicationStatus
) {}
```

다른 경계는 위 계약의 값만 사용한다. `Restaurant`, `Creator`, `Video` 도메인 객체 자체를 반환하지 않는다.

## 4. Visit의 관계 표현과 소유

Visit는 Restaurant·Creator·Video를 객체 연관으로 소유하지 않는다. 다음 세 식별자를 필수 값으로 가진 관계 Aggregate로 표현한다.

```text
Visit
├─ VisitId
├─ RestaurantId
├─ CreatorId
├─ VideoId
└─ PublicationStatus
```

- Restaurant와 Creator 사이의 단순 다대다 연결이 아니라 **근거 Video를 포함하는 삼항 관계**다.
- 같은 `(RestaurantId, CreatorId, VideoId)`는 하나만 존재한다.
- 같은 Restaurant·Creator라도 Video가 다르면 별도 Visit를 허용한다.
- 같은 Video가 여러 Restaurant 방문을 보여주면 Restaurant별 Visit를 허용한다.
- 실제 방문 확인과 게시 채널 일치는 생성 전 필수다.
- Visit가 무효·비공개여도 Restaurant 기본 정보는 유지한다.
- 방문일, 검증 상태, 검증자·검증 시각은 현재 논리 모델에서 저장하지 않는다.

JPA 매핑에서도 다른 도메인의 Entity 객체를 `@ManyToOne`으로 직접 노출하기보다 식별자 컬럼으로 매핑하는 것을 목표로 한다. 실제 FK는 물리 DB 설계에서 보장하되 Java 객체 그래프는 경계를 넘지 않는다.

## 5. 허용되는 협력

| 요청자 | 대상 | 허용 방식 | 예 |
|---|---|---|---|
| 교차 도메인 Orchestration | Restaurant·Creator·Video | 공개 입력 Port와 최소 Snapshot | Visit 등록 전 존재·공개·채널 일치 확인 |
| Orchestration Query | 네 도메인 데이터 | 읽기 전용 Query Port/Projection | 맛집 상세 DTO 조합 |
| Restaurant 탐색 Query | Visit 관계 | 읽기 전용 식별자/Projection | Creator 조건에 맞는 Restaurant 필터 |
| Visit Domain | 외부 도메인 | 전달받은 식별자와 Snapshot 값 | 채널 ID 일치, 근거 확인 값 판정 |
| Presentation | Application | 입력 Port | 요청 변환과 응답 매핑 |

## 6. 금지되는 직접 참조

- `restaurant.domain.Restaurant`가 `VisitRepository`를 호출
- `visit.domain.Visit`가 `Restaurant` JPA Entity를 필드로 보유
- `creator.application`이 `video.infrastructure.persistence.VideoJpaRepository`를 호출
- 상세 Controller가 네 Repository를 직접 호출해 DTO를 조합
- `common` 서비스가 네 도메인 Repository를 한꺼번에 호출
- 외부 제공자 DTO를 Creator·Video Domain 모델 또는 공개 API 응답으로 재사용

## 7. 관리자 기능의 위치

Admin은 별도 비즈니스 도메인이 아니다.

- 단일 도메인의 등록 검증·생성: 해당 도메인의 `application`과 `presentation.admin`
- 여러 도메인의 참조 확인과 관계 생성 순서: `orchestration.application.command`
- 인증·JWT·Refresh Token: `security` 기술 경계
- 공통 `/api/admin` URL과 역할 요구: Presentation 및 Security 설정

`admin` 최상위 패키지에 Restaurant·Creator·Video·Visit 규칙을 모으지 않는다. 관리자 Controller도 각 자원 또는 교차 유스케이스의 소유 패키지에 둔다.

## 8. 경계 변경 기준

다음 변경은 팀 공동 리뷰가 필요하다.

- 다른 도메인 Entity나 Repository의 직접 import 추가
- 공개 Snapshot에 새 필드 추가
- Visit 삼항 관계 또는 복합 유일성 변경
- `orchestration`이 도메인 규칙·영속 데이터를 소유하도록 변경
- 읽기 Projection이 다른 도메인의 테이블을 쓰도록 변경

독립 배포, 별도 DB, 비동기 이벤트 일관성까지 요구되면 [ADR-ARCH-001](../07-adr/architecture/arch-001-domain-monolith.md)의 재검토 조건에 해당하므로 추가 ADR이 필요하다.
