---
related_documents:
  - architecture-overview.md
  - module-boundaries.md
  - transaction-boundaries.md
  - query-composition.md
  - security-boundary.md
  - external-integration.md
  - diagrams/restaurant-detail-sequence.md
  - diagrams/visit-registration-sequence.md
  - ../05-specs/api/detail/restaurant-detail-api.md
  - ../05-specs/api/admin/visit-registration-api.md
  - ../07-adr/security/auth-003-confirmation-token.md
  - ../07-adr/platform/web-003-routing-boundary.md
---

# 애플리케이션 흐름

## 1. 공통 요청 처리

```text
HTTP Request
  → Correlation/Trace Filter
  → Spring Security Filter Chain
  → Controller
  → Input Port / Application Service
  → Domain + Output Port
  → Adapter
  → Controller Response Mapping
```

- 공개 `GET /api/restaurants`, `GET /api/restaurants/{restaurantId}`, `GET /api/creators`, `GET /api/creators/{creatorId}`, `GET /api/creators/{creatorId}/restaurants`, `GET /api/creators/{creatorId}/videos`는 인증 없이 Controller로 전달한다.
- `POST /api/admin/auth/tokens`는 로그인 자격 증명, `POST /api/admin/auth/tokens/refresh`는 Refresh Token 쿠키만 검증한다.
- `DELETE /api/admin/auth/tokens`는 JWT와 Refresh Token 쿠키를 모두 검증하고, 나머지 `/api/admin/**`은 JWT와 `ADMIN` 권한을 먼저 확인한다.
- 정의되지 않은 `/api/**`는 기본 거부하고 `/internal/**`은 인터넷 Nginx 경로로 전달하지 않는다.
- Controller는 형식·필수값 검증과 HTTP 변환만 한다.
- Application은 유스케이스 순서, 권한 컨텍스트 사용, 트랜잭션과 오류 의미를 소유한다.
- Domain은 전달받은 값으로 비즈니스 규칙을 판정한다.
- Adapter는 DB·Redis·Kakao·YouTube 세부사항을 담당한다.

## 2. 주요 Query

| Query | 입력 | Application 책임 | 출력 |
|---|---|---|---|
| 맛집 목록·복합 필터 | 이름, Region, FoodCategory, Creator ID, 페이지 | 조건 정규화, 공개 조건, 페이지·정렬, Creator 관계 필터 조합 | 목록 DTO와 페이지 메타데이터 |
| Creator 선택 목록 | 없음 | 공개 Creator 조회, 채널명 오름차순 | ID·채널명 목록 |
| 맛집 상세 | Restaurant ID | 기본 정보 필수 조회, 콘텐츠 조합, 부분 실패 변환 | `RestaurantDetailResult` |

### 맛집 목록

1. `RestaurantSearchController`가 쿼리 파라미터와 페이지 계약을 검증한다.
2. `SearchRestaurantsQueryService`가 조건을 Query 객체로 변환한다.
3. Creator 조건이 없으면 Restaurant 중심 Projection을 조회한다.
4. Creator 조건이 있으면 읽기 전용 Query Adapter가 공개·유효 Visit 조건을 SQL/JPQL의 `EXISTS`로 결합한다.
5. Creator 이름은 별도 건별 조회하지 않고 목록 Projection에서 최대 3명과 전체 고유 수를 계산한다.
6. Application이 `외 N명` 파생값과 페이지 응답을 만든다.

관계 유효성 규칙의 소유자는 Visit다. Query Adapter는 Visit의 확정된 공개·유효 조건을 읽기 계약으로 적용하며 이를 독자적으로 변경하지 않는다.

### 맛집 상세

[맛집 상세 조회 Sequence](diagrams/restaurant-detail-sequence.md)를 따른다.

1. `RestaurantDetailController`가 ID 형식을 검증한다.
2. `RestaurantDetailQueryService`가 공개 Restaurant 기본 정보를 먼저 조회한다.
3. 없거나 비공개·삭제면 `RESTAURANT_NOT_FOUND`를 반환한다.
4. 콘텐츠 Query Port가 공개·유효 Visit와 Creator·Video 표시 정보를 한 번의 Projection 조회로 가져온다.
5. Application이 Creator·Video를 ID 기준으로 중복 제거하고 상세 DTO를 만든다.
6. 콘텐츠 조회 실패만 발생하면 기본 정보는 유지하고 `TEMPORARILY_UNAVAILABLE`, 빈 배열 두 개를 반환한다.
7. 공개 조회 경로에서 Kakao·YouTube를 호출하지 않는다.

## 3. 주요 Command

| Command | 입력 | 트랜잭션 | 결과 |
|---|---|---|---|
| 기본 데이터 검증 미리보기 | 관리자 입력·Principal | 없음 | 정규화 후보, 판정, 확인 Token |
| Restaurant 생성 확정 | 확인 Token·Principal | Restaurant 생성 단위 | 공개 Restaurant |
| Creator 생성 확정 | 확인 Token·Principal | Creator 생성 단위 | 공개 Creator |
| Video 생성 확정 | 확인 Token·Principal | Video 생성 단위 | 공개 Video |
| Visit 등록 | 세 ID, 근거 확인, Principal | 전체 유스케이스 단위 | 공개 Visit 관계 |

