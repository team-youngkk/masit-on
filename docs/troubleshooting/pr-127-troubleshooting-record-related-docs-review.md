---
related_documents:
  - README.md
  - pr-125-develop-to-main-sync-policy-review.md
---

# PR #127 리뷰 트러블슈팅: 트러블슈팅 기록의 related_documents 누락 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#127 PR #125 develop→main 승격 리뷰 트러블슈팅 기록 추가](https://github.com/team-youngkk/masit-on/pull/127) |
| 작성자 | tjdgns0618 |
| 처리 일자 | 2026-08-04 |
| 범위 | PR 본문 인라인 리뷰 스레드 1건 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|
| [related_documents에 CLAUDE.md 누락](https://github.com/team-youngkk/masit-on/pull/127#discussion_r3708711279) | `pr-125-develop-to-main-sync-policy-review.md` 3·6절이 판단·검증 근거로 `CLAUDE.md` 9절을 실제 인용하는데, 상단 `related_documents`에는 빠져 있어 추가해 달라는 요청 | 수정 필요 | `related_documents`에 `../../CLAUDE.md` 추가 | 문서 3·6절에서 `CLAUDE.md` 인용 여부 재확인, `test -f CLAUDE.md`로 상대 경로 대상 파일 존재 확인 |

## 3. 문제 현상

- 재현 조건: `docs/troubleshooting/pr-125-develop-to-main-sync-policy-review.md`의 상단 frontmatter `related_documents`와 본문 3·6절을 대조한다.
- 실제 결과: 3절("문제 현상")과 6절("검증")은 판단 근거로 `.github/PULL_REQUEST_TEMPLATE.md:77`뿐 아니라 `CLAUDE.md` 9절도 함께 인용했지만, `related_documents`에는 `PULL_REQUEST_TEMPLATE.md`만 있고 `CLAUDE.md`는 빠져 있었다.
- 기대 결과: 본문에서 실제로 인용한 근거 문서는 `related_documents`에서 모두 역추적 가능해야 한다.
- 영향 범위: 문서 역추적성. 판단 내용 자체(정정 방향)에는 변화가 없다.

## 4. 근본 원인

`pr-125-develop-to-main-sync-policy-review.md` 작성 시 근거 문서를 본문에 인용하면서 `related_documents` 갱신을 함께 하지 않았다. `.github/PULL_REQUEST_TEMPLATE.md`는 추가했지만, 같은 절에서 병렬로 인용한 `CLAUDE.md`는 누락했다.

## 5. 해결

- 변경 내용: `docs/troubleshooting/pr-125-develop-to-main-sync-policy-review.md`의 `related_documents`에 `../../CLAUDE.md`를 추가했다.
- 선택 이유: 본문 인용과 메타데이터를 일치시키는 최소 수정이다.
- 변경 파일: `docs/troubleshooting/pr-125-develop-to-main-sync-policy-review.md`
- 고려한 대안: 없음 — 리뷰가 제시한 추가 방향을 그대로 반영했다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `test -f CLAUDE.md` | 통과 | `docs/troubleshooting/`에서 `../../CLAUDE.md` 상대 경로가 저장소 루트의 `CLAUDE.md`를 정확히 가리킴 |
| 본문 3·6절 재대조 | 통과 | 두 절 모두 `CLAUDE.md` 9절을 인용하고 있어 추가가 타당함을 확인 |

## 7. 재발 방지

- 트러블슈팅 기록 작성 시 본문에서 인용한 근거 문서 목록과 `related_documents`를 마지막에 한 번 더 대조한다.

## 8. 남은 사항

- 없음.
