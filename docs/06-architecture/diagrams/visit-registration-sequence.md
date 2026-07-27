# 방문 관계 등록 Sequence

```mermaid
sequenceDiagram
    actor Admin
    participant Security as Spring Security
    participant Controller as VisitRegistrationController
    participant Service as RegisterVisitService
    participant Restaurant as Restaurant Reference Port
    participant Creator as Creator Reference Port
    participant Video as Video Reference Port
    participant VisitUseCase as CreateVisitUseCase
    participant Visit as Visit Domain
    participant Repository as Visit Repository Port
    participant DB as PostgreSQL

    Admin->>Security: POST /api/admin/visit-relationships + JWT
    Security->>Security: 서명, 만료, ADMIN 검증
    Security->>Controller: AdminPrincipal
    Controller->>Controller: ID 형식, evidence=true 검증
    Controller->>Service: register(command, principal)
    Note over Service,DB: 쓰기 트랜잭션 시작
    Service->>Restaurant: findPublic(restaurantId)
    Restaurant->>DB: Restaurant reference query
    DB-->>Restaurant: reference
    Restaurant-->>Service: RestaurantReference
    Service->>Creator: findPublic(creatorId)
    Creator->>DB: Creator reference query
    DB-->>Creator: reference
    Creator-->>Service: CreatorReference
    Service->>Video: findPublic(videoId)
    Video->>DB: Video reference query
    DB-->>Video: reference
    Video-->>Service: VideoReference
    Service->>Service: 공개 상태와 채널 일치 검증
    Service->>VisitUseCase: create(verified references, evidence)
    VisitUseCase->>Repository: existsByTriple(ids)
    Repository->>DB: duplicate query
    DB-->>Repository: exists / not exists
    Repository-->>VisitUseCase: duplicate result
    VisitUseCase->>Visit: create(ids, evidence, channelMatch)
    Visit-->>VisitUseCase: Visit
    VisitUseCase->>Repository: save(visit)
    Repository->>DB: INSERT with FK + UNIQUE
    DB-->>Repository: saved
    Repository-->>VisitUseCase: VisitId
    VisitUseCase-->>Service: VisitId
    Note over Service,DB: commit
    Service-->>Controller: RegisterVisitResult
    Controller-->>Admin: 201 Created
```

어느 검증이나 저장 단계에서 예외가 발생하면 트랜잭션 전체를 rollback한다. 동시 요청은 DB 복합 UNIQUE가 최종 차단한다.
