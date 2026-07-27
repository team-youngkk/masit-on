---
id: PRD-ADMIN-001
title: 관리자 데이터 등록
status: draft
workstream: WS-04
owner: 김인안
reviewers:
  - 박진영
related_requirements:
  - FR-ADMIN-001
  - FR-ADMIN-002
  - FR-ADMIN-003
  - FR-ADMIN-004
  - FR-VISIT-001
related_business_rules:
  - BR-RESTAURANT-003
  - BR-RESTAURANT-004
  - BR-RESTAURANT-005
  - BR-RESTAURANT-006
  - BR-RESTAURANT-007
  - BR-RESTAURANT-008
  - BR-CREATOR-001
  - BR-CREATOR-002
  - BR-CREATOR-003
  - BR-CREATOR-005
  - BR-VIDEO-001
  - BR-VIDEO-002
  - BR-VIDEO-003
  - BR-VIDEO-004
  - BR-VIDEO-005
  - BR-VIDEO-006
  - BR-VISIT-001
  - BR-VISIT-002
  - BR-VISIT-003
  - BR-VISIT-004
  - BR-VISIT-005
  - BR-VISIT-006
  - BR-VISIT-007
  - BR-ADMIN-001
  - BR-ADMIN-002
  - BR-ADMIN-003
  - BR-ADMIN-004
  - BR-ADMIN-005
  - BR-ADMIN-006
  - BR-ADMIN-007
  - BR-ADMIN-008
related_nfr:
  - NFR-PERFORMANCE-003
  - NFR-SECURITY-001
  - NFR-SECURITY-002
  - NFR-SECURITY-003
  - NFR-INTEGRITY-001
  - NFR-INTEGRITY-002
  - NFR-INTEGRITY-003
  - NFR-EXTERNAL-003
  - NFR-OBSERVABILITY-001
  - NFR-OBSERVABILITY-002
  - NFR-OBSERVABILITY-003
  - NFR-TEST-001
  - NFR-TEST-002
  - NFR-TEST-003
  - NFR-PRIVACY-001
  - NFR-PRIVACY-002
related_documents:
  - ../00-product-overview.md
  - ../../../01-requirements/functional-requirements.md
  - ../../../01-requirements/requirements-review.md
  - ../../../01-requirements/business-rules.md
  - ../../../02-analysis/mvp-workstreams.md
  - ../../../03-team/ownership.md
  - ../../../05-specs/api/admin/README.md
  - ../../../05-specs/data/README.md
  - ../../../07-adr/security/auth-001-spring-security-jwt.md
  - ../../../07-adr/platform/web-003-routing-boundary.md
  - ../../../07-adr/integration/ext-001-reference-verification.md
  - ../../../01-requirements/non-functional-requirements.md
  - ../../traceability.md
---

# 관리자 데이터 등록 PRD

## 1. 문서 정보

