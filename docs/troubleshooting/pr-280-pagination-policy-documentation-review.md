---
related_documents:
  - ../01-requirements/requirements-review.md
  - ../05-specs/api/common/pagination-contract.md
  - ../05-specs/api/discovery/restaurant-discovery-api.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../06-architecture/query-composition.md
  - ../07-adr/platform/web-002-data-state.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #280 리뷰 트러블슈팅: 페이지네이션 예외 정책 문서 정합성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#280 맛집 탐색 페이지 기본 크기를 21개로 조정](https://github.com/team-youngkk/masit-on/pull/280) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-21 |
| 범위 | 페이지네이션 예외 정책과 요구사항·아키텍처·웹 ADR 기준 문서 동기화 |
| 주 문제 유형 | 기타 — 문서 계약 정합성 |
| 기존 기록 | [PR #174 코스 공개 화면·검색 상태 리뷰 반영](pr-174-course-public-screen-review.md)을 확인했다. 코스 화면의 기존 `size=20` 호출 호환 범위와 이번 WS-01 맛집 탐색 endpoint 예외를 분리해 기록했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [inan0226 인라인 의견](https://github.com/team-youngkk/masit-on/pull/280#discussion_r3830251395) | 요구사항 검토·조회 조합·웹 ADR에 기존 기본 20/허용 10·20·50 정책이 남아 계약이 충돌함 | 기타 — 문서 계약 정합성 | 수정 필요 | 세 기준 문서에 일반 목록 정책과 WS-01 맛집 탐색·자연어 검색의 21 예외를 명시했다. | 관련 문서 대조 및 `git diff --check` |
| [tjdgns0618 인라인 의견](https://github.com/team-youngkk/masit-on/pull/280#discussion_r3830291600) | 일반 목록의 기본 20은 유지하고 맛집 탐색 GET·자연어 검색 POST에만 21 예외를 명시해야 함 | 기타 — 문서 계약 정합성 | 수정 필요 | 동일 원인에 대한 중복 스레드로 함께 반영했다. | 일반 목록 20, 맛집 탐색 21 정책을 문서별로 대조 |

두 인라인 의견은 같은 누락을 지적하므로 하나의 변경과 검증으로 처리했다.

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 문서 간 페이지네이션 정책 불일치
- 발생 환경: PR #280의 `codex/fix-restaurant-page-size-21` 브랜치에서 구현·API 계약은 맛집 탐색 기본 21, 허용 10·20·21·50으로 변경된 상태
- 재현 조건: `requirements-review.md`, `query-composition.md`, `web-002-data-state.md`에서 페이지 크기 규칙을 검색·대조
- 실제 결과: 세 기준 문서가 맛집 목록을 기본 20, 허용 10·20·50으로만 설명해 endpoint 계약과 충돌
- 기대 결과: 일반 목록의 공통 정책은 기본 20, 허용 10·20·50으로 유지하고, WS-01 `GET /api/restaurants`와 `POST /api/restaurants/natural-language-search`만 기본 21, 허용 10·20·21·50으로 일관되게 설명
- 영향 범위: 요구사항 추적, 조회 설계, 프론트 상태 관리 기준 문서의 해석과 후속 구현 검토

## 4. 근본 원인

기존 공통 페이지네이션 정책을 기준 문서에 먼저 기록한 뒤, 3열 맛집 카드 UI를 위한 endpoint 예외를 API·구현·일부 요구사항 문서에 추가하면서 요구사항 검토 결과, 조회 조합 설계, 웹 상태 ADR까지 변경 범위를 전파하지 않았다. 구현 결함이 아니라 문서 계약 전파 누락이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #280의 미해결 인라인 스레드와 리뷰 제출 내용을 확인 | 동일 P2 지적 2건과 같은 원인의 변경 요청 2건 확인 | 중복 스레드를 하나의 문서 정합성 수정으로 묶음 |
| `rg`로 세 기준 문서의 페이지 크기 규칙 대조 | 기존 기본 20·허용 10/20/50 표현 확인 | 일반 목록 정책은 유지하고 WS-01 예외 문구를 추가 |
| API 계약 및 공통 페이지네이션 계약 대조 | 맛집 탐색 endpoint는 기본 21·허용 10/20/21/50이며 `size=20` 호환 | 기준 문서에도 같은 범위와 호환 조건을 명시 |
| 기존 트러블슈팅 기록 `pr-174` 확인 | 코스 화면의 기존 `size=20` 호출을 보존한 기록 확인 | 코스 호출과 맛집 탐색 endpoint 예외를 혼동하지 않도록 분리 기록 |

## 6. 최종 해결

- 변경 내용: 일반 목록의 기본 20 정책을 유지하면서 WS-01 맛집 탐색 GET과 자연어 검색 POST의 기본 21·허용 10/20/21/50·기존 `size=20` 호환을 세 기준 문서에 추가했다.
- 선택 이유: 구현·API·공통 페이지네이션 계약에 이미 적용된 endpoint 범위를 요구사항·아키텍처·ADR에 동일하게 전파해 문서 계약의 단일 해석을 보장하기 위해서다.
- 변경 파일:
  - `docs/01-requirements/requirements-review.md`
  - `docs/06-architecture/query-composition.md`
  - `docs/07-adr/platform/web-002-data-state.md`
  - `docs/troubleshooting/README.md`
  - `docs/troubleshooting/pr-280-pagination-policy-documentation-review.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `rg` 기준 문서 대조 | 통과 | 세 문서에 일반 목록 20 정책과 WS-01 endpoint 21 예외가 함께 기술됨 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| 기존 프론트·백엔드 테스트 | 통과 | 문서만 추가 수정했으며 PR의 기존 검증 결과(`npm.cmd test` 288개, typecheck, 대상 Gradle 테스트 BUILD SUCCESSFUL)를 유지 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 페이지네이션 계약 변경 시 공통 계약, 요구사항 검토, 조회 조합, 관련 ADR을 함께 검색·대조하는 체크 항목을 이번 기록에 남겼다.
- 다음 확인: 없음. PR 재검토에서 동일 기준 문서의 정책 정합성을 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 문서 계약 정합성 | 3개 기준 문서에 endpoint 예외 누락 | 페이지 크기 정책 문자열과 API 계약 수동 대조 | 확인 예정 | 코드·API·기준 문서가 같은 정책을 설명하는지 PR 재검토에서 확인 | WS-01 리뷰어, PR #280 재검토 시점 |

## 10. 남은 사항

- 없음. 두 인라인 스레드 모두 동일 변경으로 처리할 수 있으며, 원격 브랜치 반영 후 인라인 답글과 해결 처리를 진행한다.
