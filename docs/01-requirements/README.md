---
related_documents:
  - functional-requirements.md
  - business-rules.md
  - non-functional-requirements.md
  - requirements-review.md
  - ../00-overview/scope.md
  - ../00-overview/glossary.md
  - ../02-analysis/domain-boundaries.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../08-planning/third-expansion-scope-and-terminology.md
---

# 요구사항

## 1. 목적

이 디렉터리는 [프로젝트 범위](../00-overview/scope.md)에서 승인된 MVP와 확장 단계에 대해 **무엇이 동작해야 하고 어떤 기준으로 판정하는지**를 검증 가능한 형태로 정의한다. PRD, API 계약, 데이터 모델, 테스트 시나리오와 구현 Task는 여기의 요구사항 ID를 추적 기준으로 사용한다.

구현 방식, 데이터 구조, API 경로와 기술 선택은 이 디렉터리에서 정하지 않는다. 그건 [명세](../05-specs/)와 [ADR](../07-adr/adr-index.md)의 몫이다.

## 2. 문서 읽기 순서

1. [기능 요구사항](functional-requirements.md): 기능별 정상 결과, 예외와 경계 조건 (`FR-*` ID)
2. [비즈니스 규칙](business-rules.md): 여러 기능에 공통 적용되는 등록·관계·공개·중복 판단 기준
3. [비기능 요구사항](non-functional-requirements.md): 성능·보안·정합성·안정성·관측성·호환성·테스트 품질 기준
4. [요구사항 검토 결과](requirements-review.md): 작성 중 나온 미결정 사항의 합의 기록

`requirements-review.md`는 확정된 결론의 근거와 아직 후속 계약에서 결정할 항목을 기록한다. 구현 계약을 작성할 때는 앞의 세 문서와 함께 미결정 상태를 확인한다.

## 3. 문서별 역할

| 문서 | 답하는 질문 | 다루지 않는 내용 |
|---|---|---|
| `functional-requirements.md` | 이 기능은 어떤 입력에 어떤 결과를 내는가? | 여러 기능에 걸친 공통 판단 기준 |
| `business-rules.md` | 등록·관계·공개·중복을 어떤 기준으로 판정하는가? | 개별 화면·API의 동작 |
| `non-functional-requirements.md` | 얼마나 빠르고 안전하고 견고해야 하는가? | 기능 범위와 업무 규칙 |
| `requirements-review.md` | 이 결정은 왜 이렇게 정해졌는가? | 아직 확정되지 않은 신규 결정 |

## 4. 기능 요구사항 ID

`FR-{도메인}-{번호}` 형식을 사용한다. 구현·테스트·PR은 이 ID로 근거를 밝히며 대응 관계는 추적표를 사용한다.

| 단계 | 도메인 ID | 표준 도메인 명칭 | 범위 |
|---|---|---|---|
| MVP | `RESTAURANT` | 맛집 | 맛집 탐색·등록 |
| MVP·1차 확장 | `CREATOR` | 유튜버 | 유튜버 탐색·등록·상세 |
| MVP | `VIDEO` | 영상 | 영상 등록·조회 |
| MVP | `VISIT` | 방문 관계 | 맛집·유튜버·영상 연결 |
| MVP | `ADMIN` | 관리자 | 관리자 인증·등록 |
| 1차 확장 | `MEMBER` | 회원 | 계정·회원 정보·탈퇴 |
| 1차 확장 | `AUTH` | 회원 인증 | 로그인·Token·세션 |
| 1차 확장 | `FAVORITE` | 찜 | 회원별 맛집 찜 |
| 1차 확장 | `RECENT` | 최근 본 맛집 | 최근 조회 기록 |
| 1차 확장 | `MAP` | 지도 탐색 | 필터 결과를 유지하는 지도 맛집 탐색 |
| 2차 확장 | `COLLECTION` | 개인 컬렉션 | 회원의 비공개 맛집 폴더 |
| 2차 확장 | `POPULAR` | 인기 맛집 | 현재 찜 수 기반 공개 순위 |
| 2차 확장 | `CURATION` | 관리자 큐레이션 | 관리자 편집 공개 콘텐츠 |
| 2차 확장 | `SUBMISSION` | 사용자 제보 | 신규 정보·관계 제안과 검토 |
| 2차 확장 | `REPORT` | 사용자 신고 | 기존 공개 정보 문제 보고와 검토 |
| 2차 확장 | `NOTIFICATION` | 사용자 알림 | 제보·신고 처리 상태 알림함 |
| 3차 확장 | `NLSEARCH` | 자연어 검색 | 자연어 조건 해석과 기존 맛집 목록 조회 |
| 3차 확장 | `AIEXTRACT` | AI 영상 정보 추출 | 관리자용 영상 정보 추출 후보와 검수 |
| 3차 확장 | `COURSE` | 동선 및 코스 추천 | 선택 맛집의 자동차 이동 순서와 경로 |

2차 확장 ID는 [용어집](../00-overview/glossary.md#7-2차-확장-기능-용어)과 [범위 승인](../00-overview/scope.md#52-2차-확장)의 표준 명칭을 사용한다. `COLLECTION`은 개인 컬렉션, `CURATION`은 관리자 큐레이션만 뜻하며 제보와 신고를 하나의 ID로 합치지 않는다.

3차 확장 ID는 [용어집](../00-overview/glossary.md#8-3차-확장-기능-용어)과 [범위 승인](../00-overview/scope.md#53-3차-확장)의 표준 명칭을 사용한다. `NLSEARCH`는 기존 조건 해석, `AIEXTRACT`는 자동 등록·예외 보정이 가능한 영상 추출, `COURSE`는 사용자가 고른 맛집의 이동 순서 제안을 뜻한다.

- [제품 추적표](../04-product/traceability.md) — 요구사항 ↔ PRD
- [API 추적표](../05-specs/api-traceability.md) — 요구사항 ↔ API
- [데이터 추적표](../05-specs/data/data-traceability.md) — 요구사항 ↔ 테이블

## 5. 변경 절차

1. 사용자 동작이나 범위가 바뀌면 [scope.md](../00-overview/scope.md)를 먼저 검토한다.
2. 기능 요구사항과 비즈니스 규칙을 수정한다.
3. 영향받는 PRD, API 계약, 데이터 명세를 같은 PR에서 갱신한다.
4. 추적표를 갱신한다.
5. 소유자 리뷰를 받는다. 요구사항별 소유자는 [ownership.md](../03-team/ownership.md)에 있다.

확정되지 않은 항목은 임의로 해석하지 않고 각 문서의 `검토 필요` 절에 남긴다.
