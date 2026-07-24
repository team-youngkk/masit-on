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
---

# 맛잇온 논리 데이터 모델

## 1. 문서 목적

1차 MVP의 조회·관리자 등록 API를 지원하는 핵심 영속 데이터와 관계를 정의한다. 이 문서는 구현 기술을 선택하지 않고 데이터 책임, 식별, 정합성과 공개 조건을 고정한다.

## 2. 모델링 원칙

- Restaurant, Creator, Video와 Visit를 서로 다른 변경 책임으로 둔다.
- `Creator`는 개인이 아니라 YouTube 채널 단위의 서비스 데이터다.
- Region과 FoodCategory는 Restaurant 도메인이 소유하는 표준 참조 데이터다.
- Visit는 단순 다대다 연결이 아니라 실제 방문 근거와 중복 규칙을 가진 삼항 관계다.
- 내부 식별자와 카카오·YouTube 외부 식별 정보를 구분한다.
- 공개 상태, 외부 리소스 가용 상태, 삭제·보관 상태와 검증 상태를 한 값으로 합치지 않는다.
- 조회 DTO의 축약·집계·상태 표현은 원천 데이터에서 계산한다.
- MVP 제외 기능을 위한 사용자·개인화·추천·AI·예약·결제 데이터는 만들지 않는다.

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

### Region 권장안

Region은 Restaurant의 자유 문자열 속성이 아니라 Restaurant 도메인이 소유하는 참조 데이터로 둔다. 서울 자치구 허용값의 일관성, 오탈자 방지, 필터 검증과 관리자 등록 판정을 한 기준으로 유지할 수 있다. MVP에서는 서울특별시 자치구 한 단계만 지원하므로 상위 Region 관계는 만들지 않는다. 지도·전국·다단계 지역 기능이 범위에 들어올 때 계층 모델을 재검토한다.

### FoodCategory 권장안

FoodCategory도 Restaurant 도메인의 참조 데이터로 둔다. Restaurant는 정확히 하나의 대표 FoodCategory를 참조하며 다중 연결 엔티티는 만들지 않는다. `기타` 선택 시 구체 음식 종류는 Restaurant의 보충 속성으로 관리한다.

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

## 6. 데이터 생성 흐름

1. 운영 기준으로 Region과 FoodCategory가 준비된다.
2. 맛집 미리보기에서 카카오 장소 동일성·서울 주소·지역·카테고리를 검증한다. `READY` 확인 뒤 Restaurant 하나를 원자적으로 만들고 즉시 공개한다.
3. 채널 미리보기에서 YouTube 채널을 확인한 뒤 Creator를 만들고 즉시 공개한다.
4. 영상 미리보기에서 외부 영상, 메타데이터와 게시 채널 외부 식별을 확인해 Video를 만든다. Creator보다 먼저 등록할 수 있으며, 일치 Creator가 존재하면 내부 연결을 해소한다.
5. 관리자가 영상의 실제 방문과 게시 채널 일치를 확인한 뒤 기존 Restaurant·Creator·Video를 참조하는 Visit를 한 번에 생성한다.
6. 어느 단계든 실패하면 해당 요청의 일부 데이터나 관계를 남기지 않는다. 서로 다른 기본 데이터 등록 요청은 독립적이며 자동으로 묶어 생성하지 않는다.

## 7. 조회 데이터 조합

- `GET /restaurants`: Restaurant에 Region과 FoodCategory를 결합하고, 공개·유효 Visit를 통해 Creator를 중복 제거한다. 최대 3명과 나머지 수는 조회 파생 값이다.
- `GET /creators`: 공개 Creator의 식별자와 표시 이름을 조회한다.
- `GET /restaurants/{restaurantId}`: Restaurant 기본 정보에 공개 Visit를 통해 Creator와 Video 표시 정보를 결합한다.
- `remainingVisitedByCount`, `contentStatus`, 페이지 메타데이터는 영속 속성이 아니다.
- `channelName`은 Video에 중복 저장해 조회하는 값이 아니라 게시 Creator에서 얻는 것을 기본 원칙으로 한다. 외부 확인 당시 원문 보관이 필요하면 별도 이력 요구를 먼저 확정한다.

## 8. 공개 상태와 외부 상태

Restaurant, Creator, Video와 Visit는 일반 사용자 노출을 위한 publication status를 가진다. Creator와 Video는 YouTube 리소스 가용 상태를 별도로 가진다. 외부 리소스 이용 불가가 확인돼도 내부 Restaurant·Creator·Video·Visit를 자동 삭제하지 않는다. 조회 시 Restaurant가 공개면 기본 정보는 유지하고, 공개·유효 조건을 만족하지 않는 관계와 콘텐츠만 제외한다.

## 9. 논리 삭제 및 이력 요구사항

비즈니스 규칙은 삭제 상태 데이터를 공개 조회에서 제외하도록 요구하지만 MVP API는 수정·삭제를 제공하지 않는다. 따라서 삭제·보관을 나타낼 독립 lifecycle 상태의 필요성은 인정하되, 물리 삭제 여부, 삭제 시각, 복구, 변경 이력과 관리자 식별 기록은 후속 설계에서 결정한다. 생성·수정 시각은 운영 추적을 위한 공통 감사 속성으로 권장한다.

## 10. 데이터 정합성 원칙

- 카카오에서 같은 장소인 Restaurant, 같은 YouTube 채널인 Creator, 같은 YouTube 원본인 Video는 각각 하나만 존재한다.
- 같은 Restaurant·Creator·Video 조합의 Visit는 하나만 존재한다.
- 외부 URL이나 표시 이름만을 유일성 기준으로 사용하지 않는다.
- Visit의 Creator는 Video의 게시 Creator와 같아야 한다.
- 참조 대상이 없거나 비공개이면 Visit를 만들지 않는다. Visit.Creator의 외부 채널 ID는 Video의 게시 채널 외부 ID와 같아야 한다.
- 저장소 고유성·참조 제약과 애플리케이션의 외부 사실 검증을 함께 사용한다.

## 11. API 지원 범위

모든 공개 조회 API, 관리자 인증 API, 맛집·유튜버·영상 검증 미리보기와 등록 API, 방문 관계 등록 API를 지원한다. 확인 토큰은 10분 만료·단일 후보 무결성을 보장해야 하지만 서명 토큰인지 단기 서버 저장 데이터인지는 이 논리 모델에서 결정하지 않는다. `REVIEW_REQUIRED` 미리보기는 등록 요청 데이터로 저장하지 않는다.

## 12. 검토 필요 항목

- 카카오 동일 장소를 저장소 유일성으로 표현할 안정된 외부 식별값과 정규화 방식
- Region·FoodCategory의 별도 업무 코드 필요 여부(이름은 API 계약의 표준 값)
- publication status와 삭제·보관 상태의 실제 값 및 전환 수단
- 생성·수정 시각 외 변경자·변경 사유 이력 범위
- 외부 상태 마지막 확인 시각의 저장 필요 여부
- 확인 토큰, 로그인 실패 제한과 Refresh Token의 키·검증값·TTL·정리 전략
- 내부 식별자 타입, 데이터베이스, 인덱스와 동시성 구현 방식

상세 상태와 우선순위는 [data-review.md](data-review.md)에 기록한다.
