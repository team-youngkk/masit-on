---
related_documents:
  - ../../01-requirements/business-rules.md
  - data-model.md
  - entity-definitions.md
  - relationship-rules.md
  - constraints.md
  - ../api/detail/restaurant-detail-api.md
  - ../api/admin/reference-data-api.md
  - physical-data-model.md
  - constraint-mapping.md
  - ../../07-adr/data/data-008-publication-lifecycle-soft-delete.md
  - second-expansion-data-contract.md
  - ../../07-adr/data/data-012-second-expansion-retention-cleanup.md
---

# 맛잇온 데이터 생명주기

## 1. 문서 목적

핵심 데이터의 생성, 공개, 외부 상태 변화, 비공개, 삭제·복구 요구를 구분한다. MVP API에 없는 수정·삭제 기능을 새로 정의하지 않는다.

## 2. 상태 정의

| 상태 축 | 질문 | 적용 데이터 | MVP 원칙 |
|---|---|---|---|
| publication status | 일반 사용자가 조회할 수 있는가 | Restaurant, Creator, Video, Visit | 생성 성공 시 PUBLIC, 비공개·삭제는 제외 |
| reference active | 신규 관계에 사용할 수 있는가 | Region, FoodCategory | 비활성 값은 신규 연결 금지 |
| external availability | 외부 YouTube 리소스가 확인 결과 이용 가능한가 | Creator, Video | publication과 분리, 실시간·주기 확인 없음 |
| lifecycle status | 내부 데이터가 활성·삭제 중인가 | 핵심 데이터 | `ACTIVE/DELETED`, ADR-DATA-008 확정 |
| verification status | 검증 대기·완료인가 | Visit 후보 | 별도 상태를 저장하지 않음; 생성 완료가 검증 완료 |

미리보기의 `READY`, `DUPLICATE`, `REVIEW_REQUIRED`는 핵심 엔티티 상태가 아니라 생성 전 판정이다. `REVIEW_REQUIRED`는 등록 요청으로 저장하지 않는다.

## 3. 맛집 생명주기

1. 관리자가 카카오 장소, 서울 주소, 전화번호와 대표 카테고리를 입력한다.
2. 미리보기에서 동일성·필수값·참조 데이터 유효성을 확인한다.
3. `READY` 후보를 관리자가 확인하면 Restaurant를 원자적으로 생성하고 즉시 PUBLIC로 취급한다.
4. Visit 없이도 목록·상세에 반영한다.
5. 오류·폐업·장기 중단이 확인되면 공개 조회에서 먼저 제외한다. MVP에는 이를 수행하는 수정·삭제 API가 없으므로 운영·후속 기능으로 처리한다.
6. 외부 영상·채널 상태는 Restaurant의 저장·공개 상태를 자동 변경하지 않는다.

## 4. 유튜버 또는 채널 생명주기

1. 관리자가 채널 URL을 제출한다.
2. YouTube 조회로 외부 채널 ID, 현재 채널명·URL과 공개 채널임을 확인한다.
3. 관리자 확인 후 Creator를 생성하고 PUBLIC 및 외부 AVAILABLE 상태로 시작한다.
4. 채널명 변경은 외부 채널 ID 동일성을 유지한 채 표시 정보 변경으로 취급하되 MVP 수정 API는 없다.
5. 삭제·비공개를 관리자가 확인하면 외부 상태를 UNAVAILABLE로 기록하고 서비스 비공개 처리한다. 일시 링크 오류만으로 전환하지 않는다.
6. 재공개가 확인되면 재검증 뒤 공개할 수 있으나 운영 수단은 후속 설계한다.

## 5. 영상 생명주기

