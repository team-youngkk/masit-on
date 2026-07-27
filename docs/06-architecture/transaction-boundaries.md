---
related_documents:
  - application-flow.md
  - query-composition.md
  - external-integration.md
  - ../05-specs/data/constraints.md
  - ../05-specs/api/admin/visit-registration-api.md
  - ../07-adr/data/data-003-spring-data-jpa.md
  - ../07-adr/data/data-004-flyway.md
  - ../07-adr/security/auth-003-confirmation-token.md
---

# 트랜잭션 경계

## 1. 적용 원칙

- 트랜잭션은 Controller나 Repository 구현체가 아니라 **Application 유스케이스의 public 메서드**에서 시작·종료한다.
- 한 HTTP 요청이 아니라 한 업무 원자성 단위를 기준으로 한다.
- Domain 객체는 트랜잭션 기술을 알지 못한다.
- 쓰기 유스케이스는 기본적으로 rollback 가능한 Runtime 예외에서 전체 rollback한다.
- 읽기는 `readOnly = true`로 의도를 표시하고 Entity를 응답으로 노출하지 않는다.
- 외부 HTTP 대기와 긴 계산을 DB 트랜잭션 안에 두지 않는다.
- 애플리케이션 선조회와 DB 제약을 함께 사용한다. 선조회만으로 동시성을 보장하지 않는다.

## 2. 유스케이스별 경계

| 유스케이스 | 트랜잭션 | 경계 |
|---|---|---|
| 공개 목록·상세 Query | 읽기 전용 | Query Service public 메서드 |
| 외부 검증 미리보기 | DB 트랜잭션 없음 | 외부 호출 후 짧은 중복 조회만 수행 |
| Restaurant/Creator/Video 생성 확정 | 쓰기 | Token 검증 완료 후 중복 재검증·생성·저장 |
| Visit 등록 | 쓰기 | 세 참조 조회부터 Visit 저장까지 |
| 로그인 | 쓰기 대상별 분리 | 계정 조회와 Refresh Token 발급·회전 정책에 따라 Security Application이 소유 |
| Token 재발급 | Redis 원자 연산 | 기존 Token 검증·폐기·새 Token 저장을 한 회전 단위로 처리 |

## 3. Visit 등록 트랜잭션

### 경계

`orchestration.application.command.RegisterVisitService.register(command, adminPrincipal)` 메서드에 트랜잭션을 둔다.

```text
트랜잭션 시작
  ├─ Restaurant 공개 Reference 조회
  ├─ Creator 공개 Reference 조회
  ├─ Video 공개 Reference 조회
  ├─ 채널 일치 검증
  └─ Visit Create 입력 Port 호출
       ├─ 기존 Visit 조합 선조회
       ├─ Visit Domain 생성
       └─ Visit 저장
커밋
```

JWT 서명·만료·역할 검증과 요청 형식 검증은 트랜잭션 전에 완료한다.

Orchestration은 Visit Domain·Repository를 직접 호출하지 않는다. Visit Application의 공개 입력 Port가 자기 Domain·출력 Port를 사용하고, 바깥 트랜잭션에 참여한다.

### 검증 순서

1. Security: 인증과 `ADMIN` 역할
2. Presentation: 세 ID 형식, 필수값, `visitEvidenceConfirmed == true`
3. Application: 참조 존재·공개 상태
4. Application/Visit Domain: 게시 채널과 Creator 채널 일치
5. Application: 동일 조합 선조회
6. Visit Domain: 관계 생성 불변 조건
7. Persistence: FK·복합 UNIQUE 최종 보장

근거 확인은 관리자 입력의 `true` 선언을 사용하되, 인증된 Principal이 전달된 경우에만 Application이 Domain 생성에 넘긴다. 별도 검증 상태·검증자·검증 시각은 현재 데이터 모델에 추가하지 않는다.

### rollback

다음 중 하나라도 발생하면 Visit 저장을 포함한 전체 유스케이스를 rollback한다.

- 참조 없음 또는 비공개
- 채널 불일치
- 근거 확인 부족
- 중복 선조회 또는 DB UNIQUE 충돌
- 저장소 오류
- 예상하지 못한 Application/Domain 예외

관계 등록은 기존 Restaurant·Creator·Video를 수정하지 않으므로 rollback 대상은 Visit 생성뿐이다. 실패한 요청이 참조 Entity의 상태를 바꾸지 않는다.

## 4. 동시성 및 일관성

Visit에는 `(restaurant_id, creator_id, video_id)` 복합 UNIQUE가 필수다. 두 요청이 동시에 선조회를 통과할 수 있으므로 다음 순서로 보장한다.

1. 애플리케이션 선조회로 일반 중복에 의미 있는 `409`를 제공한다.
2. PostgreSQL UNIQUE가 최종 경쟁 조건을 차단한다.
3. 한 요청만 커밋한다.
4. 다른 요청의 제약 위반을 `DUPLICATE_VISIT_RELATIONSHIP`으로 변환한다.

