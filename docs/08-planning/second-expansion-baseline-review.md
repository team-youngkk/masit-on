---
status: Review
review_date: 2026-08-03
baseline_commit: cf13e59
related_documents:
  - README.md
  - expansion-1-task-breakdown.md
  - expansion-1-implementation-plan.md
  - ../00-overview/scope.md
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/account/member-authentication.md
  - ../04-product/prd/personal/personal-restaurant-management.md
  - ../05-specs/api/account/member-authentication-api.md
  - ../05-specs/api/personal/personal-restaurant-api.md
  - ../05-specs/data/table-definitions.md
  - ../07-adr/security/auth-002-member-jwt-refresh-token.md
  - ../07-adr/security/auth-005-member-action-mail-outbox.md
  - ../07-adr/adr-backlog.md
---

# 맛잇온 2차 확장 선행 상태 검토

## 1. 목적

이 문서는 개인 컬렉션, 인기 맛집, 큐레이션, 사용자 제보·신고, 사용자 알림의 2차 확장 계약을 작성하기 전에 현재 저장소의 선행 상태를 확인한다. 계약과 구현 Task의 사전 문서화·담당자 배정은 선행 기능의 구현 완료와 무관하게 진행할 수 있지만, 구현 브랜치 생성·코드 작성·구현 PR은 이 문서의 착수 게이트를 통과한 뒤에만 시작한다.

현재 저장소에서 `M2`는 이미 MVP 초기 운영 배포 마일스톤과 `M2-*` Task를 뜻한다. 따라서 이 문서에서 기능 단계는 **2차 확장**으로 표기하며, 신규 Task ID는 범위와 Workstream이 확정되기 전까지 부여하지 않는다.

이 문서는 2차 확장 범위나 정책을 확정하지 않는다. 문서 간 상태 불일치와 아직 선택하지 않은 정책은 구현 가정으로 넘기지 않고 결정 게이트로 남긴다.

## 2. 조사 기준

