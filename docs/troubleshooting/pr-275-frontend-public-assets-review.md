---
related_documents:
  - ../07-adr/platform/runtime-001-docker.md
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../08-planning/m2-deployment-plan.md
  - ../01-requirements/non-functional-requirements.md
  - ../troubleshooting/README.md
---

# PR #275 리뷰 트러블슈팅: 프론트 Dockerfile EOF 빈 줄

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #275](https://github.com/team-youngkk/masit-on/pull/275) |
| 작성자 | @w00lam |
| 처리 일자 | 2026-08-21 |
| 범위 | `frontend/Dockerfile` EOF 빈 줄 리뷰 1건 |
| 주 문제 유형 | 배포 |
| 기존 기록 | [PR #273 화면 동기화 리뷰](pr-273-frontend-ui-sync-review.md)를 확인했다. 해당 기록은 원본 PNG 자산 정리 사건으로, 이번 runtime Dockerfile EOF 문제와는 별개였다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [PR #275](https://github.com/team-youngkk/masit-on/pull/275) · `frontend/Dockerfile:68` | 파일 끝의 불필요한 빈 줄을 제거하고 `git diff --check`를 다시 실행 | 배포 | 수정 필요 | 마지막 빈 줄을 제거하고 단일 개행만 남겼다. | 원격 PR 파일의 마지막 바이트를 확인한 뒤 정규화했고, 로컬 `git diff --check`가 통과했다. |
| [PR #275](https://github.com/team-youngkk/masit-on/pull/275) · `docs/troubleshooting/pr-275-frontend-public-assets-review.md:67` | Docker 이미지 검증 시점을 실제 CI workflow 조건에 맞게 정정 | 배포 | 수정 필요 | PR에서는 이미지 job이 실행되지 않는다는 제한과 `main`·`deploy/m2` 대상 push 및 배포 후 smoke test 시점을 명시했다. | `.github/workflows/ci.yml`의 `images` job 조건과 수정 문서를 대조했고, `git diff --check`가 통과했다. |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `frontend/Dockerfile:68: new blank line at EOF`
- 발생 환경: GitHub PR `fix/frontend-public-assets`, `frontend/Dockerfile` 원격 파일
- 재현 조건: Dockerfile을 GitHub Contents API로 갱신한 뒤 파일 끝에 개행이 두 번 이상 남은 상태에서 `git diff --check` 실행
- 실제 결과: EOF의 빈 줄을 정적 검사에서 발견했다.
- 기대 결과: Dockerfile은 파일 끝에 단일 개행만 포함해야 한다.
- 영향 범위: 실행 동작에는 직접 영향이 없지만, 저장소 정적 검사와 PR 품질 게이트를 통과하지 못한다.

## 4. 근본 원인

원격 PR 브랜치에 파일 내용을 갱신할 때 입력 문자열의 끝 개행을 그대로 전달해, 마지막 개행 외에 빈 줄이 하나 더 저장됐다. Dockerfile의 `public` 복사 동작 자체가 아니라 원격 파일 형상 정규화가 누락된 문제다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 로컬 `frontend/Dockerfile`의 마지막 바이트 확인 | 단일 LF로 끝남 | 로컬 작업 파일 자체는 리뷰 현상과 달랐다. 원격 PR 파일을 별도로 확인했다. |
| PR 브랜치의 `frontend/Dockerfile` 끝부분 확인 | 마지막에 빈 줄이 존재함 | 리뷰 요청이 재현돼 수정 필요로 판단했다. |
| 파일 끝 개행을 제거한 뒤 단일 LF로 정규화 | 원격 수정 커밋 생성 | PR 브랜치에 반영하고 검증 후 스레드에 답글을 단다. |

## 6. 최종 해결

- 변경 내용: `frontend/Dockerfile` 파일 끝의 추가 빈 줄을 제거하고 단일 개행만 유지했다.
- 선택 이유: Dockerfile 실행 동작이나 런타임 구성을 바꾸지 않고 리뷰 요청의 형상 문제만 최소 수정한다.
- 변경 파일: `frontend/Dockerfile`
- 고려한 대안: 없음. 포매터나 전체 파일 재생성은 불필요한 변경 범위를 만들 수 있어 사용하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 로컬 Dockerfile에 EOF 공백 오류가 없다. |
| PR 브랜치 파일 끝 확인 | 통과 | 원격 `frontend/Dockerfile`이 단일 개행으로 끝난다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 원격 파일 갱신 시 입력 내용을 개행 정규화한 뒤 커밋한다.
- 다음 확인: PR에서는 `images` job이 실행되지 않는다. Docker 이미지 클린 빌드와 이미지 내부 `public` 자산 확인은 `main` 또는 `deploy/m2` 대상 push의 이미지 빌드·검증 단계에서 수행하고, 배포 후 smoke test에서 `/images/...` 응답을 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| EOF 정적 검사 | 실패: `new blank line at EOF` | `git diff --check` 및 PR CI | 확인 예정 | 파일 형상 오류가 사라졌는지 확인 | @w00lam · PR #275 CI 완료 시 |
| 이미지 자산 404율 | 미측정 | `main` 또는 `deploy/m2` 대상 push의 이미지 검증과 배포 후 `/images/...` 요청 HTTP 상태를 확인 | 확인 예정 | 배포 전후 비교 | @w00lam · 이미지 배포 후 |

## 10. 남은 사항

- 인라인 리뷰 스레드: 수정 답글 후 해결 처리한다.
- PR에서는 이미지 job이 실행되지 않는다. 로컬 Docker 데몬이 없어 이미지 빌드와 배포 후 smoke test는 `main` 또는 `deploy/m2` 대상 push의 GitHub Actions 이미지 job과 운영 배포 단계에서 확인한다.
