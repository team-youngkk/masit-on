# 맛잇온 데이터 엔티티 정의

## 공통 규칙

- 모든 핵심 데이터는 외부 식별자와 분리된 불투명 내부 식별자를 가진다. 구체 타입은 후속 설계에서 결정한다.
- publication status는 `PUBLIC`과 일반 사용자 비노출 상태를 구분한다. 삭제·보관 상태의 물리 표현은 별도 검토한다.
- `createdAt`, `updatedAt`은 공통 감사 속성 후보이며 정확한 적용 범위와 변경자 이력은 후속 설계에서 결정한다.
- API 응답의 집계·축약·조합 필드는 엔티티 속성이 아니다.

## Restaurant

### 정의

카카오에서 한 장소로 확인되어 서비스에 등록된 음식점 지점이다. 영상이나 Visit 없이 독립적으로 존재하고 조회될 수 있다.

### 소유 도메인

- Restaurant

### 식별 기준

- 내부 식별자: API와 내부 관계에서 사용하는 서비스 식별자
- 업무 식별 기준: 카카오에서 확인한 동일 장소
- 외부 식별자: 카카오 장소 동일성을 안정적으로 나타내는 값. 구체 추출·정규화 방식은 검토 필요
- 이름은 식별자가 아니며 같은 상호의 다른 지점을 허용한다.

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 변경 가능 여부 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 불가 | 공개 | 타입 미확정 |
| name | 맛집 표시 이름 | 필수 | 단독 유일 아님 | 후속 관리 기능 | 공개 | 동일 상호 허용 |
| kakaoPlaceIdentity | 카카오 동일 장소 판정값 | 필수 | 유일 필요 | 원칙상 불가 | 비공개 | 물리 표현 검토 필요 |
| kakaoPlaceUrl | 카카오 장소 링크 | 필수 | 단독 유일성에 의존하지 않음 | 후속 관리 기능 | 공개 | HTTPS 허용 호스트 검증 |
| roadAddress | 서울특별시 전체 도로명주소 | 필수 | 단독 유일 아님 | 후속 관리 기능 | 공개 | 지점 구분 보조 정보 |
| detailAddress | 건물명·층·호 등 상세 위치 | 선택 | 아님 | 후속 관리 기능 | 공개 | 없으면 null |
| phoneNumber | 확인된 전화번호 | 필수 | 아님 | 후속 관리 기능 | 공개 | 현재 API 필수 |
| otherCategoryName | `기타` 카테고리의 구체 음식 종류 | 조건부 필수 | 아님 | 후속 관리 기능 | 검토 필요 | `기타`가 아니면 없음 |
| publicationStatus | 사용자 공개 여부 | 필수 | 아님 | 운영 정정 시 | 비공개 | 생성 성공 시 PUBLIC |
| lifecycleStatus | 활성·삭제/보관 구분 | 검토 필요 | 아님 | 가능 | 비공개 | 구현·값 미확정 |
| createdAt / updatedAt | 생성·변경 시각 | 공통 요구 | 아님 | 시스템 | 비공개 | API 미노출 |

설명, 대표 이미지 URL, 영업 정보는 확정 요구사항과 API에 없으므로 저장 속성으로 추가하지 않는다. 지점명은 필수값이 아니며 카카오 장소와 주소로 지점을 구분한다.

### 관계

- Region 정확히 1개를 참조한다.
- FoodCategory 정확히 1개를 참조한다.
- Visit 0개 이상에서 참조될 수 있다.

### 생성 규칙

- 필수 정보, 서울 주소, 카카오 장소 일치, Region과 FoodCategory 유효성을 검증한다.
- 검증 미리보기 `READY`를 관리자가 확인한 한 요청에서 원자적으로 생성한다.

### 변경 규칙

- MVP API는 수정 기능을 제공하지 않는다. 이름·주소 변경과 장소 이전은 BR-RESTAURANT-009·010에 따라 후속 관리 기능에서 재검증한다.

### 공개 규칙

- 등록 성공 시 즉시 PUBLIC이다. 필수 정보가 유효하고 publication status가 공개일 때만 일반 조회에 포함한다.

### 삭제 또는 비활성화 규칙

- MVP 삭제 API는 없다. 비공개 또는 삭제 상태가 되면 Restaurant와 그 상세 맥락을 일반 조회에서 제외하되 외부 영상 상태 때문에 자동 삭제하지 않는다.

### 중복 판단

- 카카오에서 같은 장소면 중복이다. 이름·주소 문자열만으로 자동 병합하지 않는다.

### 관련 요구사항

