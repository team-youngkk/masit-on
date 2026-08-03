---
related_documents:
  - ../../01-requirements/business-rules.md
  - data-model.md
  - entity-definitions.md
  - relationship-rules.md
  - lifecycle-rules.md
  - ../api/admin/reference-data-api.md
  - ../api/admin/visit-registration-api.md
  - ../../07-adr/data/data-001-postgresql.md
  - ../../07-adr/security/auth-003-confirmation-token.md
  - ../api/discovery/restaurant-discovery-api.md
  - ../api/admin/authentication-api.md
  - ../../01-requirements/non-functional-requirements.md
  - physical-data-model.md
  - constraint-mapping.md
  - second-expansion-data-contract.md
---

# 맛잇온 데이터 제약조건

## 1. 문서 목적

논리 모델의 필수값, 고유성, 참조 무결성, 상태, 중복과 원자성 요구를 저장소와 애플리케이션 책임으로 나눈다. SQL 구문과 구체 인덱스는 정의하지 않는다.

## 2. 필수값 제약

- Restaurant: 내부 식별자, 이름, 카카오 장소 동일성·링크, 전체 도로명주소, 전화번호, Region, FoodCategory, publication status가 필수다. 상세 주소와 지도용 WGS84 좌표는 선택이며 좌표는 위·경도 쌍으로만 저장한다. `기타` 카테고리도 별도 구체 음식 종류를 저장하지 않는다.
- Region·FoodCategory: 내부 식별자, 표준 이름과 활성 상태가 필수다.
- Creator: 내부 식별자, 외부 채널 ID, 채널명, 채널 URL, publication status와 외부 가용 상태가 필수다. 프로필 이미지 URL, 소개, handle은 선택 표시 정보다.
- Video: 내부 식별자, 외부 영상 ID, 제목, 원본 URL, 썸네일 URL, 게시 채널 외부 ID, publication status와 외부 가용 상태가 필수다. 내부 Creator 참조와 게시일은 선택이다.
- Visit: 내부 식별자, Restaurant·Creator·Video와 publication status가 필수다. 방문일·검증 상태·검증자·검증 시각은 저장하지 않는다.
- AdminAccount: 내부 식별자, 로그인 ID, 안전한 비밀번호 자격 증명, 활성 여부가 필수다.
- AdminRefreshToken: 계정 참조, 토큰·계열 검증 값과 생성·만료 시각이 필수다.
- MemberAccount: 내부 식별자, 정규화 이메일, 안전한 비밀번호 해시와 상태가 필수다. 이메일 인증·탈퇴 요청 시각은 상태 전이를 보조하는 선택 속성이다.
- MemberActionToken: 회원 참조, 용도, SHA-256 해시, 상태와 만료 시각이 필수다.
- MemberSessionRevocation: 폐기된 `sid`, 폐기 시각과 Access Token 만료 시각이 필수다. 회원 FK를 두지 않아 회원 개인정보 파기 뒤에도 기존 Token을 거부한다.
- Favorite: 회원과 맛집 참조가 필수이고 `(member, restaurant)` 조합으로 식별한다.
- RecentRestaurantView: 회원과 맛집 참조, 마지막 조회 시각이 필수이고 `(member, restaurant)` 조합으로 식별한다.

## 3. 유일성 제약

### DATA-CONSTRAINT-001 Restaurant 외부 장소 동일성

