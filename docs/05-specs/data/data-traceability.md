# 맛잇온 데이터 추적성

## 1. 문서 목적

PRD, 기능·비기능 요구사항, 비즈니스 규칙, API와 Workstream이 어떤 영속 데이터와 제약으로 충족되는지 추적한다. 응답 필드가 저장값인지 관계 조합·파생값인지도 구분한다.

## 2. PRD → 데이터 개념 매핑

| PRD | 사용자·관리자 결과 | 주 데이터 | 관계·조합 데이터 |
|---|---|---|---|
| PRD-DISCOVERY-001 | 맛집 목록·이름·지역·카테고리 탐색 | Restaurant, Region, FoodCategory | Visit, Creator |
| PRD-DISCOVERY-002 | 유튜버 선택 및 방문 맛집 탐색 | Creator | Visit, Restaurant, Video 공개 유효성 |
| PRD-DETAIL-001 | 맛집 기본 정보와 방문 콘텐츠 | Restaurant | Region, FoodCategory, Visit, Creator, Video |
| PRD-ADMIN-001 | 인증된 관리자 검증·등록 | AdminAccount, AdminRefreshToken | Restaurant, Creator, Video, Visit |

## 3. 기능 요구사항 → 데이터 개념 매핑

| 요구사항 | 데이터 모델 반영 | 주요 제약·파생 |
|---|---|---|
| FR-RESTAURANT-001·002·005~007 | Restaurant | publication 필터, 이름 검색, 고유 결과·정렬·페이지는 조회 책임 |
| FR-RESTAURANT-003·009 | Region, Restaurant | 서울 자치구 1개 참조 |
| FR-RESTAURANT-004·010 | FoodCategory, Restaurant | 대표 카테고리 정확히 1개 |
| FR-RESTAURANT-008·011 | Restaurant | Visit 없이 기본 상세 조회 |
| FR-CREATOR-001·002 | Creator, Visit, Video | 공개·유효 관계와 채널 일치, 중복 제거 |
| FR-CREATOR-003 | Creator | 공개 Creator 최소 선택 정보 |
| FR-VIDEO-001 | Video, Creator, Visit | 공개 관련 영상, 외부 장애 격리 |
| FR-ADMIN-001 | AdminAccount, AdminRefreshToken | 사전 발급 활성 계정, JWT·Refresh 회전·ADMIN 권한 |
| FR-ADMIN-002 | Restaurant, Region, FoodCategory | 카카오 동일성, 서울 주소, 단일 카테고리, 원자적 공개 생성 |
| FR-ADMIN-003 | Creator | 외부 채널 ID 유일, 채널 단위 생성 |
| FR-ADMIN-004 | Video, Creator | 외부 영상 ID 유일, 게시 채널 필수, 원본 미저장 |
| FR-VISIT-001 | Visit, Restaurant, Creator, Video | 세 참조·실제 근거·채널 일치·복합 유일·원자성 |

## 4. 비즈니스 규칙 → 제약조건 매핑

