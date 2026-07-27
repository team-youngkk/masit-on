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
---

# 맛잇온 데이터 모델 검토

## 1. 검토 목적

요구사항·계층형 PRD·API 계약과 논리 데이터 모델의 일치 여부를 확인하고, 확정 결정과 물리 설계·ADR 전 남은 질문을 분리한다.

## 2. 검토 결과 요약

- 논리 모델 차단 항목은 없다. 채널 관리 단위, 단일 지역·카테고리, Visit 삼항 관계, 중복·공개 초기값은 상위 문서에서 확정됐다.
- Restaurant는 영상·Visit 없이 존재하며 Video 하나는 여러 Restaurant의 Visit 근거가 된다.
- API DTO의 축약·집계·판정 값은 저장 모델에서 제외했다.
- 물리 설계 전 반드시 결정할 Critical 항목은 카카오 장소 동일성 키와 상태·삭제 구현이다.
- 관리자 내부 계정과 Redis Refresh Token 상태는 MVP 저장 범위에 포함하되 구체 키·검증값·TTL은 후속 설계로 남겼다.
- 확인 Token은 PostgreSQL 저장형 불투명 Token과 원자적 소비·결과 재현으로 확정됐다.

## 3. 모델링 차단 항목

현재 논리 모델 작성과 ERD를 막는 미결정은 없다. 다음 두 항목은 구현 착수 전에는 차단 요소가 된다.

### RV-DATA-001 카카오 장소 동일성의 저장 표현

