---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - domain-boundaries.md
  - ../03-team/roles.md
  - ../03-team/ownership.md
  - ../04-product/traceability.md
  - ../01-requirements/non-functional-requirements.md
  - ../01-requirements/requirements-review.md
---

# 맛잇온 MVP Workstream

## 1. 문서 목적

이 문서는 맛잇온 1차 MVP의 기능 요구사항과 도메인 경계를, 한 명의 최종 책임자가 사용자 가치 또는 관리자 업무를 처음부터 끝까지 완성할 수 있는 세로 단위 Workstream으로 변환한다. 이후 [roles.md](../03-team/roles.md), [ownership.md](../03-team/ownership.md), PRD, API 계약, 데이터 모델, 구현 계획과 Task 분배는 이 문서의 책임 경계와 요구사항 배정을 기준으로 구체화한다.

이 문서의 Workstream은 기술 계층이나 도메인과 일대일 대응하지 않는다. Workstream은 여러 도메인의 협업을 포함할 수 있지만 각 도메인의 데이터와 정책 소유권은 [domain-boundaries.md](domain-boundaries.md)를 따른다. 확정되지 않은 API 구조, 저장 방식과 운영 기준은 권장안 또는 검토 항목으로 구분하며 임의로 확정하지 않는다.

## 2. Workstream 분리 원칙

- 하나의 Workstream은 일반 사용자의 완결된 탐색 경험 또는 관리자의 완결된 등록 업무를 제공한다.
- 데이터 모델, API 일부, 기술 계층만을 독립 Workstream으로 만들지 않는다.
- 하나의 기능 요구사항에는 구현 완료를 책임지는 주 Workstream을 정확히 하나만 둔다.
- Workstream이 여러 도메인을 사용해도 각 도메인의 불변 조건과 변경 책임은 원래 소유 도메인에 남긴다.
- Workstream 사이에는 식별자, 상태, 표시 정보와 판정 결과를 주고받는 최소 계약만 둔다. 다른 Workstream의 내부 모델을 직접 사용하지 않는다.
- 양방향 조회 협업은 계약 의존으로 제한하고 순환 변경·구현 의존으로 만들지 않는다.
- 공통 응답, 오류, 인증 기반, 테스트 환경과 배포 기반은 기능 Workstream과 분리하되 결정 책임과 구현 책임을 명시한다.
- 테스트, 오류 처리, 계약 문서 갱신과 통합 지원을 각 Workstream의 완료 범위에 포함한다.
- 기능 완결성과 의존성 감소를 작업량 균등 배분보다 우선한다.
- 1차 MVP에서 제외된 수정·삭제, 유튜버 상세, 지도, 개인화, 추천, 관리자 확인 없는 자동 등록·자동 주기 동기화와 AI 추출 기능은 포함하지 않는다.

## 3. Workstream 구성 요약

### 3.1 권장 구성

