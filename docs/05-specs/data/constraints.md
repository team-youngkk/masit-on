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
  - ../api/discovery/restaurant-discovery-api.md
  - ../api/admin/authentication-api.md
  - ../../01-requirements/non-functional-requirements.md
---

# 맛잇온 데이터 제약조건

## 1. 문서 목적

논리 모델의 필수값, 고유성, 참조 무결성, 상태, 중복과 원자성 요구를 저장소와 애플리케이션 책임으로 나눈다. SQL 구문과 구체 인덱스는 정의하지 않는다.

## 2. 필수값 제약

- Restaurant: 내부 식별자, 이름, 카카오 장소 동일성·링크, 전체 도로명주소, 전화번호, Region, FoodCategory, publication status가 필수다. 상세 주소는 선택이다. `기타` 카테고리에는 구체 음식 종류가 필수다.
- Region·FoodCategory: 내부 식별자, 표준 이름과 활성 상태가 필수다.
- Creator: 내부 식별자, 외부 채널 ID, 채널명, 채널 URL, publication status와 외부 가용 상태가 필수다.
- Video: 내부 식별자, 외부 영상 ID, 제목, 원본 URL, 썸네일 URL, 게시 채널 외부 ID, publication status와 외부 가용 상태가 필수다. 내부 Creator 참조와 게시일은 선택이다.
- Visit: 내부 식별자, Restaurant·Creator·Video와 publication status가 필수다. 방문일·검증 상태·검증자·검증 시각은 저장하지 않는다.
- AdminAccount: 내부 식별자, 로그인 ID, 안전한 비밀번호 자격 증명, 활성 여부가 필수다.
- AdminRefreshToken: 계정 참조, 토큰·계열 검증 값과 생성·만료 시각이 필수다.

## 3. 유일성 제약

### DATA-CONSTRAINT-001 Restaurant 외부 장소 동일성

- 적용 데이터: Restaurant
- 제약: 카카오에서 같은 장소로 확인되는 Restaurant는 하나만 존재한다. 이름은 유일하지 않다.
- 보장 수준: 저장소와 애플리케이션 모두. 저장소 키의 구체 표현은 물리 설계 전 확정한다.
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

## 4. 참조 무결성

### DATA-CONSTRAINT-007 핵심 참조 존재

- Restaurant는 존재하는 Region과 FoodCategory를 참조한다.
- Video.creatorId가 있으면 존재하는 Creator를 참조한다. Creator와 Video의 기본 등록 순서는 강제하지 않는다.
- Visit는 존재하는 Restaurant, Creator, Video를 참조한다.
- AdminRefreshToken은 존재하는 AdminAccount를 참조한다.
- 보장 수준: 저장소 수준 필수
- 관련 요구사항: [NFR-INTEGRITY-001](../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)

참조 대상을 비공개·비활성으로 새로 연결할 수 있는지는 저장소 참조 제약만으로 충분하지 않으므로 애플리케이션에서 검증한다. Visit는 세 참조가 모두 공개일 때만 생성한다. 비활성 Region·FoodCategory는 신규 Restaurant에 연결하지 않는다.

### DATA-CONSTRAINT-008 Video·Creator 게시 채널 일치

- 적용 데이터: Video, Creator, Visit
- 제약: Video.creatorId가 있으면 외부 게시 채널 ID와 Creator.externalChannelId가 같고, Visit.Creator.externalChannelId는 Visit.Video.publisherExternalChannelId와 같아야 한다.
- 보장 수준: 애플리케이션 검증 필수. 저장소에서 복합 참조로 보장할지는 후속 물리 설계에서 결정한다.
- 위반 시 처리: `VIDEO_CHANNEL_MISMATCH`
- 관련 규칙/API: [BR-CREATOR-005](../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치), [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록)

## 5. 상태 제약

- publication status는 허용된 값만 사용하고 생성 성공한 Restaurant·Creator·Video·Visit는 PUBLIC이다.
- 비공개·삭제 상태는 일반 조회에 노출하지 않는다.
- Creator·Video의 external availability는 publication status와 별도 값이다.
- 외부 일시 오류만으로 external availability나 publication status를 자동 변경하지 않는다.
- 삭제·보관 상태 값과 전환은 후속 설계에서 확정한다.