1. 관리자가 YouTube 원본 URL을 제출한다.
2. 외부 영상 ID, 제목, 썸네일, 게시 채널과 원본 URL을 확인한다.
3. 관리자 확인 후 Video를 생성하고 PUBLIC·ACTIVE 및 외부 AVAILABLE 상태로 시작한다. 일치 Creator가 이미 있으면 내부 참조를 연결하고, 없으면 외부 게시 채널 ID만 저장한다.
4. Video 등록만으로 Restaurant와 연결되지 않는다.
5. 삭제·비공개를 관리자가 확인하면 외부 상태를 UNAVAILABLE로 기록하고 Video를 비공개 처리한다. 관련 Visit와 Restaurant를 자동 삭제하지 않는다.
6. 공개 맛집 기본 정보는 유지하고 해당 영상과 그 영상에만 근거한 관계를 사용자 조회에서 제외한다.

## 6. 방문 관계 생명주기

1. Restaurant, Creator와 Video가 먼저 존재하고 모두 공개여야 한다.
2. 관리자가 실제 방문 장면과 Video 게시 채널 일치를 확인한다.
3. 세 참조 조합 중복을 검사하고 Visit를 원자적으로 생성해 즉시 PUBLIC로 취급한다.
4. 생성 성공 뒤 유튜버 필터와 맛집 상세에 반영한다.
5. Visit의 별도 검증 상태, 방문일과 검증자 속성은 두지 않는다.
6. 잘못된 관계는 일반 조회에서 먼저 제외하고 재검증한다. MVP에는 수정·삭제 API가 없다.

## 7. 공개·비공개 전환

- Restaurant 비공개: 목록·검색·필터·상세와 그 맥락의 연결 정보를 모두 제외한다.
- Creator 비공개: Creator 선택 목록, 필터와 상세의 해당 Creator 관계를 제외하되 Restaurant 기본 정보는 유지한다.
- Video 비공개: 영상과 그 영상에 근거한 Visit를 제외하되 Restaurant 기본 정보는 유지한다.
- Visit 비공개: 관계 기반 정보만 제외하고 Restaurant 기본 정보는 유지한다.
- 모든 공개 조회는 같은 판정 규칙을 사용한다.
- 전환 API는 MVP 제외이며 운영 절차 또는 후속 관리 기능이다.

## 8. 외부 리소스 상태 변경

- 사용자 조회 시 YouTube 또는 카카오를 실시간 호출하지 않는다.
- 자동 주기 동기화·상태 확인은 MVP에서 제외한다.
- 외부 장애는 저장된 내부 데이터의 유효성이나 생명주기를 자동 변경하지 않는다.
- 관리자 확인으로 외부 리소스 이용 불가가 확정된 경우에만 외부 상태와 공개 상태를 조정한다.
- Creator와 Video의 마지막 외부 확인 시각은 `external_status_checked_at`에 저장한다. 생성 시 검증 미리보기의 확인 시각을 기록하고, 관리자가 외부 상태를 다시 확인하면 최신 시각으로 갱신한다.

## 9. 삭제 및 복구 요구사항

- 외부 영상·채널 삭제를 내부 데이터 물리 삭제로 전파하지 않는다.
- 핵심 행을 보존하는 논리 삭제를 사용하고 `PRIVATE`, `DELETED`, `deleted_at`을 한 트랜잭션에서 설정한다.
- 잘못된 데이터는 먼저 비공개하고 출처와 사실을 재검증한 뒤 공개 조건을 충족할 때만 재공개한다.
- 관련 관계를 다른 기본 데이터로 자동 이전하거나 임의 병합하지 않는다.
- 논리 삭제된 행은 인증된 운영 명령으로만 복구할 수 있고 참조 행은 보존한다. 상태 변경의 행위자·대상·이전/이후 상태·사유·traceId는 운영 감사 로그에 기록한다.

## 10. 확정 운영 정책

### 10.1 회원 개인화 관계 정리

