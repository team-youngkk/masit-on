---
related_documents:
  - ../../01-requirements/business-rules.md
  - data-model.md
  - entity-definitions.md
  - lifecycle-rules.md
  - constraints.md
  - ../api/discovery/creator-discovery-api.md
  - ../api/detail/restaurant-detail-api.md
  - ../api/admin/visit-registration-api.md
  - ../diagrams/erd-spec.md
---

# 맛잇온 데이터 관계 규칙

## 1. 문서 목적

핵심 데이터의 논리 관계, 카디널리티, 필수성, 생성·해제와 조회 영향을 정의한다. 구현상의 양방향 객체 참조나 테이블 배치는 정하지 않는다.

## 2. 관계 요약

| 관계 | 카디널리티 | 관계 소유 | 핵심 제약 |
|---|---|---|---|
| Region–Restaurant | 1 : N | Restaurant | Restaurant당 자치구 1개 |
| FoodCategory–Restaurant | 1 : N | Restaurant | Restaurant당 대표 카테고리 1개 |
| Creator–Video | Creator 1 : N, Video 0..1 : Creator | Video | 외부 게시 채널은 필수, 내부 Creator 연결은 선택 |
| Restaurant–Visit | 1 : N | Visit | Visit의 Restaurant 필수 |
| Creator–Visit | 1 : N | Visit | Visit의 Creator 필수 |
| Video–Visit | 1 : N | Visit | Visit의 근거 Video 필수 |
| AdminAccount–AdminRefreshToken | 1 : N | 관리자 JWT 재발급 | 활성 Refresh Token 최대 1개 |

## 3. Restaurant–Region

### 관계

- Region 1 : N Restaurant

### 의미와 필수성

- Restaurant는 전체 도로명주소가 속한 서울특별시 자치구 정확히 1개를 참조한다.
- Region은 Restaurant 없이도 기준값으로 존재할 수 있다.
- MVP에는 Region 상위·하위 관계가 없다.

### 생성·중복·상태 규칙

- 도로명주소에서 판정한 활성 Region만 신규 Restaurant에 연결한다.
- Region 이름은 표준값으로 유일하다.
- Region 비활성화는 기존 Restaurant의 지역 의미를 지우지 않으며 신규 연결만 막는다.

### 관련 규칙

- [BR-RESTAURANT-003](../../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보)·[BR-RESTAURANT-005](../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속)

## 4. Restaurant–FoodCategory

### 관계

- FoodCategory 1 : N Restaurant

### 의미와 필수성

- Restaurant는 사전 정의된 대표 FoodCategory 정확히 1개를 참조한다.
- 복수 카테고리와 Restaurant–FoodCategory 관계 엔티티는 MVP에 없다.

### 생성·상태 규칙

- 활성 FoodCategory만 신규 Restaurant에 연결한다.
- `기타`이면 Restaurant에 구체 음식 종류를 함께 기록한다.
- 비활성화해도 기존 Restaurant 관계는 자동 삭제하지 않는다.

### 관련 규칙

- [BR-RESTAURANT-004](../../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리)

## 5. Creator/Channel–Video

### 관계

- Creator 1 : N Video
- Video는 등록 시점에 일치하는 내부 Creator를 0개 또는 1개 참조한다.

### 의미

Creator는 YouTube 채널 단위이고 Video는 외부 게시 채널 ID를 필수로 가진 원본 영상이다. API가 Creator와 Video의 등록 순서를 강제하지 않으므로 내부 참조는 외부 채널 ID가 일치하는 Creator가 존재할 때만 해소한다.

### 필수성 및 생성 조건

- Video는 정확히 하나의 외부 게시 채널 ID를 저장한다.
- 내부 Creator를 연결하면 영상 미리보기에서 확인한 외부 게시 채널과 Creator.externalChannelId가 같아야 한다.
- Creator는 Video 없이 존재할 수 있다.

### 삭제 또는 비공개 영향

