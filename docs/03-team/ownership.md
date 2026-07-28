---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/business-rules.md
  - ../01-requirements/non-functional-requirements.md
  - ../02-analysis/mvp-workstreams.md
  - roles.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../00-overview/service-overview.md
  - ../00-overview/scope.md
  - ../00-overview/glossary.md
  - ../02-analysis/domain-boundaries.md
  - ../../README.md
---

# 맛잇온 기능 소유권

## 1. 문서 목적

이 문서는 맛잇온 MVP의 Workstream, 기능 요구사항, 비즈니스 규칙, 비기능 요구사항, 공통 작업과 문서의 최종 책임자를 추적한다. 소유자는 담당 항목의 요구사항 구체화, 계약, 구현, 테스트, 문서화와 통합 완료를 책임진다.

현재 배정은 2026-07-27 승인된 Workstream 구조와 책임 균형 기준안이다. 일정 차단이나 지속적인 부담 불균형이 확인될 때만 11장의 절차로 조정하며, 조정 중에도 각 Workstream과 기능 요구사항의 최종 책임자는 한 명만 유지한다.

## 2. 소유권 원칙

- 소유권 상태는 `배정 완료`, `공동 검토 필요`, `팀 결정 필요`, `후속 단계 배정`, `MVP 제외` 중 하나만 사용한다.
- 각 Workstream과 기능 요구사항에는 최종 책임자 한 명만 지정한다.
- 최종 책임자는 문서, 구현, 테스트와 통합 완료를 끝까지 책임진다.
- 보조 책임자와 협업 담당은 리뷰·계약·통합을 지원하지만 최종 책임을 공유하지 않는다.
- 기능은 기술 계층별로 분리하지 않고 완결된 사용자 가치 또는 관리자 업무 흐름으로 소유한다.
- 공동 정책은 팀이 결정하고 최초 구현과 변경 조율은 지정된 구현 책임자가 맡는다.
- 공통 기반 담당자가 각 Workstream의 적용 코드까지 모두 작성하지 않는다.
- 자신의 코드와 문서는 자신이 최종 승인하지 않는다.
- 담당자 결정이라도 다른 Workstream의 계약이나 핵심 데이터 관계에 영향을 주면 팀 협의를 거친다.
- MVP 제외 항목에는 구현 책임자를 배정하지 않는다.

## 3. Workstream 소유권

| Workstream | 최종 책임자 | 보조 책임자 | 주요 협업자 | 상태 |
|---|---|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 맛집 탐색 | 양성훈 | 이우람 | 박진영, 김인안 | 배정 완료 |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 맛집 상세 및 콘텐츠 조회 | 박진영 | 이우람 | 양성훈, 김인안 | 배정 완료 |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 유튜버 기반 탐색 | 이우람 | 박진영 | 양성훈, 김인안 | 배정 완료 |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 관리자 데이터 등록 | 김인안 | 박진영 | 이우람, 양성훈 | 배정 완료 |

### Workstream 복잡도 분석

| Workstream | 기능 요구사항 수 | 관련 영역 | 규칙·데이터 복잡도 | 의존성·조회 조합 | 정합성·테스트·연동 | 통합 위험 | 복잡도 |
|---|---:|---|---|---|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 7 | Restaurant, Visit | 검색·필터·공개·페이지 규칙과 다중 조건 데이터 접근 | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 판정 결과를 최종 AND 조건·정렬·페이지에 결합 | 조합·경계값·중복 제거 테스트, 탐색 화면 연동 범위가 큼 | 관계 판정과 등록 데이터 반영 지연 | High |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 6 | Restaurant, Visit, Creator, Video | 네 영역의 표시 정보와 공개 상태 조합 | 기본 상세와 관계·콘텐츠를 조합하고 빈 콘텐츠를 정상 처리 | 관계 정합성, 중복, 외부 링크 장애와 상세 화면 전체 테스트 | 부분 실패 정책과 공유 모델 충돌 | High |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 2 | Visit, Creator, Video, Restaurant | 최소 선택 목록과 관계 유효성·채널 일치·공개 상태·중복 정책 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)에 선택·판정 계약을 제공하지만 최종 조합은 소유하지 않음 | 관계 Fixture와 공개 상태 조합 테스트 | 잘못된 판정이 세 조회 흐름에 전파 | Medium |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 5 | 관리자 유스케이스와 네 책임 영역 | 인증, 필수값, 중복·참조·원자성과 등록 순서가 복합적 | 공통 인증과 도메인별 등록 계약을 사용하고 세 조회에 결과 제공 | 동시 등록·실패·조회 반영 테스트, 관리자 화면 연동 범위가 큼 | 작업량과 데이터 모델 충돌 | High |

### 권장 최종 배정안과 근거

- 이우람은 Medium인 [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)을 소유하고 아키텍처·공통 계약·CI·배포 조율을 맡는다. 공통 책임이 많으므로 High Workstream을 함께 배정하지 않는다.
- 양성훈은 조회 조합이 큰 [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)을 소유하고 페이지네이션·공통 응답 및 탐색 API 연동을 맡는다.
- 박진영은 네 책임 영역을 조합하는 [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)를 소유하고 관계 정합성, 마이그레이션과 통합 테스트 기반을 보조한다.
- 김인안은 가장 넓은 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)를 소유한다. AI 기술 의사결정은 김인안·이우람·양성훈, 프론트엔드 기술 의사결정은 양성훈·김인안이 주도하며, 각 Workstream 담당자는 자기 기능의 연동을 검증한다.
- 배정은 네 흐름의 병렬 개발, Visit 판정의 재사용, 등록 후 조회 반영과 공통 책임 부담을 기준으로 했다. 개인별 기술 경험이나 선호도는 반영하지 않았으므로 팀 확인 후 조정할 수 있다.

### 횡단 역할 소유권

| 역할 영역 | 의사결정 주도 | 실행 책임 | 최종 결정 | 상태 |
|---|---|---|---|---|
| 리더 | 이우람 | 관련 Workstream 담당자 | 영향받는 담당자 공동 | 배정 완료 |
| 발표자료 제작·발표 리허설 | 영크크(팀 공동) | 전체 팀 | 팀 공동 | 배정 완료 |
| 회의록·README 기록 | 영크크(팀 공동) | 전체 팀 | 팀 공동 | 배정 완료 |
| AI | 김인안, 이우람, 양성훈 | 관련 티켓 담당자 | 영향받는 담당자 공동 | 배정 완료 |
| 인프라 | 이우람 | 관련 티켓 담당자 | 영향받는 담당자 공동 | 배정 완료 |
| 백엔드 | 양성훈, 박진영 | 관련 티켓 담당자 | 영향받는 담당자 공동 | 배정 완료 |
| 프론트엔드 | 양성훈, 김인안 | 관련 티켓 담당자 | 영향받는 담당자 공동 | 배정 완료 |

