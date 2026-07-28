---
status: Ready
plan_date: 2026-07-27
owners:
  - 이우람
  - 양성훈
  - 박진영
  - 김인안
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/non-functional-requirements.md
  - ../02-analysis/mvp-workstreams.md
  - ../03-team/ownership.md
  - ../04-product/wireframes/README.md
  - ../05-specs/api/README.md
  - ../05-specs/data/README.md
  - ../06-architecture/README.md
  - ../07-adr/platform/deploy-002-validation-deployment-before-expansion.md
---

# 맛잇온 MVP 구현 계획

## 1. 목표 요약

- 해결 문제: YouTube 영상에 흩어진 맛집 방문 정보를 이름·서울 자치구·음식 카테고리·유튜버 기준으로 탐색하기 어렵다.
- 사용자 가치: 일반 사용자는 계정 없이 검증된 맛집과 방문 근거 영상을 찾고, 관리자는 확인한 데이터를 등록할 수 있다.
- 최종 결과: 로컬 Docker 환경에서 공개 탐색·상세와 관리자 인증·등록의 전체 흐름이 실행되고 핵심 자동화 테스트가 통과한다.
- 가용 인력: 4명 전원 투입

## 2. 요구사항 분석

### 기능 요구사항

- 공개 맛집 목록, 이름 검색과 서울 자치구·대표 음식 카테고리·유튜버 단일 필터를 AND로 조합한다.
- 페이지는 1부터 시작하고 크기는 10·20·50, 기본값은 20이다.
- 상세에서 이름, 카테고리, 도로명주소, 전화번호, Kakao 장소 링크를 제공한다.
- 공개·유효한 방문 관계의 채널명·채널 링크와 영상 제목·썸네일·원본 링크를 제공한다.
- 영상 관계가 없는 맛집도 목록과 상세 기본 정보를 조회한다.
- 사전 발급된 `ADMIN` 계정만 관리자 기능에 접근한다.
- 맛집·유튜버·영상은 검증 미리보기와 확인 Token을 거쳐 등록한다.
- 방문 관계는 세 참조의 존재·공개 상태·영상 채널 일치·중복을 검증해 등록한다.
- Kakao·YouTube는 로컬에서 WireMock으로 검증하고 단위 테스트는 고정 응답 Fake를 사용한다.

### 비기능 요구사항

- 보안: 공개 GET만 무인증, 관리자 API는 JWT·`ADMIN`; 비밀번호·Token·API Key 로그 금지
- 정합성: PostgreSQL FK·UNIQUE·CHECK와 애플리케이션 선검증을 함께 적용
- 안정성: 외부 HTTP 대기는 DB 트랜잭션 밖에서 처리하고 자동 재시도하지 않음
- 관측성: 오류 응답과 대응 로그에 `traceId`; Live·Ready·Dependencies 상태 구분
- 호환성: 360·390·768·1280·1440px에서 핵심 흐름 사용 가능
- 테스트: 단위, PostgreSQL·Redis 통합, WireMock 외부 계약과 핵심 인수 흐름

### 범위 제외

- 1차 확장: 일반 사용자 인증, 찜, 최근 본 맛집, 지도, 유튜버 상세
- 2차 확장: 컬렉션, 인기 맛집, 큐레이션, 제보·신고, 알림
- 3차 확장: 자연어 검색, AI 영상 추출, 동선·코스 추천
- 평점·리뷰·Restaurant 대표 이미지·영업시간·이메일 구독·예약·결제
- EC2·ECR·RDS·CloudWatch와 AWS 운영 배포

## 3. 사용자 흐름 및 예외 시나리오

### 정상 흐름

1. 사용자가 맛집 목록에서 검색·필터·페이지 조건을 선택한다.
2. URL 조건과 같은 공개 맛집 목록을 조회한다.
3. 맛집을 선택해 기본 정보와 방문 유튜버·영상을 확인한다.
4. 관리자는 로그인 후 외부 기준정보 검증 미리보기를 요청한다.
5. 관리자가 후보를 확인해 맛집·유튜버·영상을 등록한다.
6. 관리자가 세 대상을 선택해 방문 관계를 등록한다.
7. 등록 결과가 공개 목록·유튜버 필터·상세에 반영된다.

### 예외 및 경계 조건

- 잘못된 페이지·필터·UUID·URL은 `400`
- 없는 공개 자원은 `404`, 중복·동시 등록은 결정적 `409`
- 관리자 미인증은 `401`, 권한 부족은 `403`
- WireMock 대상 없음·timeout·제한 초과·잘못된 계약은 핵심 Entity 저장 없이 종료
- 확인 Token 만료·재사용·다른 관리자 사용·후보 불일치는 거부
- 등록 중 실패하면 부분 데이터가 남지 않음
- Redis 장애 시 재발급은 fail-closed, 공개 조회는 정상 유지
- 영상 없는 맛집과 검색 결과 없음은 오류가 아닌 정상 빈 상태

## 4. 현재 시스템 조사 결과