분산 락, 비관적 락과 Redis 락은 도입하지 않는다. 현재 단일 DB의 UNIQUE로 충분하며, 락이 필요한 근거가 측정되면 별도 결정한다. 구체 제약명과 인덱스는 물리 DB 설계에서 확정한다.

기본 격리 수준을 임의로 강화하지 않는다. Spring/PostgreSQL 기본 격리와 UNIQUE를 사용하되, 실제 동시성 통합 테스트 결과가 불충분하면 격리 수준·락·upsert를 **추가 ADR**로 검토한다.

## 5. 읽기 전용 트랜잭션

- 목록과 상세 Query Service는 `readOnly = true`를 사용한다.
- 상세는 기본 정보와 콘텐츠를 분리 조회해 콘텐츠 실패를 격리한다.
- 두 조회가 반드시 동일 시점 Snapshot이어야 한다는 요구는 현재 없다.
- 응답 생성 중 Lazy Loading이 발생하지 않도록 필요한 값을 Projection에서 완결한다.
- Open Session in View에 의존해 Controller에서 연관을 읽지 않는다.

**확인 필요:** Spring Boot 초기 설정에서 Open Session in View를 명시적으로 비활성화하고 Projection/명시 조회 원칙을 적용할지 공통 Spring·JPA 컨벤션에 반영한다.

## 6. 외부 API와 DB 트랜잭션

관리자 검증 미리보기의 Kakao·YouTube 호출은 DB 트랜잭션 밖에서 수행한다.

```text
외부 확인 → 관리자 후보 확인 → 확인 Token → 짧은 생성 트랜잭션
```

외부 호출과 DB 저장을 한 트랜잭션에 묶을 수 없으며, DB 트랜잭션이 외부 응답을 기다리게 하면 커넥션 점유와 rollback 불확실성이 커진다. 미리보기 실패는 핵심 Entity를 저장하지 않는다.

확인 Token 덕분에 생성 확정 요청은 외부 API를 다시 호출하지 않는다. PostgreSQL에 저장한 후보 Snapshot과 Token 상태를 사용하며 구체 정책은 [ADR-AUTH-003](../07-adr/security/auth-003-confirmation-token.md)을 따른다.

## 7. 이벤트와 비동기 처리

현재 MVP에는 도메인 이벤트, 메시지 브로커와 비동기 Worker를 도입하지 않는다.

- 등록 성공 후 목록·상세 반영은 동일 DB 커밋으로 완료한다.
- 이메일·알림·외부 동기화 이벤트를 추가하지 않는다.
- 향후 이벤트가 필요하면 DB 커밋 전에 외부 부수 효과를 실행하지 않는다.
- 유실 불가 이벤트가 필요하면 Transactional Outbox 등 일관성 선택을 별도 ADR로 결정한다.

## 8. 확인 Token 트랜잭션

확인 Token은 PostgreSQL 저장형 불투명 Token이다. 서버는 최소 256-bit 난수 원문을 클라이언트에 한 번 전달하고 SHA-256 해시, 관리자, 자원 종류, 후보 JSONB Snapshot과 상태를 저장한다.

생성 확정 트랜잭션은 다음을 한 원자적 범위로 처리한다.

1. Token 해시 행 잠금
2. 관리자·자원 종류·`ISSUED/CREATED/DUPLICATE` 상태와 만료 검증
3. 외부 동일성 중복 재확인
4. `ON CONFLICT DO NOTHING RETURNING` 기반 Entity 생성 시도
5. Token을 `CREATED`와 생성 ID 또는 `DUPLICATE`와 기존 ID로 갱신
6. Entity와 Token 결과 함께 커밋

`CREATED` 재시도는 기존 Entity의 `200`, `DUPLICATE` 재시도는 같은 기존 자원 정보의 `409`를 반환한다. 예상하지 못한 오류로 rollback되면 Token 상태도 `ISSUED`로 남는다. 완료 레코드와 미사용 만료 레코드는 결과 재현을 위해 각각 완료·만료 후 24시간 보관하며 새 Token 발급 시 제한적으로 지연 정리한다.

이 범위의 `ON CONFLICT DO NOTHING`은 Token과 동시 중복 결과를 같은 트랜잭션에서 확정하기 위한 제한된 사용이며 일반 저장 로직의 광범위한 upsert 전환이 아니다.

## 9. 검증

- PostgreSQL Testcontainers에서 두 개 이상의 동시 Visit 등록을 실행해 한 행만 커밋되는지 확인한다.
- 참조 없음·비공개·채널 불일치·근거 부족마다 Visit 행이 0개 증가하는지 확인한다.
- 생성 후 응답 직전 예외를 주입해 rollback되는지 확인한다.
- 같은 확인 Token의 동시 제출에서 Entity 한 건, 최초 `201`, 재시도 `200`이 되는지 확인한다.
- 다른 요청이 먼저 같은 외부 자원을 생성하면 확인 Token이 `DUPLICATE`로 완료되고 최초·재시도 모두 같은 `409`인지 확인한다.
- 외부 API 지연 중 DB 트랜잭션·커넥션이 열리지 않는지 확인한다.
- 읽기 Query에서 의도치 않은 Entity 변경과 추가 쿼리가 없는지 확인한다.