| 규칙 ID | 규칙 | 데이터 모델 반영 | 저장소 제약 | 애플리케이션 검증 |
|---|---|---|---:|---:|
| BR-RESTAURANT-002 | 영상과 독립된 맛집 | Restaurant와 Visit 선택 관계 | 참조 방향 | 필요 |
| BR-RESTAURANT-003~005 | 최소 정보·카테고리·지역 | 필수 속성, Region·FoodCategory 1개 | 필요 | 필요 |
| BR-RESTAURANT-006·007 | 카카오 동일성·지점 구분 | kakaoPlaceIdentity 유일, 이름 비유일 | 필요 | 필요 |
| BR-CREATOR-001~003 | 채널 관리 단위·최소 정보·중복 | externalChannelId 유일 | 필요 | 필요 |
| BR-CREATOR-005 | Visit 채널 일치 | Video 게시 Creator와 Visit.Creator 일치 | 후속 설계 | 필요 |
| BR-VIDEO-001~003 | 원본 미저장·필수 메타·중복 | Video 메타, externalVideoId 유일 | 필요 | 필요 |
| BR-VIDEO-004·005 | 다대상·실제 방문 | Video 1:N Visit, 생성 전 확인 | 참조 필요 | 필요 |
| BR-VIDEO-006 | 게시일·방문일 구분 | Visit 방문일 없음, Video 게시일 선택 | 해당 없음 | 필요 |
| BR-VISIT-001~004 | 삼항 구성·근거·중복·범위 | 세 필수 참조, 복합 유일 | 필요 | 필요 |
| BR-VISIT-005·BR-PUBLICATION-001~008 | 조회 공개 유효성 | 개별 publication·외부 상태 | 허용값 필요 | 필요 |
| BR-VISIT-006·007 | 방문일·검증 상태 제외 | 속성 미생성, 생성 완료가 검증 완료 | 해당 없음 | 필요 |
| BR-ADMIN-003·007 | 정합성·동시성 | 유일·참조·원자성 | 필요 | 필요 |
| BR-ADMIN-008 | 보류 요청 | 핵심 엔티티·보류 레코드 미생성 | 해당 없음 | 필요 |

## 5. API 요청 → 데이터 변경 매핑

| API ID | 요청 목적 | 생성·변경 데이터 | 필수 참조 | 원자성 범위 | 담당 Workstream |
|---|---|---|---|---|---|
| API-ADMIN-AUTH-001 | 관리자 로그인 | AdminRefreshToken 생성·기존 활성 Token 폐기 | AdminAccount | 계정당 활성 Token 전환 | WS-04 |
| API-ADMIN-AUTH-002 | 토큰 재발급 | 기존 Token 폐기·AdminRefreshToken 회전 | AdminAccount, AdminRefreshToken | 검증·회전 원자성 | WS-04 |
| API-ADMIN-AUTH-003 | 로그아웃 | AdminRefreshToken 폐기 | AdminRefreshToken | 현재 Token 하나 | WS-04 |
| API-ADMIN-RESTAURANT-PREVIEW-001 | 외부 장소·입력 검증 | 핵심 데이터 변경 없음 | Region, FoodCategory 기준 | 토큰 발급 여부만 일관 | WS-04 |
| API-ADMIN-RESTAURANT-001 | 맛집 생성 | Restaurant와 필수 참조 연결 | Region, FoodCategory | Restaurant 한 건 전체 | WS-04 / Restaurant |
| API-ADMIN-CREATOR-PREVIEW-001 | 외부 채널 검증 | 핵심 데이터 변경 없음 | 없음 | 토큰 발급 여부만 일관 | WS-04 |
| API-ADMIN-CREATOR-001 | Creator 생성 | Creator | 없음 | Creator 한 건 전체 | WS-04 / Creator |
| API-ADMIN-VIDEO-PREVIEW-001 | 외부 영상·게시 채널 검증 | 핵심 데이터 변경 없음 | 게시 채널 후보 | 토큰 발급 여부만 일관 | WS-04 |
| API-ADMIN-VIDEO-001 | Video 생성 | Video와 게시 채널 외부 식별 | 없음(내부 Creator 연결 선택) | Video 한 건 전체 | WS-04 / Video |
| API-ADMIN-VISIT-001 | 방문 관계 생성 | Visit | Restaurant, Creator, Video | 검증·복합 중복·저장 전체 | WS-04 / Visit |

확인 토큰은 10분 만료와 후보 무결성을 제공하지만 서버 저장 레코드인지 서명된 단기 값인지는 후속 기술 설계 대상이다. `REVIEW_REQUIRED`는 등록 요청으로 저장하지 않는다.

## 6. API 응답 → 데이터 조회 매핑