| 항목 | 값 |
|---|---|
| 조사일 | 2026-08-03 |
| 브랜치 | `develop` |
| 기준 커밋 | `cf13e59` |
| 작업 트리 | 조사 시작 시 변경 없음 |
| 1차 확장 Task 기준 | [1차 확장 최종 Task 분해](expansion-1-task-breakdown.md#2-전체-task-표)의 `E1-T01`~`E1-T13` |
| 판정 우선순위 | 현재 코드·마이그레이션·테스트 → 병합 이력 → 계약 문서 → 계획의 과거 상태 |

## 3. 선행 상태 요약

| 확인 사항 | 판정 | 근거와 영향 |
|---|---|---|
| 1차 확장 계약이 확정됐는가 | **부분 충족** | 기능 요구사항은 1차 확장을 확정 범위로 선언하고 구현 계획·Task 분해는 `Ready`, 인증·데이터 ADR은 `Accepted`다. 그러나 회원·개인화 PRD와 API 명세 frontmatter는 여전히 `draft`다. 문서 owner가 상태를 확인하고 추적표와 함께 동기화하기 전에는 2차 확장이 이 계약을 최종 선행 계약으로 인용할 수 없다. |
| `E1-T03` 회원 기반이 구현됐는가 | **구현 근거 있음** | `member_account`, Action Token·메일 Outbox, 세션 폐기·복구와 Redis 회원 세션 경계가 V2 마이그레이션과 `member`·`security` 코드에 존재한다. `E1-T10` 교차 인수 PR이 병합됐고 README가 1차 확장 완료 상태를 선언한다. |
| `E1-T04` 회원 인증이 구현됐는가 | **구현 근거 있음** | 가입·이메일 인증·로그인·재발급·로그아웃·비밀번호 재설정·탈퇴 API와 화면이 존재하며 운영 `prod` 프로파일의 SMTP·회원 비밀정보 주입도 기록돼 있다. |
| `E1-T11` 지도 뷰포트 비종속 조회가 구현됐는가 | **미충족** | 양성훈 owner 승인으로 문서 계약은 필터 기반 조회와 지도 이동 시 결과 유지로 변경됐다. 현재 Controller·Command·SQL·TanStack Query `queryKey`와 지도 `idle` 재조회는 아직 bounds 계약을 사용하므로, `E1-T11` 구현·회귀 검증 전까지 2차 확장 기준선은 통과하지 않는다. |
| `E1-T12` 가입 이메일 인증 8자 코드가 구현됐는가 | **미충족** | 문서 계약은 8자 CSPRNG 코드와 제출 제한으로 변경됐지만 현재 이메일 인증 Token 생성·메일·API·화면은 기존 불투명 Token 계약을 사용한다. `E1-T12` 구현과 회원 인증 회귀 검증 전까지 2차 확장 기준선은 통과하지 않는다. |
| [OPS-VALIDATION](../02-analysis/first-expansion-workstreams.md#ops-validation-공통-운영배포-트랙)의 `E1-T13` 검증 참여자 쿠키 세션이 구현됐는가 | **미충족** | 현재 운영은 Nginx Basic Auth를 사용해 회원·관리자 Bearer와 `Authorization` 헤더가 충돌한다. 문서는 전용 쿠키 세션으로 변경됐으며 `E1-T13` 구현·운영 전환과 반복 인증창 0회 검증 전까지 2차 확장 기준선은 통과하지 않는다. |
| 맛집 공개 상태를 재사용할 수 있는가 | **충족** | `publication_status`와 `lifecycle_status`가 공개 목록·상세 및 개인화 조회 경계에 적용돼 있다. 컬렉션·큐레이션·인기 결과도 같은 공개 판정을 사용한다는 계약은 각 기능 문서에서 다시 명시해야 한다. |
| 인기 집계용 행동 데이터가 있는가 | **부분 충족** | 찜과 최근 본 맛집 데이터는 구현돼 있다. 다만 최근 기록은 회원·맛집별 최신 1건, 최신 50건 상한, 30일 보존 구조이므로 반복 조회 횟수나 전체 조회 이벤트를 나타내지 않는다. 현재 데이터만으로 `조회수 기반 인기`를 정의해서는 안 된다. |
| 비동기 작업을 운용할 수 있는가 | **제한적으로 충족** | 단일 EC2 애플리케이션에서 Spring `@Scheduled`, PostgreSQL `SKIP LOCKED` Outbox, 재시도 작업을 운용하고 있다. 이는 회원 Action 메일 등 승인된 좁은 사례에 한정되며 범용 이벤트·메시지 브로커의 승인을 뜻하지 않는다. |
| 사용자 알림을 운용할 수 있는가 | **미충족** | 운영 SMTP와 CloudWatch→Slack 운영 알림은 있으나 일반 사용자 알림 채널은 아니다. FCM은 `Post-MVP`, 범용 비동기 이벤트·Outbox는 `Conditional` 상태다. 사용자 식별·동의·해지·토큰 수명주기·실패 처리를 승인하고 관련 ADR을 활성화해야 한다. |

## 4. 기능별 선행 관계

| 2차 확장 기능 | 1차 확장 선행 요소 | 현재 판정 | 구현 Task 착수 조건 |
|---|---|---|---|
| 개인 컬렉션 | 회원 인증, 맛집 공개 상태 | 기반 구현 있음 / 계약 상태 동기화 필요 | 컬렉션 소유권·공개 범위·항목 중복·정렬·탈퇴 정리 계약 확정 |
| 인기 맛집 | 찜·조회 등 집계 기준으로 사용할 행동 데이터 | 찜·최근 기록 있음 / 집계 의미 미확정 | 집계 신호, 기간, 가중치, 중복 제거, 비공개 전환, 최소 표본과 갱신 주기 확정 |
| 큐레이션 | 관리자 인증, 맛집 공개 상태 | 기반 구현 있음 | 큐레이션 작성·게시·정렬·회수 권한과 비공개 맛집 처리 확정 |
| 제보·신고 | 회원 인증, 관리자 처리 흐름 | 회원 인증만 구현됨 | 제보와 신고의 대상·증거·상태·중복·오남용 제한·관리자 처리·보존 정책 확정 |
| 사용자 알림 | 회원 식별, 동의·해지, 제보·신고 처리 결과 | 회원 식별만 구현됨 | 알림 이벤트·채널·동의·해지·읽음·보존·재시도·토큰 및 비밀정보 수명주기 확정 |

## 5. 구현 전에 필요한 결정

### 5.1 1차 확장 계약 상태 동기화

다음 문서는 본문에서 기능과 세부 결정을 확정하고 실제 구현·교차 인수까지 완료했지만 frontmatter가 `draft`다.

- [사용자 계정·인증 PRD](../04-product/prd/account/member-authentication.md)
- [개인 맛집 관리 PRD](../04-product/prd/personal/personal-restaurant-management.md)
- [일반 회원 계정·인증 API](../05-specs/api/account/member-authentication-api.md)
- [개인 맛집 관리 API](../05-specs/api/personal/personal-restaurant-api.md)

각 owner가 `draft` 유지가 의도인지 확인해야 한다. 확정 전환에 합의하면 해당 문서의 상태, 제품·API·데이터·ADR 추적표, 1차 확장 완료 기록을 같은 문서 변경 범위에서 동기화한다.

### 5.2 인기 맛집의 행동 신호

다음 중 무엇을 인기 집계의 원천으로 사용할지 제품 계약에서 먼저 결정한다.

- 찜 수: 현재 `favorite`로 집계할 수 있지만 누적값인지 기간 내 신규 찜인지 정해야 한다.
- 최근 본 회원 수: 현재 `recent_restaurant_view`로 제한된 기간의 고유 회원 근사치는 만들 수 있으나, 최신 50건 상한과 삭제가 집계 결과를 왜곡할 수 있다.
- 조회 횟수: 현재 저장소에는 조회 이벤트 이력이 없으므로 별도 행동 이벤트 계약·수집·보존·개인정보 기준이 필요하다.
- 복합 점수: 각 신호의 가중치, 시간 감쇠, 최소 표본과 동률 정렬을 확정해야 한다.

결정 전에는 현재 최근 기록을 `조회수`로 이름만 바꾸거나 반복 조회 횟수로 해석하지 않는다.

### 5.3 비동기 작업과 사용자 알림

[ADR-AUTH-005](../07-adr/security/auth-005-member-action-mail-outbox.md)는 회원 Action Token에 종속된 메일 한 종류에만 PostgreSQL Outbox를 허용하며, 단일 소비 Token으로 중복 전달을 흡수할 수 없는 향후 알림에는 재사용을 금지한다. 따라서 제보·신고 처리 알림은 다음을 별도로 결정해야 한다.

- 알림 채널: 서비스 내 알림, 이메일, FCM 중 MVP 기능 범위
- 수신 동의와 해지 단위, 기본값, 변경 이력
- 알림 이벤트와 처리 결과 Snapshot, 중복 전달의 사용자 영향
- at-most-once / at-least-once 등 전달 의미와 멱등성 key
- 재시도, 만료, 실패 보관, 운영자 확인과 개인정보 삭제
- 단일 EC2 Scheduler 유지 또는 별도 Queue·Worker 도입 여부와 비용·관측성

FCM을 선택하면 [ADR-NOTIFY-001](../07-adr/adr-backlog.md#adr-notify-001-fcm-푸시-알림)을 활성화한다. 유실 방지 후속 이벤트나 범용 Outbox·Queue가 필요하면 ADR Backlog의 자동 복원력 결정을 별도 활성화하고 기존 회원 Action 메일 ADR의 범위를 넓히지 않는다.

### 5.4 제보·신고 관리자 처리 흐름

현재 관리자 인증과 데이터 등록 흐름은 제보·신고 접수함이나 처리 상태 머신을 제공하지 않는다. 접수, 중복 판정, 담당자 확인, 승인·기각·보완 요청, 처리 결과 통지, 감사 이력, 신고 대상의 임시 노출 제한을 하나의 사용자·관리자 흐름으로 먼저 확정한다.

## 6. 착수 게이트

2차 확장 계약과 구현 Task 문서화, 담당자 사전 배정은 지금 진행할 수 있다. 다음 조건을 모두 충족하기 전에는 `implementation_gate: Blocked`를 유지하고 실제 구현 브랜치 생성·코드 작성·구현 PR을 시작하지 않는다.

1. 1차 확장 회원·개인화 PRD와 API 문서의 `draft` 상태를 owner가 확인하고, `E1-T11`~`E1-T13` 계약 정합화와 회귀 검증을 완료한 뒤 추적표 상태를 동기화한다.
2. 각 2차 확장 기능의 요구사항과 MVP 범위, 주 PRD owner를 확정한다.
3. 인기 맛집의 집계 신호와 행동 데이터 보존·개인정보 기준을 확정한다.
4. 제보·신고의 관리자 처리 상태와 결과 통지 이벤트를 확정한다.
5. 사용자 알림의 채널·동의·해지·전달 보장과 운영 토폴로지를 확정하고 필요한 ADR을 활성화한다.
6. 확정 계약을 제품·API·데이터·ADR 추적표에서 역추적할 수 있게 연결한다.

## 7. 검증 결과와 제약

- 소스·V2 마이그레이션·화면과 `E1-T10` 교차 인수 및 1차 확장 운영 배포 병합 이력에서 `E1-T03`·`E1-T04` 구현 근거를 확인했다.
- `MemberAuthenticationServiceTest`, `MemberAuthenticationControllerTest`, `MemberActionMailOutboxServiceTest`를 실행해 회원 인증·API 응답·메일 Outbox 관련 테스트가 통과하는 것을 확인했다.
- 깨끗한 전체 테스트에서는 `ClassNotFoundException`이 0건이었다. 현재 Docker Desktop 엔진이 실행되지 않아 Testcontainers 기반 40개 suite가 초기화에 실패했으므로 PostgreSQL·Redis 통합 테스트의 전체 통과는 재확인하지 못했다.
- 기존 운영 기록은 단일 EC2, PostgreSQL, Redis, SMTP, Scheduler, CloudWatch·Slack 운영 알림의 운용 근거만 제공한다. FCM, 사용자 알림 저장소, 범용 Queue·Worker가 준비됐다는 근거로 사용하지 않는다.