- FR-RESTAURANT-001~011, FR-ADMIN-002

### 관련 비즈니스 규칙

- BR-RESTAURANT-001~011, BR-PUBLICATION-001~003·007·008

### 관련 API

- API-DISCOVERY-001, API-DETAIL-001, API-ADMIN-RESTAURANT-PREVIEW-001, API-ADMIN-RESTAURANT-001

## Region

### 정의

Restaurant 등록과 지역 필터에 공통으로 사용하는 서울특별시 자치구 표준 참조 데이터다. 독립 도메인은 아니다.

### 소유 도메인

- Restaurant

### 식별 기준

- 내부 식별자: Restaurant 관계용 식별자
- 업무 식별 기준: 서울특별시 자치구의 표준 이름
- 외부 식별자: 없음

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 변경 가능 여부 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 불가 | 비공개 | API는 이름을 사용 |
| name | 자치구 표준 이름 | 필수 | 유일 | 기준 변경 시 | 공개 | 서울 자치구만 허용 |
| code | 별도 업무 코드 | 검토 필요 | 사용 시 유일 | 기준 변경 시 | 비공개 | API 계약에는 없음 |
| sortOrder | 선택 목록 정렬 | 선택 | 아님 | 가능 | 비공개 | 현재 API 정렬 계약 없음 |
| active | 신규 연결 허용 여부 | 필수 | 아님 | 가능 | 비공개 | 기존 Restaurant 관계 유지 |

### 관계 및 규칙

- Region 1 : N Restaurant. Restaurant는 정확히 하나를 참조한다.
- 계층·상위 Region은 MVP에 두지 않는다.
- 비활성 Region은 신규 Restaurant에 연결하지 않으며 기존 데이터의 주소 의미는 유지한다.

### 관련 요구사항·규칙·API

- FR-RESTAURANT-003·009, FR-ADMIN-002
- BR-RESTAURANT-003·005
- API-DISCOVERY-001, API-DETAIL-001, API-ADMIN-RESTAURANT-PREVIEW-001

## FoodCategory

### 정의

Restaurant의 주된 메뉴와 영업 정체성을 나타내는 사전 정의된 대표 음식 카테고리다.

### 소유 도메인

- Restaurant

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 변경 가능 여부 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 불가 | 비공개 | 관계용 |
| name | API에 노출되는 표준 카테고리 | 필수 | 유일 | 범위 변경 시 | 공개 | 확정 10개 값 |
| code | 별도 업무 코드 | 검토 필요 | 사용 시 유일 | 기준 변경 시 | 비공개 | API 계약에는 없음 |
| sortOrder | 선택 목록 정렬 | 선택 | 아님 | 가능 | 비공개 | 현재 계약 없음 |
| active | 신규 연결 허용 여부 | 필수 | 아님 | 가능 | 비공개 | 기존 관계 유지 |

### 관계 및 규칙

- FoodCategory 1 : N Restaurant. Restaurant는 정확히 하나를 참조한다.
- Restaurant–FoodCategory 다중 관계 엔티티는 만들지 않는다.
- `기타`일 때 Restaurant.otherCategoryName이 필요하다.
- 관련 항목: FR-RESTAURANT-004·010, FR-ADMIN-002, BR-RESTAURANT-004, API-DISCOVERY-001·DETAIL-001·ADMIN-RESTAURANT-PREVIEW-001

## Creator

### 정의

서비스에서 `유튜버`라고 표시하는 YouTube 채널 단위 콘텐츠 주체다. 개인 제작자 수와 무관하게 채널 하나를 하나의 Creator로 관리한다.

### 소유 도메인

- Creator

### 식별 기준

- 내부 식별자: API와 Visit 관계에서 사용하는 서비스 식별자
- 업무·외부 식별 기준: YouTube의 안정된 채널 식별자
- 채널명과 URL은 표시·접근 정보이며 단독 식별자가 아니다.

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 변경 가능 여부 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 불가 | 공개 | 불투명 문자열로 응답 |
| externalChannelId | YouTube 채널 고유 식별자 | 필수 | 유일 | 불가 | 비공개 | 중복 기준 |
| channelName | 현재 확인된 채널 표시 이름 | 필수 | 단독 유일 아님 | 후속 관리 기능 | 공개 | 변경돼도 동일성 유지 |
| channelUrl | 정규화된 YouTube 채널 링크 | 필수 | 보조 유일성 검토 | 가능 | 공개 | ID 유일성을 대체하지 않음 |
| publicationStatus | 서비스 공개 여부 | 필수 | 아님 | 운영 정정 시 | 비공개 | 생성 시 PUBLIC |
| externalAvailabilityStatus | YouTube 채널 가용 상태 | 필수 | 아님 | 관리자 확인 시 | 비공개 | 공개 상태와 분리 |
| lastExternalStatusCheckedAt | 마지막 외부 확인 시각 | 검토 필요 | 아님 | 시스템 | 비공개 | 자동 주기 확인은 제외 |
| createdAt / updatedAt | 생성·변경 시각 | 공통 요구 | 아님 | 시스템 | 비공개 |  |

