---
related_documents:
  - ../../02-analysis/domain-boundaries.md
  - ../../01-requirements/business-rules.md
  - entity-definitions.md
  - relationship-rules.md
  - lifecycle-rules.md
  - constraints.md
  - ../diagrams/erd-spec.md
  - ../../02-analysis/mvp-workstreams.md
  - data-review.md
  - ../../07-adr/security/auth-003-confirmation-token.md
  - physical-data-model.md
  - ../../07-adr/data/data-007-uuid-v4-identifiers.md
  - ../../07-adr/data/data-008-publication-lifecycle-soft-delete.md
---

# 맛잇온 논리 데이터 모델

## 1. 문서 목적

MVP와 1차 확장 계약의 조회·인증·개인화 API를 지원하는 핵심 영속 데이터와 관계를 정의한다. 이 문서는 구현 기술을 선택하지 않고 데이터 책임, 식별, 정합성과 공개 조건을 고정한다.

## 2. 모델링 원칙

- Restaurant, Creator, Video와 Visit를 서로 다른 변경 책임으로 둔다.
- `Creator`는 개인이 아니라 YouTube 채널 단위의 서비스 데이터다.
- Region과 FoodCategory는 Restaurant 도메인이 소유하는 표준 참조 데이터다.
- Visit는 단순 다대다 연결이 아니라 실제 방문 근거와 중복 규칙을 가진 삼항 관계다.
- 내부 식별자와 카카오·YouTube 외부 식별 정보를 구분한다.
- 공개 상태, 외부 리소스 가용 상태, 삭제·보관 상태와 검증 상태를 한 값으로 합치지 않는다.
- 조회 DTO의 축약·집계·상태 표현은 원천 데이터에서 계산한다.
- 1차 확정 범위인 회원·개인화·지도·유튜버 상세 데이터는 포함하되, 추천·AI·예약·결제 데이터는 만들지 않는다.

## 3. 핵심 데이터 개념 요약

| 데이터 개념 | 책임 | 결정 상태 |
|---|---|---|
| Restaurant | 카카오에서 확인한 한 음식점 지점의 이름·주소·전화번호·지역·대표 카테고리·공개 상태 | 확정 |
| Region | 서울특별시 자치구 표준 선택값 | 별도 참조 데이터 권장, 단일 단계 확정 |
| FoodCategory | 사전 정의된 대표 음식 카테고리 10개 | 별도 참조 데이터 권장, 맛집당 정확히 1개 확정 |
| Creator | YouTube 채널 단위 유튜버의 외부 식별·표시·링크·상태 | 채널 단위 확정 |
| Video | YouTube 원본 영상의 외부 식별·표시 메타데이터·게시 채널·상태 | 확정 |
| Visit | Restaurant·Creator·Video를 연결하는 검증 완료 방문 관계 | 삼항 관계 확정 |
| AdminAccount | 사전 발급 관리자 자격 증명과 활성 여부 | 내부 계정 사용 확정 |
| AdminRefreshToken | JWT 재발급 Token의 회전·만료·무효화 상태 | Redis 8.8 저장 확정 |
| MemberAccount | 이메일 회원의 식별·상태·인증 기반 | 1차 확장 확정 |
| MemberActionToken | 이메일 인증·비밀번호 재설정용 1회성 Token | 1차 확장 확정 |
| MemberSessionRevocation | 폐기된 회원 Access Token `sid` 거부 상태 | 1차 확장 확정 |
| Favorite | 회원–맛집 찜 관계 | 1차 확장 확정 |
| RecentRestaurantView | 회원–맛집 최근 조회 관계 | 1차 확장 확정 |

### Region 권장안

Region은 Restaurant의 자유 문자열 속성이 아니라 Restaurant 도메인이 소유하는 참조 데이터로 둔다. 서울 자치구 허용값의 일관성, 오탈자 방지, 필터 검증과 관리자 등록 판정을 한 기준으로 유지할 수 있다. MVP에서는 서울특별시 자치구 한 단계만 지원하므로 상위 Region 관계는 만들지 않는다. 지도·전국·다단계 지역 기능이 범위에 들어올 때 계층 모델을 재검토한다.

### FoodCategory 권장안

FoodCategory도 Restaurant 도메인의 참조 데이터로 둔다. Restaurant는 정확히 하나의 대표 FoodCategory를 참조하며 다중 연결 엔티티는 만들지 않는다. `기타`도 별도 보충 속성 없이 표준 FoodCategory 참조만 저장한다.

## 4. 도메인별 데이터 소유권