| Workstream | 사용자 가치 | 주 도메인 | 주요 기능 | 주요 의존성 |
|---|---|---|---|---|
| [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | 이름·지역·음식 카테고리·유튜버 조건으로 공개 맛집 탐색 | Restaurant | 목록, 검색, 필터 조합, 페이지, 정렬 | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 유효 방문 관계 판정, [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터 |
| [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세 및 콘텐츠 조회 | 한 맛집의 기본 정보와 방문 유튜버·관련 영상을 한 흐름에서 확인 | Restaurant, Visit | 기본 상세, 주소·카테고리, 유튜버·영상 조합, 빈 콘텐츠 처리 | Creator·Video 표시 정보, [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터 |
| [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | 특정 유튜버가 실제 방문한 공개 맛집 탐색 | Visit | 유효 관계 기반 맛집 조회, 중복 제거, 공개 상태 적용 | Restaurant·Creator·Video 상태와 표시 정보, [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터 |
| [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 데이터 등록 | 조회에 필요한 맛집·유튜버·영상·방문 관계를 검증 순서대로 등록 | 관리자 유스케이스, Restaurant, Creator, Video, Visit | 관리자 접근, 기본 데이터 등록, 관계 등록, 중복·참조 검증 | 공통 인증 기반, 각 도메인의 등록 정책 |

권장 Workstream 수는 4개다. 이는 4명에게 수평 계층을 나누기 위한 숫자가 아니라, 네 개의 완결된 가치 흐름이 있고 각 흐름에 최종 책임자 한 명을 둘 수 있다는 분석 결과다.

### 3.2 Workstream 규모 요약

| Workstream | 기능 요구사항 수 | 주요 테스트 범위 | 예상 복잡도 | 병렬 개발 가능성 |
|---|---:|---|---|---|
| [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | 7 | 목록·검색·필터·페이지·정렬 | High | 높음 |
| [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 6 | 기본 상세·조합 조회·빈 콘텐츠·공개 상태 | High | 높음 |
| [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 2 | 유튜버 선택 목록·관계 유효성·유튜버 조건·중복 제거 | Medium | 높음 |
| [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 5 | 인증·필수값·중복·참조·등록 순서·조회 반영 | High | 보통 |
| 합계 | 20 | 기능 요구사항 전체 | - | - |

복잡도는 기능 개수만이 아니라 도메인 협업, 공개 상태, 데이터 정합성과 통합 책임을 함께 반영한다. [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 요구사항 수는 적지만 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)과 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 재사용할 Visit 판정 계약을 제공하므로 단순 작업으로 보지 않는다.

### 3.3 구성 대안 판단

#### 맛집 탐색과 유튜버 필터

| 기준 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)에 전부 포함 | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)으로 관계 기반 탐색 분리 |
|---|---|---|
| 사용자 흐름 | 한 탐색 화면과 직접 대응한다. | 독립 유튜버 탐색 가치를 제공하면서 같은 화면에서 조건으로 재사용할 수 있다. |
| Visit 의존성 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 Restaurant와 Visit 규칙을 함께 변경할 위험이 있다. | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 관계 유효성과 공개 상태 판정을 캡슐화한다. |
| 충돌 가능성 | 목록과 관계 판정 파일을 한 담당자가 폭넓게 수정한다. | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)은 최종 조건 조합, [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 유튜버 조건 후보 산출로 경계가 선명하다. |
| API 계약 | 단일 탐색 계약은 자연스럽지만 내부 책임이 커진다. | 외부 계약은 단일 탐색으로 유지할 수 있고 내부 판정 계약만 분리할 수 있다. |
| 독립 테스트 | Visit 데이터 없이는 유튜버 조건 테스트가 어렵다. | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 관계 판정과 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)의 조건 결합을 각각 테스트할 수 있다. |
| 4인 병렬 개발 | 독립 작업 하나가 줄어든다. | 네 개 Workstream 병렬 배치와 잘 맞는다. |
| MVP 복잡도 | 초기 계약 수는 적지만 결합 복잡도가 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)에 집중된다. | 최소 판정 계약이 필요하지만 소유권과 장애 범위가 명확하다. |

**결정:** [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)을 유지한다. [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회), [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)과 [BR-SEARCH-007](../01-requirements/business-rules.md#br-search-007-유튜버-필터의-방문-근거)의 완료 책임은 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 갖고, [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)은 [FR-RESTAURANT-005](../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합)에 따라 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 제공하는 선택·판정 결과를 이름·지역·카테고리 조건과 결합한다. 사용자에게는 하나의 탐색 흐름으로 제공하되 구현 책임만 분리한다.

#### 맛집 상세와 관련 콘텐츠

| 기준 | 하나의 Workstream | 기본 상세와 콘텐츠 분리 |
|---|---|---|
| 사용자 가치 | 한 화면에서 기본 정보와 방문 근거를 확인하는 완결된 가치다. | 각 부분만으로는 최종 상세 경험의 완료 책임이 불명확하다. |
| 도메인 협업 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 조합 책임을 명시적으로 가진다. | Restaurant와 Visit 사이 통합 책임자가 추가로 필요하다. |
| API 호출 구조 | 단일 또는 복수 호출 여부와 무관하게 한 WS가 최종 계약을 검증한다. | 호출은 격리할 수 있지만 사용자 화면 인수 테스트가 분산된다. |
| 병렬 개발 | WS 내부에서 기본 상세와 콘텐츠 어댑터를 병렬화할 수 있다. | Workstream 수와 조정 비용이 늘어난다. |
| 테스트·장애 격리 | 하위 계약을 독립 테스트하고 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 조합·부분 실패를 검증한다. | 하위 장애는 잘 격리되지만 빈 콘텐츠 정상 처리의 소유가 모호해진다. |

**권장안:** [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 하나로 유지한다. [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)는 조합 유스케이스와 사용자 계약을 소유하지만 Restaurant, Visit, Creator와 Video의 내부 정책을 가져오지 않는다. 상세 기본 정보는 관계가 없어도 제공하고, 유효 관계·유튜버·영상이 없으면 빈 목록을 정상 결과로 제공한다.

#### 관리자 등록

| 기준 | 하나의 WS | 도메인 Workstream에 분산 | 기본 데이터와 관계 등록 분리 |
|---|---|---|---|
| 데이터 생성 순서 | 한 책임자가 맛집·유튜버·영상 후 관계 등록 순서를 인수 테스트한다. | 각 등록은 응집되지만 전체 관리자 업무 완료 책임이 분산된다. | 선행 순서는 가장 명확하지만 Workstream이 5개가 된다. |
| 참조 무결성 | WS 내부 단계와 도메인 공개 계약으로 검증한다. | 통합 시 별도 조율이 필요하다. | 관계 WS가 기본 데이터 계약에 명시적으로 의존한다. |
| 도메인 소유권 | WS가 조율하고 각 도메인이 규칙을 소유하면 보존된다. | 가장 직접적으로 보존된다. | 보존되지만 관리자 진입과 공통 오류 책임이 중복될 수 있다. |
| 충돌·작업량 | 범위가 크지만 관리자 전용 조합부의 소유가 단일하다. | 조회 담당자와 같은 도메인 파일 충돌 가능성이 크다. | 충돌은 낮지만 현재 4인 구성에서는 배치 조정이 필요하다. |

**권장안:** [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 하나로 유지하되 내부를 `기본 데이터 등록`과 `방문 관계 등록`의 두 단계로 나눈다. [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)는 인증·진입·순서·오류 조합을 책임지고, Restaurant·Creator·Video·Visit는 각자의 필수값·중복·관계 불변 조건을 책임진다. 역할 배정 시 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 높은 작업량을 공통 인증 기반 담당과 테스트 지원으로 보완한다.

## 4. 선행 공동 작업

병렬 개발을 시작하기 전에 아래 최소 계약만 합의한다. 저장 구조, 구체 API URL과 구현 기술은 이 단계에서 확정하지 않는다.

| 최소 공동 계약 | 현재 근거 또는 결정 | 시작 전 산출물 | 차단 대상 |
|---|---|---|---|
| Critical 후속 설계 항목 | 관리자 제품 정책은 확정, 인증 기술과 일부 NFR 측정값은 미확정 | 인증 전달·보호 계약과 미확정 수치의 책임자·결정 시점 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 보안 검증 |
| 유튜버 관리 단위 | YouTube 채널을 고유 단위로 사용 | 채널 식별·표시 정보 계약 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 맛집·음식 카테고리 | 사전 정의된 대표 카테고리 정확히 1개 | 카테고리 값과 `기타` 기록 검증 계약 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 지역 관리 단위 | 서울특별시 전체 도로명주소와 자치구 1개 | 자치구 입력·표시·검증 계약 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 공통 식별자 | 표현 방식은 후속 설계 사항 | 도메인 경계를 넘는 식별자 표현 규칙 | 전체 |
| 공개·비공개 기준 | 네 대상이 모두 공개·유효한 관계만 노출 | 대상별 상태 판정과 조합 우선순위 | 전체 조회·등록 |
| 공통 응답·오류 | 형식은 미확정 | 성공, 빈 목록, 잘못된 요청, 찾을 수 없음, 충돌, 인증 실패 계약 | 전체 |
| 페이지네이션 | 크기 10·20·50, 기본 20, 범위 밖은 빈 목록 | 페이지 입력과 메타데이터 계약 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) |
| 날짜·시간 | 방문 날짜는 MVP에서 관리하지 않음 | 공통 날짜·시간 표현과 시간대 원칙 | 등록·운영 공통 |
| 핵심 관계 | 방문 관계는 맛집·유튜버·영상 각 1개를 연결 | 존재·채널 일치·중복·유효성 판정 계약 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 관리자 권한 | 사전 발급 계정, 동일 등록 권한 | 인증 주체 전달과 접근 거부 계약 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) |
| 테스트·통합 | 세부 규칙은 팀 합의 필요 | 필수 테스트 명령, 품질 게이트, 공유 파일 변경 규칙 | 전체 |

## 5. WS-01 맛집 탐색

### 사용자 가치

사용자는 계정 없이 공개 맛집 목록을 조회하고 이름, 서울특별시 자치구, 대표 음식 카테고리와 유튜버 조건을 조합하여 원하는 맛집을 페이지 단위로 찾을 수 있다.

### 주요 사용자 흐름

1. 사용자가 맛집 탐색 화면에 접근한다.
2. 시스템이 공개된 맛집의 첫 페이지를 기본 정렬로 반환한다.
3. 사용자가 검색어 또는 하나 이상의 필터 조건을 입력한다.
4. 시스템이 지정된 모든 조건을 만족하는 고유 맛집을 페이지 단위로 반환한다.
5. 결과나 요청 페이지에 항목이 없으면 빈 목록을 정상 반환한다.

### 포함 기능

- 맛집 목록과 목록용 방문 YouTube 채널명 조회
- 맛집 이름 부분 일치 검색과 앞뒤 공백·영문 대소문자 처리
- 서울특별시 자치구 1개 필터
- 대표 음식 카테고리 1개 필터
- [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 유효 관계 판정을 이용한 유튜버 1명 필터
- 검색과 필터의 AND 조합
- 페이지 크기 10·20·50, 기본 20과 페이지 이동
- 맛집 이름, 전체 도로명주소 순 기본 정렬
- 빈 결과와 잘못된 조건 처리

### 제외 기능

- 자연어 검색, 자동 완성, 추천, 오타 교정과 초성·유사어 검색
- 복수 값 동시 선택, 인기·개인화 정렬과 무한 스크롤
- 지도, 현재 위치, 거리·반경 탐색
- 유튜버 상세 페이지

### 주 소유 도메인

- Restaurant

### 협업 도메인

- Visit: 유효 방문 관계에 따른 유튜버 조건과 목록 채널명 제공
- Creator, Video: [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)을 통해 유튜버·영상 공개 상태 판정에 간접 참여

### 관련 요구사항

- [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회) ~ [FR-RESTAURANT-007](../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용)

### 관련 비즈니스 규칙

- [BR-RESTAURANT-001](../01-requirements/business-rules.md#br-restaurant-001-맛집의-의미), [BR-RESTAURANT-002](../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집), [BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건)
- [BR-SEARCH-001](../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준) ~ [BR-SEARCH-009](../01-requirements/business-rules.md#br-search-009-기본-정렬)
- [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-003](../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출) ~ [BR-PUBLICATION-006](../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회)

### 관련 비기능 요구사항

- [NFR-PERFORMANCE-001](../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간), [NFR-PERFORMANCE-002](../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간), [NFR-PERFORMANCE-004](../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한)
- [NFR-SECURITY-002](../01-requirements/non-functional-requirements.md#nfr-security-002-입력-및-웹-공격-방어), [NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호)
- [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책), [NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리)
- [NFR-COMPATIBILITY-002](../01-requirements/non-functional-requirements.md#nfr-compatibility-002-응답-형식과-문자-처리), [NFR-COMPATIBILITY-003](../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기)
- [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층), [NFR-TEST-002](../01-requirements/non-functional-requirements.md#nfr-test-002-변경외부-의존성성능-검증)

### 필요한 입력 데이터

- 공개 맛집 기본 정보, 자치구, 대표 음식 카테고리
- 공개·유효 관계에서 산출한 맛집별 YouTube 채널 표시 정보
- 선택한 유튜버에 대해 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 판정한 맛집 식별 결과

### 제공 결과

- 검색·필터·정렬이 적용된 고유 맛집 요약 목록
- 맛집 식별자, 이름, 자치구, 대표 음식 카테고리와 방문 채널명
- 페이지 이동 정보

### 선행 조건

- 목록 응답, 페이지네이션, 식별자와 오류 계약
- 맛집과 관계 정보의 공개 상태 정책
- [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 유튜버 조건 판정 계약

### 다른 Workstream 의존성

- [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 관계 판정이 필요하지만 계약 Stub으로 유튜버 조건을 제외한 범위를 독립 개발할 수 있다.
- [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 운영 데이터를 생성하지만 테스트 Fixture로 개발을 선행할 수 있다.

### 독립 개발 가능 범위

- 목록, 이름 검색, 지역·카테고리 필터, 조합, 페이지, 기본 정렬과 빈 결과
- Visit 결과를 받는 계약 경계와 중복 제거

### 통합 지점

- [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 유튜버별 유효 맛집 결과와 목록 채널명
- [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 등록한 공개 맛집과 방문 관계의 조회 반영
- 공통 응답·오류·보안 계약

### 테스트 범위

- 기본 목록, 이름 검색과 공백·대소문자 처리
- 각 필터 단독 적용과 모든 조건 조합
- 유튜버 관계 중복이 있는 맛집의 단일 표시
- 빈 결과, 범위 밖 페이지와 잘못된 입력
- 기본 정렬의 안정성과 페이지 간 누락·중복 방지
- 비공개·삭제 맛집과 무효 관계 제외
- 영상 연결이 없는 공개 맛집 포함

### 완료 조건

- 관련 기능 요구사항의 인수 조건과 적용 비기능 요구사항을 충족한다.
- 각 검색·필터·페이지·정렬 경계의 자동화 테스트가 통과한다.
- 비공개 데이터가 노출되지 않고 빈 결과가 오류로 처리되지 않는다.
- [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 계약과 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터가 실제 탐색 결과에 반영된다.
- 계약 문서와 실제 응답이 일치한다.

### 주요 리스크

- 유튜버 조건과 목록 채널명 조합이 Visit 의존성과 조회 비용을 높일 수 있다.
- 목록에 표시할 유튜버 수와 축약 방식이 아직 결정되지 않았다.
- 목표 데이터 규모와 응답 시간 측정값이 확정되지 않았다.

## 6. WS-02 맛집 상세 및 콘텐츠 조회

### 사용자 가치

사용자는 공개 맛집의 주소·전화번호·카테고리·카카오 장소 링크를 확인하고, 실제 방문이 검증된 유튜버와 관련 YouTube 영상을 같은 상세 흐름에서 확인할 수 있다.

### 주요 사용자 흐름

1. 사용자가 탐색 결과에서 맛집을 선택한다.
2. 시스템이 공개 맛집의 기본 정보를 조회한다.
3. 시스템이 해당 맛집의 공개·유효 방문 관계를 판정한다.
4. 시스템이 중복 제거한 유튜버와 영상 표시 정보를 기본 정보와 조합한다.
5. 관계나 영상이 없거나 외부 링크에 장애가 있어도 기본 정보와 빈 콘텐츠 목록을 정상 반환한다.

### 포함 기능

- 맛집 기본 정보, 전체 도로명주소·상세 위치, 전화번호, 대표 음식 카테고리와 카카오 장소 링크
- 영상 연결이 없는 맛집의 상세 조회
- 방문 유튜버 채널명·채널 링크와 중복 제거
- 영상 제목·썸네일·게시 채널명·원본 링크와 중복 제거
- Restaurant, Visit, Creator, Video 결과의 조합
- 비공개·삭제 대상 및 무효 관계 제외

### 제외 기능

- 지도, 길찾기, 지번주소와 거리 정보
- 유튜버 상세 페이지와 영상 재생·저장·재배포
- 외부 링크 자동 복구나 메타데이터 자동 동기화

### 주 소유 도메인

- Restaurant: 맛집 기본 상세와 공개 여부
- Visit: 연결 콘텐츠의 관계 유효성, 범위와 중복 제거

### 협업 도메인

- Creator: 공개 유튜버 표시 정보와 상태
- Video: 공개 영상 표시 정보·원본 링크와 상태

[WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)는 최종 상세 조합의 Workstream 소유자다. 실제 조합을 Restaurant 애플리케이션 책임 또는 전용 조회 책임 중 어디에 둘지는 후속 아키텍처 결정으로 남긴다.

### 관련 요구사항

- [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) ~ [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회)
- [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인)
- [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)

### 관련 비즈니스 규칙

- [BR-RESTAURANT-002](../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집), [BR-RESTAURANT-004](../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리), [BR-RESTAURANT-005](../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속), [BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건)
- [BR-CREATOR-004](../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보), [BR-CREATOR-007](../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리)
- [BR-VIDEO-001](../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위), [BR-VIDEO-004](../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결), [BR-VIDEO-007](../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리) ~ [BR-VIDEO-009](../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리)
- [BR-VISIT-004](../01-requirements/business-rules.md#br-visit-004-방문-관계의-연결-범위), [BR-VISIT-005](../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성)
- [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-003](../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출) ~ [BR-PUBLICATION-007](../01-requirements/business-rules.md#br-publication-007-외부-영상-삭제의-영향-범위)

### 관련 비기능 요구사항

- [NFR-PERFORMANCE-001](../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간)
- [NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리)
- [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책), [NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리)
- [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리), [NFR-EXTERNAL-002](../01-requirements/non-functional-requirements.md#nfr-external-002-외부-호출-실패와-변경-격리)
- [NFR-COMPATIBILITY-002](../01-requirements/non-functional-requirements.md#nfr-compatibility-002-응답-형식과-문자-처리), [NFR-COMPATIBILITY-003](../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기)
- [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층), [NFR-TEST-002](../01-requirements/non-functional-requirements.md#nfr-test-002-변경외부-의존성성능-검증)

### 필요한 입력 데이터

- 맛집 식별자와 공개 기본 정보
- 맛집에 연결된 공개·유효 방문 관계
- 관계가 참조하는 공개 유튜버와 영상의 표시 정보

### 제공 결과

- 맛집 기본 상세
- 중복 제거된 방문 유튜버 목록
- 중복 제거된 관련 영상 목록과 원본 링크
- 관계 또는 콘텐츠가 없을 때의 빈 목록

### 선행 조건

- 맛집 기본 정보와 식별자 계약
- Visit 관계 유효성·중복 제거 계약
- Creator·Video 표시 정보와 공개 상태 계약
- 상세 조합의 오류·부분 실패 정책

### 다른 Workstream 의존성

- [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)과 Visit의 관계 유효성 판정 규칙을 공유하지만, [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 완성 API 구현에는 의존하지 않는다.
- [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터가 필요하지만 Fixture와 Fake 제공자로 독립 개발할 수 있다.

### 독립 개발 가능 범위

- 맛집 기본 상세와 찾을 수 없음 처리
- Creator·Video·Visit 계약을 Stub으로 둔 조합, 빈 콘텐츠와 중복 제거
- 외부 링크 장애와 내부 기본 정보의 격리

### 통합 지점

- Visit의 맛집별 유효 관계 결과
- Creator와 Video의 표시 정보·공개 상태
- [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 등록한 기본 데이터와 관계

### 테스트 범위

- 공개 맛집 기본 상세, 주소·카테고리와 링크 표시
- 존재하지 않거나 비공개인 맛집의 찾을 수 없음
- 관계 없음, 유튜버 없음, 영상 없음과 외부 링크 장애
- 여러 유튜버·영상과 동일 대상 중복 제거
- Creator·Video·Visit 중 하나가 비공개·삭제된 조합 제외
- 하나의 영상이 여러 맛집과 연결된 경우 해당 맛집별 결과

### 완료 조건

- 여섯 기능 요구사항의 인수 조건과 적용 비기능 요구사항을 충족한다.
- 연결 콘텐츠가 없어도 맛집 기본 정보가 누락되지 않는다.
- 네 도메인의 공개 상태 우선순위와 관계 유효성 규칙이 자동화 테스트로 검증된다.
- 외부 링크 장애가 내부 상세 조회 실패로 확산되지 않는다.
- 조합 계약 문서와 실제 응답이 일치한다.

### 주요 리스크

- 조합 책임의 실제 코드 위치가 미결정이라 공유 파일 충돌이 생길 수 있다.
- 다중 관계의 중복 제거와 상태 확인이 조회 성능에 영향을 줄 수 있다.
- 일부 정보 제공자의 실패를 빈 결과로 볼지 전체 오류로 볼지 API 계약에서 확정해야 한다.

## 7. WS-03 유튜버 기반 탐색

### 사용자 가치

사용자는 특정 YouTube 채널 단위 유튜버를 선택해, 공개 영상으로 실제 방문이 확인된 공개 맛집만 탐색할 수 있다.

### 주요 사용자 흐름

1. 사용자가 유튜버 1명을 탐색 조건으로 선택한다.
2. 시스템이 유튜버·영상·방문 관계의 공개·유효 상태를 확인한다.
3. 시스템이 해당 관계에 연결된 공개 맛집을 고유하게 산출한다.
4. [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 이 결과를 다른 탐색 조건과 결합하여 최종 목록과 페이지를 제공한다.
5. 유효 관계가 없으면 빈 목록을 정상 반환한다.

### 포함 기능

- 공개 유튜버 식별자와 현재 채널명의 최소 선택 목록
- 유튜버 1명 기준 실제 방문 맛집 조회
- 근거 영상과 유효 방문 관계 판정
- 비공개·삭제 유튜버·영상·관계·맛집 제외
- 같은 맛집의 중복 제거
- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 사용할 유튜버 조건 판정 결과 제공

### 제외 기능

- 복수 유튜버 동시 선택
- 유튜버 상세 페이지, 구독자·인기 정보와 추천
- 근거 영상이 없는 추정 방문
- 유튜버 목록 검색·페이지네이션·프로필·구독자 정보와 상세 조회

### 주 소유 도메인

- Visit

### 협업 도메인

- Creator: 채널 동일성과 공개 상태
- Video: 근거 영상의 게시 채널과 공개 상태
- Restaurant: 맛집 공개 상태와 표시 정보

### 관련 요구사항

- [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)
- [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)

### 관련 비즈니스 규칙

- [BR-CREATOR-001](../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미), [BR-CREATOR-004](../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보), [BR-CREATOR-005](../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치), [BR-CREATOR-007](../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리)
- [BR-VIDEO-005](../01-requirements/business-rules.md#br-video-005-실제-방문-근거), [BR-VIDEO-009](../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리)
- [BR-VISIT-001](../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성) ~ [BR-VISIT-007](../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태)
- [BR-SEARCH-003](../01-requirements/business-rules.md#br-search-003-필터-종류와-단일-선택) ~ [BR-SEARCH-007](../01-requirements/business-rules.md#br-search-007-유튜버-필터의-방문-근거)
- [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-003](../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출) ~ [BR-PUBLICATION-006](../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회)

### 관련 비기능 요구사항

- [NFR-PERFORMANCE-002](../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간)
- [NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리)
- [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책), [NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리)
- [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층), [NFR-TEST-002](../01-requirements/non-functional-requirements.md#nfr-test-002-변경외부-의존성성능-검증)
- [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계), [NFR-MAINTAINABILITY-002](../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치)

### 필요한 입력 데이터

- 선택한 유튜버 식별자와 공개 상태
- 맛집·유튜버·영상을 연결한 방문 관계
- 영상 게시 채널과 공개 상태
- 맛집 식별자·공개 상태·목록 표시 정보

### 제공 결과

- 선택한 유튜버가 유효하게 방문한 고유 공개 맛집 목록
- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 다른 탐색 조건과 결합할 수 있는 맛집 식별 결과

### 선행 조건

- YouTube 채널 단위 유튜버 식별 계약
- 네 대상의 공개 상태와 Visit 유효성 계약
- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)과 공유할 유튜버 조건 판정 계약

### 다른 Workstream 의존성

- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)과 목록 표현·페이지 계약을 공유하되 최종 조건 결합은 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 책임진다.
- [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 관계 유효성 규칙을 공유하되 서로의 구현을 호출하지 않는다.
- [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 관계 데이터를 생성하지만 Fixture로 독립 개발할 수 있다.

### 독립 개발 가능 범위

- 등록된 관계 Fixture를 이용한 관계 유효성, 공개 상태, 채널 일치와 중복 제거 판정
- Restaurant 표시 정보 제공자를 Fake로 둔 유튜버별 맛집 산출

### 통합 지점

- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)의 최종 검색·필터 AND 조합
- [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)의 맛집별 연결 콘텐츠 조회와 동일한 관계 유효성 규칙
- [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 방문 관계 등록 결과

### 테스트 범위

- 유효 관계가 있는 유튜버의 단일·다중 맛집 조회
- 근거 영상 없음, 게시 채널 불일치와 존재하지 않는 참조 제외
- 유튜버·영상·관계·맛집 비공개 또는 삭제 제외
- 같은 맛집의 다중 관계 중복 제거
- 빈 결과와 존재하지 않거나 공개되지 않은 유튜버 입력
- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)의 이름·지역·카테고리 조건과 결합

### 완료 조건

- [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)과 [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)의 인수 조건과 적용 비기능 요구사항을 충족한다.
- 관계 유효성 판정이 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 동일한 정책을 사용한다.
- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 실제 계약으로 결과를 결합하고 중복 없는 목록을 제공한다.
- 비공개·무효 관계가 사용자 결과에 노출되지 않는다.
- 자동화 테스트와 계약 문서가 완료된다.

### 주요 리스크

- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)과 Visit 내부를 공유하면 순환 구현 의존이 생길 수 있으므로 판정 계약 경계를 지켜야 한다.
- 요구사항 수에 비해 타 Workstream의 통합 지원 부담이 크다.

## 8. WS-04 관리자 데이터 등록

### 사용자 가치

사전 발급 계정으로 인증한 관리자는 검증된 맛집, YouTube 채널 단위 유튜버와 영상 정보를 등록한 뒤 실제 방문 관계를 연결하여 사용자 탐색과 상세 조회에 반영할 수 있다.

### 주요 사용자 흐름

1. 관리자가 사전 발급 계정으로 인증해 등록 기능에 접근한다.
2. 관리자가 출처와 사실을 확인한 맛집, 유튜버와 영상을 각각 등록한다.
3. 각 도메인이 필수값, 동일 대상 중복과 공개 조건을 검증한다.
4. 관리자가 등록된 세 대상을 선택하고 영상의 게시 채널·실제 방문 근거를 확인한다.
5. Visit가 참조 존재, 채널 일치와 관계 중복을 검증해 방문 관계를 등록한다.
6. 등록 결과가 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 사용자 조회에 반영된다.

### 포함 기능

- 관리자 인증과 등록 기능 접근 통제
- 맛집·유튜버·영상 기본 정보 등록
- 맛집·유튜버·영상 방문 관계 등록
- 필수값, 사전 정의 값, 외부 링크와 사실 확인
- 동일 장소·채널·영상·관계의 중복 및 동시 등록 방지
- 참조 대상 존재와 영상 게시 채널·유튜버 일치 검증
- 판정 불가 요청의 비노출·보류 처리 원칙
- 등록 데이터의 사용자 조회 반영

### 제외 기능

- 관리자 회원가입, 등급, 기능별 권한과 승인 워크플로
- 데이터 수정·삭제 화면과 별도 검증 상태
- 관리자 확인 없는 자동 등록·자동 주기 동기화, AI 추출과 영상 원본 저장
- 실제 방문 날짜 관리

### 주 소유 도메인

- 관리자 유스케이스: 인증, 공통 진입, 등록 순서와 오류 조합
- Restaurant: 맛집 등록 규칙
- Creator: 유튜버 등록 규칙
- Video: 영상 등록 규칙
- Visit: 방문 관계 등록 규칙

### 협업 도메인

- Visit 등록 시 Restaurant, Creator와 Video가 존재·동일성·표시 정보를 제공한다.
- Video와 Visit는 선택 영상의 게시 채널이 선택 유튜버와 일치하는지 Creator 기준으로 확인한다.

### 관련 요구사항

- [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) ~ [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)
- [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)

### 관련 비즈니스 규칙

- [BR-RESTAURANT-003](../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보) ~ [BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건)
- [BR-CREATOR-001](../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미) ~ [BR-CREATOR-003](../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단), [BR-CREATOR-005](../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치)
- [BR-VIDEO-001](../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위) ~ [BR-VIDEO-006](../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분)
- [BR-VISIT-001](../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성) ~ [BR-VISIT-007](../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태)
- [BR-ADMIN-001](../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증) ~ [BR-ADMIN-008](../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리)
- [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-002](../01-requirements/business-rules.md#br-publication-002-비공개-데이터의-접근), [BR-PUBLICATION-008](../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성)

### 관련 비기능 요구사항

- [NFR-PERFORMANCE-003](../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간)
- [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제) ~ [NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호)
- [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성) ~ [NFR-INTEGRITY-003](../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성)
- [NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보)
- [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류) ~ [NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단)
- [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층) ~ [NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트)
- [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화), [NFR-PRIVACY-002](../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호)

### 필요한 입력 데이터

- 관리자 인증 정보
- 검증된 맛집·장소·주소·카테고리 정보
- 검증된 YouTube 채널과 영상 표시·원본 링크 정보
- 등록된 맛집·유튜버·영상 식별자와 실제 방문 근거

### 제공 결과

- 중복 없이 등록된 기본 데이터와 방문 관계
- 보류 또는 기존 대상 사용을 구분할 수 있는 결과
- [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)에서 조회 가능한 공개 데이터

### 선행 조건

- MVP 최소 관리자 인증과 접근 거부 계약
- 도메인별 식별자, 필수값·중복·공개 상태 계약
- 등록 순서, 원자성, 충돌과 보류 오류 계약
- 테스트 데이터 정리와 마이그레이션 기준

### 다른 Workstream 의존성

- 조회 Workstream 구현을 기다리지 않고 도메인별 등록과 관계 생성까지 독립 개발할 수 있다.
- 조회 반영 인수 조건은 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 계약 또는 검증용 읽기 경로가 필요하다.

### 독립 개발 가능 범위

- 기본 데이터 세 종류의 등록과 중복 검증
- 등록된 대상을 이용한 방문 관계 생성, 참조·채널 일치 검증
- 관리자 인증 제공자를 Stub으로 둔 등록 흐름

### 통합 지점

- 공통 관리자 인증과 권한 기반
- 각 도메인의 공개 등록 유스케이스
- 세 조회 Workstream의 데이터 반영 검증

### 테스트 범위

- 인증 성공·실패와 일반 사용자 접근 차단
- 각 등록의 필수값, 값 범위, 링크와 사실 검증
- 동일 장소·채널·영상·관계 중복 및 동시 요청
- 참조 대상 없음, 게시 채널 불일치와 근거 영상 없음
- 기본 데이터 선행 순서와 부분 실패 시 원자성
- 보류 요청 비노출과 기존 대상 재사용
- 등록 직후 세 조회 흐름 반영

### 완료 조건

- 다섯 기능 요구사항의 인수 조건과 적용 비기능 요구사항을 충족한다.
- 인증되지 않은 요청은 등록할 수 없고 공개 조회는 인증 없이 가능하다.
- 각 도메인의 필수값·중복 규칙과 Visit 참조 무결성이 자동화 테스트로 검증된다.
- 동일 대상의 동시 요청에도 데이터 또는 관계가 하나만 생성된다.
- 등록 데이터가 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 실제 조회에 반영된다.
- 등록·오류 계약과 운영 문서가 실제 동작과 일치한다.

### 주요 리스크

- 네 도메인의 등록 흐름을 조율하므로 다른 Workstream보다 작업량과 통합 범위가 크다.
- 인증 기술과 계정 발급·회수·복구의 세부 운영 절차는 후속 설계가 필요하다.
- 보류 요청을 실제로 저장·관리하는 방식은 후속 설계가 필요하며 MVP에 승인 화면을 추가해서는 안 된다.

## 9. Workstream 간 의존 관계

### WS-01 ↔ WS-02

- 구분: 설계 및 데이터 계약 의존성
- 내용:
  - 두 Workstream은 동일한 맛집 식별자, 기본 표시 정보와 공개 상태를 사용한다.
  - [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)은 상세 진입에 필요한 식별자를 제공하고 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)는 그 식별자로 상세를 완성한다.
  - [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인)의 주 책임인 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)는 맛집별 방문 채널 표시 계약을 제공하고 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)은 목록 표현과 페이지 조합을 책임진다.
- 선행 필요: 목록·상세 사이 식별자, 공통 맛집 필드와 방문 채널 표시 정보의 의미 합의
- 개발 차단 여부: 차단하지 않는다. 각자 Fixture로 개발하고 계약 테스트에서 결합한다.
- 순환 방지: 서로의 구현을 호출하지 않고 공통 Restaurant 계약만 사용한다.

### WS-01 ↔ WS-03

- 구분: 설계, 데이터 및 통합 계약 의존성
- 내용:
  - [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 유튜버 조건에 맞는 공개·유효 맛집 식별 결과를 제공한다.
  - [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)은 그 결과를 이름·지역·카테고리 조건과 결합하고 최종 목록·페이지를 소유한다.
- 선행 필요: 유튜버 식별자, 관계 유효성, 결과 집합과 오류 의미 합의
- 개발 차단 여부: 유튜버 필터 통합만 차단한다. Stub으로 나머지 탐색 개발은 가능하다.
- 순환 방지: Visit는 최종 검색 조합을 구현하지 않고 Restaurant는 관계 유효성을 재구현하지 않는다.

### WS-02 ↔ WS-03

- 구분: 설계 및 정책 의존성
- 내용:
  - 두 Workstream은 같은 Visit 관계 유효성·공개 상태 판정을 사용한다.
  - [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)는 맛집에서 콘텐츠로, [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 유튜버에서 맛집으로 조회 방향만 다르다.
- 선행 필요: 유효 관계 판정과 Creator·Video 공개 상태 계약
- 개발 차단 여부: 차단하지 않는다. 공유 정책 계약에 대한 독립 테스트가 가능하다.
- 순환 방지: 공통 판정 규칙의 소유자는 Visit이며 두 Workstream이 서로 호출하지 않는다.

### WS-04 → WS-01

- 구분: 데이터 및 운영 의존성
- 내용: [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 등록한 공개 맛집과 관계 데이터가 목록·검색·필터에 반영된다.
- 선행 필요: 맛집 필수값, 공개 상태와 목록 표시 정보 계약
- 개발 차단 여부: 운영 인수 테스트 전에는 필요하지만 Fixture로 독립 개발 가능하다.

### WS-04 → WS-02

- 구분: 데이터 및 통합 의존성
- 내용: 기본 데이터와 방문 관계 등록 결과가 상세·유튜버·영상 조합에 반영된다.
- 선행 필요: 네 대상 식별자, 표시 정보와 관계 참조 계약
- 개발 차단 여부: 전체 상세 인수 테스트 전에는 필요하지만 Fake 제공자로 독립 개발 가능하다.

### WS-04 → WS-03

- 구분: 데이터 및 운영 의존성
- 내용: 등록 완료된 유효 방문 관계가 유튜버 기반 탐색의 근거가 된다.
- 선행 필요: 관계 등록 완료의 의미, 중복과 공개 상태 계약
- 개발 차단 여부: 실제 데이터 통합 전에는 필요하지만 관계 Fixture로 독립 개발 가능하다.

구현 변경 방향은 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 조율 → 각 소유 도메인과 `조회 Workstream → 도메인이 공개한 조회 계약`으로 유지한다. 조회 결과가 등록 기능을 호출하거나, Restaurant와 Visit가 서로의 내부 모델을 변경하는 순환은 허용하지 않는다.

## 10. 병렬 개발 전략

### 10.1 시작 전 공동 합의

- 최소 데이터 개념과 도메인 간 식별자
- 핵심 목록·상세·관계 판정·등록 계약
- 공통 성공·빈 결과·오류 형식
- 대상별 공개 상태와 조합 우선순위
- 필수 테스트, 브랜치 통합과 공유 파일 변경 규칙

### 10.2 병렬 개발 배치 예시

| 개발자 | 주 Workstream | 보조 책임 |
|---|---|---|
| 개발자 A | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | 공통 페이지네이션 계약 |
| 개발자 B | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 조회 조합 계약 |
| 개발자 C | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | Visit 조회·유효성 규칙 |
| 개발자 D | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 공통 관리자 권한 기반 |

이 표는 최종 Role 배정안이 아니라 네 Workstream이 4인 병렬 개발에 적합한지 검증하기 위한 예시다. 실제 담당자와 소유 파일은 [roles.md](../03-team/roles.md)와 [ownership.md](../03-team/ownership.md)에서 확정한다.

각 개발자는 주 Workstream의 요구사항 구체화, 계약 상세화, 데이터 접근, 비즈니스 로직, 오류 처리, 자동화 테스트, 문서 갱신과 통합 지원을 끝까지 책임진다. [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 작업량이 크므로 개발자 C는 관계 유효성 계약, 개발자 B는 등록 후 상세 반영 검증처럼 계약 소유 범위 안에서 통합을 지원할 수 있다.

### 10.3 통합 방식

- 공통 모델과 계약은 소유자 협의 없이 변경하지 않는다.
- 다른 Workstream에 영향을 주는 계약 변경은 영향, 전환 시점과 테스트 결과를 문서화한다.
- 미완료 제공자는 실제 계약과 같은 인터페이스의 Stub 또는 Fake로 대체한다.
- 임시 구현은 통합 시 검색하여 제거하거나 테스트 전용으로 격리됐는지 확인한다.
- 공유 파일 변경은 최소화하고 한 시점에 한 책임자가 병합한다.
- 결정 책임자는 계약을 확정하고, 구현 책임자는 합의된 계약을 구현한다. 둘이 항상 같은 사람일 필요는 없다.

## 11. 공통 작업과 담당 방식

| 공통 작업 | 목적 | 필요한 시점 | 팀 공동 결정 | 구현 담당자 1명 | Workstream 접점 | 완료 조건 |
|---|---|---|---|---|---|---|
| 프로젝트 초기 설정 | 동일한 실행·빌드 기준 제공 | 개발 시작 전 | 예 | 필요 | 전체 | 모든 개발자가 같은 절차로 실행·검증 가능 |
| 공통 응답·오류 처리 | 빈 결과와 업무·인증·시스템 오류의 일관된 표현 | API 상세화 전 | 예 | 필요 | 전체 | 계약 테스트와 문서가 실제 응답과 일치 |
| 관리자 인증·권한 기반 | 등록 기능 접근 통제 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 연결 전 | 최소 정책은 예 | 필요 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록), 공개 조회 보안 | 인증 성공·실패·우회 테스트 통과 |
| 마이그레이션 기반 | 한 저장소의 변경 순서와 재현성 보장 | 데이터 구현 전 | 규칙은 예 | 필요 | 전체, 특히 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 빈 환경과 기존 환경에서 동일 적용 가능 |
| 테스트 환경 | Fixture, Stub/Fake와 통합 테스트 기준 제공 | 병렬 개발 전 | 예 | 필요 | 전체 | 각 WS가 독립·통합 테스트 실행 가능 |
| API 문서화 기반 | 계약의 단일 추적 지점 제공 | 계약 합의 시 | 형식은 예 | 필요 | 전체 | 필수 계약과 오류가 자동 또는 수동 검증 가능 |
| CI 검증 | 병합 전 품질 게이트 제공 | 첫 통합 전 | 예 | 필요 | 전체 | 필수 빌드·테스트 실패 시 병합 차단 |
| 배포·헬스체크 | 배포 성공과 저장소·외부 장애 구분 | 전체 인수 전 | 최소 기준은 예 | 필요 | 전체 | 배포 전후 검사와 상태 확인 통과 |
| 코드 스타일·패키지 정책 | 공유 파일 충돌과 경계 침범 감소 | 개발 시작 전 | 예 | 필요 | 전체 | 자동 검사와 모듈 경계 규칙 통과 |

공통 작업 담당자는 모든 공통 코드를 독점하지 않는다. 팀이 정책과 공개 계약을 공동 결정하고, 지정된 구현 담당자는 최초 구현·문서화·변경 조율을 책임진다. 각 Workstream 담당자는 자신의 기능에서 공통 계약을 적용하고 필요한 테스트를 소유한다.

## 12. 요구사항 배정

| 요구사항 ID | 기능 | 주 Workstream | 협업 Workstream | 배정 근거 |
|---|---|---|---|---|
| [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회) | 맛집 목록 조회 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 탐색 시작점이며 채널명과 등록 데이터가 필요하다. |
| [FR-RESTAURANT-002](../01-requirements/functional-requirements.md#fr-restaurant-002-맛집-이름-검색) | 맛집 이름 검색 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | Restaurant 탐색 규칙이며 등록된 이름을 사용한다. |
| [FR-RESTAURANT-003](../01-requirements/functional-requirements.md#fr-restaurant-003-지역별-필터) | 지역별 필터 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | Restaurant가 소유하는 자치구 조건이다. |
| [FR-RESTAURANT-004](../01-requirements/functional-requirements.md#fr-restaurant-004-음식-카테고리별-필터) | 음식 카테고리별 필터 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | Restaurant가 소유하는 대표 카테고리 조건이다. |
| [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회) | 유튜버 기준 방문 맛집 조회 | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | Visit의 유효 관계 판정이 핵심이고 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 결과를 조합한다. |
| [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) | 유튜버 필터 선택 목록 조회 | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 공개 유튜버 선택 계약을 제공하고 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 필터 화면에서 사용한다. |
| [FR-RESTAURANT-005](../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합) | 검색 및 필터 조건 조합 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 최종 AND 조합과 맛집 목록 완료 책임은 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)에 있다. |
| [FR-RESTAURANT-006](../01-requirements/functional-requirements.md#fr-restaurant-006-페이지-단위-조회) | 페이지 단위 조회 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | 없음 | 최종 탐색 결과의 페이지 정책이다. |
| [FR-RESTAURANT-007](../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용) | 기본 정렬 적용 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) | 없음 | 최종 맛집 목록의 안정 정렬 책임이다. |
| [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) | 맛집 기본 정보 조회 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 상세 가치의 기본 정보이며 등록 데이터가 필요하다. |
| [FR-RESTAURANT-009](../01-requirements/functional-requirements.md#fr-restaurant-009-지역-정보-확인) | 지역 정보 확인 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 상세 화면의 전체 도로명주소 제공 책임이다. |
| [FR-RESTAURANT-010](../01-requirements/functional-requirements.md#fr-restaurant-010-음식-카테고리-확인) | 음식 카테고리 확인 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 상세 화면의 대표 카테고리 제공 책임이다. |
| [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회) | 영상 연결이 없는 맛집 상세 조회 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 기본 상세와 빈 콘텐츠의 조합 책임이 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)에 있다. |
| [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인) | 방문 유튜버 정보 확인 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 맛집 상세·목록 맥락의 관계 기반 유튜버 표시 기능이며 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)가 공통 표시 계약을 완결한다. |
| [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인) | 관련 영상 정보 확인 | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색), [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 상세 화면에서 관계 기반 영상을 조합하는 기능이다. |
| [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | 관리자 등록 기능 접근 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 공통 인증 작업 | 관리자 등록 흐름 전체의 선행 접근 책임이다. |
| [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | 맛집 정보 등록 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 관리자 데이터 생성 흐름이며 두 조회에 반영된다. |
| [FR-ADMIN-003](../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | 유튜버 정보 등록 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 관계 생성의 선행 데이터이며 유튜버 조회에 반영된다. |
| [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | 영상 정보 등록 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 관계 근거의 선행 데이터이며 상세·필터 유효성에 쓰인다. |
| [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | 맛집·유튜버·영상 방문 관계 등록 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회), [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 관리자 등록 흐름의 마지막 단계이며 모든 관계 기반 조회에 반영된다. |

기능 요구사항 20개가 모두 한 번씩 주 Workstream에 배정되었으며 중복 주 배정이나 미배정 요구사항은 없다.

## 13. 완료 순서 및 통합 순서

| 단계 | 선행 작업 | 병렬 가능 작업 | 완료 판단 |
|---:|---|---|---|
| 1. Critical 정책 결정 | 관리자 인증 최소 기준, 식별·공개·정합성 정책 확인 | NFR 측정 계획 정리 | 구현을 차단하는 미확정 항목에 책임자·결정 시점 존재 |
| 2. 최소 API·데이터 계약 합의 | 단계 1 | 네 WS의 계약 초안 동시 작성 | 식별자, 상태, 오류, 페이지, 관계 판정 계약 승인 |
| 3. Workstream별 독립 개발 | 단계 2 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) ~ [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 모두 | 각 WS가 Fixture·Stub/Fake로 자동화 테스트 통과 |
| 4. 기본 데이터 등록 통합 | [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 기본 등록, 공통 인증·마이그레이션 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 기본 탐색, [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 기본 상세 계속 | 맛집·유튜버·영상 등록 및 기본 조회 반영 |
| 5. 조회 Workstream 통합 | Restaurant 표시 계약 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 목록과 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 상세 병렬 통합 | 목록에서 상세 진입, 기본 정보 일치 |
| 6. 방문 관계 기반 조회 통합 | [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 관계 판정, [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관계 등록 | [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 유튜버 조건과 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 콘텐츠 조합 병렬 | 등록 관계가 세 조회에 동일 정책으로 반영 |
| 7. 공통 보안·오류 검증 | 통합 API | 모든 WS의 실패·경계 테스트 | 인증, 비공개, 오류·부분 실패 정책 통과 |
| 8. 전체 인수 테스트 | 단계 4 ~ 7 | 성능·호환성·관찰성 검증 병행 | 기능 요구사항 20개와 MVP 시나리오 통과 |
| 9. 배포 검증 | CI 품질 게이트 | 운영 문서 최종화 | 배포 전후 검사, 헬스체크와 복구 절차 확인 |

첫 통합 대상으로 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 기본 맛집 등록 → [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 유튜버 조건 없는 기본 목록을 권장한다. 가장 적은 관계 의존으로 등록 데이터의 조회 반영, 공개 상태, 페이지와 기본 오류 계약을 조기에 검증할 수 있기 때문이다. 이후 [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 기본 상세를 붙이고 마지막에 Visit 관계 기반 세 흐름을 동시에 연결한다.

## 14. MVP 완료 기준

- 기능 요구사항 20개가 하나의 주 Workstream에 배정되어 있고 각 인수 조건을 충족한다.
- 각 Workstream의 자동화 테스트, 계약 문서, 오류 처리와 통합 지원이 완료된다.
- 사용자는 계정 없이 맛집 목록, 이름 검색, 지역·음식 카테고리·유튜버 필터와 조건 조합을 사용할 수 있다.
- 페이지 이동과 기본 정렬에서 항목이 누락되거나 중복되지 않는다.
- 사용자는 맛집 상세에서 기본 정보, 방문 유튜버와 관련 영상을 확인할 수 있다.
- 관계나 영상이 없는 공개 맛집도 목록과 상세에서 조회할 수 있다.
- 사용자는 유효 방문 관계를 기준으로 유튜버의 방문 맛집을 탐색할 수 있다.
- 인증된 관리자는 맛집, 유튜버, 영상과 방문 관계를 중복 없이 등록할 수 있다.
- 관리자 등록 데이터가 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색), [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)와 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)에 반영된다.
- 비공개·삭제된 맛집은 일반 사용자에게 노출되지 않고, 비공개·삭제된 유튜버·영상·관계는 연결 결과에서 제외된다.
- 주요 입력 오류, 빈 결과, 찾을 수 없음, 인증 실패, 중복·동시 등록과 외부 링크 장애 테스트가 통과한다.
- Workstream 간 계약, API 문서와 실제 구현이 일치하고 임시 Stub/Fake가 운영 경로에 남지 않는다.
- MVP 제외 기능이 구현·선행 구조 범위에 포함되지 않는다.
- [roles.md](../03-team/roles.md)와 [ownership.md](../03-team/ownership.md)에서 각 Workstream 최종 책임자와 계약·공유 파일 소유자를 지정할 수 있다.

## 15. 검토 필요 항목

### RV-WS-001 실제 Workstream 개수

- 현재 상태: 결정 완료 — [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) ~ [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 4개 유지
- 관련 요구사항: 기능 요구사항 20개 전체
- 선택지:
  1. 권장안대로 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) ~ [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 유지
  2. [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 기본 데이터 등록과 방문 관계 등록을 분리
- 영향: 책임 완결성, [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 작업량, 팀원 배치와 통합 일정
- 결정 시점: Role 배정 전

### RV-WS-002 유튜버별 필터의 Workstream 소유권

- 현재 상태: 결정 완료 — [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 관계 판정·유튜버별 조회를 소유하고 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 최종 조건을 조합
- 관련 요구사항:
  - [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)
  - [FR-RESTAURANT-005](../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합)
- 선택지:
  1. [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 관계 판정까지 소유
  2. [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 관계 판정·유튜버별 조회를 소유하고 [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)이 결과를 조합
- 영향: Visit 의존성, API 계약, 독립 테스트와 통합 일정
- 결정 시점: Role 배정 및 API 명세 전

### RV-WS-003 맛집 상세 조합 조회의 책임 위치

- 현재 상태: [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) Workstream 소유는 권장, 실제 애플리케이션 책임 위치는 팀 결정 필요
- 관련 요구사항:
  - [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) ~ [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회)
  - [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인)
  - [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 선택지:
  1. Restaurant 중심 애플리케이션 책임이 Visit 결과를 조합
  2. 별도 조회 조합 책임을 두되 새로운 비즈니스 도메인으로 만들지 않음
- 영향: 모듈 의존, 성능, 공유 파일, 장애 격리와 인수 테스트
- 결정 시점: API 명세 및 아키텍처 설계 전

### RV-WS-004 유튜버 목록 조회의 MVP 포함 여부

- 현재 상태: 결정 완료 — 최소 선택 목록을 MVP에 포함
- 관련 요구사항:
  - [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)
  - [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- 결정 내용:
  - 공개 유튜버의 식별자와 현재 채널명만 채널명 오름차순으로 제공한다.
  - 검색·페이지네이션·프로필·구독자 정보와 상세 조회는 제외한다.
- 영향: 요구사항 추적, 프론트엔드 필터 구성과 Creator 조회 계약
- 결정 시점: API 명세 작성 전

### RV-WS-005 관리자 수정·삭제 기능의 MVP 포함 여부

- 현재 상태: 결정 완료 — 1차 MVP 제외
- 관련 요구사항:
  - [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) ~ [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)
  - [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 결정 내용: 등록만 포함하며 수정·삭제·승인 상태 관리는 구현하지 않는다.
- 영향: [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 범위와 정정 운영 절차
- 근거: [BR-ADMIN-005](../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계), [RV-BR-009](../01-requirements/requirements-review.md#rv-br-009-잘못-등록된-데이터의-정정), [RV-DOMAIN-008](domain-boundaries.md#rv-domain-008-관리자-수정삭제-기능의-mvp-포함-여부)

### RV-WS-006 기본 데이터 등록과 방문 관계 등록의 분리 여부

- 현재 상태: 결정 완료 — 하나의 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 안에서 기본 데이터와 방문 관계 등록의 두 단계로 관리
- 관련 요구사항:
  - [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) ~ [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록)
  - [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 선택지:
  1. 하나의 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)에서 단계와 도메인 계약만 분리
  2. 두 Workstream으로 분리
- 영향: 작업량, 참조 무결성, 관리자 업무 완결성과 개발자 수
- 결정 시점: Role 배정 전

### RV-WS-007 공통 인증 작업의 담당 방식

- 현재 상태: 제품 정책과 담당 결정 완료, 인증 기술은 후속 설계
- 관련 요구사항:
  - [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근)
  - [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제)
- 선택지:
  1. [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 책임자가 공통 작업 구현도 담당
  2. 다른 개발자가 공통 구현을 보조하고 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 인수·적용 책임
- 영향: [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 작업량, 보안 검증과 공통 코드 소유권
- 결정 시점: 구현 시작 전

### RV-WS-008 Workstream 작업량 차이

- 현재 상태: [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)가 가장 넓고 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)은 요구사항 수보다 통합 지원 비중이 큼
- 관련 요구사항: 기능 요구사항 전체
- 조정 원칙:
  - Workstream을 인위적으로 쪼개기보다 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 담당자가 Visit 계약과 관계 기반 통합을 지원한다.
  - [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 담당자는 자신이 소비하는 등록 결과의 인수 테스트를 소유한다.
- 영향: 보조 책임, 일정과 리뷰 부하
- 결정 시점: Role 배정 시

### RV-WS-009 Stub 또는 Fake가 필요한 통합 지점

- 현재 상태: 사용 권장, 구체 도구와 제거 기준은 팀 결정 필요
- 필요 지점:
  - [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색)의 [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 조건 결과
  - [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)의 Visit·Creator·Video 표시 정보
  - [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 Restaurant 표시 정보
  - [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) ~ [WS-03](mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 데이터
  - [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록)의 관리자 인증 제공자
- 영향: 병렬 개발, 계약 일치와 임시 구현 잔존 위험
- 결정 시점: 독립 개발 시작 전

### RV-WS-010 첫 번째 통합 대상

- 현재 상태: [WS-04](mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 기본 맛집 등록 → [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 기본 목록 권장
- 관련 요구사항:
  - [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록)
  - [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)
- 대안: [WS-01](mvp-workstreams.md#5-ws-01-맛집-탐색) 목록 → [WS-02](mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 기본 상세를 먼저 연결
- 영향: 데이터 반영·공개 상태의 조기 검증과 Visit 통합 시점
- 결정 시점: 병렬 개발 중 첫 통합 계획 확정 시

### RV-WS-011 조회 API와 등록 API의 구현 우선순위

- 현재 상태: 계약은 동시 작성하고 구현은 독립 병렬 진행 권장
- 관련 요구사항: 기능 요구사항 전체
- 권장 순서:
  1. Fixture 기반 조회와 기본 데이터 등록을 병렬 구현
  2. 기본 맛집 등록과 목록을 먼저 통합
  3. 방문 관계 등록과 관계 기반 조회를 통합
- 영향: 테스트 데이터, 조기 사용자 가치 검증과 통합 위험
- 결정 시점: 구현 계획 작성 전

### RV-WS-012 맛집 목록의 유튜버 표시 인원과 축약 방식

- 현재 상태: 결정 완료
- 관련 요구사항:
  - [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)
  - [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인)
- 결정 내용: 중복 제거한 채널명을 채널명 오름차순으로 최대 3명까지 표시하고 나머지는 `외 N명`으로 축약한다. 전체 목록은 맛집 상세에서 제공한다.
- 영향: 목록 응답 크기, 성능, 모바일 표시와 계약
- 결정 시점: API·화면 명세 작성 전

### RV-WS-013 일부 상세 제공자 실패의 처리

- 현재 상태: 결정 완료
- 관련 요구사항:
  - [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회)
  - [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인)
  - [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 결정 내용:
  - 맛집 기본 정보 제공자가 실패하면 상세 전체를 실패 처리한다.
  - 방문 관계·유튜버·영상 정보 제공자만 실패하면 기본 정보를 제공하고 정상 빈 결과와 구분되는 일시적 콘텐츠 조회 실패로 처리한다.
  - 비공개·삭제·무효 콘텐츠 제외와 실제 관계 없음은 정상 빈 결과다.
- 영향: 오류 계약, 장애 격리, 관찰성과 사용자 경험
- 결정 시점: API 명세 작성 전
