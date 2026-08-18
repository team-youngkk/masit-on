---
related_documents:
  - ../data/data-model.md
  - ../data/entity-definitions.md
  - ../data/relationship-rules.md
  - ../data/lifecycle-rules.md
  - ../data/constraints.md
  - ../data/data-review.md
  - ../data/physical-data-model.md
  - ../data/constraint-mapping.md
  - ../../07-adr/security/auth-003-confirmation-token.md
  - ../../07-adr/security/auth-007-unified-account-rbac-session.md
  - ../../07-adr/data/data-007-uuid-v4-identifiers.md
  - ../../07-adr/data/data-008-publication-lifecycle-soft-delete.md
---

# 맛잇온 ERD 작성 명세

## 1. 문서 목적

`erd.mmd`가 논리 데이터 모델의 엔티티, 핵심 속성, 관계, 카디널리티와 고유성 제약을 일관되게 시각화하도록 범위와 표기 규칙을 정한다. ERD는 데이터 문서의 시각화이며 별도 기준 문서가 아니다.

## 2. 포함 엔티티

| 분류 | 엔티티 | 포함 이유 |
|---|---|---|
| 기본 | Restaurant | 독립 맛집 기본 정보와 공개 상태 |
| 참조 | Region | 서울 자치구 표준 필터 값 |
| 참조 | FoodCategory | 대표 음식 카테고리 1개 |
| 기본 | Creator | YouTube 채널 단위 유튜버 |
| 기본 | Video | 외부 영상 메타데이터와 게시 채널 |
| 관계 | Visit | 맛집·채널·근거 영상 삼항 관계 |
| 인증 | MemberAccount | 회원·관리자 통합 계정과 `MEMBER|ADMIN` 역할 |
| 인증 | AuthSession | Redis 기반 통합 JWT Refresh session 상태 |

## 3. 제외 엔티티

- 찜, 최근 본 맛집, 컬렉션, 알림, 사용자 제보
- 추천 결과, 자연어 검색 데이터, AI 추출 작업 데이터
- 예약, 결제와 영상 원본 파일
- 다중 카테고리 관계와 Region 계층
- 별도 방문 근거, 방문일·검증 상태 엔티티
- 검증 미리보기·보류 요청: 핵심 자원을 생성하지 않으며 `REVIEW_REQUIRED`를 저장하지 않음
- 확인 Token: PostgreSQL 단기 기술 테이블로 확정됐지만 핵심 도메인 ERD에서는 제외([ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md))
- 로그인 실패 카운터: Redis `auth:login-failure:{loginIdHash}`의 15분 TTL 단기 기술 아티팩트

## 4. 엔티티별 핵심 속성

| 엔티티 | 식별·고유 속성 | 필수 관계 속성 | 상태·주요 속성 |
|---|---|---|---|
| Restaurant | id, kakaoPlaceIdentity | regionId, foodCategoryId | name, address, phone, publicationStatus, lifecycleStatus |
| Region | id, code, name | 없음 | sortOrder, active |
| FoodCategory | id, code, name | 없음 | sortOrder, active |
| Creator | id, externalChannelId | 없음 | channelName, channelUrl, publicationStatus, lifecycleStatus, externalAvailabilityStatus |
| Video | id, externalVideoId | publisherExternalChannelId; creatorId는 선택 | title, sourceUrl, thumbnailUrl, publicationStatus, lifecycleStatus, externalAvailabilityStatus |
| Visit | id, (restaurantId+creatorId+videoId) | 세 참조 모두 | publicationStatus, lifecycleStatus |
| MemberAccount | id, email | 없음 | passwordCredential, status, role |
| AuthSession | sessionId, tokenCredential | memberAccountId | tokenFamilyId, expiresAt, invalidatedAt |

논리 ERD는 publication과 lifecycle을 서로 다른 상태 축으로 표시한다. 실제 값, `deleted_at` 조합과 FK 삭제 동작은 [물리 데이터 모델](../data/physical-data-model.md)과 [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md)을 따른다.

## 5. 관계와 카디널리티