| 데이터 개념 | 주 소유 도메인 | 생성 책임 | 변경 책임 | 주요 조회 사용자 |
|---|---|---|---|---|
| Restaurant | Restaurant | 관리자 맛집 등록 유스케이스([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 조율) | Restaurant | 일반 사용자, 관리자 |
| Region | Restaurant | 기준 데이터 운영 | Restaurant | Restaurant 등록·탐색 |
| FoodCategory | Restaurant | 기준 데이터 운영 | Restaurant | Restaurant 등록·탐색 |
| Creator | Creator | 관리자 유튜버 등록 유스케이스([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 조율) | Creator | 일반 사용자, Visit, 관리자 |
| Video | Video | 관리자 영상 등록 유스케이스([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 조율) | Video | 일반 사용자, Visit, 관리자 |
| Visit | Visit | 관리자 방문 관계 등록 유스케이스([WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 조율) | Visit | [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), 관리자 |
| AdminAccount | 관리자 인증 애플리케이션 책임 | 수동 운영 발급 | 수동 운영 | 관리자 인증 |
| AdminRefreshToken | 관리자 인증 애플리케이션 책임 | JWT 로그인·재발급 성공 | 회전·로그아웃·만료·재사용 탐지 | 관리자 인증 API |
| MemberAccount | Member/Auth | 회원가입·인증·로그인 유스케이스([WS-05](../../02-analysis/first-expansion-workstreams.md#4-ws-05-사용자-계정인증)) | Member/Auth | 회원 인증·개인화 |
| MemberActionToken | Member/Auth | 메일 인증·재설정 발급 | Member/Auth | 회원 인증 API |
| MemberSessionRevocation | Member/Auth | 로그아웃·세션 축출·탈퇴·재사용 탐지 | Member/Auth | 회원 인증 API |
| Favorite | Personal | 회원 찜 명령 | Personal | 개인 맛집 관리 |
| RecentRestaurantView | Personal | 공개 상세 부수효과·개별 삭제 | Personal | 개인 맛집 관리 |

Admin은 독립 비즈니스 도메인이 아니다. 인증과 등록 흐름을 조율하며 Restaurant, Creator, Video, Visit의 규칙을 우회하거나 기본 데이터를 직접 소유하지 않는다.

## 5. 핵심 관계 요약

| 관계 | 카디널리티 | 필수성 |
|---|---|---|
| Region–Restaurant | Region 1 : N Restaurant | Restaurant는 Region 1개 필수, Region은 Restaurant 0개 가능 |
| FoodCategory–Restaurant | FoodCategory 1 : N Restaurant | Restaurant는 FoodCategory 1개 필수 |
| Creator–Video | Creator 1 : N Video, Video 관점 0..1 Creator | Video는 게시 채널 외부 식별이 필수이고 내부 Creator 연결은 등록 순서상 선택 |
| Restaurant–Visit | Restaurant 1 : N Visit | Visit는 Restaurant 1개 필수, Restaurant는 Visit 없이 존재 가능 |
| Creator–Visit | Creator 1 : N Visit | Visit는 Creator 1개 필수 |
| Video–Visit | Video 1 : N Visit | Visit는 근거 Video 1개 필수, 한 Video는 여러 Restaurant의 근거 가능 |
| AdminAccount–AdminRefreshToken | AdminAccount 1 : N AdminRefreshToken | 유효 Token은 계정 1개 필수, 계정당 활성 Token은 최대 1개 |
| MemberAccount–Favorite | MemberAccount 1 : N Favorite | Favorite는 회원 1명 필수 |
| MemberAccount–RecentRestaurantView | MemberAccount 1 : N RecentRestaurantView | Recent는 회원 1명 필수 |
| Restaurant–Favorite | Restaurant 1 : N Favorite | Favorite는 맛집 1개 필수 |
| Restaurant–RecentRestaurantView | Restaurant 1 : N RecentRestaurantView | Recent는 맛집 1개 필수 |

## 6. 데이터 생성 흐름

1. 운영 기준으로 Region과 FoodCategory가 준비된다.
2. 맛집 미리보기에서 카카오 장소 동일성·서울 주소·지역·카테고리를 검증한다. `READY` 확인 뒤 Restaurant 하나를 원자적으로 만들고 즉시 공개한다.
3. 채널 미리보기에서 YouTube 채널을 확인한 뒤 Creator를 만들고 즉시 공개한다.
4. 영상 미리보기에서 외부 영상, 메타데이터와 게시 채널 외부 식별을 확인해 Video를 만든다. Creator보다 먼저 등록할 수 있으며, 일치 Creator가 존재하면 내부 연결을 해소한다.
5. 관리자가 영상의 실제 방문과 게시 채널 일치를 확인한 뒤 기존 Restaurant·Creator·Video를 참조하는 Visit를 한 번에 생성한다.
6. 회원가입·인증·재설정은 MemberAccount와 MemberActionToken을 사용하며 Refresh Token은 Redis namespace로 분리한다.
7. 공개 맛집 상세가 인증 회원으로 성공하면 RecentRestaurantView를 upsert하고 회원별 30일·50건 정리를 같은 Command 범위에서 수행한다.
8. 어느 단계든 실패하면 해당 요청의 일부 데이터나 관계를 남기지 않는다. 서로 다른 기본 데이터 등록 요청은 독립적이며 자동으로 묶어 생성하지 않는다.

## 7. 조회 데이터 조합

- `GET /api/restaurants`: Restaurant에 Region과 FoodCategory를 결합하고, 공개·유효 Visit를 통해 Creator를 중복 제거한다. 최대 3명과 나머지 수는 조회 파생 값이다.
- `GET /api/creators`: 공개 Creator의 식별자와 표시 이름을 조회한다.
- `GET /api/restaurants/{restaurantId}`: Restaurant 기본 정보에 공개 Visit를 통해 Creator와 Video 표시 정보를 결합한다.
- `GET /api/me/favorites`, `GET /api/me/recent-restaurants`: Favorite·RecentRestaurantView를 공개 Restaurant와 결합해 최신순 페이지 목록을 만든다.
- `GET /api/restaurants/map-points`: Restaurant의 공개 상태와 nullable WGS84 좌표를 bounds와 AND 결합한다.
- `GET /api/creators/{creatorId}`: Creator의 저장된 채널 표시 정보와 공개·유효 Visit 관계를 결합한다.
- `remainingVisitedByCount`, `contentStatus`, 페이지 메타데이터는 영속 속성이 아니다.
- `channelName`은 Video에 중복 저장해 조회하는 값이 아니라 게시 Creator에서 얻는 것을 기본 원칙으로 한다. 외부 확인 당시 원문 보관이 필요하면 별도 이력 요구를 먼저 확정한다.

## 8. 공개 상태와 외부 상태

Restaurant, Creator, Video와 Visit는 일반 사용자 노출을 위한 publication status를 가진다. Creator와 Video는 YouTube 리소스 가용 상태를 별도로 가진다. Favorite와 RecentRestaurantView는 공개 여부를 자체 저장하지 않고 대상 Restaurant 공개 상태를 조회 시점에 따른다. 외부 리소스 이용 불가가 확인돼도 내부 Restaurant·Creator·Video·Visit를 자동 삭제하지 않는다. 조회 시 Restaurant가 공개면 기본 정보는 유지하고, 공개·유효 조건을 만족하지 않는 관계와 콘텐츠만 제외한다.

## 9. 논리 삭제 및 이력 요구사항

비즈니스 규칙은 삭제 상태 데이터를 공개 조회에서 제외하도록 요구하지만 MVP API는 수정·삭제를 제공하지 않는다. publication과 lifecycle을 분리하고 `deleted_at`을 결합한 논리 삭제·FK `RESTRICT`를 사용한다. 핵심 테이블의 MVP 감사 필드는 생성·수정·삭제 시각이며, 인증된 운영 명령의 상태 변경은 별도 운영 감사 로그에 기록한다. 변경자·사유를 구조화된 DB 이력으로 조회·보존하는 요구는 관리 기능 도입 시 재검토한다.

## 10. 데이터 정합성 원칙

- 카카오에서 같은 장소인 Restaurant, 같은 YouTube 채널인 Creator, 같은 YouTube 원본인 Video는 각각 하나만 존재한다.
- 같은 Restaurant·Creator·Video 조합의 Visit는 하나만 존재한다.
- 외부 URL이나 표시 이름만을 유일성 기준으로 사용하지 않는다.
- Visit의 Creator는 Video의 게시 Creator와 같아야 한다.
- 참조 대상이 없거나 비공개이면 Visit를 만들지 않는다. Visit.Creator의 외부 채널 ID는 Video의 게시 채널 외부 ID와 같아야 한다.
- 저장소 고유성·참조 제약과 애플리케이션의 외부 사실 검증을 함께 사용한다.

## 11. API 지원 범위

모든 공개 조회 API, 관리자 인증 API, 맛집·유튜버·영상 검증 미리보기와 등록 API, 방문 관계 등록 API, 회원 인증 API와 개인 맛집 관리 API를 지원한다. 확인 Token은 PostgreSQL에 해시·관리자·자원 종류·후보 스키마 버전·JSONB Snapshot과 결과 상태를 저장하는 10분 수명의 단기 기술 데이터다. 핵심 도메인 모델에는 포함하지 않으며 저장·소비·24시간 결과 재현은 [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md)을 따른다. `REVIEW_REQUIRED` 미리보기는 등록 요청 데이터로 저장하지 않는다.

## 12. 확정 및 조건부 재검토

- 삭제·비공개 전환은 별도 운영 명령으로 수행하고 논리 삭제 데이터는 자동 purge 없이 보존한다.
- 외부 표시 메타데이터는 최신값만 유지하고 변경 이력을 저장하지 않는다.
- 로그인 실패 제한과 Refresh Token의 Redis 키·검증값·정리 전략은 [data-review.md](data-review.md#rv-data-006-관리자-refresh-token로그인-제한-저장)와 인증 ADR에서 확정했다.
- 검색 인덱스와 추가 동시성 제어는 확정 부하·데이터 규모의 성능 측정에서 병목이 확인될 때만 활성화한다.

상세 상태와 우선순위는 [data-review.md](data-review.md)에 기록한다.