프로필 이미지 URL은 확정 요구사항과 API에 없으므로 추가하지 않는다.

### 관계와 규칙

- Video 0개 이상을 게시한다.
- Visit 0개 이상에서 참조될 수 있다.
- 채널 이용 불가 확인 시 비공개로 전환하되 Creator·Video·Visit를 자동 삭제하지 않는다.
- 관련 항목: FR-CREATOR-001~003, FR-ADMIN-003, BR-CREATOR-001~007, API-CREATOR-DISCOVERY-001, API-DISCOVERY-001, API-DETAIL-001, API-ADMIN-CREATOR-PREVIEW-001·ADMIN-CREATOR-001

## Video

### 정의

Visit의 실제 방문 근거 후보로 등록된 YouTube 원본 콘텐츠의 서비스 메타데이터다. 영상 파일 자체는 저장하지 않는다.

### 소유 도메인

- Video

### 식별 기준

- 내부 식별자: API와 Visit에서 사용하는 서비스 식별자
- 외부 식별자: YouTube 원본 영상 ID, 유일
- 제목과 URL 문자열은 단독 동일성 기준이 아니다.

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 변경 가능 여부 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 불가 | 공개 |  |
| externalVideoId | YouTube 원본 영상 식별자 | 필수 | 유일 | 불가 | 비공개 | 중복 기준 |
| title | 확인된 영상 제목 | 필수 | 단독 유일 아님 | 후속 관리 기능 | 공개 |  |
| sourceUrl | YouTube 원본 링크 | 필수 | 보조 유일성 검토 | 가능 | 공개 | 원본 파일 아님 |
| thumbnailUrl | 확인된 썸네일 URL | 필수 | 아님 | 후속 관리 기능 | 공개 | BR-VIDEO-002 |
| publisherExternalChannelId | 외부 영상의 게시 YouTube 채널 ID | 필수 | 아님 | 원칙상 불가 | 비공개 | Creator보다 먼저 등록 가능 |
| creatorId | 일치하는 내부 Creator 참조 | 선택 | 아님 | 연결 해소 시 | 비공개 | 존재하면 외부 채널 ID가 같아야 함 |
| publishedAt | YouTube 게시 시각 | 선택 | 아님 | 외부 정보 갱신 시 | 현재 API 비공개 | 방문일과 다름 |
| publicationStatus | 서비스 공개 여부 | 필수 | 아님 | 운영 정정 시 | 비공개 | 생성 시 PUBLIC |
| externalAvailabilityStatus | 외부 영상 가용 상태 | 필수 | 아님 | 관리자 확인 시 | 비공개 | 공개 상태와 분리 |
| lastExternalStatusCheckedAt | 마지막 외부 확인 시각 | 검토 필요 | 아님 | 시스템 | 비공개 | 자동 확인 제외 |
| createdAt / updatedAt | 생성·변경 시각 | 공통 요구 | 아님 | 시스템 | 비공개 |  |

### 관계와 규칙

- 외부 게시 채널 식별은 정확히 하나가 필수다. 일치 Creator의 내부 참조는 기본 데이터 등록 순서를 강제하지 않기 위해 선택이며, 존재하면 publisherExternalChannelId와 Creator.externalChannelId가 같아야 한다.
- Visit 0개 이상에서 근거로 참조될 수 있고 한 영상이 여러 맛집의 근거가 될 수 있다.
- 등록만으로 Restaurant와 연결되지 않는다.
- 외부 이용 불가 확인 시 관계와 맛집을 자동 삭제하지 않는다.
- 관련 항목: FR-VIDEO-001, FR-ADMIN-004, BR-VIDEO-001~009, API-DETAIL-001, API-ADMIN-VIDEO-PREVIEW-001·ADMIN-VIDEO-001

## Visit

### 정의

등록된 Creator가 등록된 Restaurant를 실제 방문했다는 사실을 등록된 Video 하나로 입증하는 검증 완료 관계다.

### 소유 도메인

- Visit

### 식별 기준

