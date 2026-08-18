---
related_documents:
  - ../../01-requirements/requirements-review.md
  - ../api-review.md
  - data-model.md
  - entity-definitions.md
  - relationship-rules.md
  - lifecycle-rules.md
  - constraints.md
  - ../diagrams/erd-spec.md
  - ../../07-adr/adr-traceability.md
  - ../../00-overview/scope.md
  - ../../01-requirements/business-rules.md
  - ../api/admin/reference-data-api.md
  - ../api/admin/visit-registration-api.md
  - ../../07-adr/security/auth-003-confirmation-token.md
  - physical-data-model.md
  - table-definitions.md
  - constraint-mapping.md
  - index-strategy.md
  - migration-plan.md
  - seed-data-plan.md
  - ../../07-adr/data/data-007-uuid-v4-identifiers.md
  - ../../07-adr/data/data-008-publication-lifecycle-soft-delete.md
  - second-expansion-data-contract.md
---

# 맛잇온 데이터 모델 검토

## 1. 검토 목적

요구사항·계층형 PRD·API 계약과 논리 데이터 모델의 일치 여부를 확인하고, 확정 결정과 물리 설계·ADR 전 남은 질문을 분리한다.

## 2. 검토 결과 요약

- 논리 모델 차단 항목은 없다. 채널 관리 단위, 단일 지역·카테고리, Visit 삼항 관계, 중복·공개 초기값은 상위 문서에서 확정됐다.
- Restaurant는 영상·Visit 없이 존재하며 Video 하나는 여러 Restaurant의 Visit 근거가 된다.
- API DTO의 축약·집계·판정 값은 저장 모델에서 제외했다.
- 카카오 장소 동일성은 [ADR-EXT-001](../../07-adr/integration/ext-001-reference-verification.md)의 제공자 place ID로 확정되어 물리 유일 키에 반영됐다.
- UUID 내부 ID와 상태·논리 삭제는 [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md)·[ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md)로 확정됐다.
- 일반 회원과 관리자는 `member_account`를 단일 계정 원천으로 사용하고 `role=MEMBER/ADMIN`으로 권한을 구분한다. Refresh 상태는 Redis 8.8 `auth:session:` 통합 namespace를 사용하며 세션 상한은 MEMBER 3개, ADMIN 1개다. 키·쿠키 상세는 후속 ADR-AUTH-007이 소유한다.
- 확인 Token은 PostgreSQL 저장형 불투명 Token과 원자적 소비·결과 재현으로 확정됐다.

## 3. 모델링 차단 항목

현재 논리 모델 작성과 ERD를 막는 미결정은 없다. 물리 설계 결과와 승인 상태는 다음과 같다.

### RV-DATA-001 카카오 장소 동일성의 저장 표현