- Region `1` — `0..N` Restaurant; Restaurant는 Region 하나 필수
- FoodCategory `1` — `0..N` Restaurant; Restaurant는 FoodCategory 하나 필수
- Creator `1` — `0..N` Video; Video는 일치 Creator를 `0..1`개 연결하며 외부 게시 채널 ID는 필수
- Restaurant `1` — `0..N` Visit; Restaurant는 Visit 없이 존재 가능, Visit의 참조는 필수
- Creator `1` — `0..N` Visit; Visit의 참조는 필수
- Video `1` — `0..N` Visit; Visit의 참조는 필수
- MemberAccount `1` — `0..N` AuthSession; 활성 session은 `MEMBER` 최대 3개, `ADMIN` 최대 1개

Restaurant–Video와 Restaurant–Creator 직접 관계는 그리지 않는다. 모두 Visit를 통해 파생된다.

## 6. 유일성 및 참조 제약

- Restaurant.kakaoPlaceIdentity 유일
- Region.code/name/sortOrder, FoodCategory.code/name/sortOrder 유일
- Creator.externalChannelId 유일
- Video.externalVideoId 유일
- Visit의 restaurantId+creatorId+videoId 복합 유일
- MemberAccount.email, AuthSession.tokenCredential 유일
- 모든 FK는 존재하는 대상을 참조한다.
- Video의 선택 Creator 및 Visit.Creator와 외부 게시 채널 ID의 일치는 애플리케이션 검증과 [물리 복합 FK](../data/constraint-mapping.md#3-fk-목록과-삭제-정책)를 함께 사용한다.

## 7. 상태 필드 표현

- Restaurant·Creator·Video·Visit의 `publicationStatus`는 사용자 노출 여부다.
- Restaurant·Creator·Video·Visit의 `lifecycleStatus`는 활성·삭제 여부다.
- Creator·Video의 `externalAvailabilityStatus`는 YouTube 리소스 가용 여부다.
- Region·FoodCategory의 `active`는 신규 연결 가능 여부다.
- MemberAccount.status·role과 AuthSession.invalidatedAt은 인증·인가·재발급 유효성을 표현한다.
- lifecycle, publication, 외부 availability와 검증 상태를 서로 합치지 않는다.

## 8. ERD 표기 규칙

- 엔티티 이름은 데이터 개념을 나타내는 대문자 단수형을 사용한다.
- `PK`, `FK`, `UK`만 논리 제약 표기에 사용한다.
- 자료형은 `identifier`, `text`, `status`, `timestamp`, `boolean` 같은 논리 타입만 사용한다.
- 선택 속성에는 설명으로 optional을 표시한다.
- 복합 유일성은 세 Visit FK의 설명과 이 문서에서 함께 명시한다.
- 카디널리티는 자식의 필수 부모 참조와 부모의 0개 이상 자식을 구분한다.

## 9. 미확정 관계

현재 MVP 핵심 관계의 카디널리티는 확정됐다. 다음은 범위 변경 시 새 결정이 필요하다.

- 복수 카테고리 도입 시 Restaurant–FoodCategory 관계 엔티티
- 전국·다단계 지역 도입 시 Region 자기 관계
- 한 방문의 복수 근거 도입 시 VisitEvidence
- 개인 제작자와 채널 분리 시 Creator/Channel 재모델링
- 인증을 외부 인증 제공자에 위임할 경우 MemberAccount–AuthSession 변경

## 10. 검증 체크리스트

- [x] Restaurant는 Visit 없이 존재한다.
- [x] Video 하나가 여러 Restaurant의 Visit 근거가 될 수 있다.
- [x] Visit의 세 참조는 모두 필수다.
- [x] Creator–Video와 Visit의 채널 일치 검증이 명시됐다.
- [x] 단일 Region·FoodCategory 카디널리티가 표현됐다.
- [x] 외부 ID와 내부 ID가 구분됐다.
- [x] publication과 외부 availability가 분리됐다.
- [x] 핵심 유일·복합 유일 제약이 문서화됐다.
- [x] MVP 제외 데이터가 없다.
- [x] ERD와 [relationship-rules.md](../data/relationship-rules.md)가 일치한다.