| 조사 대상 | 확인 결과 | 계획 영향 |
|---|---|---|
| 애플리케이션 소스 | 없음 | 백엔드·프론트엔드 스캐폴딩부터 필요 |
| 제품·API·데이터 명세 | 문서화 완료 | 문서를 구현 계약으로 사용 |
| 백엔드 | Java 21, Spring Boot 4.1, Gradle Groovy 단일 모듈 | `com.masiton` 도메인 중심 패키지 구성 |
| 프론트엔드 | Next.js 16.2.11, TypeScript 7.0.2 | App Router, URL 검색 상태 사용 |
| 저장소 | PostgreSQL 17.10, Redis 8.8 | Docker와 Testcontainers 사용 |
| 외부 연동 | Kakao·YouTube Port/Adapter 결정 | WireMock 시나리오로 로컬 검증 |
| 와이어프레임 | 확장 기능을 포함한 5개 이미지 | MVP 요소만 적용하고 제외 메뉴 미노출 |
| 배포 | 최종 확장 이후 AWS 배포로 이관 | 이번 일정에는 로컬 통합만 포함 |

## 5. 구현 계획

### 5.1 구현 순서

1. 공통 실행 기반을 먼저 만든다.
   - 이우람: Spring Boot·Gradle·Docker Compose·헬스체크
   - 양성훈: Next.js·공통 Layout·디자인 Token
   - 박진영: Flyway V1~V5·JPA 영속성 기반
   - 김인안: Spring Security·Redis·WireMock Fixture
2. API DTO, 오류 형식, 식별자, 페이지와 공개 상태 계약을 고정한다.
3. 네 Workstream의 독립 수직 슬라이스를 병렬 구현한다.
   - WS-01: 맛집 목록·검색·필터
   - WS-02: 맛집 상세·콘텐츠 조합
   - WS-03: 유튜버 선택 목록·Visit 유효성 판정
   - WS-04: 관리자 인증·기본 데이터 등록
4. 맛집·유튜버·영상 기본 등록을 실제 PostgreSQL과 연결한다.
5. Visit 등록 트랜잭션을 연결한다.
6. WS-03의 관계 판정을 WS-01 유튜버 필터와 WS-02 상세 콘텐츠에 통합한다.
7. 관리자 UI를 실제 인증·등록 API에 연결한다.
8. 관리자 등록 → 공개 목록·필터 → 상세의 전체 인수 흐름을 검증한다.
9. 전체 인수 흐름이 연결되면 기능 추가를 중단하고 결함 수정·회귀 검증만 수행한다.

### 5.2 선행 관계

| 제공 계약·기반 | 선행 제공자 | 후속 작업 | 차단 범위 |
|---|---|---|---|
| Spring Boot·Docker 실행 | T-01 | T-03, T-04와 모든 백엔드 Task | 백엔드 전체 |
| Next.js 공통 화면 골격 | T-02 | T-05, T-06, T-12 | 프론트엔드 전체 |
| Flyway·JPA Adapter | T-03 | T-05~T-09 | 실제 DB 통합 |
| JWT·Redis 인증 | T-04 | T-08, T-12 | 관리자 API·UI |
| WS-01 기본 목록 | T-05 | T-10, T-13 | 유튜버 필터 최종 조합 |
| WS-02 기본 상세 | T-06 | T-11, T-13 | 방문 콘텐츠 조합 |
| WS-03 관계 판정 | T-07 | T-09~T-11 | 관계 등록·필터·상세 |
| 기본 데이터 등록 | T-08 | T-09, T-12, T-13 | Visit와 관리자 전체 흐름 |
| Visit 등록 | T-09 | T-10~T-13 | 모든 관계 기반 조회 |

- 공통 계약이 준비되지 않아도 각 Workstream은 같은 입력·출력 형식의 Fake로 독립 개발한다.
- 실제 통합을 차단하는 핵심 선행 관계는 `T-03 → T-08 → T-09`다.
- 프론트엔드는 API 구현 완료를 기다리지 않고 확정 응답 예제와 타입으로 화면을 구성한다.

### 5.3 병렬 작업 범위

| 실행 순서 | 양성훈 | 박진영 | 이우람 | 김인안 |
|---|---|---|---|---|
| 1. 실행 기반 구성 | Next.js·공통 UI | Flyway·JPA 기반 | Spring Boot·Docker 기반 | 인증·WireMock 기반 |
| 2. 핵심 수직 슬라이스 | WS-01 API·목록 UI | WS-02 API·상세 UI | WS-03 관계 조회 | WS-04 기본 등록 |
| 3. 관계·등록 통합 | 유튜버 필터 통합 준비 | 상세 콘텐츠 통합 준비 | Visit 등록 지원 | 인증·등록 완결 |
| 4. 화면·조회 통합 | 관리자 UI 지원 | Projection·쿼리 지원 | WS-01·02 관계 통합 | 관리자 UI API 연결 |
| 5. 완료 검증 | 통합·회귀·반응형 검증 | 통합·DB 정합성 검증 | 통합·실행 검증 | 통합·권한 검증 |

- 각 담당자는 자기 Workstream의 Domain·Application·Adapter·테스트를 수직으로 소유한다.
- 공통 파일은 동시에 수정하지 않는다. Spring Boot·Docker는 이우람, 프론트 공통 Layout은 양성훈, Flyway 순서는 박진영, 인증 공통은 김인안이 최종 병합한다.
- 공동 작업은 표의 앞 담당자가 최종 병합하고 지원 담당자는 자기 소유 계약만 변경한다.

### 5.4 통합 순서