- 중요도: Critical
- 현재 상태: 물리 설계 전 결정 필요
- 관련 문서: [scope.md](../../00-overview/scope.md), [BR-RESTAURANT-006](../../01-requirements/business-rules.md#br-restaurant-006-맛집-중복-판단)·[BR-RESTAURANT-007](../../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분), [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기)
- 영향 데이터: Restaurant
- 영향 API: [API-ADMIN-RESTAURANT-PREVIEW-001](../api/admin/reference-data-api.md#api-admin-restaurant-preview-001-맛집-등록-검증-미리보기), [API-ADMIN-RESTAURANT-001](../api/admin/reference-data-api.md#api-admin-restaurant-001-맛집-등록-확정)
- 결정 질문: 카카오의 동일 장소를 어떤 안정된 외부 값과 정규화 규칙으로 저장소에서 유일하게 보장하는가?
- 선택지: 제공자 장소 ID / 검증된 정규 URL 기반 키 / 별도 정규화 키
- 영향: 지점 구분, 동시 등록, URL 변경 대응과 마이그레이션
- 결정 시점: 물리 모델·DDL 작성 전

### RV-DATA-002 공개 상태와 삭제·보관 상태 표현

- 중요도: Critical
- 현재 상태: ADR 또는 물리 설계 전 결정 필요
- 관련 문서: [BR-RESTAURANT-008](../../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건), [BR-PUBLICATION-001](../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위)~[BR-PUBLICATION-008](../../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성), [BR-ADMIN-005](../../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계)·[BR-ADMIN-006](../../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙)
- 영향 데이터: Restaurant, Creator, Video, Visit
- 영향 API: 모든 공개 조회; 현재 수정·삭제 API는 없음
- 결정 질문: publication과 lifecycle 상태를 어떤 값·전환·보존 정책으로 분리하는가?
- 선택지: 독립 상태 필드 / 비공개 상태 + 삭제 시각 / 별도 보관 모델
- 영향: 공개 판정 일관성, 복구, 참조 보존, 인덱스와 운영 절차
- 결정 시점: 데이터 스키마와 운영 정정 수단 구현 전

## 4. API와 데이터 모델 충돌

- 직접 충돌은 발견하지 못했다.
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

## 7. 물리 설계 전 결정 항목

### RV-DATA-003 내부 식별자 전략

- 중요도: High
- 현재 상태: 후속 설계에서 결정
- 영향 데이터: 모든 엔티티
- 결정 질문: 내부 ID의 생성 위치·형식·JSON 문자열 변환은 무엇인가?
- 결정 시점: 스키마·API 구현 전

### RV-DATA-004 참조 데이터 코드와 배포 방식

- 중요도: Medium
- 현재 상태: 후속 설계에서 결정
- 영향 데이터: Region, FoodCategory
- 결정 질문: API 표준 이름 외 별도 code가 필요한가, 기준값은 migration·seed·운영 중 무엇으로 관리하는가?
- 결정 시점: 초기 데이터 적재 전

### RV-DATA-005 감사 필드와 변경 이력 범위

- 중요도: High
- 현재 상태: 팀 결정 필요
- 영향 데이터: 모든 핵심 데이터와 관리자 인증
- 결정 질문: createdAt·updatedAt 외 변경자, 사유, 상태 이력을 어디까지 저장하는가?
- 결정 시점: 정정·비공개 운영 설계 전

### RV-DATA-006 관리자 Refresh Token·로그인 제한 저장

- 중요도: High
- 현재 상태: 후속 보안 설계에서 결정
- 영향 데이터: AdminAccount, AdminRefreshToken 및 단기 로그인 실패 상태
- 결정 질문: Redis 키, 안전한 Refresh Token 검증값, 계정당 활성 Token 유일성·회전·재사용 탐지와 실패 제한을 어떻게 보장하는가?
- 결정 시점: 인증 구현 전

### RV-DATA-007 검증 확인 토큰 구현

- 중요도: High
- 현재 상태: 결정 완료 (2026-07-27)
- 영향 데이터: 핵심 엔티티 없음, 단기 등록 후보
- 결정: PostgreSQL에 SHA-256 Token 해시·관리자·자원 종류·후보 스키마 버전·JSONB Snapshot과 `ISSUED/CREATED/DUPLICATE` 결과를 저장한다. 생성과 소비를 한 트랜잭션으로 처리하고 완료·만료 결과는 24시간 재현한다.
- 근거: [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md)

### RV-DATA-008 저장소 대 애플리케이션 검증 경계

- 중요도: High
- 현재 상태: 논리 책임 확정, 구체 구현 결정 필요
- 영향 데이터: Video, Visit, 모든 고유 엔티티
- 결정 질문: 선택 Video.creatorId와 외부 게시 채널, Visit.Creator의 일치를 저장소에서도 강제할 것인가?
- 결정 시점: 물리 관계·트랜잭션 설계 전

## 8. ADR 대상

| 대상 | ADR 필요성 | 이유 |
|---|---|---|
| 데이터베이스 제품 | 필요 | 제약·트랜잭션·운영에 장기 영향 |
| 내부 식별자 전략 | 필요 | 모든 API·관계·마이그레이션 영향 |
| Visit 모델링 | 현재 불필요 | 상위 규칙과 API가 삼항 관계로 확정; 변경 시 필요 |
| 논리 삭제·보관 전략 | 필요 | 공개·복구·참조와 운영 영향 |
| 스키마 마이그레이션 도구 | 필요 | 배포·복구 절차 영향 |
| 위치 데이터 표현 | 현재 불필요 | MVP는 주소+서울 자치구; 지도 도입 시 필요 |
| 외부 영상 식별 전략 | 필요 여부 검토 | 외부 ID 정규화와 URL 변경 대응 |
| 동시 중복 방지 전략 | 필요 | DB 기능·격리·오류 변환 영향 |
| 확인 Token 저장 전략 | 결정 완료 | [ADR-AUTH-003](../../07-adr/security/auth-003-confirmation-token.md) |
| Refresh Token 저장 전략 | 추가 상세 필요 | Redis 키·회전·로그인 제한 운영 영향 |

## 9. 구현 중 결정 가능한 항목

- 변수·클래스·테이블 이름, SQL 자료형과 ORM 매핑
- 표시값 변환과 조회 DTO 조립 위치(도메인 소유권은 유지)
- 세부 인덱스 열 순서와 쿼리 튜닝
- 공통 감사 필드 자동 입력 방식
- 외부 URL 정규화 코드 구성(동일성 원칙은 변경 금지)

## 10. 권장 결정 순서

1. 데이터베이스 제품과 내부 식별자 전략
2. 카카오 장소·YouTube 외부 식별 정규화
3. publication/lifecycle 및 논리 삭제 전략
4. 저장소 고유성·참조·채널 일치와 동시성 방식
5. AdminAccount·AdminRefreshToken 보안 저장과 확정된 확인 Token 물리 테이블 반영
6. 감사·이력 범위와 기준 데이터 적재
7. 물리 모델·마이그레이션·인덱스 설계

## 11. 데이터 모델 완료 기준

- 핵심 8개 데이터 개념과 소유 책임이 정의됐다.
- Restaurant는 Visit 없이 존재하고 Video는 여러 Restaurant Visit의 근거가 된다.
- Visit의 세 필수 참조, 실제 근거, 채널 일치와 복합 중복 기준이 정의됐다.
- 공개·외부·삭제·검증 상태가 구분됐다.
- 모든 MVP API의 조회·변경 데이터가 추적된다.
- 저장소와 애플리케이션 검증 책임이 구분됐다.
- MVP 제외 데이터는 포함하지 않았다.
- 물리 설계·ADR 전 결정 항목이 식별됐다.
- `erd.mmd`의 카디널리티가 관계 문서와 일치한다.

## 12. Open Questions

- [RV-DATA-001](data-review.md#rv-data-001-카카오-장소-동일성의-저장-표현)~[RV-DATA-006](data-review.md#rv-data-006-관리자-refresh-token로그인-제한-저장)과 [RV-DATA-008](data-review.md#rv-data-008-저장소-대-애플리케이션-검증-경계)의 구현 전 결정 항목
- 외부 상태 마지막 확인 시각과 외부 메타데이터 갱신 이력의 실제 저장 범위
- 삭제·비공개 운영 수단이 MVP 내부 운영 절차로 필요한지, 후속 관리 API까지 미룰지

## 13. Assumptions

- 현재 수정된 [scope.md](../../00-overview/scope.md), 확정 비즈니스 규칙과 API 계약을 이 작업 시점의 정본으로 사용했다.
- API가 확인한 YouTube 채널·영상은 안정된 외부 ID를 제공하며, 구체 필드명·형식은 외부 연계 설계에서 정한다.
- Region·FoodCategory를 별도 참조 데이터로 두되 독립 도메인으로 승격하지 않는다.