- Creator가 비공개·삭제 상태이면 그 Creator와 관련 Visit를 공개 조회에서 제외한다.
- Creator 상태 변경이 Video 또는 Visit를 자동 삭제하지 않는다.
- 외부 채널 이용 불가 상태와 서비스 공개 상태를 분리한다.

### 관련 규칙

- [BR-CREATOR-001](../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)·[BR-CREATOR-003](../../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단)·[BR-CREATOR-005](../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치)·[BR-CREATOR-007](../../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리), [BR-VIDEO-002](../../01-requirements/business-rules.md#br-video-002-영상-최소-등록-정보)·[BR-VIDEO-003](../../01-requirements/business-rules.md#br-video-003-영상-식별-및-중복-판단)·[BR-VIDEO-009](../../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리)

## 6. Restaurant–Visit

### 관계

- Restaurant 1 : N Visit

### 의미

하나의 맛집은 여러 채널의 여러 영상에서 실제 방문될 수 있다.

### 필수성

- Visit는 반드시 하나의 Restaurant를 참조한다.
- Restaurant는 Visit 없이 존재하고 목록·상세에 노출될 수 있다.

### 생성 조건

- Restaurant가 존재하고 공개 상태여야 한다.
- Creator·Video와 함께 실제 방문 근거 규칙을 만족해야 한다.

### 중복 기준

- Restaurant만으로 중복을 판단하지 않고 Restaurant·Creator·Video 전체 조합을 사용한다.

### 삭제 또는 비공개 영향

- Restaurant가 비공개·삭제 상태이면 그 Restaurant와 관련 콘텐츠 맥락 전체를 일반 조회에서 제외한다.
- Restaurant 상태 변경이 Visit를 물리 삭제하는지는 후속 삭제 전략에서 결정한다.

### 관련 규칙

- [BR-RESTAURANT-002](../../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집)·[BR-RESTAURANT-008](../../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건), [BR-VISIT-001](../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-005](../../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성), [BR-PUBLICATION-003](../../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출)

## 7. Creator/Channel–Visit

### 관계

- Creator 1 : N Visit

### 의미와 필수성

- Visit는 실제 방문 주체인 채널 단위 Creator 하나를 반드시 참조한다.
- Creator는 Visit 없이 존재할 수 있다.
- Visit.Creator.externalChannelId는 Visit.Video.publisherExternalChannelId와 같아야 하며, Video.creatorId가 있으면 같은 Creator여야 한다.

### 삭제 또는 비공개 영향

- Creator가 비공개·삭제이거나 외부 이용 불가가 관리자에 의해 확인돼 비공개 전환되면 관련 Visit를 유튜버 필터와 상세 콘텐츠에서 제외한다.
- 공개 Restaurant 기본 정보는 유지한다.

### 관련 규칙

- [BR-CREATOR-001](../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)·[BR-CREATOR-005](../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치)·[BR-CREATOR-007](../../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리), [BR-PUBLICATION-004](../../01-requirements/business-rules.md#br-publication-004-유튜버-상태와-관계-노출)

## 8. Video–Visit

### 관계

- Video 1 : N Visit

### 의미와 필수성

- Visit는 실제 방문을 확인할 수 있는 Video 하나를 반드시 참조한다.
- 한 Video는 영상에 실제로 등장한 여러 Restaurant에 대해 별도 Visit의 근거가 될 수 있다.
- Video는 Visit 없이 근거 후보로 존재할 수 있다.

### 중복 및 재방문

- 같은 Video와 Creator가 여러 Restaurant를 방문하면 Restaurant별 Visit를 만든다.
- 같은 Restaurant와 Creator라도 다른 Video면 별도 Visit를 만들 수 있다. 이는 현재 모델에서 재방문 또는 별도 근거를 구분하는 방식이며 방문일은 저장하지 않는다.

### 삭제 또는 비공개 영향

- 외부 영상 이용 불가 또는 서비스 비공개가 확인되면 그 Video와 해당 Video에만 근거한 Visit를 일반 조회에서 제외한다.
- Video·Visit·Restaurant를 자동 삭제하지 않는다.

### 관련 규칙

- [BR-VIDEO-004](../../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결)~[BR-VIDEO-007](../../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리)·[BR-VIDEO-009](../../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리), [BR-VISIT-001](../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-006](../../01-requirements/business-rules.md#br-visit-006-방문-날짜-관리-제외), [BR-PUBLICATION-005](../../01-requirements/business-rules.md#br-publication-005-영상-상태와-관계-노출)·[BR-PUBLICATION-007](../../01-requirements/business-rules.md#br-publication-007-외부-영상-삭제의-영향-범위)

## 9. 관계 생성 및 해제 규칙

- Visit 생성은 세 참조 존재, 모두 공개, 채널 일치, 실제 방문 확인과 중복 부재를 하나의 트랜잭션에서 검증한다.
- `visitEvidenceConfirmed=true`는 저장 속성이 아니라 생성 명령의 관리자 선언이다.
- 생성 성공한 Visit는 즉시 공개된다.
- MVP는 관계 수정·해제·삭제 API를 제공하지 않는다. 잘못된 관계는 우선 공개 조회에서 제외하고 후속 운영 절차로 정정한다.

## 10. 관계 삭제 영향

- 참조 대상 삭제를 물리 연쇄 삭제로 구현한다고 결정하지 않는다.
- 공개 조회는 Restaurant·Creator·Video·Visit 네 대상의 상태를 모두 확인한다.
- Creator·Video·Visit만 비공개이면 공개 Restaurant 기본 정보는 유지한다.
- 실제 삭제, 보관과 복구 방식은 ADR 및 물리 설계 대상이다.

## 11. 카디널리티 검토

### Visit 모델 비교

| 기준 | A. 삼항 Visit | B. VideoAppearance | C. Visit + VisitEvidence |
|---|---|---|---|
| 현재 규칙 적합성 | 세 대상 조합과 정확히 일치 | Creator가 Video에서 파생돼 API 자원명과 차이 | 한 방문의 복수 근거를 전제 |
| 한 영상·여러 맛집 | Visit 여러 건 | 자연스러움 | Evidence 여러 건 필요 |
| 동일 채널 재방문 | 다른 Video로 구분 | 다른 VideoAppearance로 구분 | 방문 식별 기준 추가 필요 |
| 한 방문·복수 영상 | 직접 표현 불가 | 직접 표현 불가 | 표현 가능 |
| 중복 판단 | 세 참조 복합 유일 | Restaurant·Video 복합 유일 | Visit와 Evidence 각각 기준 필요 |
| 관리자 흐름·조회 | 현재 API와 일치, 단순 | Creator 입력이 중복되어 보일 수 있음 | 요청·트랜잭션 복잡도 증가 |
| 데이터 정합성 | 채널 일치 제약 필요 | Video 게시 채널로 일치 자동 도출 | Evidence와 Visit 채널 일치 필요 |

### 권장 모델

- 선택: A, 삼항 Visit
- 이유: [BR-VISIT-001](../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)·[BR-VISIT-003](../../01-requirements/business-rules.md#br-visit-003-방문-관계-중복-판단)과 [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록)이 Restaurant·Creator·Video 하나씩을 명시하고 동일 세 대상 조합을 중복으로 확정했다.
- 감수하는 제약: 실제 하나의 방문 사실에 여러 근거 영상을 묶을 수 없고, 다른 영상은 별도 Visit로 표현된다.
- 변경 조건: 한 방문의 복수 근거, 영상 없는 검증된 방문, 방문일 기반 재방문 이력 또는 채널과 실제 출연자 분리가 범위에 들어올 때 C 또는 새 모델을 검토한다.
- ADR 필요 여부: 현재는 확정 비즈니스·API 계약을 그대로 명세하므로 필수 ADR이 아니다. 변경 시 장기 구조 영향이 크므로 ADR을 작성한다.

## 12. 검토 필요 항목

- 물리 삭제 시 참조 제한·보관·복구 정책
- 카카오 외부 장소 동일성의 물리 키
- publication/lifecycle 상태 값과 운영 전환 수단
- 향후 복수 방문 근거·방문일 도입 시 Visit 식별 기준