1. `기본 맛집 등록 → 유튜버 조건 없는 맛집 목록`
   - 스키마, 등록, 공개 상태, 페이지와 공통 오류를 가장 먼저 검증한다.
2. `기본 맛집 등록 → 맛집 상세 기본 정보`
   - 목록 식별자로 상세 진입하고 주소·전화번호·카테고리가 일치하는지 확인한다.
3. `유튜버·영상 등록 → Visit 등록`
   - 세 참조, 영상 채널 일치, 공개 상태와 중복을 검증한다.
4. `Visit 등록 → 유튜버 필터`
   - WS-03 후보 맛집 식별자를 WS-01의 다른 조건과 AND로 결합한다.
5. `Visit 등록 → 상세 콘텐츠`
   - WS-02가 방문 유튜버·영상 Projection을 조합하고 빈 콘텐츠를 정상 처리한다.
6. `관리자 로그인·미리보기·확정 UI → 실제 등록 API`
   - 인증 복구, 확인 Token과 필드 오류 표시를 연결한다.
7. `전체 인수 흐름`
   - 관리자 등록 → 목록 → 유튜버 필터 → 상세 → YouTube 원본 링크 순으로 검증한다.

통합 단계마다 해당 단계의 자동 테스트가 통과해야 다음 단계로 진행한다. 실패한 통합을 Fake로 우회한 채 완료 처리하지 않는다.

### 5.5 Stub·Fake 사용 위치

| 위치 | 대역 | 사용 목적 | 적용 구간 | 제거·전환 조건 |
|---|---|---|---|---|
| Kakao HTTP Adapter | WireMock Stub | 장소 정상·없음·timeout·제한·계약 오류 재현 | 외부 Adapter 개발·통합 전 과정 | 초기 운영 배포 전 실제 Sandbox 계약 테스트 추가 |
| YouTube HTTP Adapter | WireMock Stub | 채널·영상·게시 채널 검증과 오류 재현 | 외부 Adapter 개발·통합 전 과정 | 초기 운영 배포 전 실제 Sandbox 계약 테스트 추가 |
| WS-01 → WS-03 | 유효 맛집 ID Query Port Fake | Visit 구현 전에 유튜버 필터 조합 개발 | WS-01 독립 구현 | T-07 완료 후 실제 Query Adapter로 교체 |
| WS-02 → Visit 콘텐츠 | 상세 콘텐츠 Query Port Fake | 관계 데이터 없이 기본·빈·부분 실패 UI 개발 | WS-02 독립 구현 | T-07·T-09 완료 후 실제 Projection으로 교체 |
| WS-04 기본 등록 | Repository Port Fake | 입력·중복·확인 Token Application 규칙 단위 테스트 | 단위 테스트 | 운영 코드에는 사용하지 않고 테스트 소스에 유지 |
| 인증 | Token·Clock Fake | 만료·회전·재사용을 결정적으로 검증 | 단위 테스트 | 테스트 소스에 유지, 실제 통합은 Redis Adapter 사용 |
| 프론트엔드 | 고정 API 응답 Fixture | 백엔드 완성 전 목록·상세·관리자 상태 렌더링 | 화면 독립 구현 | 관련 API 완료 후 실제 fetch로 교체 |

- WireMock은 실제 Kakao·YouTube 네트워크 호출을 대체하는 MVP의 확정 검증 수단이므로 제거하지 않는다.
- Workstream 간 Fake와 프론트 고정 응답은 병렬 개발용 임시 대역이다. 관련 선행 Task가 끝나면 실제 구현으로 교체한다.
- 임시 대역은 테스트 소스 또는 명시적 로컬 테스트 프로파일에만 둔다. 기본 실행 프로파일에 Fake Bean을 등록하지 않는다.
- 통합 완료 시 `fake`, `stub`, `fixture` 사용 위치를 검색해 테스트 전용인지 확인한다.

## 6. Workstream별 Task 분해

### 6.1 WS-01 맛집 탐색

- 담당자: 양성훈
- 지원·리뷰: 이우람(Visit 조회 계약), 김인안(등록 데이터 반영)
- 관련 요구사항:
  - [FR-RESTAURANT-001~007](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회)
- 관련 PRD:
  - [PRD-DISCOVERY-001 맛집 탐색](../04-product/prd/discovery/restaurant-discovery.md)
- 관련 API:
  - [API-DISCOVERY-001 맛집 목록 및 조건 검색](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)
  - [공통 페이지네이션](../05-specs/api/common/pagination-contract.md)
  - [공통 필터](../05-specs/api/common/filtering-contract.md)
- 관련 ADR:
  - [ADR-ARCH-001 도메인 모놀리스](../07-adr/architecture/arch-001-domain-monolith.md)
  - [ADR-DATA-003 Spring Data JPA](../07-adr/data/data-003-spring-data-jpa.md)
  - [ADR-WEB-002 프론트엔드 데이터·상태](../07-adr/platform/web-002-data-state.md)
  - [ADR-DATA-008 공개·삭제 생명주기](../07-adr/data/data-008-publication-lifecycle-soft-delete.md)
- 관련 테이블: `restaurant`, `region`, `food_category`; 유튜버 조건 사용 시 `visit`, `creator`, `video`

