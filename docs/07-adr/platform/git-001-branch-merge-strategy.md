---
id: ADR-GIT-001
title: 브랜치 병합 방식과 역동기화 정책
status: Accepted
decision_date: 2026-08-03
owners:
  - 이우람
related_requirements: []
related_documents:
  - ../../../CLAUDE.md
  - ../../06-architecture/implementation-conventions.md
  - ci-001-github-actions-quality-gate.md
  - ../adr-backlog.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-GIT-001 브랜치 병합 방식과 역동기화 정책

## 1. 상태

Accepted

## 2. 결정 요약

작업 브랜치 → `develop`은 Squash Merge, `develop` → `main`은 Create a merge commit을 사용한다. 정상적인 `develop → main` 승격 뒤에는 커밋 수 차이만을 이유로 `main → develop` 역동기화 PR을 만들지 않고, `main` 전용 Hotfix가 있을 때만 별도 PR로 역동기화한다.

## 3. 배경

MVP 초기 정책은 반대였다 — 기능·수정 브랜치에서 `develop`으로 병합할 때 일반 Merge, `develop`에서 `main`으로 병합할 때만 Squash Merge를 사용했다. 1차 확장 운영 배포([PR #89](https://github.com/team-youngkk/masit-on/pull/89), `v0.2.0`)를 거치며, `develop → main` 승격 PR을 Squash로 병합할 때마다 `main`의 릴리즈 커밋과 `develop`의 원본 구현 커밋들이 서로 다른 SHA를 가진 별개 커밋이 되는 문제가 반복됐다. 이 때문에 다음 릴리즈 전에 `main`의 내용을 `develop`으로 다시 병합해 두 브랜치의 커밋 이력을 맞추는 역동기화 PR이 매 릴리즈마다 필요했다([PR #98](https://github.com/team-youngkk/masit-on/pull/98) 등).

## 4. 결정 문제

작업 브랜치·`develop`·`main` 세 층위의 병합 방식을 어떻게 조합해야, 릴리즈마다 반복되는 역동기화 PR 없이 `main`이 `develop`의 실제 커밋 계보를 그대로 보존할 수 있는가.

## 5. 고려한 선택지

- 기존 방식 유지: 작업 브랜치 → `develop`은 일반 Merge, `develop` → `main`은 Squash Merge.
- 현재 방식: 작업 브랜치 → `develop`은 Squash Merge, `develop` → `main`은 Create a merge commit.
- 두 층위 모두 Squash Merge.
- 두 층위 모두 Create a merge commit(일반 Merge).

각 대안이 이 프로젝트에 맞지 않는 이유는 다음과 같다.

- 기존 방식(작업 브랜치 일반 Merge / develop→main Squash): `develop → main` Squash가 매번 `main`에 새 SHA의 커밋을 만들어, `develop`에 있는 원본 구현 커밋들과 조상 관계가 끊긴다. 다음 승격 전에 `main`의 Squash 커밋을 `develop`으로 역동기화해야만 두 브랜치가 다시 같은 조상을 공유했다.
- 두 층위 모두 Squash: `develop → main`에서도 Squash를 쓰면 위와 같은 문제가 그대로 남는다.
- 두 층위 모두 Merge Commit: 작업 브랜치의 여러 구현 커밋(오탈자 수정, 리뷰 반영 등)이 `develop` 이력에 그대로 쌓여 `git log`·`git blame`으로 PR 단위 변경을 추적하기 어렵다.

## 6. 결정

작업 브랜치 → `develop`은 Squash Merge, `develop` → `main`은 Create a merge commit을 사용한다.

| PR 방향 | 허용 방식 | 병합 후 역동기화 |
|---|---|---|
| 작업 브랜치 → `develop` | Squash Merge | 없음. 후속 브랜치는 최신 `develop`에서 분기·갱신 |
| `develop` → `main` | Create a merge commit | 없음. `main`의 Merge Commit만 없다는 이유로 역동기화하지 않음 |
| 운영 Hotfix → `main` | Create a merge commit(직접 승격의 팀 승인 예외) | Hotfix 내용을 `develop`에 별도 PR로 반드시 반영 |

## 7. 선택 근거

작업 브랜치의 여러 구현 커밋은 `develop`에서 PR당 하나의 Squash Commit으로 정리해 PR 단위 추적성을 유지한다. `develop` → `main` 승격은 Merge Commit을 써서 `main`의 릴리즈 커밋이 `develop`의 Squash Commit들을 그대로 조상으로 포함하게 한다. 이렇게 하면 `main`이 이미 `develop`의 실제 이력을 포함하므로, 커밋 수 차이만을 이유로 한 역동기화가 구조적으로 불필요해진다.

## 8. 트레이드오프

`develop` → `main` 승격 PR의 head가 정적 커밋이 아니라 살아 있는 `develop` 브랜치이므로, 리뷰 도중 `develop`에 새 PR이 병합되면 승격 PR의 diff가 계속 늘어난다([PR #125](https://github.com/team-youngkk/masit-on/pull/125) 트러블슈팅([pr-125 기록](../../troubleshooting/pr-125-develop-to-main-sync-policy-review.md))에서 실제로 재발해 고정 브랜치(`build/promote-{버전}`)로 전환했다). 이 ADR은 병합 방식 자체(Squash vs. Merge Commit)만 다루며, 승격 PR의 head를 무엇으로 할지는 별도 결정 사항이다.

## 9. 적용 범위

`masit-on` 저장소의 모든 브랜치 병합에 적용한다. `feature/**`, `fix/**`, `docs/**`, `chore/**`, `build/**`, `ci/**`, `test/**`, `refactor/**` 등 작업 브랜치 전체가 대상이며, 예외는 운영 Hotfix의 `main` 직접 승격뿐이다.

## 10. 강제 규칙

GitHub 저장소 ruleset `Protect develop`의 `allowed_merge_methods: [squash]`, `Protect main`의 `allowed_merge_methods: [merge]`가 이 표를 강제한다. 두 ruleset 모두 PR 승인 2명, 미해결 리뷰 스레드 차단, 필수 상태 검사(백엔드 빌드·테스트, 프론트엔드 빌드·타입 검사)를 함께 유지하며 `bypass_actors`가 비어 있어 예외 없이 강제된다.

## 11. 금지 사항

`develop → main` 승격에 Squash Merge나 Rebase Merge를 사용하지 않는다. 정상적인 `develop → main` 병합 뒤 커밋 수 차이만을 이유로 `main → develop` 역동기화 PR을 만들지 않는다.

## 12. 구현 및 운영 영향

2026-08-03 GitHub 저장소 ruleset을 이 표에 맞게 재설정했다(`Protect develop`·`Protect main`의 `allowed_merge_methods`). 기존에 겹쳐 걸려 있던 classic 브랜치 보호는 두 메커니즘이 공존하면 어느 쪽이 무엇을 강제하는지 추적하기 어려워 삭제했다. PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`)과 `CLAUDE.md`·`implementation-conventions.md` 9절/7.2절에 병합 방식과 역동기화 예외를 동기화했다.

## 13. 검증 방법

`Protect develop`·`Protect main` ruleset을 `gh api repos/{owner}/{repo}/rulesets/{id}`로 조회해 `allowed_merge_methods`가 이 표와 일치하는지 확인한다. `develop → main` 승격 PR을 실제로 병합해 `main`의 릴리즈 커밋이 `develop`의 Squash Commit들을 조상으로 포함하는지(`git log --graph`) 확인한다.

## 14. 재검토 조건

배포 토폴로지나 릴리즈 절차가 바뀌어 다른 병합 전략이 필요해지거나, `develop → main` 승격 PR의 head 운영 방식(고정 브랜치 전환 등)이 이 병합 방식 자체와 상충하는 것으로 확인되면 재검토한다.

## 15. 관련 문서

- [CLAUDE.md](../../../CLAUDE.md) 9절
- [구현 컨벤션](../../06-architecture/implementation-conventions.md) 7.2절
- [ADR-CI-001](ci-001-github-actions-quality-gate.md)
- [PR #125 트러블슈팅: develop head 문제와 고정 브랜치 전환](../../troubleshooting/pr-125-develop-to-main-sync-policy-review.md)
- [PR #129 트러블슈팅: 이 ADR 부재를 지적한 리뷰 반영](../../troubleshooting/pr-129-deploy-cutover-and-rate-limit-review.md)
