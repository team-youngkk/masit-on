---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
---

# 이슈 419 기존 Restaurant 재사용·관계 연결 설계와 검증

> 상태: **승인(2026-10-14)**. 사용자가 계약 소유자 합의 확보를 확인하여 기존 맛집·영상·방문 재사용과 생성 자원만의 롤백 정책을 계약·코드에 반영했다. 같은 방문 관계는 유일 제약으로 중복 생성하지 않는다.

## 승인된 결정

독립 리뷰의 롤백 CAS 경합 P1을 원자 커밋 서비스로 수정하고 재검토에서 코드 P1/P2 지적은 없었다. 문서의 과거 Proposed 상태가 승인 기록과 충돌한다는 P2를 승인 상태로 정정했다. Flyway 전체 기준 데이터와 재사용·명령·실행·하드 삭제 회귀를 부모 에이전트가 다시 실행해 통과했다.

검증된 Kakao place ID의 Restaurant가 이미 있으면 그 Restaurant를 재사용하고, 새 Video와 Visit를 연결한다. 같은 creator-video-restaurant 방문 관계의 반복은 기존 관계를 재사용하며 새 관계를 만들지 않는다.

## 제안 상태 전이

1. 외부 검증(장소·카테고리·채널·영상·방문 근거)을 완료한 뒤 짧은 DB transaction에서 Kakao place ID를 기준으로 Restaurant를 잠금/조회한다.
2. Restaurant가 없으면 생성하고 `restaurantCreated=true`, 있으면 재사용하고 `restaurantCreated=false`를 등록 결과의 `createdResources` 또는 명시적 created flags에 저장한다.
3. Video는 검증된 YouTube video ID로 기존 행을 재사용하거나 생성한다. Visit는 `(restaurant_id, creator_id, video_id)` 유니크 제약으로 생성하며 충돌 시 기존 관계를 재사용한다.
4. `ai_registration_unit`에는 Restaurant/Creator/Video/Visit 식별자, `reusedResources`, `createdResources`를 현재 결과로 저장해 멱등 응답이 같은 값을 재구성하게 한다. append-only review에는 결정 사유를 남긴다.

## 롤백·동시성 제안

- 롤백은 이 등록 단위가 **생성한** Visit·Video·Restaurant만, 아직 다른 참조가 없고 동일 registration-unit의 created flag가 확인될 때 역순으로 제거한다. 재사용 resource는 어떤 경우에도 삭제하지 않는다.
- 기존 Restaurant 재사용으로 생긴 새 Visit를 삭제해도 Restaurant는 남긴다.
- 동일 unit의 상태 전이는 기존 lock/CAS를 유지한다. 서로 다른 unit의 같은 Restaurant/Video/Visit 경합은 DB unique 제약을 정상 재조회로 전환하고, 일부 쓰기 실패는 전체 transaction을 롤백한다.
- `createdResources`와 `reusedResources` 형식, 식별자 노출, 롤백 가능 상태가 API·data 계약 변경이므로 새 Flyway migration과 API 버전 없는 응답 확장이 필요하다.

## 검증 계획

PostgreSQL Testcontainers로 다른 영상/같은 Restaurant, 다른 creator, 같은 관계 재요청, 동시에 같은 관계 생성, 중간 실패 후 0건, 재사용 Restaurant의 롤백 보존, 새로 만든 연쇄의 롤백을 검증한다. Kakao·YouTube 검증은 WireMock 고정 후보로 대체한다.

## 의존성과 위험

#421 자동 보완은 이 재사용 정책을 호출하지 않고 결정 결과만 소비한다. 두 이슈는 `RegistrationUnitExecutionService`를 동시에 수정하지 않으며, #419 승인·병합 뒤 #421이 필요한 port로만 연동한다. 잘못된 재사용은 다른 영상의 방문을 오연결할 수 있으므로 external ID, 채널/영상 검증, 유일 제약을 모두 통과하지 못하면 현행 차단을 유지한다.

## 승인 및 구현 설계 확정 (2026-10-14)

사용자가 전체 계약 제안을 승인했고 주 에이전트가 소유자 합의 확보를 전달했다. 아래 구체 설계를 승인된 정책으로 구현한다. 앞 절의 신규 createdResources 컬럼 제안은 기존 스키마 조사 결과 불필요하다. 기존 4종 registered_* 식별자와 reused_resources에 restaurant·visit를 추가하면 생성 여부는 그 여집합으로 정확히 재구성한다. V21에서 허용값 CHECK 함수만 확장하며 과거 creator·video 값은 그대로 보존한다.