| API ID | 응답 영역 | 주 데이터 | 조합 데이터 | 파생 필드 | 조회 책임 |
|---|---|---|---|---|---|
| API-DISCOVERY-001 | 맛집 목록 | Restaurant | Region, FoodCategory, 공개 Visit·Creator | `visitedBy` 최대 3명, `remainingVisitedByCount`, page | WS-01, 관계 판정 WS-03 |
| API-CREATOR-DISCOVERY-001 | 유튜버 선택 목록 | Creator | 없음 | 채널명 정렬 | WS-03 |
| API-DETAIL-001 | 맛집 기본 정보 | Restaurant | Region, FoodCategory | address DTO | WS-02 |
| API-DETAIL-001 | 방문 유튜버 | Visit | Creator, Video 공개 유효성 | Creator 식별자 중복 제거 | WS-02, 판정 Visit |
| API-DETAIL-001 | 관련 영상 | Visit | Video, Creator | Video 식별자 중복 제거, `contentStatus` | WS-02 |
| API-ADMIN-*-PREVIEW-001 | 후보·중복 판정 | 외부 확인 결과 | 기존 핵심 데이터 | `decision`, token, expiry, candidate DTO | WS-04 |
| API-ADMIN-*-001 | 생성 결과 | 생성 엔티티 | 표준 표시값 | 응답 DTO 조합 | WS-04와 소유 도메인 |
| API-ADMIN-AUTH-001·002 | 인증·재발급 | AdminRefreshToken | AdminAccount 활성 여부 | JWT Access Token, 만료 시간 | WS-04 |

`contentStatus`, 페이지 메타데이터, 후보 `decision`, `remainingVisitedByCount`는 엔티티에 저장하지 않는다.

## 7. Workstream → 데이터 소유권 매핑

| Workstream | 변경 소유 | 조회·의존 데이터 | 책임 경계 |
|---|---|---|---|
| WS-01 맛집 탐색 | Restaurant 조회 규칙 | Region, FoodCategory, Visit·Creator 판정 결과 | Visit 규칙을 재구현하지 않음 |
| WS-02 상세 및 콘텐츠 | 상세 조합 | Restaurant, Visit, Creator, Video | 기본 데이터와 관계를 임의 변경하지 않음 |
| WS-03 유튜버 기반 탐색 | Visit 관계 판정 계약 | Creator, Video, Restaurant 상태 | 최종 Restaurant 페이지 조합은 WS-01 |
| WS-04 관리자 등록 | 인증·등록 흐름 조율 | AdminAccount·AdminRefreshToken 및 네 소유 도메인 | 도메인 고유·정합성 규칙을 우회하지 않음 |

## 8. 미매핑 항목

- Restaurant 설명·대표 이미지·영업 정보는 확정 요구사항/API가 없어 저장 모델에서 제외했다.
- Creator 프로필 이미지, Video 게시일의 외부 API 노출, Visit 방문일·검증 상태·검증자는 MVP에서 제외하거나 선택 데이터다.
- 수정·삭제·승인·보류 목록 API가 없으므로 관련 운영 전환은 API 변경으로 만들지 않았다.
- 로그인 실패 제한 카운터와 확인 토큰 저장 방식은 API 동작 요구는 있으나 핵심 ERD 저장 모델로 확정하지 않았다.

## 9. 변경 영향 추적

- 지역 단계·범위 변경: Region, Restaurant, 탐색/등록 API와 BR-RESTAURANT-005를 함께 검토한다.
- 다중 카테고리 변경: Restaurant–FoodCategory 카디널리티, 필터 API와 BR-RESTAURANT-004를 함께 변경한다.
- Creator를 개인 단위로 변경: Creator·Video·Visit 식별과 모든 유튜버 API를 재설계한다.
- 복수 근거·방문일 도입: Visit 모델, 복합 유일성, 관리자 요청과 상세 응답을 재검토한다.
- 공개·삭제 정책 변경: 네 핵심 데이터, 모든 공개 조회와 운영 정정 흐름을 함께 검토한다.
- 외부 동기화 도입: Creator·Video 상태·이력, 외부 호출 NFR과 운영 책임을 추가한다.