- 최근 기록 upsert Command는 현재 맛집의 최신 조회 시각 갱신과 회원별 최신 50개 상한 정리만 수행한다.
- 30일 경과 최근 기록은 회원 조회·상세 조회와 독립된 idempotent 주기 cleanup Command가 매일 한 번 이상 물리 삭제한다. 실행 실패는 관측·재시도 대상이며, 다음 실행은 이미 삭제된 행이 없어도 성공해야 한다.
- 최근 기록 목록 GET은 어떤 경우에도 정리 트랜잭션을 열지 않고 30일·50개 범위만 읽기 전용으로 필터링한다.
- 찜·최근 목록 조회는 `readOnly` 조회로 유효 기간과 공개 상태만 필터링하며 삭제·정리 Command를 실행하지 않는다.
- 회원 탈퇴와 맛집 물리 삭제는 해당 Command 또는 FK 정책에서 개인화 관계를 정리한다. 비공개 맛집 관계는 보존하되 공개 목록에서 숨긴다.

### 10.2 통합 계정과 권한 생명주기

- 공개 회원가입은 `member_account.role='MEMBER'`로만 시작하며 요청 본문으로 역할을 선택하지 않는다.
- `ADMIN` 발급, `MEMBER ↔ ADMIN` 변경과 관리자 권한 회수는 승인된 운영 절차만 수행한다. 역할·상태·비밀번호가 바뀌면 해당 계정의 Redis 활성 세션을 모두 폐기한다.
- 활성 세션 상한은 `MEMBER` 3개, `ADMIN` 1개다. 계정이 `DISABLED` 또는 `DELETION_PENDING`으로 바뀌면 새 로그인·재발급을 막는다.
- `active=true` legacy 관리자는 검증된 유일 이메일·호환 가능한 비밀번호 해시와 기존 `ACTIVE` 회원 상태가 확인된 경우에만 `ACTIVE/ADMIN`으로 이전한다. 기존 회원이 `PENDING_VERIFICATION`, `DELETION_PENDING`, `DISABLED`이면 migration이 상태를 복구하거나 역할을 부여하지 않고 중단한다. `active=false` legacy 관리자는 승인된 비활성 보존 대상으로만 이전하며 기존 회원은 불변, 새 identity-only 행은 `DISABLED/MEMBER`로 만든다. cutover 시 legacy 관리자 Refresh 세션은 모두 무효화해 재로그인을 요구한다.

- 논리 삭제 데이터는 기한 없이 보존하고 자동 purge하지 않으며 승인된 운영자만 별도 명령으로 복구할 수 있다.
- 상태 변경 화면·관리 API는 MVP에 만들지 않고 인증된 운영 명령만 제공한다.
- 채널명·영상 제목 등 외부 표시 정보는 최신값만 유지하고 변경 이력을 별도로 저장하지 않는다.
- 상태 변경 감사 로그에는 행위자·대상·이전/이후 상태·사유·traceId를 남긴다.
- Region·FoodCategory를 비활성화해도 기존 Restaurant는 해당 이름을 계속 표시하고 신규 연결만 금지한다.

## 11. 2차 확장 생명주기

- 개인 컬렉션·구성은 사용자 삭제와 회원 탈퇴에서 물리 삭제한다.
- 인기 순위는 저장하지 않아 별도 보존·삭제·재계산 생명주기가 없다.
- 큐레이션은 `DRAFT/PUBLISHED`를 전환하고 삭제 API 없이 보존한다. 구성 맛집 비공개·삭제는 관계를 유지하고 공개 결과에서 숨긴다.
- 제보·신고는 종료 후 1년 또는 탈퇴 시 회원 FK를 `NULL`로 바꿔 식별 연결을 제거한다. 비식별 요청·ModerationHistory는 자동 purge하지 않는다.
- 알림은 생성 90일 이내이거나 회원별 최신 200개이면 유지하고 두 조건을 모두 벗어나면 물리 삭제한다. 탈퇴 시 모두 삭제한다.
- 멱등 성공 기록은 24시간 뒤 물리 삭제한다.

정확한 FK 삭제 정책과 cleanup 인덱스는 [2차 확장 데이터 계약](second-expansion-data-contract.md)을 따른다.