## 6. 중복 방지

- 저장 전 외부 식별·조합을 검증해 사용자에게 의미 있는 중복 결과를 제공한다.
- 최종 생성 시점에도 저장소 고유성 제약을 적용해 미리보기 이후 동시 등록을 막는다.
- 중복이면 새 데이터를 만들지 않고 기존 자원 식별자를 사용한다.
- 동일성 판단이 불가능한 `REVIEW_REQUIRED`는 확인 토큰과 자원을 생성하지 않는다.

## 7. 트랜잭션 원자성 요구사항

- 각 기본 데이터 생성 요청은 해당 엔티티와 필수 관계·상태를 한 원자적 범위에서 만든다.
- Visit 생성은 세 참조·채널 일치·공개 상태·중복 확인과 관계 저장을 한 원자적 범위에서 처리한다.
- 실패 시 부분 데이터나 부분 공개 상태가 남지 않는다.
- 검증 미리보기는 핵심 엔티티를 생성하지 않는다. 확인 토큰의 소비와 생성 요청 재사용 방지는 토큰 구현 방식과 함께 후속 설계한다.
- 서로 별도인 맛집·Creator·Video 등록을 하나의 거대 트랜잭션으로 묶지 않는다.

## 8. 애플리케이션 검증과 저장소 제약의 구분

| 규칙 | 저장소 제약 | 애플리케이션 검증 | 이유 |
|---|---:|---:|---|
| 필수 속성 누락 방지 | 필요 | 필요 | 정합성과 오류 메시지 모두 필요 |
| 카카오 동일 장소 중복 금지 | 필요 | 필요 | 동시성과 외부 사실 판정 모두 필요 |
| 외부 채널·영상 ID 중복 금지 | 필요 | 필요 | 동시 등록과 외부 확인 모두 필요 |
| Visit 세 참조 존재 | 필요 | 필요 | 참조 무결성과 대상별 404 처리 |
| Visit 세 참조 복합 중복 금지 | 필요 | 필요 | 동시 등록과 409 처리 |
| Video 게시 채널·선택 Creator·Visit.Creator 일치 | 후속 설계 | 필요 | 독립 등록 순서, 외부 확인과 교차 엔티티 정책 |
| 참조 대상 공개·참조 데이터 활성 | 불충분 | 필요 | 상태 시점 정책 |
| 실제 방문 장면 확인 | 불가 | 필요 | 관리자 업무 판단 |
| `기타` 구체 이름 조건부 필수 | 후속 설계 | 필요 | 교차 필드 조건과 오류 설명 |
| publication 허용값 | 필요 | 필요 | 값 무결성과 전환 정책 |
| 계정당 활성 Refresh Token 최대 1개 | Redis·애플리케이션 | 필요 | 키·회전·무효화 전략에 의존 |

## 9. 동시성 검토

- Restaurant 외부 장소 키, Creator 외부 채널 ID, Video 외부 영상 ID, Visit 세 참조 조합에 최종 고유성 보장이 필요하다.
- 애플리케이션의 선조회만으로 중복을 막지 않는다.
- 고유성 충돌은 기존 자원을 반환할 수 있는 도메인 중복 오류로 변환한다.
- 락, 격리 수준, upsert, 재시도와 확인 토큰 단일 사용 방식은 데이터베이스 제품 선택 후 ADR 또는 물리 설계에서 결정한다.

## 10. 검토 필요 항목

- Kakao 장소 동일성 키와 정규화 규칙
- Video–Creator와 Visit–Creator 일치를 저장소에서도 강제할 물리 구조
- publication/lifecycle 상태의 정확한 값과 조건부 제약
- Redis에서 계정당 활성 Refresh Token 하나와 회전·재사용 탐지를 보장하는 방법
- 확인 토큰 단일 사용·만료·변조 방지 방식
- URL 보조 유일성, 대소문자·공백 정규화와 구체 인덱스