| Task | 구현 범위 | 테스트 범위 | 완료 조건 |
|---|---|---|---|
| WS1-01 맛집 조건 Query를 구현한다 | 이름 포함 검색, 자치구·카테고리 조건, 공개 상태, UUID Projection | 단일 조건, 전체 AND 조합, 잘못된 enum·UUID | 확정 조건과 공개 맛집만 반환 |
| WS1-02 안정 정렬과 페이지를 구현한다 | 1-base 페이지, 크기 10·20·50, 기본 20, 안정 정렬 | 첫·마지막·범위 밖 페이지, 중복·누락 | 페이지 이동 중 항목 중복·누락 0건 |
| WS1-03 유튜버 후보 Port를 결합한다 | WS-03이 제공한 맛집 ID 후보와 나머지 조건을 AND 결합 | 후보 없음·다수·중복, 비공개 관계 | 유튜버 조건 결과가 Visit 정책과 일치 |
| WS1-04 공개 목록 UI를 구현한다 | URL 검색 상태, 검색·필터·페이지, 빈 결과·오류, 반응형 | 새로고침·뒤로가기·공유 URL, 지정 화면 폭 | 같은 URL이 같은 결과를 재현 |
| WS1-05 탐색 인수 테스트를 추가한다 | Controller부터 PostgreSQL까지 실제 조회 | 기본·전체 조합·빈 결과·경계 페이지 | API 계약 및 요구사항 인수 조건 통과 |

### 6.2 WS-02 맛집 상세 및 콘텐츠 조회

- 담당자: 박진영
- 지원·리뷰: 이우람(Visit 판정), 김인안(등록 데이터), 양성훈(목록 식별자)
- 관련 요구사항:
  - [FR-RESTAURANT-008~011](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회)
  - [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인)
  - [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인)
- 관련 PRD:
  - [PRD-DETAIL-001 맛집 상세 및 콘텐츠 조회](../04-product/prd/detail/restaurant-detail.md)