- 내부 식별자: API 응답과 후속 관리에서 사용하는 식별자, 필수
- 업무 식별 기준: Restaurant·Creator·Video 내부 식별자 조합, 복합 유일

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 변경 가능 여부 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 불가 | 관리자 응답 |  |
| publicationStatus | 관계 공개 여부 | 필수 | 아님 | 운영 정정 시 | 비공개 | 생성 시 PUBLIC |
| lifecycleStatus | 활성·삭제/보관 구분 | 검토 필요 | 아님 | 가능 | 비공개 | 물리 전략 미확정 |
| createdAt / updatedAt | 생성·변경 시각 | 공통 요구 | 아님 | 시스템 | 비공개 |  |

방문일은 MVP에서 저장하지 않는다. 별도 검증 상태·검증자·검증 시각도 저장하지 않는다. `visitEvidenceConfirmed=true`는 생성 전 명령 검증값이며 Visit 속성이 아니다. 생성 완료 자체가 관리자 검증 완료를 뜻한다.

### 관계와 규칙

- Restaurant, Creator, Video를 각각 정확히 1개 참조한다.
- Creator는 Video의 게시 Creator와 같아야 한다.
- 세 대상이 존재하고 공개이며 실제 방문을 관리자가 확인한 경우에만 원자적으로 생성한다.
- 같은 세 대상 조합은 중복이며, 같은 Restaurant·Creator라도 Video가 다르면 별도 Visit를 허용한다.
- 관련 항목: FR-VISIT-001, BR-CREATOR-005, BR-VIDEO-004~006, BR-VISIT-001~007, API-ADMIN-VISIT-001

## AdminAccount

### 정의

MVP 관리자 인증에 사용하는 사전 발급 내부 계정이다. 일반 사용자 회원 모델과 분리된다.

### 소유 책임

- WS-04의 관리자 인증 애플리케이션 책임. 발급·회수·복구는 수동 운영이다.

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---|
| id | 내부 식별자 | 필수 | 유일 | 비공개 | API 미노출 |
| loginId | 로그인 식별자 | 필수 | 유일 | 비공개 | 존재 여부를 오류로 구분하지 않음 |
| passwordCredential | 안전하게 변환된 비밀번호 자격 증명 | 필수 | 아님 | 비공개 | 평문 저장 금지, 방식은 후속 보안 설계 |
| active | 로그인·등록 권한 활성 여부 | 필수 | 아님 | 비공개 | 동일 등록 권한만 사용 |
| createdAt / updatedAt | 발급·변경 시각 | 공통 요구 | 아님 | 비공개 |  |

회원가입, 역할 목록, 계정 관리 화면과 비밀번호 복구 데이터는 MVP에 추가하지 않는다.

### 관련 항목

- FR-ADMIN-001, BR-ADMIN-001, NFR-SECURITY-001·003, NFR-PRIVACY-002, API-ADMIN-AUTH-001~003

## AdminRefreshToken

### 정의

관리자 JWT Access Token 재발급과 폐기를 통제하기 위해 Redis 8.8에 저장하는 Refresh Token 상태다. Access Token JWT 자체는 영속 엔티티로 저장하지 않는다.

### 속성

| 속성 | 의미 | 필수 여부 | 유일성 | 공개 여부 | 비고 |
|---|---|---:|---:|---:|---|
| tokenId | 내부 토큰 식별자 | 필수 | 유일 | 비공개 | JWT 또는 쿠키 원문과 분리 |
| tokenCredential | 쿠키 Refresh Token과 대조할 안전한 검증 값 | 필수 | 유일 | 비공개 | 원문 저장·로그 금지 |
| tokenFamilyId | 회전·재사용 탐지 단위 | 필수 | 아님 | 비공개 | 재사용 시 계열 폐기 |
| createdAt | 로그인·회전 시각 | 필수 | 아님 | 비공개 |  |
| expiresAt | Refresh Token 만료 시각 | 필수 | 아님 | 비공개 | 정확한 TTL은 구현 전 확정 |
| invalidatedAt | 로그아웃·대체 로그인·재사용 탐지 폐기 시각 | 선택 | 아님 | 비공개 |  |

### 관계와 규칙

- 정확히 하나의 AdminAccount를 참조한다.
- 계정당 활성 Refresh Token은 최대 하나이며 새 로그인은 기존 Token을 폐기한다.
- 저장 매체는 Redis 8.8로 확정한다. 키 형식, 검증값 해시·암호화, 정확한 TTL, 서명 키 교체와 로그인 제한 카운터는 후속 기술 설계 대상이다.
