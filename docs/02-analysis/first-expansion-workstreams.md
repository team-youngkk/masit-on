---
related_documents:
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - domain-boundaries.md
  - mvp-workstreams.md
  - ../03-team/ownership.md
  - ../04-product/README.md
  - ../04-product/traceability.md
  - ../04-product/user-flows/first-expansion-user-flows.md
---

# 맛잇온 1차 확장 Workstream

## 1. 목적

이 문서는 확정된 1차 확장 기능을 독립적인 사용자 목표 단위로 나누고 WS-05~WS-08의 최종 책임, 의존성과 완료 경계를 정의한다. WS-01~WS-04의 MVP 책임은 [MVP Workstream](mvp-workstreams.md)이 계속 소유한다. 제품 기능이 아닌 제한 공개 진입 경계는 번호형 WS를 추가하지 않고 `OPS-VALIDATION` 공통 운영·배포 트랙으로 관리한다.

## 2. 구성과 확정 배정

| Workstream | 사용자 가치 | 관련 요구사항 | 최종 책임자 | 기본 리뷰어 | 상태 |
|---|---|---|---|---|---|
| [WS-05](first-expansion-workstreams.md#4-ws-05-사용자-계정인증) 사용자 계정·인증 | 개인화 데이터를 여러 기기에서 안전하게 사용 | `FR-MEMBER-001`~`005`, `FR-AUTH-001`~`003` | 김인안 | 이우람 | 확정 |
| [WS-06](first-expansion-workstreams.md#5-ws-06-개인-맛집-관리) 개인 맛집 관리 | 다시 찾을 맛집과 이전에 본 맛집으로 복귀 | `FR-FAVORITE-001`~`004`, `FR-RECENT-001`~`003` | 박진영 | 김인안 | 확정 |
| [WS-07](first-expansion-workstreams.md#6-ws-07-지도-탐색) 지도 탐색 | 필터 결과를 유지하는 맛집 위치와 분포 탐색 | `FR-MAP-001`~`002` | 양성훈 | 박진영 | 확정 |
| [WS-08](first-expansion-workstreams.md#7-ws-08-유튜버-상세) 유튜버 상세 | 채널·방문 맛집·근거 영상을 한곳에서 탐색 | `FR-CREATOR-004`~`006` | 이우람 | 박진영 | 확정 |

각 기능 요구사항은 하나의 확장 Workstream만 완료 책임을 가진다. 기존 Workstream은 필요한 공개 데이터와 판정 계약을 제공하되 확장 기능의 최종 사용자 인수 책임을 중복 소유하지 않는다.

### OPS-VALIDATION 공통 운영·배포 트랙

`OPS-VALIDATION`은 사용자 가치 단위의 제품 Workstream이 아니라 정식 공개 전까지만 존재하는 교차 운영 트랙이다. 따라서 WS-09 이후의 제품 Workstream 번호를 소비하지 않는다.

| 구분 | 확정 내용 |
|---|---|
| 최종 책임자 / 기본 리뷰어 | 이우람 / 김인안 |
| 소유 계약 | `NFR-SECURITY-001`, `NFR-SECURITY-003`, `NFR-DEPLOYMENT-002`, `NFR-DEPLOYMENT-004`; [검증 참여자 API 계약](../05-specs/api/common/validation-access-contract.md); [ADR-DEPLOY-004](../07-adr/platform/deploy-004-public-api-validation-gate-boundary.md) |
| 구현·검증 Task | [FE-12](../08-planning/expansion-1-implementation-plan.md#8-전체-task-표), [E1-T13](../08-planning/expansion-1-task-breakdown.md#e1-t13-검증-참여자-제한-공개-쿠키-세션-전환) |
| 구현 경계 | 검증 로그인 화면·세션 API·내부 검증 Adapter, Redis `auth:verification:` namespace와 실패 제한, Parameter Store 비밀 주입, Nginx `auth_request`, Basic Auth 제거, 배포·관측·브라우저 회귀 |
| 협업 경계 | WS-05의 회원·관리자 Bearer 인증 계약은 변경하지 않고 동시 동작만 회귀 검증한다. M2 운영 기준선과 Nginx·Redis·비밀정보 구성을 사용한다. |
| 완료 판단 | 최초 검증 로그인 뒤 7일 동안 페이지 이동·새로고침·회원 로그인에서 반복 인증창 0회, 무세션·Redis 장애 fail-closed, 비밀정보 로그 0건, 정식 공개 제거 리허설 통과 |
| 종료 조건 | 정식 공개 Task에서 로그인 화면/API, 쿠키, Redis key, Parameter Store 값, Nginx subrequest와 전용 테스트·알람을 함께 제거한다. |

## 3. 공통 선행 작업

| 선행 계약 | 주 조율 | 영향 Workstream | 완료 판단 |
|---|---|---|---|
| 회원 identity·권한·JWT audience·쿠키·Redis namespace 분리 | 김인안 | WS-05, WS-06 | 인증 ADR과 API·보안 계약 승인 |
| 회원 개인정보·탈퇴 정리·메일 실패 정책 | 김인안 | WS-05, WS-06 | 개인정보·운영·장애 인수 시나리오 승인 |
| 맛집 좌표와 기존 데이터 backfill | 박진영 | WS-07, WS-04 | 데이터·마이그레이션 계약 및 backfill 검증 계획 승인 |
| Kakao Maps SDK 키·오류·호출량 경계 | 양성훈 | WS-07 | 외부 연동 계약과 브라우저 검증 계획 승인 |
| Creator 상세 표시 정보와 관계 목록 계약 | 이우람 | WS-08, WS-03, WS-04 | API·데이터 계약과 관리자 확인 흐름 승인 |
| 공통 인증 만료·권한·빈·비공개·삭제 화면 상태 | 양성훈 | WS-05~WS-08 | 사용자 흐름·와이어프레임 인수 리뷰 완료 |
| 검증 참여자 쿠키 세션·Nginx·Redis 진입 경계 | 이우람 | OPS-VALIDATION, WS-05 | API·ADR·M2 계약 승인과 `E1-T13` 브라우저·보안·제거 회귀 완료 |

## 4. WS-05 사용자 계정·인증

### 책임

- 이메일 회원가입, 가입 인증, 로그인, Access Token 재발급과 로그아웃
- 비밀번호 재설정, 회원 탈퇴와 계정 상태 전이
- 회원용 Token·세션, 최대 3개 활성 세션, 실패·호출 제한과 계정 열거 방지
- Redis·메일 장애의 fail-closed 처리와 공개 조회 장애 격리

### 의존성

- WS-06은 인증된 회원 식별자와 탈퇴 정리 계약을 사용한다.
- 기존 관리자 인증 구현 중 JWT RS256·Redis Refresh Token의 검증된 기술 요소는 재사용할 수 있지만 identity, audience, 역할, 쿠키와 namespace는 분리한다.
- 공개 조회 Workstream은 인증 장애에 의존하지 않는다.

### 완료 경계

[사용자 계정·인증 PRD](../04-product/prd/account/member-authentication.md)의 전체 여정과 관련 `FR-MEMBER-*`, `FR-AUTH-*`, `BR-MEMBER-*`, `BR-AUTH-*`, 보안·개인정보 NFR을 자동화 검증하고 후속 API·데이터·ADR 계약과 일치시키면 완료한다.

## 5. WS-06 개인 맛집 관리

### 책임

- 찜 추가·해제·현재 상태와 최신순 페이지 목록
- 상세 정상 조회에 따른 최근 기록 생성·갱신과 최신순 페이지 목록
- 회원별 소유권, 중복·동시성, 비공개·삭제 숨김과 탈퇴 연계 삭제
- 최근 기록 최대 50개·30일 보존과 빈 목록 경험

### 의존성

- WS-05의 인증된 회원 식별자와 탈퇴 정리 신호
- WS-01·WS-02의 공개 맛집 요약·상세와 공개 상태 판정
- Restaurant 내부 상태를 직접 소유하지 않고 공개 판정 계약만 사용한다.

### 완료 경계

[개인 맛집 관리 PRD](../04-product/prd/personal/personal-restaurant-management.md)의 정상·빈·비공개·삭제·다른 회원 접근·동시 요청 흐름과 `FR-FAVORITE-*`, `FR-RECENT-*`를 자동화 검증하면 완료한다.

## 6. WS-07 지도 탐색

### 책임

- Kakao 지도, 공개 맛집 마커와 키보드 접근 가능한 대체 목록
- 마커·목록 선택 동기화, 요약과 상세 이동
- URL의 이름·자치구·카테고리·유튜버 탐색 조건 AND 결합
- 지도 이동·확대·축소 중 기존 마커·목록·선택 유지와 서버 비재조회
- 좌표 없음, 빈 결과, 200개 초과, 호출 제한과 SDK 장애 화면

### 의존성

- WS-01의 검색·필터·공개 맛집 결과
- WS-03의 유튜버 조건 판정
- WS-04와 데이터 책임자의 신규 좌표 저장·기존 좌표 backfill
- 지도 SDK 장애가 WS-01·WS-02·WS-08에 전파되지 않아야 한다.

### 완료 경계

[지도 탐색 PRD](../04-product/prd/discovery/map-discovery.md)의 PC·모바일·키보드 흐름과 `FR-MAP-*`, `BR-MAP-*`, 지도 성능·외부 연동·접근성·개인정보 NFR을 검증하면 완료한다.

## 7. WS-08 유튜버 상세

### 책임

- 공개 Creator 표시 정보와 상세 Route
- 공개·유효 관계에 기반한 방문 맛집과 근거 영상의 독립 페이지 목록
- 중복 제거, 최신 등록 순, 빈 목록과 찾을 수 없음 상태
- 사용자 조회 중 외부 YouTube API를 호출하지 않는 장애 격리

### 의존성

- WS-03의 Creator·Visit 공개·유효성 판정
- WS-02의 맛집·영상 표시 정보와 상세 이동 계약
- WS-04의 Creator 표시 정보 등록·갱신 확인 흐름

### 완료 경계

[유튜버 상세 PRD](../04-product/prd/detail/creator-detail.md)의 채널·맛집·영상 정상·빈·비공개·삭제·외부 이용 불가 흐름과 `FR-CREATOR-004`~`006`, `BR-CREATOR-008`~`012`를 검증하면 완료한다.

## 8. 통합 순서

1. WS-05가 회원 인증 주체와 세션·탈퇴 계약을 제공한다.
2. WS-06이 해당 계약을 사용해 개인화 기능을 통합한다.
3. 좌표 계약과 backfill 계획을 확정한 뒤 WS-07을 WS-01·WS-03 결과와 통합한다.
4. Creator 표시·관계 계약을 확정한 뒤 WS-08을 WS-02·WS-03·WS-04와 통합한다.
5. 공통 사용자 흐름에서 인증 장애가 공개 지도·유튜버 상세에 전파되지 않는지 교차 검증한다.
6. `OPS-VALIDATION`은 WS-05 회원 인증과 독립된 진입 경계로 통합하고, `E1-T13` 완료 증거를 2차 확장 `E2-T01` 기준선에 인계한다.

WS-07과 WS-08은 WS-05 완료를 기다리지 않고 공개 조회 계약을 기준으로 병렬 개발할 수 있다. WS-06은 WS-05의 실제 구현 전에도 인증 주체 계약 Stub으로 내부 규칙을 검증할 수 있다.

## 9. 리뷰와 변경 규칙

- WS-05: 이우람 기본 리뷰, 개인정보·데이터 정리는 박진영 추가 리뷰
- WS-06: 김인안 기본 리뷰, 공개 맛집 화면 계약은 양성훈 추가 리뷰
- WS-07: 박진영 기본 리뷰, 외부 연동·장애 격리는 이우람 추가 리뷰
- WS-08: 박진영 기본 리뷰, 목록·화면 계약은 양성훈 추가 리뷰
- OPS-VALIDATION: 김인안 기본 리뷰, Nginx·Redis·Parameter Store·배포 복구 변경은 박진영 추가 리뷰
- 공통 인증, 핵심 관계, 좌표 마이그레이션과 개인정보 정책 변경은 영향받는 책임자 공동 리뷰를 거친다.
- API·데이터·ADR 미확정 사항은 각 후속 문서에서 승인하며 이 문서에서 구현 세부를 새로 확정하지 않는다.
- 각 Workstream의 구현은 해당 API 계약 승인 뒤 시작한다. 회원·인증 API를 우선 작성하고 계정 상태·메일 장애의 동일 응답, 인증 전달, Redis 장애와 로그아웃 성공 조건을 확정한 뒤 WS-05·WS-06을 통합한다.