- 관련 API:
  - [API-DETAIL-001 맛집 상세 조회](../05-specs/api/detail/restaurant-detail-api.md#api-detail-001-맛집-상세-조회)
  - [공통 식별자](../05-specs/api/common/identifier-contract.md)
  - [공통 오류](../05-specs/api/common/error-contract.md)
- 관련 ADR·설계:
  - [ADR-ARCH-001 도메인 모놀리스](../07-adr/architecture/arch-001-domain-monolith.md)
  - [ADR-DATA-003 Spring Data JPA](../07-adr/data/data-003-spring-data-jpa.md)
  - [조회 조합 설계](../06-architecture/query-composition.md)
  - [ADR-DATA-008 공개·삭제 생명주기](../07-adr/data/data-008-publication-lifecycle-soft-delete.md)
- 관련 테이블: `restaurant`, `region`, `food_category`, `visit`, `creator`, `video`

| Task | 구현 범위 | 테스트 범위 | 완료 조건 |
|---|---|---|---|
| WS2-01 맛집 기본 상세 Query를 구현한다 | 이름·카테고리·도로명주소·전화번호·Kakao 링크, 공개 상태 | 정상, 없는 UUID, 비공개·삭제 맛집 | 공개 맛집만 계약 필드로 조회 |
| WS2-02 방문 콘텐츠 Projection을 구현한다 | 유효 Visit의 채널명·채널 링크·영상 제목·썸네일·원본 링크 | 비공개 Creator·Video·Visit, 중복 관계 | 유효 콘텐츠만 중복 없이 표시 |
| WS2-03 상세 조합 서비스를 구현한다 | 기본 정보와 콘텐츠 결과, 필수 `contentStatus`, 빈 콘텐츠 정상화 | 콘텐츠 없음, 조회 실패, 부분 실패 | 기본 정보와 콘텐츠 상태 계약 일치 |
| WS2-04 N+1과 결과 크기를 통제한다 | Projection 또는 묶음 조회, Lazy 순회 금지 | 관계 0·1·다수의 쿼리 수 | Visit 수에 비례한 추가 쿼리 없음 |
| WS2-05 상세 UI와 인수 테스트를 구현한다 | 기본 정보·외부 링크·영상 카드·빈/오류 상태, 반응형 | 목록→상세, 영상 없음, 잘못된 ID | 핵심 상세 흐름과 API 계약 통과 |

### 6.3 WS-03 유튜버 기반 탐색

- 담당자: 이우람
- 지원·리뷰: 양성훈(최종 목록 결합), 박진영(상세 콘텐츠)
- 관련 요구사항:
  - [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회)
  - [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회)
- 관련 PRD:
  - [PRD-DISCOVERY-002 유튜버 기반 탐색](../04-product/prd/discovery/creator-discovery.md)
- 관련 API:
  - [API-CREATOR-DISCOVERY-001 유튜버 선택 목록](../05-specs/api/discovery/creator-discovery-api.md#api-creator-discovery-001-유튜버-필터-선택-목록)
  - [API-DISCOVERY-001 맛집 목록 및 조건 검색](../05-specs/api/discovery/restaurant-discovery-api.md#api-discovery-001-맛집-목록-및-조건-검색)
- 관련 ADR·설계:
  - [ADR-ARCH-001 도메인 모놀리스](../07-adr/architecture/arch-001-domain-monolith.md)
  - [ADR-ARCH-002 외부 Port·Adapter](../07-adr/architecture/arch-002-external-ports-adapters.md)
  - [ADR-DATA-003 Spring Data JPA](../07-adr/data/data-003-spring-data-jpa.md)
  - [모듈 경계](../06-architecture/module-boundaries.md)
- 관련 테이블: `creator`, `visit`, `restaurant`, `video`

| Task | 구현 범위 | 테스트 범위 | 완료 조건 |
|---|---|---|---|
| WS3-01 공개 유튜버 선택 목록을 구현한다 | 식별자·현재 채널명 최소 응답, 공개·삭제 상태 적용 | 0·1·다수, 비공개·삭제 Creator | 프로필·구독자 없이 최소 계약만 반환 |
| WS3-02 유효 Visit 판정 정책을 구현한다 | Restaurant·Creator·Video·Visit의 공개·유효 조합 | 대상별 비공개·삭제와 외부 영상 이용 불가 | 모든 조회가 같은 판정 결과 사용 |
| WS3-03 유튜버별 맛집 후보 Port를 구현한다 | Creator ID로 중복 없는 Restaurant ID 집합 제공 | 중복 Visit, 관계 없음, 잘못된 ID | WS-01이 재판정 없이 후보 사용 가능 |
| WS3-04 상세 콘텐츠 조회 계약을 제공한다 | 맛집 기준 유효 Visit 표시 데이터 계약 | 채널·영상 중복과 정렬 | WS-02 Projection과 의미가 일치 |
| WS3-05 관계 판정 통합 테스트를 추가한다 | PostgreSQL 실제 상태 조합 Fixture | 공개 상태 전 조합과 중복 제거 | WS-01·02 계약 테스트가 같은 Fixture로 통과 |

### 6.4 WS-04 관리자 데이터 등록

- 담당자: 김인안
- 지원·리뷰: 이우람(인증·Visit), 박진영(상세 반영), 양성훈(목록 반영)
- 관련 요구사항:
  - [FR-ADMIN-001~004](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근)
  - [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록)
- 관련 PRD:
  - [PRD-ADMIN-001 관리자 데이터 등록](../04-product/prd/admin/admin-data-management.md)
- 관련 API:
  - [API-ADMIN-AUTH-001~003 관리자 인증](../05-specs/api/admin/authentication-api.md#api-admin-auth-001-관리자-로그인)
  - [관리자 기본 데이터 API](../05-specs/api/admin/reference-data-api.md)
  - [API-ADMIN-VISIT-001 방문 관계 등록](../05-specs/api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록)
- 관련 ADR:
  - [ADR-AUTH-001 Spring Security JWT](../07-adr/security/auth-001-spring-security-jwt.md)
  - [ADR-AUTH-003 확인 Token](../07-adr/security/auth-003-confirmation-token.md)
  - [ADR-DATA-005 Redis Refresh Token](../07-adr/data/data-005-redis-refresh-token.md)
  - [ADR-EXT-001 외부 기준정보 확인](../07-adr/integration/ext-001-reference-verification.md)
  - [ADR-ARCH-002 외부 Port·Adapter](../07-adr/architecture/arch-002-external-ports-adapters.md)
- 관련 테이블·저장소: `admin_account`, `confirmation_token`, `region`, `food_category`, `restaurant`, `creator`, `video`, `visit`, Redis Refresh Token·로그인 실패 키

| Task | 구현 범위 | 테스트 범위 | 완료 조건 |
|---|---|---|---|
| WS4-01 관리자 인증을 구현한다 | 로그인, RS256 Access Token, Refresh 회전·재사용 탐지, 로그아웃, matcher | 정상·실패·잠금, 만료·변조·issuer/audience, Redis 장애 | 공개 GET과 보호 API의 200·401·403 계약 통과 |
| WS4-02 Kakao·YouTube 미리보기를 구현한다 | Port/Adapter, WireMock, timeout, URL·최종 호스트, 후보 변환 | 정상·없음·timeout·제한·잘못된 계약 | 외부 실패 시 핵심 Entity 저장 0건 |
| WS4-03 확인 Token을 구현한다 | SHA-256 해시, 후보 Snapshot, 10분 만료, 관리자 귀속, 원자 소비 | 만료·재사용·변조·다른 관리자·동시 확정 | 한 Token으로 논리 자원 하나만 생성 |
| WS4-04 맛집 등록을 구현한다 | 지역·카테고리·Kakao 후보·중복·공개 상태 검증 | 필수값, 동일 장소, 동시 등록 | 하나만 생성되고 목록·상세에 반영 |
| WS4-05 유튜버·영상 등록을 구현한다 | 외부 ID 고유성, 영상 게시 채널, URL·썸네일 저장 | 중복 채널·영상, 채널 불일치 | 관계 등록 가능한 선행 데이터 생성 |
| WS4-06 Visit 등록 트랜잭션을 구현한다 | 세 참조, 공개 상태, 영상 채널 일치, 복합 UNIQUE | 존재하지 않는 참조, 비공개, 중복·동시 요청, rollback | 부분 저장 0건, 논리 관계 한 건 |
| WS4-07 관리자 UI를 구현한다 | 로그인, 미리보기·후보 확인·확정, Visit Form, 필드 오류 | 인증 복구, Token 만료, 중복, WireMock 오류 | 브라우저에서 전체 관리자 흐름 완료 |
| WS4-08 등록→조회 인수 테스트를 추가한다 | 기본 데이터·Visit 등록 후 목록·필터·상세 확인 | 정상·중복·부분 실패·권한 우회 | 세 공개 조회에 같은 커밋 결과 반영 |

### 6.5 Workstream 공통 완료 규칙

- 각 Task는 대상 Workstream의 운영 코드, 자동화 테스트와 관련 계약 문서 갱신을 함께 완료한다.
- DB 제약·공개 상태·오류 코드는 Workstream별로 재정의하지 않고 공통 명세를 사용한다.
- 임시 Workstream Fake는 실제 Port 통합 후 테스트 전용으로 격리하거나 제거한다.
- 담당자 테스트만 통과한 상태는 완료가 아니다. 영향받는 Workstream 계약 테스트까지 통과해야 한다.
- `WS4-04 기본 맛집 등록 → WS1 목록 → WS2 상세`와 `WS4-06 Visit 등록 → WS3 판정 → WS1 필터·WS2 콘텐츠`의 두 통합 경로가 모두 통과해야 Workstream 구현을 완료 처리한다.

## 7. 순서별 구현 계획

### 1단계 — 실행 기반과 계약 고정

- 목표: 네 Workstream이 독립 개발할 수 있는 빌드·DB·API 경계를 제공한다.
- 작업: Spring Boot·Next.js·Docker 스캐폴딩, Flyway, 공통 오류, WireMock Fixture
- 산출물: 빌드 가능한 빈 수직 구조와 로컬 의존 서비스
- 종료 조건: 네 명 모두 같은 명령으로 애플리케이션과 테스트를 실행한다.

### 2단계 — Workstream별 핵심 수직 슬라이스

- 목표: 목록, 상세, 유튜버 조건, 관리자 기본 등록을 독립 완성한다.
- 작업: WS-01~04 API·데이터 접근·최소 UI·자동 테스트
- 산출물: Fake 또는 실제 Port로 독립 시연 가능한 네 흐름
- 종료 조건: 각 담당자의 기능 테스트와 대표 화면이 동작한다.

### 3단계 — 관계·인증·화면 통합

- 목표: 실제 Port로 교체하고 등록 데이터가 세 공개 조회에 반영되게 한다.
- 작업: JWT·Redis, 확인 Token, Visit 등록, 유튜버 필터, 상세 콘텐츠, 관리자 UI
- 산출물: 관리자 등록 → 공개 조회의 통합 흐름
- 종료 조건: 핵심 인수 시나리오가 로컬에서 한 번 이상 성공한다.

### 4단계 — 결함 수정과 완료 검증

- 목표: 신규 기능 추가 없이 핵심 경로를 안정화한다.
- 작업: 권한·중복·rollback·빈 상태·반응형 테스트, 실행 문서 정리
- 산출물: 통합 테스트 결과와 재현 가능한 실행 절차
- 종료 조건: 완료 정의 체크리스트와 필수 CI가 통과한다.

## 8. Task 목록

| ID | 담당 | Task | 상세 작업 | 예상 영역 | 선행 | 병렬 | 크기 | 완료 조건 |
|---|---|---|---|---|---|---|---|---|
| T-01 | 이우람 | 백엔드·Docker 실행 기반을 구성한다 | Gradle, Spring Boot, PostgreSQL·Redis·WireMock Compose, 헬스체크 | 루트, backend, infra | 없음 | 가능 | M | 깨끗한 환경에서 기동·기본 테스트 성공 |
| T-02 | 양성훈 | Next.js와 공통 MVP 화면 골격을 구성한다 | App Router, 디자인 Token, 헤더·푸터·폼·카드, 제외 메뉴 제거 | frontend | 없음 | 가능 | M | 지정 화면 폭에서 공통 Layout 렌더링 |
| T-03 | 박진영 | Flyway 스키마와 JPA 영속성 기반을 구현한다 | V1~V5, 제약·인덱스·기준 데이터, Adapter 골격 | migration, persistence | T-01 인터페이스 | 가능 | M | 빈 DB migration·JPA validate·제약 테스트 성공 |
| T-04 | 김인안 | 관리자 인증과 외부 검증 테스트 기반을 구현한다 | JWT matcher, Redis Token, WireMock 정상·오류 Fixture | security, external | T-01 인터페이스 | 가능 | M | 401·403·로그인·재발급·외부 오류 테스트 성공 |
| T-05 | 양성훈 | 맛집 검색 API와 목록 화면을 완성한다 | 이름·구·카테고리 조건, 페이지·정렬, URL 상태·빈 결과 | restaurant, public UI | T-02, T-03 | 가능 | M | 허용 조건 조합과 페이지 인수 테스트 성공 |
| T-06 | 박진영 | 맛집 상세 API와 상세 화면을 완성한다 | 기본 정보·Kakao 링크·콘텐츠 상태·영상 없는 정상 응답 | orchestration detail, UI | T-02, T-03 | 가능 | M | 기본·빈 콘텐츠·없는 자원 테스트 성공 |
| T-07 | 이우람 | 유튜버 선택 목록과 Visit 조회 계약을 구현한다 | 공개 유튜버 최소 목록, 유효 관계 판정·중복 제거 | creator, visit query | T-03 | 가능 | M | 비공개·삭제·중복 관계 제외 테스트 성공 |
| T-08 | 김인안 | 기본 데이터 등록 API를 구현한다 | 미리보기·확인 Token·맛집·유튜버·영상 등록, 중복 처리 | admin, domain commands | T-03, T-04 | 가능 | M | 세 자원의 정상·중복·만료·재사용 테스트 성공 |
| T-09 | 이우람·김인안 | 방문 관계 등록 트랜잭션을 완성한다 | 참조·공개·채널 일치·복합 중복 검증과 rollback | orchestration visit | T-07, T-08 | 불가 | M | 실패 부분 저장 0건, 동시 중복 한 건 |
| T-10 | 양성훈·이우람 | 유튜버 조건을 최종 맛집 목록에 통합한다 | Visit 후보와 나머지 AND 조건·페이지 결합 | restaurant query | T-05, T-07 | 불가 | S | 모든 허용 필터 조합 테스트 성공 |
| T-11 | 박진영·이우람 | 상세에 방문 유튜버와 영상을 통합한다 | 공개 관계 Projection, N+1 방지, `contentStatus` | detail query | T-06, T-07, T-09 | 불가 | S | 등록 관계가 상세에 한 번씩 표시 |
| T-12 | 양성훈·김인안 | 관리자 로그인·등록 화면을 완성한다 | 로그인, 미리보기·확정, Visit 등록 Form과 오류 표시 | admin UI | T-02, T-04, T-08, T-09 | 불가 | M | 관리자 전체 등록 흐름 브라우저 시연 성공 |
| T-13 | 전원 | 관리자 등록부터 공개 조회까지 인수 테스트한다 | 등록 → 목록·필터 → 상세, 권한·빈 상태·rollback | 전체 | T-09~T-12 | 불가 | M | 핵심 인수 시나리오 전체 성공 |
| T-14 | 전원 | 로컬 실행·회귀 검증 결과를 확정한다 | CI, 반응형, 비밀·로그 검사, README 실행 절차 | 전체 | T-13 | 불가 | S | 완료 정의 체크리스트와 필수 명령 통과 |

`T-09`부터 `T-12`의 공동 담당 Task는 앞에 적힌 담당자가 최종 병합 책임을 갖고 뒤 담당자가 자신의 계약 영역을 지원한다.

`T-13`의 자동화 검증은 `AdminRegistrationJourneyAcceptanceTest`에서 실제 PostgreSQL·Redis와 WireMock을 사용해 관리자 로그인, 세 기준정보 미리보기·확정, Visit 등록, 공개 목록·유튜버 필터·상세·유튜버 선택 목록 반영을 하나의 사용자 여정으로 실행한다. 같은 테스트 묶음에서 무인증 접근, 빈 상태, 외부 검증 실패, 중복 Visit의 부분 저장 방지도 확인한다. 실제 Kakao·YouTube API와 운영 키는 사용하지 않는다.

## 9. 의존성 및 실행 순서

- 1차 병렬 실행: `T-01`, `T-02`, `T-03`, `T-04`
- 2차 병렬 실행: `T-05`, `T-06`, `T-07`, `T-08`
- 관계·화면 통합: `T-09 → T-10/T-11/T-12`
- 최종 통합과 검증: `T-13 → T-14`
- 핵심 경로: `T-01 → T-03 → T-08 → T-09 → T-12 → T-13 → T-14`
- 첫 통합 시점: 맛집 등록 결과를 유튜버 조건 없는 목록에서 확인
- 두 번째 통합 시점: Visit 등록 결과를 유튜버 필터와 상세에 동시에 연결
- 전체 인수 흐름 연결 이후: 완료 정의에 없는 기능과 리팩터링 착수 금지

## 10. 테스트 및 검증 계획

| 수준 | 검증 대상 | 주요 시나리오 | 성공 기준 |
|---|---|---|---|
| 단위 | 도메인·입력 규칙 | 상태 판정, URL, 카테고리, 관계 일치 | 핵심 분기 전부 통과 |
| 통합 | PostgreSQL·Redis | FK·UNIQUE·rollback·Token 회전 | 정합성 위반·부분 저장 0건 |
| 외부 계약 | WireMock | 성공·없음·timeout·제한·계약 오류 | 핵심 Entity 오저장 0건 |
| API 인수 | 공개·관리자 API | 검색·필터·상세·등록·권한 | 명세 상태·응답 계약 일치 |
| UI 통합 | 공개·관리자 화면 | URL 유지, 빈 상태, 등록 오류 | 핵심 흐름 차단 결함 0건 |
| 반응형 수동 | 지정 화면 폭 | 목록·상세·관리자 Form | 가로 잘림 없이 작업 완료 |
| 회귀 | 전체 | 등록 결과의 세 공개 조회 반영 | 필수 CI 명령 전부 통과 |

성능 전체 부하 시험은 MVP 완료 조건에서 제외하지 않지만, 기준 데이터 자동 생성이나 도구 설치가 핵심 흐름을 지연시키면 쿼리 수·실행계획 Smoke Test까지만 수행하고 정식 p95 측정을 후속 안정화 위험으로 기록한다.

## 11. 배포 및 롤백 계획

- 이번 단계의 배포: 없음. 로컬 Docker 통합 실행이 완료 조건이다.
- 데이터 변경: Flyway V1~V5를 빈 PostgreSQL에 적용하고 파일 수정 대신 후속 migration 원칙을 사용한다.
- 로컬 복구: 컨테이너와 볼륨 초기화 후 migration·seed를 재적용한다.
- 기능 플래그: 도입하지 않는다. 확장 기능 Route·메뉴 자체를 만들지 않는다.
- AWS 배포: [ADR-DEPLOY-002](../07-adr/platform/deploy-002-validation-deployment-before-expansion.md)에 따라 이 단계 완료 후 M2 초기 운영 배포에서 다음 확장 단계보다 먼저 수행한다.

## 12. 위험 요소

| 위험 | 가능성 | 영향 | 대응 |
|---|---:|---:|---|
| 신규 코드베이스에서 MVP 전체를 구현 | 높음 | 높음 | 수직 슬라이스 병렬화, 전체 인수 흐름 연결 후 기능 동결 |
| T-08·T-09에 WS-04 작업 집중 | 높음 | 높음 | 이우람이 Visit, 박진영이 Projection 통합 지원 |
| 공통 계약 변경으로 병렬 작업 충돌 | 높음 | 높음 | 1단계에서 DTO·Port 고정, 변경 시 네 명 즉시 공유 |
| 최신 고정 버전 설치 호환 문제 | 중간 | 높음 | T-01·T-02를 먼저 실행해 설치 호환성을 검증 |
| 실제 외부 API와 WireMock 차이 | 중간 | 중간 | Adapter 경계 유지, 초기 운영 배포 전 Sandbox 계약 검증 |
| 와이어프레임의 확장 기능 혼입 | 중간 | 중간 | 와이어프레임 검수 체크리스트 적용 |
| 정식 성능 검증 시간 부족 | 높음 | 중간 | 쿼리 수·실행계획 우선, 미달 항목 명시 |
| 통합 이후 결함 수정 범위 증가 | 높음 | 높음 | 전체 인수 흐름 연결 후 신규 기능을 금지하고 핵심 경로만 수정 |

## 13. 가정 및 확인 결과

### 확정

- 구현 범위는 문서에 정의된 MVP 전체다.
- 4명이 MVP 구현에 모두 투입된다.
- MVP 구현 단계는 로컬 Docker 통합 검증으로 완료하며 이 단계에서는 AWS에 배포하지 않는다.
- AWS 배포는 MVP 구현 완료 후 M2 초기 운영 배포에서 1차 확장보다 먼저 수행한다.
- 제공된 와이어프레임은 MVP 요소만 적용한다.
- 별도 와이어프레임이 없는 목록·상세·관리자 화면은 같은 디자인 언어로 파생한다.
- Kakao·YouTube는 WireMock, 단위 테스트는 고정 응답 Fake로 검증한다.

### 가정

- 네 명 모두 로컬에서 Docker를 실행할 수 있다. 틀리면 `T-01`, `T-03`, `T-04`가 차단된다.
- 디자인 폰트·아이콘 라이선스는 별도 유료 자산을 요구하지 않는다. 틀리면 `T-02`, `T-05`, `T-06`, `T-12`가 영향받는다.
- 구현 중 제품 범위와 API·데이터 계약을 추가 변경하지 않는다. 틀리면 모든 병렬 Task의 재작업이 발생한다.

### 확인 필요

구현 착수를 막는 제품 범위 질문은 모두 해결됐다. 실제 구현 중 명세 충돌이 발견되면 임의로 기능을 추가하지 않고 상위 범위에 맞는 가장 작은 해석을 적용한 뒤 결정 기록을 남긴다.

## 14. 완료 정의

- [ ] 네 명 모두 문서화된 명령으로 로컬 전체 환경을 실행할 수 있다.
- [ ] 공개 맛집 목록, 이름 검색과 세 필터의 AND 조합이 동작한다.
- [ ] 페이지 크기·정렬·빈 결과·잘못된 입력이 계약대로 처리된다.
- [ ] 상세 기본 정보와 방문 유튜버·영상이 표시된다.
- [ ] 영상 없는 맛집도 정상 조회된다.
- [ ] 관리자 로그인·재발급·로그아웃과 접근 통제가 동작한다.
- [ ] WireMock 미리보기와 확인 Token을 거쳐 세 기본 자원을 등록한다.
- [ ] Visit 관계의 참조·채널 일치·중복·rollback이 검증된다.
- [ ] 등록 결과가 목록·유튜버 필터·상세에 반영된다.
- [ ] 지도·테마·보관함·일반 로그인 등 확장 기능이 노출되지 않는다.
- [ ] 비밀번호·Token·API Key 원문이 로그·응답·저장소에 없다.
- [ ] 핵심 단위·통합·계약·인수 테스트가 통과한다.
- [ ] 지정 화면 폭에서 공개·관리자 핵심 흐름을 완료할 수 있다.
- [ ] AWS 리소스를 생성하거나 운영 배포하지 않는다.

## 가장 먼저 착수할 Task

1. `T-01` 백엔드·Docker 실행 기반을 구성한다.
2. `T-02` Next.js와 공통 MVP 화면 골격을 구성한다.
3. `T-03` Flyway 스키마와 JPA 영속성 기반을 구현한다.