- 중요도: Critical
- 현재 상태: 결정 완료
- 관련 문서: [scope.md](../../00-overview/scope.md), [BR-RESTAURANT-006](../../01-requirements/business-rules.md#br-restaurant-006-맛집-중복-판단)·[BR-RESTAURANT-007](../../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분), [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기)
- 영향 데이터: Restaurant
- 영향 API: [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-ADMIN-RESTAURANT-001](../api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정)
- 결정: [ADR-EXT-001](../../07-adr/integration/ext-001-reference-verification.md)이 공식 API의 Kakao place ID를 동일성 기준으로 확정했다. 물리 모델은 `restaurant.kakao_place_id` UK로 구현한다.
- 영향: URL·이름·주소는 표시 정보이며 중복 키로 사용하지 않는다.
- 근거: [physical-data-model.md](physical-data-model.md#3-외부-동일성-결정)

### RV-DATA-002 공개 상태와 삭제·보관 상태 표현

- 중요도: Critical
- 현재 상태: 결정 완료 (2026-07-27)
- 관련 문서: [BR-RESTAURANT-008](../../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건), [BR-PUBLICATION-001](../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위)~[BR-PUBLICATION-008](../../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성), [BR-ADMIN-005](../../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계)·[BR-ADMIN-006](../../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙)
- 영향 데이터: Restaurant, Creator, Video, Visit
- 영향 API: 모든 공개 조회; 현재 수정·삭제 API는 없음
- 결정: `PUBLIC/PRIVATE`, `ACTIVE/DELETED`, `deleted_at`을 분리하고 FK `RESTRICT`와 논리 삭제를 사용한다.
- 영향: 공개 판정, 복구, 참조 보존과 partial index
- 근거: [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md), 2026-07-27 사용자 승인

## 4. API와 데이터 모델 충돌

- 물리 저장 모델과 API 필드 사이의 직접 충돌은 없다.
- 외부 제공자 ID는 내부 동일성 판정·후보 Snapshot·저장소 유일 키에만 사용하고 API·화면에는 노출하지 않는 것으로 [RV-API-016](../api-review.md#rv-api-016-관리자-미리보기의-외부-제공자-id-표시)에서 확정해 [ADR-EXT-001](../../07-adr/integration/ext-001-reference-verification.md)과 동기화했다.
- API의 `creatorId` 명칭은 서비스 표준 용어 `유튜버`를 따르지만 저장 개념은 YouTube 채널 단위 Creator다.
- API 후보 응답에는 외부 채널·영상 ID가 노출되지 않지만 동일성 보장을 위해 내부 저장 모델에는 안정된 외부 ID가 필요하다. 클라이언트 입력값이 아니라 서버의 외부 검증 결과다.
- 기본 데이터 API가 Creator와 Video의 등록 순서를 강제하지 않으므로 Video의 게시 채널 외부 ID는 필수로 저장하고 내부 Creator 참조는 선택으로 두었다. Visit 생성 시에는 등록된 Creator와 외부 게시 채널 일치를 필수 검증한다.
- API가 publication 입력을 받지 않고 생성 성공을 PUBLIC로 확정하므로 모델도 초기값을 고정한다.
- API의 `contentStatus`, `remainingVisitedByCount`, `decision`은 파생·과정 값으로 저장하지 않는다.

## 5. 관계 및 카디널리티 검토

| 검토 항목 | 결론 | 근거·후속 조건 |
|---|---|---|
| 유튜버 개인 대 채널 | 채널 단위 확정 | scope, [BR-CREATOR-001](../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미) |
| 지역 관리 단위·계층 | 서울 자치구 참조 데이터, 단일 단계 | glossary, [BR-RESTAURANT-005](../../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속) |
| 다중 음식 카테고리 | 허용하지 않음, 정확히 1개 | [BR-RESTAURANT-004](../../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리) |
| Visit 구조 | Restaurant·Creator·Video 삼항 관계 | [BR-VISIT-001](../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성), [API-ADMIN-VISIT-001](../api/admin/visit-registration-api.md#api-admin-visit-001-방문-관계-등록) |
| Visit와 게시 채널 일치 | 반드시 일치 | [BR-CREATOR-005](../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치) |
| 한 방문의 여러 영상 근거 | MVP 미지원 | 세 대상 조합마다 별도 Visit |
| 한 영상의 여러 맛집 | 허용, 맛집별 Visit | [BR-VIDEO-004](../../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결) |
| 재방문 | 다른 Video면 별도 Visit | [BR-VISIT-003](../../01-requirements/business-rules.md#br-visit-003-방문-관계-중복-판단); 방문일 도입 시 재검토 |
| 영상 없는 Visit | 금지 | [BR-VISIT-002](../../01-requirements/business-rules.md#br-visit-002-방문-근거-필수) |

## 6. 필수값·중복·공개 정책 검토

| 검토 항목 | 결론 |
|---|---|
| 맛집 중복 | 카카오 동일 장소; 이름 단독 사용 금지 |
| 동일 상호 다른 지점 | 다른 카카오 장소면 허용 |
| Creator 중복 | 외부 YouTube 채널 ID |
| Video 중복 | 외부 YouTube 원본 영상 ID |
| Visit 중복 | Restaurant·Creator·Video 복합 조합 |
| 방문일 | 저장하지 않음, 게시일 대체 금지 |
| 검증 상태·검증자 | 저장하지 않음, 생성 완료가 검증 완료 |
| publication | Restaurant·Creator·Video·Visit 각각 필요, 생성 시 PUBLIC |
| 외부 영상 상태 | publication과 분리; 관리자 확인 기반, 자동 주기 확인 없음 |
| 동시 중복 | 저장소 고유성 + 애플리케이션 오류 변환 모두 필요 |
| 부분 저장 | 각 생성 요청 원자성으로 금지 |

## 7. 물리 설계 결과 및 승인 항목

### RV-DATA-003 내부 식별자 전략

- 중요도: High
- 현재 상태: 결정 완료 (2026-07-27)
- 영향 데이터: 모든 엔티티
- 결정: 애플리케이션 생성 UUID v4, PostgreSQL `uuid`, API 불투명 문자열
- 근거: [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md), 2026-07-27 사용자 승인

### RV-DATA-004 참조 데이터 코드와 배포 방식

- 중요도: Medium
- 현재 상태: 물리 설계 결정 완료
- 영향 데이터: Region, FoodCategory
- 결정: 고정 UUID·code를 두고 Region 25개·FoodCategory 10개를 Flyway 초기 스키마 baseline으로 적재한다.
- 근거: [seed-data-plan.md](seed-data-plan.md)

### RV-DATA-005 감사 필드와 변경 이력 범위

- 중요도: High
- 현재 상태: MVP 물리 범위 결정 완료
- 영향 데이터: 모든 핵심 데이터와 관리자 인증
- 결정: PostgreSQL 핵심 테이블에는 `created_at`, `updated_at`, 삭제 데이터의 `deleted_at`만 저장하고 별도 상태 변경 이력 테이블이나 변경 관리자 FK를 두지 않는다. 인증된 운영 명령의 상태 변경은 행위자·대상·이전/이후 상태·사유·traceId를 운영 감사 로그에 기록한다. DB에 구조화된 변경 이력을 영속화하는 요구는 관리 API 또는 법적 감사 범위가 승인될 때 재검토한다.
- 근거: [physical-data-model.md](physical-data-model.md#42-이력-범위)

### RV-DATA-006 관리자 Refresh Token·로그인 제한 저장

- 중요도: High
- 현재 상태: 결정 완료 (2026-07-27)
- 영향 데이터: MemberAccount, AuthSession 및 단기 로그인 실패 상태
- 결정: Refresh Token은 `auth:session:` 통합 namespace에 SHA-256 Token 해시와 Token 계열·만료 정보를 저장한다. 역할별 활성 세션 상한은 MEMBER 3개, ADMIN 1개다. 로그인 실패 식별·TTL 상세는 후속 ADR-AUTH-007 계약을 따른다.
- 원자성: 새 로그인·회전·재사용 탐지와 역할·상태·비밀번호 변경에 따른 전체 폐기는 Redis Lua Script 또는 동등한 단일 원자 연산으로 처리한다.
- 전환: cutover 때 legacy 관리자 Refresh 세션을 모두 무효화하고 재로그인을 요구한다.
- 결정 시점: 인증 구현 전

### RV-DATA-007 검증 확인 토큰 구현

- 중요도: High
- 현재 상태: 결정 완료 (2026-07-27)
- 영향 데이터: 핵심 엔티티 없음, 단기 등록 후보
- 결정: PostgreSQL에 SHA-256 Token 해시·관리자·자원 종류·후보 스키마 버전·JSONB Snapshot과 `ISSUED/CREATED/DUPLICATE` 결과를 저장한다. 생성과 소비를 한 트랜잭션으로 처리하고 완료·만료 결과는 24시간 재현한다.
- 근거: [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md)

### RV-DATA-008 저장소 대 애플리케이션 검증 경계

- 중요도: High
- 현재 상태: 물리 설계 결정 완료
- 영향 데이터: Video, Visit, 모든 고유 엔티티
- 결정: `video(creator_id, publisher_external_channel_id)`와 `visit(video_id, creator_id)` 복합 FK로 저장소에서도 강제한다.
- 근거: [constraint-mapping.md](constraint-mapping.md#3-fk-목록과-삭제-정책)

## 8. ADR 대상

| 대상 | ADR 필요성 | 이유 |
|---|---|---|
| 데이터베이스 제품 | 결정 완료 | [ADR-DATA-001](../../07-adr/data/data-001-postgresql.md) |
| 내부 식별자 전략 | 결정 완료 | [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md) |
| Visit 모델링 | 현재 불필요 | 상위 규칙과 API가 삼항 관계로 확정; 변경 시 필요 |
| 논리 삭제·보관 전략 | 결정 완료 | [ADR-DATA-008](../../07-adr/data/data-008-publication-lifecycle-soft-delete.md) |
| 스키마 마이그레이션 도구 | 결정 완료 | [ADR-DATA-004](../../07-adr/data/data-004-flyway.md) |
| 위치 데이터 표현 | 현재 불필요 | MVP는 주소+서울 자치구; 지도 도입 시 필요 |
| 외부 자원 식별 전략 | 결정 완료 | [ADR-EXT-001](../../07-adr/integration/ext-001-reference-verification.md)의 제공자 원본 ID |
| 동시 중복 방지 전략 | 기본안 완료·강화 조건부 | UK+오류 변환; 강화는 [ADR-DATA-006](../../07-adr/adr-backlog.md#adr-data-006-동시-쓰기-충돌-제어) |
| 확인 Token 저장 전략 | 결정 완료 | [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md) |
| Refresh Token 저장 전략 | 후속 ADR로 대체 | [ADR-DATA-005](../../07-adr/data/data-005-redis-refresh-token.md)는 역사 기록, 정본은 후속 ADR-AUTH-007 |

## 9. 구현 중 결정 가능한 항목

- 변수·클래스 이름과 ORM 세부 매핑. 테이블·컬럼명과 SQL 자료형은 [table-definitions.md](table-definitions.md)를 변경하지 않는다.
- 표시값 변환과 조회 DTO 조립 위치(도메인 소유권은 유지)
- [index-strategy.md](index-strategy.md)의 초기 인덱스는 그대로 구현하고, 운영 유사 데이터의 실행계획 근거가 생긴 뒤 후속 인덱스 추가·제거와 쿼리 튜닝을 결정한다.
- 공통 감사 필드 자동 입력 방식
- 외부 URL 정규화 코드 구성(동일성 원칙은 변경 금지)

## 10. 확정 결과와 남은 상세

1. 내부 ID, 상태·논리 삭제, 외부 동일성, 채널 일치와 Flyway·seed 계획은 확정됐다.
2. 상세 콘텐츠 정렬과 외부 제공자 ID 노출 경계도 API 계약에 반영됐다.
3. Redis 로그인 제한 구조와 외부 timeout 수치는 보안·외부 연동 상세 설계에 확정됐으며 구현과 검증 단계에서 해당 계약을 그대로 적용한다.

## 11. 데이터 모델 완료 기준

- 핵심 8개 데이터 개념과 소유 책임이 정의됐다.
- Restaurant는 Visit 없이 존재하고 Video는 여러 Restaurant Visit의 근거가 된다.
- Visit의 세 필수 참조, 실제 근거, 채널 일치와 복합 중복 기준이 정의됐다.
- 공개·외부·삭제·검증 상태가 구분됐다.
- 모든 MVP API의 조회·변경 데이터가 추적된다.
- 저장소와 애플리케이션 검증 책임이 구분됐다.
- MVP 제외 데이터는 포함하지 않았다.
- 물리 설계와 필수 ADR이 확정됐다.
- `erd.mmd`의 카디널리티가 관계 문서와 일치한다.

## 12. Open Questions 상태

- 현재 MVP 물리 모델의 미결 Open Question은 없다.
- 외부 표시 메타데이터는 최신값만 유지하고 변경 이력을 저장하지 않는다.
- 삭제·비공개 전환은 별도 운영 명령으로 수행하며 관리자 API는 후속 범위로 미룬다.
- 논리 삭제 데이터는 자동 물리 purge 없이 보존한다.

## 13. Assumptions

- 현재 수정된 [scope.md](../../00-overview/scope.md), 확정 비즈니스 규칙과 API 계약을 이 작업 시점의 정본으로 사용했다.
- API가 확인한 YouTube 채널·영상은 안정된 외부 ID를 제공하며, 구체 필드명·형식은 외부 연계 설계에서 정한다.
- Region·FoodCategory를 별도 참조 데이터로 두되 독립 도메인으로 승격하지 않는다.

## 14. 2차 확장 결정

- 2차 확장 데이터의 정본은 [2차 확장 데이터 계약](second-expansion-data-contract.md)이다.
- 인기 순위는 기존 Favorite의 현재 행에서 계산하고 Metric·Snapshot을 저장하지 않는다.
- 알림은 필수 서비스 내 기록만 저장하며 Preference·DeviceToken·Outbox를 만들지 않는다.
- 제보·신고는 별도 테이블, 상태 감사는 두 요청 중 하나를 참조하는 공통 ModerationHistory로 저장한다.
- 적용된 마이그레이션은 수정하지 않고 실제 최신 순서 다음의 신규 마이그레이션을 계획한다. 통합 계정은 확장·호환 관찰과 별도 계약 제거의 두 단계로 나누며, 운영 전 통합 예외와 새 계약 마이그레이션을 혼동하지 않는다. 제거 전 행 수·FK·로그인·rollback rehearsal 증거가 필수다.
