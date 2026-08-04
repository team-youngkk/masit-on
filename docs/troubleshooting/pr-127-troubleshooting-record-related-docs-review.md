---
related_documents:
  - README.md
  - pr-125-develop-to-main-sync-policy-review.md
---

# PR #127 리뷰 트러블슈팅: 트러블슈팅 기록 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#127 PR #125 develop→main 승격 리뷰 트러블슈팅 기록 추가](https://github.com/team-youngkk/masit-on/pull/127) |
| 작성자 | tjdgns0618 |
| 처리 일자 | 2026-08-04 |
| 범위 | 변경 파일 인라인 리뷰 스레드 5건(3.1~3.5) |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [related_documents에 CLAUDE.md 누락](https://github.com/team-youngkk/masit-on/pull/127#discussion_r3708711279) | `pr-125-develop-to-main-sync-policy-review.md` 3·6절이 판단·검증 근거로 `CLAUDE.md` 9절을 실제 인용하는데, 상단 `related_documents`에는 빠져 있어 추가해 달라는 요청 | 수정 필요 | `related_documents`에 `../../CLAUDE.md` 추가 | 문서 3·6절에서 `CLAUDE.md` 인용 여부 재확인, 상대 경로 해석 명령으로 대상 파일 존재 확인 |
| [PR 본문이 실제 diff 범위를 축소 서술](https://github.com/team-youngkk/masit-on/pull/127#discussion_r3708765758) | 위 수정으로 파일이 3개(README.md, pr-125 문서, 신규 pr-127 문서)로 늘었는데 PR 본문의 변경 목적·범위·테스트 결과는 여전히 최초 커밋(파일 2개) 기준으로만 서술돼 있어, 완료 점검의 "변경 범위가 실제 diff와 일치" 조건을 리뷰어가 확인할 수 없다는 지적 | 수정 필요 | PR 본문의 변경 목적·관련 문서·테스트 결과 절을 현재 diff(파일 3개, 트러블슈팅 기록 2건)에 맞춰 갱신 | `git diff --stat origin/develop origin/docs/pr-125-review-troubleshooting`로 실제 변경 파일 3개 확인, 갱신한 본문과 diff 재대조 |
| [검증 명령이 주장을 증명하지 못함](https://github.com/team-youngkk/masit-on/pull/127#discussion_r3708765764) | 6절 표의 명령 `test -f CLAUDE.md`는 저장소 루트 기준 존재만 확인할 뿐, frontmatter에 적은 상대 경로 `../../CLAUDE.md`가 문서 위치(`docs/troubleshooting/`)에서 실제로 올바르게 해석되는지는 검증하지 못한다는 지적 | 수정 필요 | 검증 명령을 `test -f docs/troubleshooting/../../CLAUDE.md`로 교체해 frontmatter 경로를 문서 위치 기준 그대로 해석하도록 수정 | 두 명령을 각각 실행해 전자는 상대 경로 오타가 있어도 우연히 통과할 수 있고 후자만 실제 문서 위치 기준 해석을 검증함을 확인 |
| [리뷰 범위 표현이 실제 스레드 위치와 다름](https://github.com/team-youngkk/masit-on/pull/127#discussion_r3708802117) | 1절 개요의 "범위"를 "PR 본문 인라인 리뷰 스레드 N건"으로 적었지만, 세 스레드 모두 PR 설명이 아니라 변경 파일의 diff 라인에 달린 리뷰이므로 실제 GitHub 위치 표현과 다르다는 지적 | 수정 필요 | "범위"를 "변경 파일 인라인 리뷰 스레드 4건"으로 수정 | GitHub GraphQL로 각 스레드의 `path` 필드가 PR 본문이 아니라 실제 변경 파일 경로임을 재확인 |
| [리뷰 처리 건수가 문서 전체에서 불일치](https://github.com/team-youngkk/masit-on/pull/127#discussion_r3708820947) | 위 수정으로 1절 범위와 2절 표는 4건으로 갱신됐지만, 6절 검증 행과 3.4 실제 결과는 여전히 "세 스레드"로 남아 있고 PR 본문의 자체 지적 건수도 3건으로 서술돼 있어 서로 불일치한다는 지적 | 수정 필요 | 6절 검증 행과 3.4의 "세 스레드"를 "네 스레드"로 맞추고, 이 스레드(5번째)를 포함해 1절 범위·2절 표·PR 본문을 5건 기준으로 다시 정합화 | GraphQL로 다섯 스레드 각각의 `path`를 재조회해 문서 전체 건수·서술과 대조 |

## 3. 문제 현상

### 3.1 related_documents 누락

- 재현 조건: `docs/troubleshooting/pr-125-develop-to-main-sync-policy-review.md`의 상단 frontmatter `related_documents`와 본문 3·6절을 대조한다.
- 실제 결과: 3절("문제 현상")과 6절("검증")은 판단 근거로 `.github/PULL_REQUEST_TEMPLATE.md:77`뿐 아니라 `CLAUDE.md` 9절도 함께 인용했지만, `related_documents`에는 `PULL_REQUEST_TEMPLATE.md`만 있고 `CLAUDE.md`는 빠져 있었다.
- 기대 결과: 본문에서 실제로 인용한 근거 문서는 `related_documents`에서 모두 역추적 가능해야 한다.
- 영향 범위: 문서 역추적성. 판단 내용 자체(정정 방향)에는 변화가 없다.

### 3.2 PR 본문이 실제 diff 범위를 축소 서술

- 재현 조건: 첫 리뷰 반영(related_documents 추가) 커밋을 올린 뒤 PR 본문과 실제 diff를 대조한다.
- 실제 결과: PR 본문은 여전히 "PR #125 리뷰 트러블슈팅 기록 추가"만 변경 목적·범위로 서술했지만, 실제 diff는 이후 `README.md` 목록 갱신과 새 `pr-127-troubleshooting-record-related-docs-review.md` 추가까지 포함해 파일 3개·트러블슈팅 기록 2건으로 늘어나 있었다.
- 기대 결과: PR 본문의 변경 목적·범위·테스트 결과가 병합 시점의 실제 diff와 일치해야 한다.
- 영향 범위: PR 본문 정확성. 코드·문서 내용 자체에는 영향이 없다.

### 3.3 검증 명령이 주장을 증명하지 못함

- 재현 조건: 이 문서(당시 버전) 6절의 `test -f CLAUDE.md` 명령과 "확인한 내용" 칸의 주장("`docs/troubleshooting/`에서 `../../CLAUDE.md`가 저장소 루트의 `CLAUDE.md`를 정확히 가리킴")을 대조한다.
- 실제 결과: 실행한 명령은 저장소 루트를 작업 디렉터리로 `CLAUDE.md`가 존재하는지만 확인했다. `related_documents`에 적은 문자열 `../../CLAUDE.md`를 문서 위치(`docs/troubleshooting/`) 기준으로 실제 해석했는지는 이 명령으로 검증되지 않는다. 예를 들어 상대 경로를 `../CLAUDE.md`로 잘못 적어도 이 명령은 여전히 통과했을 것이다.
- 기대 결과: 검증 명령은 문서에 실제로 적은 상대 경로 문자열을 문서 위치 기준으로 그대로 해석해 대상 파일을 찾아야 한다.
- 영향 범위: 이 트러블슈팅 문서의 검증 절 정확성. `related_documents`의 실제 값 자체는 이미 올바르다.

### 3.4 리뷰 범위 표현이 실제 스레드 위치와 다름

- 재현 조건: 이 문서 1절 개요의 "범위" 서술과 GraphQL로 조회한 각 스레드의 `path` 필드를 대조한다.
- 실제 결과: "범위"는 "PR 본문 인라인 리뷰 스레드 N건"이라고 적었지만, 이 지적을 포함한 네 스레드의 `path`는 각각 `pr-125-develop-to-main-sync-policy-review.md`, `docs/troubleshooting/README.md`, `pr-127-troubleshooting-record-related-docs-review.md`(검증 명령 스레드), `pr-127-troubleshooting-record-related-docs-review.md`(이 스레드 자신)였다. 즉 모두 PR 설명(본문)이 아니라 변경된 파일의 diff 라인에 달린 리뷰였다.
- 기대 결과: 기록의 범위 서술이 리뷰가 실제로 달린 GitHub 위치(PR 본문 vs. 변경 파일 diff 라인)와 일치해야 한다.
- 영향 범위: 이 트러블슈팅 문서의 서술 정확성. 처리한 스레드의 판단·해결 내용 자체에는 변화가 없다.

### 3.5 리뷰 처리 건수가 문서 전체에서 불일치

- 재현 조건: 3.4 반영 커밋 이후 1절 개요·2절 표·6절 검증 행·3.4 실제 결과·PR #127 본문에 등장하는 스레드 처리 건수를 모두 grep해 대조한다.
- 실제 결과: 3.4를 반영하면서 1절 범위와 2절 표는 "4건"으로 갱신했지만, 6절 검증 행("GraphQL로 스레드 3건의 `path` 필드 재조회")과 3.4 자체의 "세 스레드" 서술은 갱신하지 않아 3과 4가 같은 문서 안에 동시에 남아 있었다. PR #127 본문의 "변경 목적"도 이 PR 자체의 지적을 "3건"으로 서술해 실제로는 4건(이후 5건)이 된 상태와 어긋났다.
- 기대 결과: 같은 문서·PR 안에서 같은 대상(이 PR 자체에서 처리한 리뷰 스레드 수)을 가리키는 건수는 모든 위치에서 일치해야 한다.
- 영향 범위: 문서·PR 본문 서술 정확성. 각 스레드의 판단·해결 내용 자체에는 변화가 없다.

## 4. 근본 원인

3.1은 `pr-125-develop-to-main-sync-policy-review.md` 작성 시 근거 문서를 본문에 인용하면서 `related_documents` 갱신을 함께 하지 않은 것이 원인이다.

3.2는 리뷰 반영 커밋을 추가할 때마다 PR 본문을 함께 갱신하는 절차가 없어, 최초 작성 시점의 본문이 그대로 남아 있었던 것이 원인이다. 이 PR은 코드 변경이 아니라 트러블슈팅 기록 자체이므로, 기록을 추가하는 과정에서 나온 후속 지적을 같은 기록에 반영하다 보니 diff가 최초 계획보다 커졌다.

3.3은 검증 명령을 작성할 때 "무엇이 참인지"(저장소에 `CLAUDE.md`가 존재한다)와 "무엇을 주장하는지"(frontmatter에 적은 상대 경로 문자열이 올바르다)를 구분하지 않고, 더 간단한 전자의 명령으로 후자를 검증했다고 기록한 것이 원인이다.

3.4는 다른 트러블슈팅 기록(예: `pr-125-*.md`)의 "범위" 표현을 그대로 옮겨 쓰면서, 그 표현이 실제로는 이번 세 스레드의 위치(변경 파일 diff 라인)와 다르다는 점을 확인하지 않은 것이 원인이다.

3.5는 3.4를 반영할 때 건수가 등장하는 한 곳(1절)만 고치고, 같은 사실을 서술하는 다른 위치(6절 검증 행, 3.4 본문, PR 본문)를 함께 grep해 대조하지 않은 것이 원인이다. 이 기록이 자기 자신의 리뷰를 다루는 자기참조적 문서라서, 새 스레드가 반영될 때마다 건수를 언급하는 모든 위치가 한 번씩 더 밀리며 누적됐다.

## 5. 해결

- 변경 내용:
  - `docs/troubleshooting/pr-125-develop-to-main-sync-policy-review.md`의 `related_documents`에 `../../CLAUDE.md` 추가(3.1).
  - PR #127 본문의 "변경 목적", "관련 문서", "테스트 결과" 절을 현재 diff(파일 3개: `README.md`, `pr-125-develop-to-main-sync-policy-review.md`, 신규 `pr-127-troubleshooting-record-related-docs-review.md`)에 맞춰 갱신(3.2).
  - 이 문서 6절의 검증 명령을 `test -f CLAUDE.md`에서 `test -f docs/troubleshooting/../../CLAUDE.md`로 교체(3.3).
  - 이 문서 1절 개요의 "범위"를 "변경 파일 인라인 리뷰 스레드 4건"으로 수정(3.4).
  - 6절 검증 행의 "스레드 3건"·"세 스레드"와 3.4 본문의 "세 스레드"를 "네 스레드"로 맞추고, 이 스레드를 반영해 1절 범위·2절 표를 "5건(3.1~3.5)" 기준으로 다시 갱신(3.5).
  - PR #127 본문의 "변경 목적"에서 이 PR 자체 지적의 정확한 건수를 매번 옮겨 적지 않고, 상세 건수·처리 내역은 트러블슈팅 기록을 참고하도록 서술을 일반화(3.5, 재발 방지).
- 선택 이유: 다섯 건 모두 판단 내용(정정 방향) 자체는 이미 옳았고, 근거·서술·검증의 정밀도만 실제 상태와 맞추면 되는 최소 수정이다. PR 본문의 건수 서술은 문서 갱신 때마다 반복해서 놓치는 지점이라, 숫자를 아예 옮겨 적지 않는 방식으로 근본 원인을 없앴다.
- 변경 파일: `docs/troubleshooting/pr-125-develop-to-main-sync-policy-review.md`, `docs/troubleshooting/pr-127-troubleshooting-record-related-docs-review.md`, PR #127 본문(GitHub GraphQL로 직접 정정, 별도 커밋 없음)
- 고려한 대안: 없음 — 리뷰가 제시한 방향을 그대로 반영했다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `test -f docs/troubleshooting/../../CLAUDE.md` | 통과 | frontmatter에 적은 `../../CLAUDE.md`를 문서 위치(`docs/troubleshooting/`) 기준으로 그대로 해석해도 저장소 루트의 `CLAUDE.md`를 정확히 가리킴 |
| 본문 3·6절 재대조 | 통과 | 두 절 모두 `CLAUDE.md` 9절을 인용하고 있어 추가가 타당함을 확인 |
| `git diff --stat origin/develop origin/docs/pr-125-review-troubleshooting` | 통과 | 실제 변경 파일이 `README.md`, `pr-125-*.md`, `pr-127-*.md` 3개임을 확인하고 PR 본문을 이에 맞춰 갱신 |
| GraphQL로 스레드 4건의 `path` 필드 재조회 | 통과 | 네 스레드 모두 PR 본문이 아니라 변경 파일 diff 라인에 달렸음을 확인 |
| `grep -n "건\\|스레드" docs/troubleshooting/pr-127-troubleshooting-record-related-docs-review.md` | 통과 | 이 스레드 반영 후 문서 전체에서 건수 서술이 1절·2절·3.5·6절 기준 5건으로 일치함을 확인 |

## 7. 재발 방지

- 트러블슈팅 기록 작성 시 본문에서 인용한 근거 문서 목록과 `related_documents`를 마지막에 한 번 더 대조한다.
- 같은 PR에 리뷰 반영 커밋을 추가로 쌓을 때는 커밋 직후 `git diff --stat <base>...<head>`로 실제 변경 파일을 확인하고 PR 본문을 함께 갱신한다.
- 검증 절을 쓸 때는 명령이 "확인한 내용" 칸의 주장을 문자 그대로 증명하는지 다시 읽어본다. 더 간단하지만 다른 것을 확인하는 명령으로 대체하지 않는다.
- 자기참조적 트러블슈팅 기록(이 문서처럼 자기 자신의 리뷰를 다루는 기록)에서 처리 건수를 고칠 때는 `grep -n "건\|스레드" <파일>`로 문서 전체의 건수 언급을 한 번에 나열해 빠진 위치가 없는지 확인한 뒤 커밋한다. 건수를 한 곳만 고치고 나머지를 다음 리뷰 라운드로 미루지 않는다.
- PR 본문처럼 반복 갱신되는 곳에는 정확한 건수를 매번 옮겨 적지 않고, 트러블슈팅 기록으로 링크해 건수의 단일 진실 원천을 하나로 유지한다.

## 8. 남은 사항

- 없음.