현재 도메인별 insertIfAbsent는 이미 PostgreSQL ON CONFLICT DO NOTHING + 정상 재조회를 사용하므로 이를 유지한다. 외부 검증은 트랜잭션 밖에 두고, 단순 kakaoPlaceId 존재 차단만 제거한다. 동일 creator/video/restaurant Visit는 기존 등록 서비스의 멱등 재사용 경로로 정상 확정한다. 기존 DUPLICATE_CONFLICT 감사 값과 복구 경로는 과거 조회 호환으로 남기며 새 실행은 단순 존재를 중복 차단으로 만들지 않는다.

반복 관계를 재사용한 등록 단위도 reused_resources에 visit를 보존한다. 관리자 롤백은 restaurant·visit를 포함한 4종 생성 여부를 이 배열로 복원한다. 재사용 자원은 삭제·PRIVATE 전환하지 않는다. 이번 단위가 생성한 자원도 다른 Visit·등록 단위·기존 Snapshot이 참조하면 보존한다. 일반 롤백은 기존 감사 보존용 PRIVATE 전환을 유지하고, 연결 실패 보상만 참조 없는 신규 자원을 역순 하드 삭제한다. 자원 row lock으로 FK 참조와 보상 삭제 경쟁을 직렬화한다. PR #244의 CAS 및 보상 경계를 변경하지 않는다.

검증은 실제 AutoRegisterVerifiedContentUseCase를 PostgreSQL Testcontainers에서 호출해 같은 맛집의 다른 영상·다른 유튜버·동일 관계 반복·동시 등록·중간 SQL 실패 전체 롤백·재사용 Restaurant 보존·다른 방문이 참조하는 신규 자원 보호를 확인한다. 외부 호출은 하지 않는다.

## 독립 리뷰 P1: 등록 단위 사후 변경 원자성

ROLLBACK은 공개 자원을 PRIVATE로 바꾸는 트랜잭션을 먼저 커밋하고 단위 CAS를 나중에 실행했다. ADJUST_CATEGORY가 같은 단위를 선점하면 409 응답에도 공개 자원 변경이 남으며, 감사 저장 실패에도 부분 반영된다. ADJUST_CATEGORY도 같은 순서 문제가 있다.

외부 호출 없는 두 사후 변경만 별도 Application commit service의 public 트랜잭션으로 묶는다. 단위 CAS를 먼저 실행해 행 잠금을 획득하고 성공할 때만 기존 orchestration 공개 Port로 자원 변경과 감사 저장을 수행한다. CAS 실패는 자원 변경 0건이며 감사 예외는 세 쓰기를 모두 롤백한다. CONFIRM의 외부 호출 구간은 그대로 둔다. 재사용 자원 보존 정책과 기존 Snapshot 롤백 경로는 유지한다.

검증: 단위 테스트로 CAS 실패 시 Port 호출 0회, PostgreSQL에서 ROLLBACK/ADJUST_CATEGORY 동시 경합 시 패자 효과 0건, 감사 삽입 실패 시 단위·공개 자원 복원을 확인한다.

CAS 성공 뒤에는 자신의 registered_* 참조가 비워진다. 이 때문에 기존 same-snapshot `count > 1` 공유 판정을 그대로 쓰면 다른 단위 한 개가 남아도 공개 자원을 숨길 수 있다. 내부 RegistrationReference에 optional registrationUnitId를 추가하고 등록 단위 롤백은 `id <> ownUnitId`인 모든 참조를 보존 대상으로 계산한다. 기존 9인자 생성자와 Snapshot 경로의 기존 공유 판정은 유지한다. HTTP 응답과 DB 스키마 변경은 없다.

P1 수정 검증 결과: Java 및 테스트 컴파일, RegistrationUnitCommandServiceTest 15건, AutoRegisterRestaurantReusePostgreSqlIntegrationTest 11건과 ArchUnit 10건 통과. 실제 PostgreSQL에서 서로 다른 트랜잭션의 ROLLBACK/ADJUST_CATEGORY 경합은 감사 1건과 패자 효과 0건을 확인했다. 실제 감사 FK 실패는 ROLLBACK 공개 상태·단위 식별자와 ADJUST_CATEGORY 카테고리·단위 판정을 모두 복원한다. 같은 Snapshot의 다른 단위 1개가 남은 4종 자원도 모두 PUBLIC을 보존한다. 실제 외부 API는 호출하지 않았다.