관리자 접근부터 맛집·유튜버·영상 기본 데이터, 방문 관계 등록과 사용자 조회 반영까지 하나의 완결된 [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 업무 흐름으로 정의한다. 화면은 `/admin/login`, `/admin/restaurants/new`, `/admin/creators/new`, `/admin/videos/new`, `/admin/visits/new`로 분리하고 백엔드 호출은 `/api/admin/**`를 사용한다.

## 2. 기능 개요

사전 발급 계정으로 인증한 관리자는 사실을 확인한 기본 데이터를 먼저 등록하고, 실제 방문 영상을 근거로 세 대상의 관계를 등록해 사용자 탐색과 상세에 반영한다.

## 3. 문제 및 사용자 요구

사용자에게 신뢰할 수 있는 탐색 결과를 제공하려면 서로 참조하는 네 종류의 데이터를 검증된 순서로 등록해야 한다. 관리자는 중복·잘못된 참조·채널 불일치·근거 없는 관계를 차단하고 결과 반영 여부를 확인할 수 있어야 한다.

## 4. 목표

- 인증된 관리자만 등록 흐름을 수행한다.
- 기본 데이터와 방문 관계를 필수값·중복·참조·실제 방문 근거에 따라 검증한다.
- 동시 요청에도 고유성과 원자성을 유지하고 공개 가능한 결과만 사용자 조회에 반영한다.

## 5. 비목표

관리자 회원가입·등급·세분 권한·계정 화면, 데이터 수정·삭제 UI, 승인 워크플로, 별도 검증 상태, 관리자 확인 없는 자동 등록·자동 주기 동기화, AI 추출, 영상 원본 저장과 실제 방문 날짜 관리는 목표가 아니다.

## 6. 대상 사용자

- 사전 발급된 동일 등록 권한의 관리자

## 7. 전제 조건

- 팀이 최소 관리자 인증과 접근 거부 계약을 확정한다.
- 방문 관계 전에 참조할 맛집·유튜버·영상이 등록돼 있어야 한다.
- 관리자 화면 진입 시 메모리 Access Token이 없으면 Refresh Token 재발급을 한 번 시도하고 실패하면 `/admin/login`으로 이동한다.
- 관리자는 출처와 사실, 영상 게시 채널과 실제 방문 근거를 확인한다.

## 8. 핵심 사용자 흐름

### 기본 데이터 등록

- 시작 조건: 관리자가 인증해 등록 기능에 접근한다.
- 사용자 행동: 검증한 맛집, YouTube 채널 단위 유튜버와 영상 정보를 각각 등록한다.
- 시스템 동작: 권한을 확인하고 유튜버·영상은 YouTube API로 존재와 표시 정보를 조회한다. 관리자가 결과를 확인하면 필수값, 사전 정의 값, 외부 링크와 동일 대상 중복을 검증해 저장한다.
- 성공 결과: 공개 조건을 충족한 고유 기본 데이터가 등록된다.
- 빈 결과 또는 실패 처리: 인증 실패·필수값 오류·중복은 등록하지 않으며, 동일성 판단 불가는 비노출 보류로 처리한다.

### 방문 관계 등록 및 반영

- 시작 조건: 세 기본 대상이 존재하고 관리자가 실제 방문 영상 근거를 확인했다.
- 사용자 행동: 맛집·유튜버·영상 조합을 선택해 방문 관계를 등록한다.
- 시스템 동작: 참조 존재, 영상 게시 채널과 유튜버 일치, 실제 방문 근거, 관계 중복과 공개 조건을 검증한다.
- 성공 결과: 고유 관계가 등록되고 [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 사용자 조회에 반영된다.
- 빈 결과 또는 실패 처리: 참조 없음·채널 불일치·근거 없음·중복이면 새 관계를 만들지 않으며 부분 실패로 불완전 관계를 공개하지 않는다.

## 9. 기능 범위

### 포함 범위

- 관리자 인증과 등록 기능 접근 통제
- 맛집·유튜버·영상 기본 정보와 방문 관계 등록
- 유튜버·영상 등록 시 YouTube API 조회와 관리자 확인 후 저장
- 필수값·사전 정의 값·외부 링크·사실·중복·동시성·참조·채널 일치 검증
- 보류 또는 기존 대상 사용 결과와 세 조회 흐름 반영

### 제외 범위

- 수정·삭제·승인 관리 화면, 세분 권한, 관리자 확인 없는 자동 등록과 자동 주기 동기화
- 관계 판정의 사용자 조회 표현과 조회 Workstream 내부 구현

### 후속 확장

- 사용자 제보·신고는 3차 확장 범위에서 검토한다. 수정·삭제 UI는 정정 정책을 유지한 별도 범위 변경 후 검토한다.

## 10. 제품 요구사항

| PRD 요구사항 | 제품 동작 | 관련 기능 요구사항 | 중요도 | 상태 |
|---|---|---|---|---|
| PR-ADMIN-001 | 인증된 관리자만 등록 기능에 접근한다. | [FR-ADMIN-001](../../../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | Must | 확정 |
| PR-ADMIN-002 | 검증된 맛집 정보를 필수값·중복·공개 조건에 따라 등록한다. | [FR-ADMIN-002](../../../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | Must | 확정 |
| PR-ADMIN-003 | YouTube 채널 단위 유튜버 정보를 검증해 등록한다. | [FR-ADMIN-003](../../../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | Must | 확정 |
| PR-ADMIN-004 | 방문 근거로 사용할 영상 정보와 원본 링크를 검증해 등록한다. | [FR-ADMIN-004](../../../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | Must | 확정 |
| PR-ADMIN-005 | 존재하는 세 대상과 실제 방문 영상을 근거로 고유 방문 관계를 등록한다. | [FR-VISIT-001](../../../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | Must | 확정 |

## 11. 비즈니스 규칙

- 기본 데이터 최소 정보·동일성·공개 조건은 [BR-RESTAURANT-003](../../../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보)~[BR-RESTAURANT-008](../../../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건), [BR-CREATOR-001](../../../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미)~[BR-CREATOR-003](../../../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단), [BR-CREATOR-005](../../../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치), [BR-VIDEO-001](../../../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위)~[BR-VIDEO-006](../../../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분)을 따른다.
- 관계의 구성·근거·중복·참조·유효성과 날짜 제외는 [BR-VISIT-001](../../../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성)~[BR-VISIT-007](../../../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태)을 따른다.
- 접근, 사실·정합성 검증, 조회 반영, MVP 경계, 정정·동시성·보류는 [BR-ADMIN-001](../../../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증)~[BR-ADMIN-008](../../../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리)을 따른다.
- 공개·비공개와 상태 변경 일관성은 [BR-PUBLICATION-001](../../../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위), [BR-PUBLICATION-002](../../../01-requirements/business-rules.md#br-publication-002-비공개-데이터의-접근), [BR-PUBLICATION-008](../../../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성)을 따른다.

## 12. 예외 및 경계 상황

| 상황 | 기대 결과 |
|---|---|
| 인증되지 않은 접근 | 등록 기능을 거부하며 공개 조회에는 영향을 주지 않는다. |
| 필수값·허용값·링크 오류 | 대상이나 관계를 등록하지 않고 수정 가능한 오류로 구분한다. |
| 동일 장소·채널·영상·관계 | 새 대상을 만들지 않고 확인된 기존 대상을 사용한다. |
| 동일 대상 동시 등록 | 하나만 등록되고 이후 요청은 기존 대상을 사용한다. |
| 참조 대상 없음·게시 채널 불일치·근거 없음 | 방문 관계를 등록하지 않는다. |
| 동일성 판정 불가 | 일반 사용자에게 노출하지 않고 보류한다. |
| 단계 중 실패 | 불완전 데이터나 관계가 공개되지 않도록 원자성을 유지한다. |

## 13. 품질 요구사항

등록 성능은 [NFR-PERFORMANCE-003](../../../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간), 접근·입력·비밀정보 보호는 [NFR-SECURITY-001](../../../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제)~[NFR-SECURITY-003](../../../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호)을 따른다. 참조·중복·원자성은 [NFR-INTEGRITY-001](../../../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성)~[NFR-INTEGRITY-003](../../../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성), 링크·관측·테스트·개인정보는 메타데이터의 관련 NFR로 검증한다. 목표 등록 시간과 인증 수준은 확정 전까지 팀 결정 필요다.

## 14. 의존성

- 선행 정책: 관리자 인증, 식별자·필수값·중복·공개 상태와 보류 오류 계약
- 데이터 의존성: 기본 데이터가 방문 관계보다 선행
- 다른 기능 PRD: 세 조회 PRD가 등록 결과 반영을 인수 검증
- 다른 Workstream: [WS-01](../../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../../../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../../../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 조회 계약
- 외부 서비스: 카카오 장소와 YouTube 채널·영상 사실 및 링크 확인
- 공통 API 계약: 인증 주체, 오류, 충돌·보류와 식별자 계약

조회 Workstream 완료를 선행 조건으로 두지 않으며 Fixture 또는 검증용 읽기 계약으로 등록 흐름을 독립 개발한다.

## 15. Workstream 및 책임자

- 주 Workstream: [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 데이터 등록
- 최종 책임자: 김인안
- 기본 리뷰어: 박진영
- 협업: 이우람(인증·Visit 계약), 양성훈(목록 반영), 박진영(상세 반영)

## 16. 성공 기준

- 관리자가 검증된 기본 데이터와 실제 방문 관계를 중복 없이 등록한다.
- 잘못되거나 판단 불가한 데이터가 일반 사용자에게 노출되지 않는다.
- 등록 결과가 탐색·유튜버 조건·상세에 일관되게 반영된다.

## 17. 완료 기준

- 5개 요구사항과 관련 규칙의 구현·자동화 테스트가 완료된다.
- 인증, 필수값, 중복·동시성, 참조·채널 일치, 원자성과 보류 시나리오가 통과한다.
- 세 조회 Workstream에서 실제 등록 결과를 인수 검증한다.
- API·인증·오류 계약, 운영 문서와 추적성이 실제 동작과 일치한다.

## 18. 리스크

- 네 대상 등록과 세 조회 통합으로 작업량과 테스트 범위가 크다.
- 관리자 최소 인증 수준과 보류 요청의 저장·운영 방식이 미확정이다.
- 공통 데이터 관계 변경이 모든 Workstream에 영향을 줄 수 있다.

## 19. 관련 문서

- [전체 제품 PRD](../00-product-overview.md)
- [기능 요구사항](../../../01-requirements/functional-requirements.md)
- [요구사항 검토 결과](../../../01-requirements/requirements-review.md)
- [비즈니스 규칙](../../../01-requirements/business-rules.md)
- [MVP Workstream](../../../02-analysis/mvp-workstreams.md)
- [소유권](../../../03-team/ownership.md)
- [추적성](../../traceability.md)

## 20. 검토 필요 항목

- JWT Access·Refresh Token의 정확한 만료 시간, 서명 키 교체와 Redis Token 운영 방식
- 보류 요청의 저장·재검토 운영 방식(승인 화면은 MVP 제외)
- [WS-04](../../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 내부 단계별 작업 분담과 등록 목표 응답 시간
- 핵심 데이터 모델 변경 승인 방식