횡단 역할의 `의사결정 주도`는 구현 독점이나 단독 승인 권한을 뜻하지 않는다. 담당자는 기준과 대안을 정리하며, 실제 구현은 티켓 담당자가 수행하고 영향받는 담당자가 함께 최종 결정한다.

## 4. 기능 요구사항 소유권

| 요구사항 ID | 기능 | Workstream | 최종 책임자 | 리뷰 담당 | 상태 |
|---|---|---|---|---|---|
| [FR-RESTAURANT-001](../01-requirements/functional-requirements.md#fr-restaurant-001-맛집-목록-조회) | 맛집 목록 조회 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 | 배정 완료 |
| [FR-RESTAURANT-002](../01-requirements/functional-requirements.md#fr-restaurant-002-맛집-이름-검색) | 맛집 이름 검색 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 | 배정 완료 |
| [FR-RESTAURANT-003](../01-requirements/functional-requirements.md#fr-restaurant-003-지역별-필터) | 지역별 필터 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 | 배정 완료 |
| [FR-RESTAURANT-004](../01-requirements/functional-requirements.md#fr-restaurant-004-음식-카테고리별-필터) | 음식 카테고리별 필터 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 | 배정 완료 |
| [FR-CREATOR-001](../01-requirements/functional-requirements.md#fr-creator-001-유튜버-기준-방문-맛집-조회) | 유튜버 기준 방문 맛집 조회 | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 | 양성훈 | 배정 완료 |
| [FR-CREATOR-003](../01-requirements/functional-requirements.md#fr-creator-003-유튜버-필터-선택-목록-조회) | 유튜버 필터 선택 목록 조회 | [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) | 이우람 | 양성훈 | 배정 완료 |
| [FR-RESTAURANT-005](../01-requirements/functional-requirements.md#fr-restaurant-005-검색-및-필터-조건-조합) | 검색 및 필터 조건 조합 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 이우람 | 배정 완료 |
| [FR-RESTAURANT-006](../01-requirements/functional-requirements.md#fr-restaurant-006-페이지-단위-조회) | 페이지 단위 조회 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 박진영 | 배정 완료 |
| [FR-RESTAURANT-007](../01-requirements/functional-requirements.md#fr-restaurant-007-기본-정렬-적용) | 기본 정렬 적용 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) | 양성훈 | 박진영 | 배정 완료 |
| [FR-RESTAURANT-008](../01-requirements/functional-requirements.md#fr-restaurant-008-맛집-기본-정보-조회) | 맛집 기본 정보 조회 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 김인안 | 배정 완료 |
| [FR-RESTAURANT-009](../01-requirements/functional-requirements.md#fr-restaurant-009-지역-정보-확인) | 지역 정보 확인 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 양성훈 | 배정 완료 |
| [FR-RESTAURANT-010](../01-requirements/functional-requirements.md#fr-restaurant-010-음식-카테고리-확인) | 음식 카테고리 확인 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 양성훈 | 배정 완료 |
| [FR-RESTAURANT-011](../01-requirements/functional-requirements.md#fr-restaurant-011-영상-연결이-없는-맛집-상세-조회) | 영상 연결이 없는 맛집 상세 조회 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 김인안 | 배정 완료 |
| [FR-CREATOR-002](../01-requirements/functional-requirements.md#fr-creator-002-방문-유튜버-정보-확인) | 방문 유튜버 정보 확인 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 이우람 | 배정 완료 |
| [FR-VIDEO-001](../01-requirements/functional-requirements.md#fr-video-001-관련-영상-정보-확인) | 관련 영상 정보 확인 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) | 박진영 | 이우람 | 배정 완료 |
| [FR-ADMIN-001](../01-requirements/functional-requirements.md#fr-admin-001-관리자-등록-기능-접근) | 관리자 등록 기능 접근 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 이우람 | 배정 완료 |
| [FR-ADMIN-002](../01-requirements/functional-requirements.md#fr-admin-002-맛집-정보-등록) | 맛집 정보 등록 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 양성훈 | 배정 완료 |
| [FR-ADMIN-003](../01-requirements/functional-requirements.md#fr-admin-003-유튜버-정보-등록) | 유튜버 정보 등록 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 이우람 | 배정 완료 |
| [FR-ADMIN-004](../01-requirements/functional-requirements.md#fr-admin-004-영상-정보-등록) | 영상 정보 등록 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 박진영 | 배정 완료 |
| [FR-VISIT-001](../01-requirements/functional-requirements.md#fr-visit-001-맛집유튜버영상-방문-관계-등록) | 맛집·유튜버·영상 방문 관계 등록 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) | 김인안 | 이우람 | 배정 완료 |

기능 요구사항 20개는 모두 정확히 하나의 Workstream과 최종 책임자에게 배정되었다. 리뷰 담당자는 기능 책임자와 분리했으며 관계·조회·등록 계약에 따라 교차 배정했다.

## 5. 비즈니스 규칙 소유권

| 규칙 ID | 규칙 | 주 도메인 | 최종 책임자 | 협업 담당 | 상태 |
|---|---|---|---|---|---|
| [BR-RESTAURANT-001](../01-requirements/business-rules.md#br-restaurant-001-맛집의-의미) | 맛집의 의미 | Restaurant | 김인안 | 양성훈, 박진영 | 배정 완료 |
| [BR-RESTAURANT-002](../01-requirements/business-rules.md#br-restaurant-002-영상과-독립된-맛집) | 영상과 독립된 맛집 | Restaurant | 박진영 | 양성훈, 김인안 | 배정 완료 |
| [BR-RESTAURANT-003](../01-requirements/business-rules.md#br-restaurant-003-맛집-최소-등록-정보) | 맛집 최소 등록 정보 | Restaurant | 김인안 | 양성훈, 박진영 | 배정 완료 |
| [BR-RESTAURANT-004](../01-requirements/business-rules.md#br-restaurant-004-대표-음식-카테고리) | 대표 음식 카테고리 | Restaurant | 김인안 | 양성훈, 박진영 | 배정 완료 |
| [BR-RESTAURANT-005](../01-requirements/business-rules.md#br-restaurant-005-맛집의-지역-소속) | 맛집의 지역 소속 | Restaurant | 김인안 | 양성훈, 박진영 | 배정 완료 |
| [BR-RESTAURANT-006](../01-requirements/business-rules.md#br-restaurant-006-맛집-중복-판단) | 맛집 중복 판단 | Restaurant | 김인안 | 양성훈 | 배정 완료 |
| [BR-RESTAURANT-007](../01-requirements/business-rules.md#br-restaurant-007-동일-상호의-지점-구분) | 동일 상호의 지점 구분 | Restaurant | 김인안 | 양성훈 | 배정 완료 |
| [BR-RESTAURANT-008](../01-requirements/business-rules.md#br-restaurant-008-맛집-공개-조건) | 맛집 공개 조건 | Restaurant | 김인안 | 양성훈, 박진영 | 공동 검토 필요 |
| [BR-RESTAURANT-009](../01-requirements/business-rules.md#br-restaurant-009-맛집-이름-변경) | 맛집 이름 변경 | Restaurant | 김인안 | 양성훈, 이우람 | 배정 완료 |
| [BR-RESTAURANT-010](../01-requirements/business-rules.md#br-restaurant-010-주소-변경과-장소-이전) | 주소 변경과 장소 이전 | Restaurant | 김인안 | 이우람, 박진영 | 공동 검토 필요 |
| [BR-RESTAURANT-011](../01-requirements/business-rules.md#br-restaurant-011-폐업과-장기-운영-중단) | 폐업과 장기 운영 중단 | Restaurant | 김인안 | 양성훈, 박진영 | 공동 검토 필요 |
| [BR-CREATOR-001](../01-requirements/business-rules.md#br-creator-001-유튜버-정보의-의미) | 유튜버 정보의 의미 | Creator | 김인안 | 이우람, 박진영 | 배정 완료 |
| [BR-CREATOR-002](../01-requirements/business-rules.md#br-creator-002-유튜버-최소-등록-정보) | 유튜버 최소 등록 정보 | Creator | 김인안 | 이우람 | 배정 완료 |
| [BR-CREATOR-003](../01-requirements/business-rules.md#br-creator-003-동일-채널-중복-판단) | 동일 채널 중복 판단 | Creator | 김인안 | 이우람 | 배정 완료 |
| [BR-CREATOR-004](../01-requirements/business-rules.md#br-creator-004-유튜버-표시-정보) | 유튜버 표시 정보 | Creator | 박진영 | 이우람, 양성훈 | 배정 완료 |
| [BR-CREATOR-005](../01-requirements/business-rules.md#br-creator-005-방문-관계의-유튜버-일치) | 방문 관계의 유튜버 일치 | Visit | 김인안 | 이우람, 박진영 | 배정 완료 |
| [BR-CREATOR-006](../01-requirements/business-rules.md#br-creator-006-채널명-변경과-동일성-유지) | 채널명 변경과 동일성 유지 | Creator | 김인안 | 이우람, 박진영 | 공동 검토 필요 |
| [BR-CREATOR-007](../01-requirements/business-rules.md#br-creator-007-채널-이용-불가-처리) | 채널 이용 불가 처리 | Creator | 이우람 | 김인안, 박진영 | 공동 검토 필요 |
| [BR-VIDEO-001](../01-requirements/business-rules.md#br-video-001-영상의-의미와-보관-범위) | 영상의 의미와 보관 범위 | Video | 김인안 | 박진영 | 배정 완료 |
| [BR-VIDEO-002](../01-requirements/business-rules.md#br-video-002-영상-최소-등록-정보) | 영상 최소 등록 정보 | Video | 김인안 | 박진영 | 배정 완료 |
| [BR-VIDEO-003](../01-requirements/business-rules.md#br-video-003-영상-식별-및-중복-판단) | 영상 식별 및 중복 판단 | Video | 김인안 | 박진영 | 배정 완료 |
| [BR-VIDEO-004](../01-requirements/business-rules.md#br-video-004-영상과-방문-관계의-다대상-연결) | 영상과 방문 관계의 다대상 연결 | Visit | 김인안 | 이우람, 박진영 | 배정 완료 |
| [BR-VIDEO-005](../01-requirements/business-rules.md#br-video-005-실제-방문-근거) | 실제 방문 근거 | Visit | 김인안 | 이우람, 박진영 | 배정 완료 |
| [BR-VIDEO-006](../01-requirements/business-rules.md#br-video-006-게시일과-방문일의-구분) | 게시일과 방문일의 구분 | Visit | 김인안 | 이우람 | 배정 완료 |
| [BR-VIDEO-007](../01-requirements/business-rules.md#br-video-007-외부-링크-장애의-격리) | 외부 링크 장애의 격리 | Video | 박진영 | 김인안, 이우람 | 배정 완료 |
| [BR-VIDEO-008](../01-requirements/business-rules.md#br-video-008-영상-표시-정보-변경) | 영상 표시 정보 변경 | Video | 김인안 | 박진영, 이우람 | 공동 검토 필요 |
| [BR-VIDEO-009](../01-requirements/business-rules.md#br-video-009-영상-이용-불가-처리) | 영상 이용 불가 처리 | Video | 이우람 | 김인안, 박진영 | 공동 검토 필요 |
| [BR-VISIT-001](../01-requirements/business-rules.md#br-visit-001-방문-관계의-구성) | 방문 관계의 구성 | Visit | 김인안 | 이우람, 박진영 | 공동 검토 필요 |
| [BR-VISIT-002](../01-requirements/business-rules.md#br-visit-002-방문-근거-필수) | 방문 근거 필수 | Visit | 김인안 | 이우람, 박진영 | 배정 완료 |
| [BR-VISIT-003](../01-requirements/business-rules.md#br-visit-003-방문-관계-중복-판단) | 방문 관계 중복 판단 | Visit | 김인안 | 이우람 | 배정 완료 |
| [BR-VISIT-004](../01-requirements/business-rules.md#br-visit-004-방문-관계의-연결-범위) | 방문 관계의 연결 범위 | Visit | 김인안 | 이우람, 박진영 | 배정 완료 |
| [BR-VISIT-005](../01-requirements/business-rules.md#br-visit-005-방문-관계의-조회-유효성) | 방문 관계의 조회 유효성 | Visit | 이우람 | 김인안, 박진영 | 배정 완료 |
| [BR-VISIT-006](../01-requirements/business-rules.md#br-visit-006-방문-날짜-관리-제외) | 방문 날짜 관리 제외 | Visit | 김인안 | 이우람 | 배정 완료 |
| [BR-VISIT-007](../01-requirements/business-rules.md#br-visit-007-등록-완료와-검증-상태) | 등록 완료와 검증 상태 | Visit | 김인안 | 이우람 | 공동 검토 필요 |
| [BR-SEARCH-001](../01-requirements/business-rules.md#br-search-001-검색-대상과-일치-기준) | 검색 대상과 일치 기준 | Restaurant | 양성훈 | 박진영 | 배정 완료 |
| [BR-SEARCH-002](../01-requirements/business-rules.md#br-search-002-검색어-공백-처리) | 검색어 공백 처리 | Restaurant | 양성훈 | 박진영 | 배정 완료 |
| [BR-SEARCH-003](../01-requirements/business-rules.md#br-search-003-필터-종류와-단일-선택) | 필터 종류와 단일 선택 | Restaurant | 양성훈 | 이우람 | 배정 완료 |
| [BR-SEARCH-004](../01-requirements/business-rules.md#br-search-004-검색과-필터-조합) | 검색과 필터 조합 | Restaurant | 양성훈 | 이우람 | 배정 완료 |
| [BR-SEARCH-005](../01-requirements/business-rules.md#br-search-005-조회-결과의-고유성) | 조회 결과의 고유성 | Restaurant | 양성훈 | 이우람 | 배정 완료 |
| [BR-SEARCH-006](../01-requirements/business-rules.md#br-search-006-빈-조회-결과) | 빈 조회 결과 | Restaurant | 양성훈 | 박진영 | 배정 완료 |
| [BR-SEARCH-007](../01-requirements/business-rules.md#br-search-007-유튜버-필터의-방문-근거) | 유튜버 필터의 방문 근거 | Visit | 이우람 | 양성훈, 박진영 | 배정 완료 |
| [BR-SEARCH-008](../01-requirements/business-rules.md#br-search-008-페이지-단위-조회) | 페이지 단위 조회 | Restaurant | 양성훈 | 박진영 | 배정 완료 |
| [BR-SEARCH-009](../01-requirements/business-rules.md#br-search-009-기본-정렬) | 기본 정렬 | Restaurant | 양성훈 | 박진영 | 배정 완료 |
| [BR-ADMIN-001](../01-requirements/business-rules.md#br-admin-001-관리자-권한-검증) | 관리자 권한 검증 | 관리자 유스케이스 | 김인안 | 이우람 | 공동 검토 필요 |
| [BR-ADMIN-002](../01-requirements/business-rules.md#br-admin-002-등록-전-사실-검증) | 등록 전 사실 검증 | 관리자 유스케이스 | 김인안 | 양성훈, 박진영 | 배정 완료 |
| [BR-ADMIN-003](../01-requirements/business-rules.md#br-admin-003-등록-정합성-검증) | 등록 정합성 검증 | 관리자 유스케이스 | 김인안 | 전체 팀 | 공동 검토 필요 |
| [BR-ADMIN-004](../01-requirements/business-rules.md#br-admin-004-검증-후-등록-및-조회-반영) | 검증 후 등록 및 조회 반영 | 관리자 유스케이스 | 김인안 | 양성훈, 박진영, 이우람 | 공동 검토 필요 |
| [BR-ADMIN-005](../01-requirements/business-rules.md#br-admin-005-mvp-관리-기능의-경계) | MVP 관리 기능의 경계 | 관리자 유스케이스 | 김인안 | 이우람 | 배정 완료 |
| [BR-ADMIN-006](../01-requirements/business-rules.md#br-admin-006-잘못-등록된-데이터의-정정-원칙) | 잘못 등록된 데이터의 정정 원칙 | 관리자 유스케이스 | 김인안 | 전체 팀 | 공동 검토 필요 |
| [BR-ADMIN-007](../01-requirements/business-rules.md#br-admin-007-동시-등록의-고유성) | 동시 등록의 고유성 | 관리자 유스케이스 | 김인안 | 박진영, 이우람 | 공동 검토 필요 |
| [BR-ADMIN-008](../01-requirements/business-rules.md#br-admin-008-보류-요청의-처리) | 보류 요청의 처리 | 관리자 유스케이스 | 김인안 | 전체 팀 | 공동 검토 필요 |
| [BR-PUBLICATION-001](../01-requirements/business-rules.md#br-publication-001-일반-사용자-공개-범위) | 일반 사용자 공개 범위 | 공통 공개 정책 | 이우람 | 전체 팀 | 공동 검토 필요 |
| [BR-PUBLICATION-002](../01-requirements/business-rules.md#br-publication-002-비공개-데이터의-접근) | 비공개 데이터의 접근 | 공통 공개 정책 | 김인안 | 이우람 | 공동 검토 필요 |
| [BR-PUBLICATION-003](../01-requirements/business-rules.md#br-publication-003-맛집-상태와-연결-정보-노출) | 맛집 상태와 연결 정보 노출 | Restaurant | 박진영 | 양성훈, 이우람 | 공동 검토 필요 |
| [BR-PUBLICATION-004](../01-requirements/business-rules.md#br-publication-004-유튜버-상태와-관계-노출) | 유튜버 상태와 관계 노출 | Creator | 이우람 | 김인안, 박진영 | 공동 검토 필요 |
| [BR-PUBLICATION-005](../01-requirements/business-rules.md#br-publication-005-영상-상태와-관계-노출) | 영상 상태와 관계 노출 | Video | 이우람 | 김인안, 박진영 | 공동 검토 필요 |
| [BR-PUBLICATION-006](../01-requirements/business-rules.md#br-publication-006-관계-상태와-맛집-기본-조회) | 관계 상태와 맛집 기본 조회 | Visit | 이우람 | 양성훈, 박진영 | 공동 검토 필요 |
| [BR-PUBLICATION-007](../01-requirements/business-rules.md#br-publication-007-외부-영상-삭제의-영향-범위) | 외부 영상 삭제의 영향 범위 | Video | 박진영 | 이우람, 김인안 | 공동 검토 필요 |
| [BR-PUBLICATION-008](../01-requirements/business-rules.md#br-publication-008-상태-변경의-일관성) | 상태 변경의 일관성 | 공통 공개 정책 | 김인안 | 전체 팀 | 공동 검토 필요 |

공동 검토가 필요한 규칙도 최종 책임자는 한 명이다. 해당 상태는 소유권 미배정이 아니라 여러 Workstream의 공개 상태, 데이터 관계 또는 조회 결과에 영향을 주므로 변경 시 공동 리뷰가 필요하다는 뜻이다.

## 6. 비기능 요구사항 소유권

### Workstream 적용형

| NFR ID | 요구사항 | 적용 유형 | 구현 책임자 | 준수 책임자 | 리뷰 담당 | 상태 |
|---|---|---|---|---|---|---|
| [NFR-PERFORMANCE-001](../01-requirements/non-functional-requirements.md#nfr-performance-001-일반-조회-응답-시간) | 일반 조회 응답 시간 | Workstream 적용형 | 각 조회 Workstream 담당자 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 담당자 | 이우람 | 배정 완료 |
| [NFR-PERFORMANCE-002](../01-requirements/non-functional-requirements.md#nfr-performance-002-검색필터-조합-응답-시간) | 검색·필터 조합 응답 시간 | Workstream 적용형 | 양성훈 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 담당자 | 이우람 | 배정 완료 |
| [NFR-PERFORMANCE-003](../01-requirements/non-functional-requirements.md#nfr-performance-003-관리자-등록-응답-시간) | 관리자 등록 응답 시간 | Workstream 적용형 | 김인안 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자 | 박진영 | 배정 완료 |
| [NFR-PERFORMANCE-004](../01-requirements/non-functional-requirements.md#nfr-performance-004-페이지-크기-및-조회량-제한) | 페이지 크기 및 조회량 제한 | Workstream 적용형 | 양성훈 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 담당자 | 박진영 | 배정 완료 |
| [NFR-SECURITY-002](../01-requirements/non-functional-requirements.md#nfr-security-002-입력-및-웹-공격-방어) | 입력 및 웹 공격 방어 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-INTEGRITY-001](../01-requirements/non-functional-requirements.md#nfr-integrity-001-참조-및-필수값-정합성) | 참조 및 필수값 정합성 | Workstream 적용형 | 김인안 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 및 관련 도메인 담당자 | 박진영 | 배정 완료 |
| [NFR-INTEGRITY-002](../01-requirements/non-functional-requirements.md#nfr-integrity-002-중복-및-동시-등록-방지) | 중복 및 동시 등록 방지 | Workstream 적용형 | 김인안 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자 | 이우람 | 배정 완료 |
| [NFR-INTEGRITY-003](../01-requirements/non-functional-requirements.md#nfr-integrity-003-등록-원자성과-공개-상태-일관성) | 등록 원자성과 공개 상태 일관성 | Workstream 적용형 | 김인안 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 및 조회 담당자 | 박진영 | 배정 완료 |
| [NFR-INTEGRITY-004](../01-requirements/non-functional-requirements.md#nfr-integrity-004-외부-링크와-내부-데이터-분리) | 외부 링크와 내부 데이터 분리 | Workstream 적용형 | 박진영 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자 | 이우람 | 배정 완료 |
| [NFR-RELIABILITY-002](../01-requirements/non-functional-requirements.md#nfr-reliability-002-저장소-장애-및-재시도-통제) | 저장소 장애 및 재시도 통제 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-RELIABILITY-003](../01-requirements/non-functional-requirements.md#nfr-reliability-003-사용자-오류-메시지와-기능-분리) | 사용자 오류 메시지와 기능 분리 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 양성훈 | 배정 완료 |
| [NFR-EXTERNAL-001](../01-requirements/non-functional-requirements.md#nfr-external-001-영상-원본과-외부-링크-분리) | 영상 원본과 외부 링크 분리 | Workstream 적용형 | 박진영 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자 | 김인안 | 배정 완료 |
| [NFR-EXTERNAL-002](../01-requirements/non-functional-requirements.md#nfr-external-002-외부-호출-실패와-변경-격리) | 외부 호출 실패와 변경 격리 | Workstream 적용형 | 박진영 | [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)·[WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자 | 이우람 | 배정 완료 |
| [NFR-EXTERNAL-003](../01-requirements/non-functional-requirements.md#nfr-external-003-링크-검증과-외부-인증정보) | 링크 검증과 외부 인증정보 | Workstream 적용형 | 김인안 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 담당자 | 이우람 | 배정 완료 |
| [NFR-COMPATIBILITY-001](../01-requirements/non-functional-requirements.md#nfr-compatibility-001-웹모바일-브라우저-호환성) | 웹·모바일 브라우저 호환성 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-COMPATIBILITY-003](../01-requirements/non-functional-requirements.md#nfr-compatibility-003-모바일-응답-크기) | 모바일 응답 크기 | Workstream 적용형 | 각 조회 Workstream 담당자 | [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)·[WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)·[WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 담당자 | 김인안 | 배정 완료 |
| [NFR-TEST-001](../01-requirements/non-functional-requirements.md#nfr-test-001-자동화-테스트-계층) | 자동화 테스트 계층 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-TEST-002](../01-requirements/non-functional-requirements.md#nfr-test-002-변경외부-의존성성능-검증) | 변경·외부 의존성·성능 검증 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-MAINTAINABILITY-001](../01-requirements/non-functional-requirements.md#nfr-maintainability-001-책임과-의존성-경계) | 책임과 의존성 경계 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 이우람 | 배정 완료 |
| [NFR-MAINTAINABILITY-002](../01-requirements/non-functional-requirements.md#nfr-maintainability-002-공통-정책과-규칙-배치) | 공통 정책과 규칙 배치 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 이우람 | 배정 완료 |
| [NFR-MAINTAINABILITY-003](../01-requirements/non-functional-requirements.md#nfr-maintainability-003-추적성과-운영-복잡도) | 추적성과 운영 복잡도 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 이우람 | 배정 완료 |
| [NFR-PRIVACY-001](../01-requirements/non-functional-requirements.md#nfr-privacy-001-mvp-개인정보-최소화) | MVP 개인정보 최소화 | Workstream 적용형 | 각 Workstream 담당자 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-PRIVACY-003](../01-requirements/non-functional-requirements.md#nfr-privacy-003-회원-기능-도입-시-재검토) | 회원 기능 도입 시 재검토 | Workstream 적용형 | 없음 | 없음 | 이우람 | MVP 제외 |

### 공통 기반형

| NFR ID | 요구사항 | 적용 유형 | 구현 책임자 | 준수 책임자 | 리뷰 담당 | 상태 |
|---|---|---|---|---|---|---|
| [NFR-SECURITY-001](../01-requirements/non-functional-requirements.md#nfr-security-001-공개-조회와-관리자-접근-통제) | 공개 조회와 관리자 접근 통제 | 공통 기반형 | 김인안 | 전체 팀 | 이우람 | 배정 완료 |
| [NFR-SECURITY-003](../01-requirements/non-functional-requirements.md#nfr-security-003-비밀정보와-오류-정보-보호) | 비밀정보와 오류 정보 보호 | 공통 기반형 | 이우람 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-RELIABILITY-001](../01-requirements/non-functional-requirements.md#nfr-reliability-001-오류-격리와-공통-오류-정책) | 오류 격리와 공통 오류 정책 | 공통 기반형 | 양성훈 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-AVAILABILITY-001](../01-requirements/non-functional-requirements.md#nfr-availability-001-상태-확인과-장애-구분) | 상태 확인과 장애 구분 | 공통 기반형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-AVAILABILITY-002](../01-requirements/non-functional-requirements.md#nfr-availability-002-최종-배포-가용성과-수동-복구) | 최종 배포 가용성과 수동 복구 | 최종 배포형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-OBSERVABILITY-001](../01-requirements/non-functional-requirements.md#nfr-observability-001-요청-추적과-오류-분류) | 요청 추적과 오류 분류 | 공통 기반형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-OBSERVABILITY-002](../01-requirements/non-functional-requirements.md#nfr-observability-002-운영-지표와-생명주기-기록) | 운영 지표와 생명주기 기록 | 공통 기반형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-OBSERVABILITY-003](../01-requirements/non-functional-requirements.md#nfr-observability-003-로그-품질과-민감정보-차단) | 로그 품질과 민감정보 차단 | 공통 기반형 | 이우람 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-COMPATIBILITY-002](../01-requirements/non-functional-requirements.md#nfr-compatibility-002-응답-형식과-문자-처리) | 응답 형식과 문자 처리 | 공통 기반형 | 양성훈 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-TEST-003](../01-requirements/non-functional-requirements.md#nfr-test-003-배포-품질-게이트) | 배포 품질 게이트 | 공통 기반형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-DEPLOYMENT-001](../01-requirements/non-functional-requirements.md#nfr-deployment-001-재현-가능한-빌드와-환경-분리) | 재현 가능한 빌드와 환경 분리 | 공통 기반형 | 이우람 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-DEPLOYMENT-002](../01-requirements/non-functional-requirements.md#nfr-deployment-002-배포-전후-검증) | 배포 전후 검증 | 공통 기반형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-DEPLOYMENT-003](../01-requirements/non-functional-requirements.md#nfr-deployment-003-버전-추적과-복구-절차) | 버전 추적과 복구 절차 | 공통 기반형 | 이우람 | 전체 팀 | 박진영 | 배정 완료 |
| [NFR-DEPLOYMENT-004](../01-requirements/non-functional-requirements.md#nfr-deployment-004-단계별-실행-및-최종-배포-복잡도-제한) | 단계별 실행 및 최종 배포 복잡도 제한 | 공통 기반형 | 이우람 | 전체 팀 | 김인안 | 배정 완료 |
| [NFR-PRIVACY-002](../01-requirements/non-functional-requirements.md#nfr-privacy-002-인증정보와-외부-키-보호) | 인증정보와 외부 키 보호 | 공통 기반형 | 이우람 | 김인안 및 외부 연동 담당자 | 김인안 | 배정 완료 |

아직 후속 설계가 필요한 기준만 `팀 결정 필요`로 유지한다. 2026-07-27 확정된 부하·데이터·브라우저·배포·알림 기준은 `배정 완료`로 관리한다.

## 7. 공통 작업 소유권

| 공통 작업 | 결정 책임 | 구현 책임 | 리뷰 담당 | 필요한 시점 | 상태 |
|---|---|---|---|---|---|
| 프로젝트 초기 설정 | 팀 공동 | 이우람 | 박진영 | 개발 시작 전 | 배정 완료 |
| 코드 스타일 및 정적 분석 기준 | 팀 공동 | 이우람 | 양성훈 | 개발 시작 전 | 배정 완료 |
| 공통 응답 형식 | 팀 공동 | 양성훈 | 김인안 | API 구현 전 | 배정 완료 |
| 공통 오류 처리 | 팀 공동 | 양성훈 | 박진영 | API 구현 전 | 배정 완료 |
| 페이지네이션 계약 | 영향받는 조회 담당자 공동 | 양성훈 | 박진영 | 조회 API 구현 전 | 배정 완료 |
| 관리자 인증 및 권한 기반 | 팀 공동 | 김인안 | 이우람 | [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 연결 전 | 배정 완료 |
| API 문서화 기반 | 팀 공동 | 양성훈 | 김인안 | API 계약 작성 전 | 배정 완료 |
| 데이터베이스 마이그레이션 기반 | 팀 공동 | 박진영 | 이우람 | 데이터 구현 전 | 배정 완료 |
| 테스트 환경 | 팀 공동 | 박진영 | 양성훈 | 병렬 개발 전 | 배정 완료 |
| CI 검증 | 팀 공동 | 이우람 | 박진영 | 첫 통합 전 | 배정 완료 |
| 배포 및 헬스체크 | 팀 공동 | 이우람 | 박진영 | 전체 인수 전 | 배정 완료 |
| 환경별 설정 및 비밀정보 관리 | 팀 공동 | 이우람 | 김인안 | 배포 설계 전 | 배정 완료 |
| AI 기능·활용 및 연동 기준 | 김인안·이우람·양성훈 주도, 영향받는 담당자 공동 | 관련 티켓 담당자 | 박진영 및 영향받는 담당자 | AI 작업 전 | 배정 완료 |
| 프론트엔드 구조·사용자 흐름·백엔드 연동 기준 | 양성훈·김인안 주도, 영향받는 담당자 공동 | 관련 티켓 담당자 | 이우람, 박진영 | 프론트엔드 구현 전 | 배정 완료 |
| 발표자료 제작 및 발표 리허설 | 팀 공동 | 영크크(팀 공동) | 전체 팀 | 발표 전 | 배정 완료 |
| 회의록 정리 | 팀 공동 | 영크크(팀 공동) | 전체 팀 | 회의 직후 | 배정 완료 |
| 통합 테스트 조율 | 팀 공동 | 박진영 | 이우람 | Workstream 통합 전 | 배정 완료 |

공통 오류 구조는 팀이 결정하고 양성훈이 공통 예외 처리 기반을 구현한다. 각 기능의 업무 오류 발생 조건과 매핑은 각 Workstream 담당자가 구현한다. 관리자 인증 정책은 팀이 공동 결정하고 김인안이 기반 구현과 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 적용을 책임지며 이우람이 보안·공통 계약을 리뷰한다.

## 8. 문서 소유권

| 문서 | 갱신 조율 책임 | 내용 작성 책임 | 리뷰·승인 | 상태 |
|---|---|---|---|---|
| [service-overview.md](../00-overview/service-overview.md) | 이우람 | 변경 제안자 | 팀 공동 | 배정 완료 |
| [scope.md](../00-overview/scope.md) | 이우람 | 변경 제안자 | 팀 공동 | 배정 완료 |
| [glossary.md](../00-overview/glossary.md) | 박진영 | 각 용어 소유 담당자 | 영향받는 담당자 | 배정 완료 |
| [functional-requirements.md](../01-requirements/functional-requirements.md) | 양성훈 | 각 Workstream 담당자 | 교차 리뷰 | 배정 완료 |
| [business-rules.md](../01-requirements/business-rules.md) | 박진영 | 각 규칙 최종 책임자 | 영향받는 담당자 | 배정 완료 |
| [non-functional-requirements.md](../01-requirements/non-functional-requirements.md) | 이우람 | 각 NFR 구현·준수 책임자 | 팀 공동 | 배정 완료 |
| [domain-boundaries.md](../02-analysis/domain-boundaries.md) | 박진영 | 변경 제안자 | 팀 공동 | 배정 완료 |
| [mvp-workstreams.md](../02-analysis/mvp-workstreams.md) | 이우람 | 각 Workstream 담당자 | 팀 공동 | 배정 완료 |
| [roles.md](roles.md) | 이우람 | 역할 변경 제안자 | 팀 공동 | 배정 완료 |
| [ownership.md](ownership.md) | 양성훈 | 각 소유권 책임자 | 팀 공동 | 배정 완료 |
| [README.md](../../README.md) | 영크크(팀 공동) | 전체 팀 | 팀 공동 | 배정 완료 |
| PRD | 김인안 | 각 Workstream 담당자 | 이우람 및 영향받는 담당자 | 후속 단계 배정 |
| 기능별 API 명세 | 각 Workstream 담당자 | 각 Workstream 담당자 | 다른 Workstream 담당자 | 후속 단계 배정 |
| 데이터 모델 및 ERD | 박진영 | 각 데이터 책임 변경 제안자 | 팀 공동 | 후속 단계 배정 |
| ADR | 이우람 | 결정 제안자 | 팀 공동 | 후속 단계 배정 |
| 구현 계획 | 이우람 | 각 Workstream 담당자 | 교차 리뷰 | 후속 단계 배정 |
| 배포 문서 | 이우람 | 배포 변경 구현자 | 박진영 | 후속 단계 배정 |

- 상위 범위 문서는 이우람이 변경을 조율하되 내용을 단독 결정하지 않는다.
- 기능별 API 명세는 해당 Workstream 담당자가 작성하고 다른 Workstream 담당자가 리뷰한다.
- 데이터 모델과 ERD는 박진영이 일관성을 조율하지만 핵심 관계는 팀이 공동 승인한다.
- ADR 작성자는 결정 제안자일 수 있으나 승인자는 팀이다.
- 문서 조율 책임자는 모든 내용을 직접 작성하지 않는다. 각 담당자가 자신의 기능 문서를 작성하고 조율 책임자가 용어와 추적성의 일관성을 검토한다.

## 9. 리뷰 및 승인 책임

### 기본 리뷰 구조

| 변경 소유 | 기본 리뷰 담당 | 추가 리뷰가 필요한 경우 |
|---|---|---|
| [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색) 양성훈 | 이우람 | 상세 식별자 변경은 박진영, 등록 데이터 변경은 김인안 |
| [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 박진영 | 김인안 | Visit 판정 변경은 이우람, 목록 공통 필드 변경은 양성훈 |
| [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 이우람 | 양성훈 | 상세 관계 결과 변경은 박진영, 등록 관계 변경은 김인안 |
| [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 김인안 | 박진영 | 인증·공통 계약은 이우람, 목록 반영은 양성훈 |

- 자신의 코드와 문서는 본인이 최종 승인하지 않는다.
- 다른 Workstream 담당자 한 명 이상이 코드와 관련 문서를 함께 리뷰한다.
- 공통 계약 변경은 영향받는 모든 담당자가 리뷰한다.
- Restaurant·Creator·Video·Visit 핵심 관계 변경은 팀 공동 리뷰 대상이다.
- 보안·배포·설정 변경은 이우람과 김인안, 마이그레이션 변경은 박진영이 필수 리뷰한다.
- PR 승인자와 기능 책임자는 분리하며 같은 리뷰 조합이 고착되지 않도록 대체 리뷰어를 순환한다.
- 외부 계약에 영향을 주지 않는 사소한 내부 리팩터링은 지정 리뷰어 한 명의 승인으로 충분하다.
- 리뷰 승인은 요구사항 ID, 계약 변경, 테스트 결과와 문서 갱신 여부를 근거로 한다.

## 10. 미배정 및 중복 소유권 검토

### 검증 결과

- 팀원 4명 모두 하나의 주요 기능 Workstream을 소유한다.
- 프로젝트 리더 이우람도 [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)의 최종 책임자다.
- 네 Workstream과 기능 요구사항 20개에 최종 책임자 한 명이 지정되어 있으며 중복 최종 책임자는 없다.
- 역할은 기술 계층별로 분리하지 않았다.
- 공통 작업은 결정 책임과 구현 책임을 구분했다.
- High Workstream 담당자에게 공통 책임이 일부 배정되지만 이우람이 Medium Workstream과 공통 인프라를 맡아 부담을 완화했다.
- 김인안의 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)는 단순 CRUD가 아니라 인증, 검증, 등록 순서, 정합성과 세 조회 흐름 반영까지 포함한다.
- Visit, Creator, Video, Restaurant 통합은 [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회) 조합, [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 판정과 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 등록 책임으로 구분했다.
- AI 의사결정은 김인안·이우람·양성훈, 프론트엔드 의사결정은 양성훈·김인안이 주도하고 각 Workstream 담당자가 자기 계약과 구현을 검증한다.
- 발표자료, 발표 리허설, 회의록과 README는 영크크 팀 공동 책임이며 모든 팀원이 작성에 참여한다.
- 문서 소유권은 기능·규칙·공통 작업의 코드 소유권과 연결했다.
- 모든 기능 요구사항의 리뷰 담당자는 최종 책임자와 분리했다.

### RV-ROLE-001 관리자 등록 Workstream 분리 여부

- 현재 상태: 결정 완료 — 하나의 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 안에서 두 단계로 유지
- 관련 Workstream:
  - [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)
- 현재 권장 담당자:
  - 김인안
- 결정 내용:
  - 김인안이 하나의 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)를 최종 소유하고 기본 데이터 등록과 방문 관계 등록을 내부 두 단계로 관리한다.
- 영향:
  - 작업량
  - 데이터 모델 충돌
  - Visit 담당자와의 협업
- 결정 시점:
  - 구현 계획 작성 전

### RV-ROLE-002 유튜버 기반 탐색의 독립 Workstream 유지

- 현재 상태: 결정 완료 — [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색) 독립 유지
- 관련 Workstream:
  - [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)
  - [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)
- 현재 권장 담당자:
  - 이우람
- 결정 내용:
  - [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)이 공개 유튜버 선택 목록, 관계 판정과 유튜버별 조회를 소유하고 [WS-01](../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)이 최종 조건 조합을 소유한다.
- 영향:
  - Visit 의존성
  - 독립 테스트
  - 탐색 API 계약
- 결정 시점:
  - API 명세 작성 전

### RV-ROLE-003 관리자 인증 정책과 담당 범위

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)
- 현재 권장 담당자:
  - 김인안
- 결정 내용:
  - 사전 발급 계정과 동일 등록 권한을 사용하며 계정 관리 화면은 MVP에서 제외한다.
  - 김인안이 공통 기반과 [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 적용을 책임한다. Spring Security JWT, Redis Refresh Token과 `com.masiton.security.application.AdminPrincipal` 계약을 따른다.
- 영향:
  - 보안 검증
  - [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 일정
  - 공통 코드 소유권
- 결정 시점:
  - 구현 시작 전

### RV-ROLE-004 맛집 상세 조합 위치와 부분 실패 정책

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - [WS-02](../02-analysis/mvp-workstreams.md#6-ws-02-맛집-상세-및-콘텐츠-조회)
- 현재 권장 담당자:
  - 박진영
- 결정 내용:
  - 맛집 기본 정보 제공자 실패는 상세 전체 실패로 처리한다.
  - 관계·유튜버·영상 제공자만 실패하면 기본 정보와 명시적 콘텐츠 조회 실패를 제공한다.
  - 실제 애플리케이션 책임은 `com.masiton.orchestration.application.query`의 전용 Query Service에 둔다.
- 영향:
  - 모듈 의존성
  - 오류 계약
  - 장애 격리와 테스트
- 결정 시점:
  - API 명세 및 아키텍처 설계 전

### RV-ROLE-005 공통 데이터 모델 변경 승인 방식

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - 전체
- 현재 권장 담당자:
  - 박진영이 변경을 조율한다.
- 결정 내용:
  - 핵심 관계, 공유 식별자와 Flyway 마이그레이션 변경은 박진영과 영향받는 Workstream 최종 책임자 1명 이상의 승인을 받아야 한다.
  - 마이그레이션 버전은 병합 순서대로 재배정하고 같은 버전 충돌을 허용하지 않는다.
- 영향:
  - 병렬 개발 충돌
  - 데이터 정합성
  - 배포 순서
- 결정 시점:
  - 데이터 모델링 전

### RV-ROLE-006 프론트엔드 AI 작업의 실제 범위

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - 전체
- 현재 권장 담당자:
  - AI는 김인안·이우람·양성훈, 프론트엔드는 양성훈·김인안이 의사결정을 주도하고 각 Workstream 담당자가 연동 검증
- 결정 내용:
  - 양성훈·김인안이 프론트엔드 공통 구조와 API 계약 의사결정을 주도하고 각 Workstream 담당자가 자기 화면의 직접 구현·연동·인수 테스트를 소유한다.
  - AI 기능은 MVP에서 구현하지 않고 범위 변경 검토가 승인될 때만 AI 담당 역할을 활성화한다.
- 영향:
  - [WS-04](../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록) 부담
  - API 변경 통제
  - 화면 인수 테스트
- 결정 시점:
  - 프론트엔드 구현 전

### RV-ROLE-007 인프라 담당자의 기능 부담

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)
- 현재 권장 담당자:
  - 이우람
- 결정 내용:
  - 이우람이 인프라 공통 작업과 [WS-03](../02-analysis/mvp-workstreams.md#7-ws-03-유튜버-기반-탐색)을 함께 유지한다.
  - 두 작업 중 하나가 통합 일정을 연속 2영업일 이상 차단하면 박진영이 인프라 검증 작업을 우선 지원한다.
- 영향:
  - 배포 준비
  - 관계 기반 조회 일정
  - 통합 위험
- 결정 시점:
  - 첫 통합 전

### RV-ROLE-008 리뷰 담당자 순환 방식

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - 전체
- 현재 권장 담당자:
  - 기본 교차 리뷰 표 적용
- 결정 내용:
  - 기본 교차 리뷰 조합을 2주마다 교체한다.
  - 기본 리뷰어가 없으면 작성자와 같은 Workstream을 소유하지 않은 팀원 한 명이 대체한다.
  - 긴급 변경도 작성자 외 1명 승인을 받고 다음 영업일에 전체 영향 리뷰를 수행한다.
- 영향:
  - 승인 독립성
  - 지식 분산
  - 리뷰 지연
- 결정 시점:
  - 첫 Pull Request 전

### RV-ROLE-009 개인별 기술 선호·경험 반영

- 현재 상태: 결정 완료 (2026-07-27)
- 관련 Workstream:
  - 전체
- 현재 권장 담당자:
  - 현재 배정 유지
- 결정 내용:
  - 현재 Workstream 담당자 배정을 유지한다.
  - 일정 차단이나 지속적인 부담 불균형이 확인될 때만 11장의 소유권 변경 절차로 재배정한다.
- 영향:
  - 학습 비용
  - 개발 속도
  - 장기 소유권
- 결정 시점:
  - 역할 확정 회의

## 11. 소유권 변경 절차

1. 변경 제안자는 대상 Workstream, 요구사항·규칙·NFR ID, 변경 이유와 현재 차단 요소를 기록한다.
2. 현재 책임자와 인수 후보자는 미완료 구현, 테스트, 계약, 데이터 변경과 문서 목록을 확인한다.
3. 다른 Workstream 계약, 핵심 데이터 관계, 공통 정책 또는 MVP 범위에 영향이 있으면 팀 공동 리뷰를 거친다.
4. 팀은 복잡도, 의존성, 공통 책임 부담과 통합 시점을 기준으로 새 최종 책임자 한 명을 확정한다.
5. 인수 시점까지 기존 책임자가 최종 책임을 유지하며, 한 항목에 두 명의 최종 책임자를 동시에 기록하지 않는다.
6. 변경과 함께 [roles.md](roles.md), [ownership.md](ownership.md), [mvp-workstreams.md](../02-analysis/mvp-workstreams.md), 관련 API 명세, 구현 계획과 Task 문서를 갱신한다.
7. 변경 후 리뷰 담당자, 완료 조건, 통합 테스트와 배포 영향을 다시 확인한다.

다음 경우 소유권 변경을 검토한다.

- Workstream 규모가 예상보다 커지거나 High 복잡도 작업이 한 명에게 집중된 경우
- 공통 작업이 기능 구현을 지속적으로 방해하는 경우
- 선행 작업 지연, 담당자 부재 또는 일정 변경으로 장기간 차단되는 경우
- 새로운 MVP 기능 또는 외부 기술이 공식 도입된 경우
- Workstream 또는 도메인 경계가 변경된 경우