### 기본 데이터 검증 미리보기

1. Security Filter가 관리자를 인증·인가한다.
2. Controller가 입력 형식과 허용값을 검증한다.
3. 해당 도메인의 Preview Application Service가 외부 확인 Port를 호출한다.
4. Adapter가 제공자 DTO를 내부 확인 결과로 변환한다.
5. Application이 기존 외부 ID 중복을 조회한다.
6. `READY`, `DUPLICATE`, `REVIEW_REQUIRED`를 결정한다.
7. `READY`일 때만 관리자·후보·10분 만료에 묶인 확인 Token을 반환한다.
8. 미리보기는 Restaurant·Creator·Video를 생성하지 않는다.

외부 HTTP 대기 중에는 DB 트랜잭션을 열지 않는다.

### 기본 데이터 생성 확정

1. Security Filter가 관리자를 인증·인가한다.
2. Controller가 `confirmationToken`의 필수값·형식을 검증한다.
3. Application이 트랜잭션을 시작하고 Token 해시로 PostgreSQL 행을 잠근다.
4. Token의 관리자·자원 종류·상태를 검증하고 `ISSUED`이면 10분 만료를 확인한다.
5. `CREATED`이면 새 작업 없이 기존 Entity를 `200 OK`, `DUPLICATE`이면 기존 자원 정보와 같은 `409`로 반환한다.
6. 유효한 `ISSUED`이면 외부 ID 중복을 다시 확인한다.
7. 저장된 후보 JSONB Snapshot으로 공개 Entity를 생성하고 고유 제약에 대해 `ON CONFLICT DO NOTHING RETURNING`으로 저장한다.
8. 생성됐으면 Token을 `CREATED`와 결과 ID, 충돌했으면 `DUPLICATE`와 기존 ID로 갱신한다.
9. Entity와 Token 결과를 함께 커밋하고 최초 생성 `201` 또는 동시 중복 `409`를 반환한다.

외부 API를 다시 호출하지 않으며 예상하지 못한 저장 실패로 rollback되면 Token도 `ISSUED`로 남는다. 상세 저장·재시도 정책은 [ADR-AUTH-003](../07-adr/security/auth-003-confirmation-token.md)을 따른다.

### Visit 등록

[방문 관계 등록 Sequence](diagrams/visit-registration-sequence.md)를 따른다.

1. Security Filter가 JWT와 `ADMIN` 권한을 검증한다.
2. Controller가 ID 형식과 `visitEvidenceConfirmed == true`를 검증한다.
3. `RegisterVisitService`가 트랜잭션을 시작한다.
4. Restaurant, Creator, Video의 공개·활성 Reference를 각각 조회한다. Creator와 Video는 외부 `AVAILABLE`도 만족해야 한다.
5. 없으면 대상별 `404`, 공개·활성·외부 가용 조건을 충족하지 않으면 `422 REFERENCE_NOT_PUBLIC`로 실패한다.
6. Creator 외부 채널 ID와 Video 게시 채널 ID 일치를 확인한다.
7. Video의 내부 Creator가 미해소이면 Video Application의 `ResolveVideoCreatorUseCase`로 같은 Creator 연결을 해소한다. 이미 같은 Creator면 그대로 진행하고 다른 Creator면 `VIDEO_CHANNEL_MISMATCH`로 실패한다.
8. `CreateVisitUseCase`에 검증된 세 Reference와 근거 확인 값을 전달한다.
9. Visit Application이 동일 세 ID 관계를 조회해 있으면 `409`로 실패한다.
10. Visit Domain이 근거 확인, 채널 일치와 세 식별자로 관계를 생성한다.
11. Visit Application이 Repository Port로 저장한다.
12. DB의 채널 일치 복합 FK와 Visit 복합 UNIQUE 충돌을 각각 도메인 오류로 변환한다.
13. Video 연결과 Visit를 포함한 바깥 Orchestration 트랜잭션이 커밋된 뒤 `201 Created`를 반환한다.

Orchestration은 Video·Visit Domain이나 Repository를 직접 import하지 않고 각 Application의 공개 입력 Port만 호출한다.

## 4. 오류 변환

오류는 발생 지점에서 HTTP 응답으로 직접 바꾸지 않는다.

```text
Domain/Application Exception
  → common.web GlobalExceptionHandler
  → API error code + safe message + traceId
```

- Adapter는 제공자 오류를 안정된 Application 실패 유형으로 변환한다.
- Repository Adapter는 유일성 충돌을 중복 오류로 변환한다.
- Controller Advice는 [공통 오류 계약](../05-specs/api/common/error-contract.md)에 따라 HTTP 상태와 본문을 만든다.
- 내부 예외명, SQL, 외부 응답 원문과 비밀값은 노출하지 않는다.

## 5. 테스트 지점

- Controller Slice: 입력 형식, 공개/관리자 접근, 로그인·재발급 예외와 오류 계약
- Application 단위: 호출 순서, 부분 실패, Domain 판정
- Domain 단위: Visit 근거·채널 일치·중복 의미
- PostgreSQL 통합: FK, UNIQUE, rollback과 동시 요청
- WireMock 계약: 정상, 없음, 429, 지연·timeout, 응답 변경
- 인수: 등록 직후 목록·필터·상세 반영