- 적용 데이터: Restaurant
- 제약: 카카오에서 같은 장소로 확인되는 Restaurant는 하나만 존재한다. 이름은 유일하지 않다.
- 보장 수준: 저장소와 애플리케이션 모두. 저장소는 검증된 `restaurant.kakao_place_id` UK를 사용한다.
- 위반 시 처리: `DUPLICATE_RESTAURANT` 또는 동일성 불명확 시 `IDENTITY_VERIFICATION_REQUIRED`
- 관련 규칙/API: [BR-RESTAURANT-006](../../01-requirements/business-rules.md#br-restaurant-006-맛집-중복-판단)·[BR-RESTAURANT-007](../../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분), [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기)·[API-ADMIN-RESTAURANT-001](../api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정)

### DATA-CONSTRAINT-002 외부 채널 ID 유일성

- 적용 데이터: Creator
- 제약: 같은 YouTube 채널 ID를 가진 Creator는 하나만 존재한다. 채널명은 유일하지 않다.
- 보장 수준: 저장소와 애플리케이션 모두
- 위반 시 처리: `DUPLICATE_CREATOR`
- 관련 규칙/API: [BR-CREATOR-001](../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)·[BR-CREATOR-003](../../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단), [API-ADMIN-CREATOR-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-creator-preview-001-유튜버-등록-검증-미리보기)·[API-ADMIN-CREATOR-001](../api/admin/reference-data-api.md#api-admin-creator-001-유튜버-등록-확정)

채널 URL은 정규화·변경 가능성이 있어 외부 채널 ID의 유일성을 대체하지 않는다. 보조 유일성 사용 여부는 물리 설계에서 결정한다.

### DATA-CONSTRAINT-003 외부 영상 ID 유일성

- 적용 데이터: Video
- 제약: 같은 YouTube 원본 영상 ID를 가진 Video는 하나만 존재한다.
- 보장 수준: 저장소와 애플리케이션 모두
- 위반 시 처리: `DUPLICATE_VIDEO`
- 관련 규칙/API: [BR-VIDEO-003](../../01-requirements/business-rules.md#br-video-003-영상-식별-및-중복-판단), [API-ADMIN-VIDEO-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-video-preview-001-영상-등록-검증-미리보기)·[API-ADMIN-VIDEO-001](../api/admin/reference-data-api.md#api-admin-video-001-영상-등록-확정)

영상 URL만으로 유일성을 판단하지 않는다.

### DATA-CONSTRAINT-004 Visit 복합 유일성

- 적용 데이터: Visit
- 제약: 같은 Restaurant·Creator·Video 조합은 하나만 존재한다.
- 보장 수준: 저장소와 애플리케이션 모두
- 위반 시 처리: `DUPLICATE_VISIT_RELATIONSHIP`
- 관련 규칙/API: [BR-VISIT-003](../../01-requirements/business-rules.md#br-visit-003-방문-관계-중복-판단), [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록)

### DATA-CONSTRAINT-005 참조 데이터 표준값 유일성

- 적용 데이터: Region, FoodCategory
- 제약: Region.name과 FoodCategory.name은 각 집합 안에서 유일하다. 별도 code를 도입하면 각 code도 유일해야 한다.
- 보장 수준: 저장소와 애플리케이션 모두
- 위반 시 처리: 기준 데이터 변경 거부
- 관련 규칙/API: [BR-RESTAURANT-004](../../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리)·[BR-RESTAURANT-005](../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속), [API-DISCOVERY-001](../api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)

### DATA-CONSTRAINT-006 관리자 로그인 ID 유일성

- 적용 데이터: AdminAccount
- 제약: 정규화된 loginId는 하나의 계정만 식별한다.
- 보장 수준: 저장소 필수, 애플리케이션 필수
- 위반 시 처리: 계정 발급 거부
- 관련 항목: [API-ADMIN-AUTH-001](../api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)

### DATA-CONSTRAINT-007 회원 이메일 유일성

- 적용 데이터: MemberAccount
- 제약: 정규화한 이메일은 하나의 회원만 식별한다.
- 보장 수준: 저장소 필수, 애플리케이션 필수
- 위반 시 처리: `DUPLICATE_EMAIL`
- 관련 규칙/API: [BR-MEMBER-001](../../01-requirements/business-rules.md#br-member-001-이메일-고유성), [API-MEMBER-AUTH-001](../api/account/member-authentication-api.md#api-member-auth-001-회원가입)

### DATA-CONSTRAINT-008 회원 Action Token 단일 활성성

- 적용 데이터: MemberActionToken
- 제약: `(member, purpose)`별 `ISSUED` Token은 하나만 존재하고, 같은 Token 해시는 한 번만 저장한다.
- 형식: `EMAIL_VERIFICATION`은 CSPRNG 8자 코드의 SHA-256 해시, `PASSWORD_RESET`은 기존 고엔트로피 불투명 값의 SHA-256 해시를 저장하며 원문 형식은 DB 제약으로 판정하지 않는다.
- 보장 수준: 저장소 필수, 애플리케이션 필수
- 위반 시 처리: 기존 `ISSUED` Token을 `REVOKED`로 완료 처리한 뒤 새 Token을 발급한다.
- 관련 규칙/API: [BR-AUTH-006](../../01-requirements/business-rules.md#br-auth-006-로그아웃과-폐기), [API-MEMBER-AUTH-002](../api/account/member-authentication-api.md#api-member-auth-002-가입-이메일-인증), [API-MEMBER-AUTH-005](../api/account/member-authentication-api.md#api-member-auth-005-비밀번호-재설정-완료)

### DATA-CONSTRAINT-009 회원별 찜 관계 유일성

- 적용 데이터: Favorite
- 제약: 같은 회원과 맛집의 찜은 하나만 존재한다.
- 보장 수준: 저장소 필수, 애플리케이션 필수
- 위반 시 처리: `PUT`은 멱등 성공으로 처리한다.
- 관련 규칙/API: [BR-FAVORITE-001](../../01-requirements/business-rules.md#br-favorite-001-회원별-찜의-고유성과-멱등성), [API-PERSONAL-001](../api/personal/personal-restaurant-api.md#api-personal-001-맛집-찜-추가)

### DATA-CONSTRAINT-010 회원별 최근 기록 유일성

- 적용 데이터: RecentRestaurantView
- 제약: 같은 회원과 맛집의 최근 기록은 하나만 존재한다.
- 보장 수준: 저장소 필수, 애플리케이션 필수
- 위반 시 처리: 기존 행을 upsert 충돌 대상으로 사용해 `last_viewed_at`만 갱신한다.
- 관련 규칙/API: [BR-RECENT-001](../../01-requirements/business-rules.md#br-recent-001-최근-본-맛집의-기록과-갱신), [API-DETAIL-001](../api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)

## 4. 참조 무결성

### DATA-CONSTRAINT-011 핵심 참조 존재

- Restaurant는 존재하는 Region과 FoodCategory를 참조한다.
- Video.creatorId가 있으면 존재하는 Creator를 참조한다. Creator와 Video의 기본 등록 순서는 강제하지 않는다.
- Visit는 존재하는 Restaurant, Creator, Video를 참조한다.
- AdminRefreshToken은 존재하는 AdminAccount를 참조한다.
- MemberActionToken, Favorite, RecentRestaurantView는 존재하는 MemberAccount를 참조한다.
- Favorite와 RecentRestaurantView는 존재하는 Restaurant를 참조한다.
- 보장 수준: 저장소 수준 필수
- 관련 요구사항: [NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)

참조 대상을 비공개·비활성으로 새로 연결할 수 있는지는 저장소 참조 제약만으로 충분하지 않으므로 애플리케이션에서 검증한다. Visit는 세 참조가 모두 공개일 때만 생성한다. Favorite는 공개 맛집에만 생성하고, 비공개 맛집 관계는 조회에서만 숨긴다. RecentRestaurantView는 공개 상세 성공 뒤에만 upsert한다. 비활성 Region·FoodCategory는 신규 Restaurant에 연결하지 않는다.

### DATA-CONSTRAINT-012 Video·Creator 게시 채널 일치

- 적용 데이터: Video, Creator, Visit
- 제약: Video.creatorId가 있으면 외부 게시 채널 ID와 Creator.externalChannelId가 같고, Visit.Creator.externalChannelId는 Visit.Video.publisherExternalChannelId와 같아야 한다.
- 보장 수준: 애플리케이션 검증과 저장소 복합 FK 모두 필수. `video(creator_id, publisher_external_channel_id)`와 `visit(video_id, creator_id)` 구조는 [constraint-mapping.md](constraint-mapping.md#3-fk-목록과-삭제-정책)를 따른다.
- 위반 시 처리: `VIDEO_CHANNEL_MISMATCH`
- 관련 규칙/API: [BR-CREATOR-005](../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치), [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록)

### DATA-CONSTRAINT-013 Restaurant 좌표 완전성

- 적용 데이터: `restaurant.latitude`, `restaurant.longitude`
- 제약: 둘 다 `NULL`이거나 둘 다 `NOT NULL`이다. 위도는 `-90..90`, 경도는 `-180..180` 범위다.
- 보장 수준: PostgreSQL CHECK와 관리자 좌표 검증
- 관련 규칙/API: [BR-MAP-001](../../01-requirements/business-rules.md#br-map-001-wgs84-좌표와-null-처리), [API-MAP-001](../api/discovery/map-discovery-api.md#api-map-001-지도-영역-맛집-조회)

### DATA-CONSTRAINT-014 Creator 상세 선택 표시값

- 적용 데이터: `creator.profile_image_url`, `creator.description`, `creator.handle`
- 제약: 선택값은 `NULL` 또는 빈 문자열이 아닌 값이다. 프로필 이미지 URL은 저장 시 HTTPS URL이어야 하며 handle은 외부 채널 ID를 대체하는 고유 키가 아니다.
- 보장 수준: PostgreSQL CHECK와 관리자 확인 흐름
- 관련 규칙/API: [BR-CREATOR-009](../../01-requirements/business-rules.md#br-creator-009-유튜버-상세-표시-정보), [API-CREATOR-DETAIL-001](../api/detail/creator-detail-api.md#api-creator-detail-001-유튜버-기본-상세-조회)

## 5. 상태 제약

- publication status는 허용된 값만 사용하고 생성 성공한 Restaurant·Creator·Video·Visit는 PUBLIC이다.
- 비공개·삭제 상태는 일반 조회에 노출하지 않는다.
- Creator·Video의 external availability는 publication status와 별도 값이다.
- 외부 일시 오류만으로 external availability나 publication status를 자동 변경하지 않는다.
- `ACTIVE/DELETED`와 `deleted_at`을 사용하며 [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md)을 따른다.
- MemberAccount는 `PENDING_VERIFICATION`, `ACTIVE`, `DELETION_PENDING`, `DISABLED`만 허용하며 `ACTIVE`만 로그인할 수 있다.

## 6. 중복 방지

- 저장 전 외부 식별·조합을 검증해 사용자에게 의미 있는 중복 결과를 제공한다.
- 최종 생성 시점에도 저장소 고유성 제약을 적용해 미리보기 이후 동시 등록을 막는다.
- 중복이면 새 데이터를 만들지 않고 기존 자원 식별자를 사용한다.
- 동일성 판단이 불가능한 `REVIEW_REQUIRED`는 확인 토큰과 자원을 생성하지 않는다.
- Favorite 추가·해제는 저장소 복합 키와 애플리케이션 멱등 처리로 중복 쓰기를 숨긴다.
- RecentRestaurantView는 복합 키 upsert와 `GREATEST(last_viewed_at)` 갱신으로 역행 시간을 막는다.

## 7. 트랜잭션 원자성 요구사항

- 각 기본 데이터 생성 요청은 해당 엔티티와 필수 관계·상태를 한 원자적 범위에서 만든다.
- Visit 생성은 세 참조·채널 일치·공개 상태·중복 확인과 관계 저장을 한 원자적 범위에서 처리한다.
- 실패 시 부분 데이터나 부분 공개 상태가 남지 않는다.
- 검증 미리보기는 핵심 엔티티를 생성하지 않고 PostgreSQL에 10분 수명의 확인 Token 해시·관리자·자원 종류·후보 스키마 버전·JSONB Snapshot을 저장한다. Token 소비와 Entity 생성 또는 중복 완료는 한 트랜잭션으로 처리한다.
- 서로 별도인 맛집·Creator·Video 등록을 하나의 거대 트랜잭션으로 묶지 않는다.
- 회원 탈퇴는 세션·개인화 관계 정리와 개인정보 물리 삭제를 같은 작업 단위로 완료해야 한다.
- 공개 상세의 Recent upsert Command는 대상 upsert와 회원별 최신 50개 정리를 같은 트랜잭션에서 처리한다. 30일 경과 기록의 물리 삭제는 회원 조회와 독립된 주기 cleanup Command가 수행한다.

## 8. 애플리케이션 검증과 저장소 제약의 구분

| 규칙 | 저장소 제약 | 애플리케이션 검증 | 이유 |
|---|---:|---:|---|
| 필수 속성 누락 방지 | 필요 | 필요 | 정합성과 오류 메시지 모두 필요 |
| 카카오 동일 장소 중복 금지 | 필요 | 필요 | 동시성과 외부 사실 판정 모두 필요 |
| 외부 채널·영상 ID 중복 금지 | 필요 | 필요 | 동시 등록과 외부 확인 모두 필요 |
| Visit 세 참조 존재 | 필요 | 필요 | 참조 무결성과 대상별 404 처리 |
| Visit 세 참조 복합 중복 금지 | 필요 | 필요 | 동시 등록과 409 처리 |
| Video 게시 채널·선택 Creator·Visit.Creator 일치 | 복합 FK 필요 | 필요 | 독립 등록 순서를 허용하면서 DB도 일치 보존 |
| 참조 대상 공개·참조 데이터 활성 | 불충분 | 필요 | 상태 시점 정책 |
| 실제 방문 장면 확인 | 불가 | 필요 | 관리자 업무 판단 |
| publication 허용값 | 필요 | 필요 | 값 무결성과 전환 정책 |
| 좌표 위·경도 null 쌍과 범위 | CHECK 필요 | 필요 | WGS84 범위와 백필 절차 |
| Member 이메일 중복 금지 | 필요 | 필요 | 동시 가입과 의미 있는 오류 |
| Token 목적별 단일 활성 | 부분 UK 필요 | 필요 | 재발급과 단일 사용 |
| Favorite 회원·맛집 조합 멱등성 | 필요 | 필요 | 동시 찜과 중복 숨김 |
| Recent upsert와 최신 시각 유지 | 복합 PK 필요 | 필요 | 재조회와 역행 방지 |
| 계정당 활성 Refresh Token 최대 1개 | Redis·애플리케이션 | 필요 | 키·회전·무효화 전략에 의존 |
| 확인 Token 단일 사용·결과 재현 | PostgreSQL 행 잠금·상태·고유 해시 | 필요 | 생성과 Token 결과를 같은 트랜잭션으로 커밋 |

## 9. 동시성 검토

- Restaurant 외부 장소 키, Creator 외부 채널 ID, Video 외부 영상 ID, Visit 세 참조 조합에 최종 고유성 보장이 필요하다.
- Member 이메일, Favorite와 RecentRestaurantView 복합 키에도 최종 고유성 보장이 필요하다.
- 애플리케이션의 선조회만으로 중복을 막지 않는다.
- 고유성 충돌은 기존 자원을 반환할 수 있는 도메인 중복 오류로 변환한다.
- 일반 동시 쓰기의 격리 수준·락·upsert 강화는 [ADR-DATA-006](../../07-adr/adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어)의 활성화 조건을 따른다. 확인 Token 생성 확정은 [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md)에 따라 행 잠금과 제한된 `ON CONFLICT DO NOTHING RETURNING`으로 결과를 원자적으로 확정한다.

## 10. 확정 및 조건부 재검토

- 삭제·비공개 전환은 별도 운영 명령으로 수행하고 논리 삭제 데이터는 자동 purge 없이 보존한다.
- Redis의 계정당 활성 Refresh Token 하나와 회전·재사용 탐지는 `auth:refresh:{adminId}` 원자 연산으로 보장한다.
- 외부 URL·표시 메타데이터 변경 이력은 저장하지 않고 검색 인덱스는 성능 실측으로 튜닝한다.

## 11. 2차 확장 제약

2차 확장의 소유권, 컬렉션·큐레이션 상한, 열린 요청 중복, 상태 전이, 알림 고유성과 다형 FK XOR 제약은 [2차 확장 데이터 계약](second-expansion-data-contract.md)을 따른다.

- DB 고유 제약: 컬렉션 맛집, 큐레이션 맛집·표시 위치, 게시 메인 위치, 열린 제보·신고, 요청·상태 알림과 상태 이력, `idempotency_record(actor_type, actor_id, api_scope, key_hash)`
- DB CHECK: 상태 허용값, 큐레이션 게시 상태·위치, `ck_moderation_history__exactly_one_request`, `ck_notification__exactly_one_request`, 완료 결과 필드 조합
- 애플리케이션+행 잠금: 회원당 컬렉션 20개, 컬렉션당 맛집 100개, 제보·신고 합산 일일 5건, Curation 구성 20개와 허용 상태 전이
- 같은 트랜잭션: 요청 상태, ModerationHistory, 처리 결과 Notification
