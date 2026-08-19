---
related_documents:
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../08-planning/blue-green-cleanup-runbook.md
  - pr-228-asg-replacement-deployment-review.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #253 리뷰 트러블슈팅: CodeDeploy 단일 경로 전환과 배포 계약 테스트 CI 회귀

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#253 운영 배포를 CodeDeploy 경로로 단일화한다](https://github.com/team-youngkk/masit-on/pull/253) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-19 |
| 범위 | 미해결 인라인 리뷰 1건과 후속 리뷰 1건, CodeDeploy 단일 경로 전환 뒤 백엔드 CI 실패 |
| 주 문제 유형 | 배포 / 애플리케이션 |
| 기존 기록 | [PR #228 ASG replacement 배포 기록](pr-228-asg-replacement-deployment-review.md)의 CodeDeploy 취소·pointer·계약 테스트 재발 방지 항목을 대조했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [RuntimeDeploymentContractTest 갱신](https://github.com/team-youngkk/masit-on/pull/253#discussion_r3811951038) | 제거된 SSM·INSTANCE_ID·deployment_target 기대를 없애고 CodeDeploy 기본값·단일 경로·취소 cleanup을 검증 | 배포 / 애플리케이션 | 수정 필요 | `RuntimeDeploymentContractTest`를 CodeDeploy 단일 경로 계약으로 갱신하고 제거된 문자열의 부재를 검증 | 타깃 계약 테스트 22건, 전체 백엔드 테스트 통과 |
| [AppRunScriptContractTest 후속 요청](https://github.com/team-youngkk/masit-on/pull/253#pullrequestreview-4970925187) | SSM bundle·64KB 제한·Nginx 설치 순서 기대를 CodeDeploy revision 자산·hook 검증으로 교체 | 배포 / 애플리케이션 | 수정 필요 | `AppRunScriptContractTest`가 revision archive·appspec·hook·Nginx 산출물을 검증하고 SSM bundle 계약은 부재를 검증 | 타깃 계약 테스트와 전체 백엔드 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 현상: PR #253의 `백엔드 빌드·테스트`가 실패했고 `RuntimeDeploymentContractTest` 2건과 `AppRunScriptContractTest` 1건이 실패했다.
- 발생 환경: GitHub Actions CI run [32238306808](https://github.com/team-youngkk/masit-on/actions/runs/32238306808), PR head `0a1db02`.
- 재현 조건: `.github/workflows/ci.yml`에서 SSM 단일 EC2 배포 번들을 제거하고 CodeDeploy revision으로 전환한 뒤, 기존 계약 테스트를 갱신하지 않은 상태로 백엔드 테스트를 실행한다.
- 실제 결과: 테스트가 이미 삭제된 `INSTANCE_ID`, `deployment_target`, SSM 명령 단계와 빈 CodeDeploy 입력 기본값을 계속 요구했다.
- 기대 결과: 운영 워크플로의 CodeDeploy 단일 경로, 운영 기본값, push 취소 cleanup, revision 자산을 테스트가 현재 계약 그대로 고정해야 한다.

## 4. 근본 원인

워크플로 구현 변경과 이를 문자열 계약으로 고정한 테스트 변경을 같은 작업에서 동기화하지 않아, 테스트가 제거된 배포 경로를 계속 회귀 기준으로 사용했다. 배포 자산의 전달 방식도 SSM 명령 문자열에서 CodeDeploy tar revision으로 바뀌었지만 `AppRunScriptContractTest`가 이전 bundle 생성·64KB 제한·실행 순서를 검증하고 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단 |
|---|---|---|
| PR 리뷰 스레드·리뷰 본문·변경 패치 조회 | 미해결 요청은 계약 테스트 2종 갱신으로 수렴 | 두 테스트 파일만 최소 수정 |
| 기존 troubleshooting 인덱스와 PR #228 기록 확인 | CodeDeploy pointer·취소 cleanup·계약 테스트 회귀 방지 항목을 재사용 | cleanup 조건과 pointer 보존 검증을 새 테스트에 포함 |
| 수정 전 타깃 테스트 실행 | 22건 중 3건 실패 | 리뷰가 지적한 CI 회귀를 로컬 재현 |
| 수정 후 타깃 테스트 실행 | 22건 모두 통과 | 수정된 계약 검증 완료 |
| 전체 백엔드 테스트 실행 | 성공, 실패 0건; 성능 측정 2건은 기존대로 건너뜀 | 이번 변경의 회귀 없음 확인 |
| workflow YAML 파싱 및 `git diff --check` | 모두 통과 | 문법·공백 오류 없음 |

## 6. 최종 해결

- `RuntimeDeploymentContractTest`는 SSM·INSTANCE_ID·deployment_target·조건부 배포 단계의 부재를 확인하고, CodeDeploy application/group/bucket 운영 기본값과 revision·업로드·취소 cleanup의 무조건 경계를 검증한다.
- `AppRunScriptContractTest`는 CodeDeploy revision의 appspec, hook, `stage/` runtime 자산, `revision.env`를 확인하고 제거된 SSM bundle·명령 JSON·64KB 제한·`lines.append` 계약은 다시 생기지 않도록 부재를 검증한다.
- 선택 이유: 운영 워크플로의 구현 계약을 바꾸지 않고, 이번 PR에서 실제로 바뀐 배포 경계만 테스트에 반영했다.

## 7. 검증

| 검증 | 결과 |
|---|---|
| `./gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest --tests com.masiton.deployment.AppRunScriptContractTest --no-daemon --console=plain` | 통과, 22건 |
| `./gradlew.bat test --no-daemon --console=plain` | 통과, 실패 0건; 성능 측정 2건 건너뜀 |
| Ruby YAML parser로 `.github/workflows/ci.yml` 파싱 | 통과 |
| `git diff --check` | 통과 |
| 실제 AWS CodeDeploy push·취소 리허설 | 미실행; 운영 자원과 CI 권한을 사용하는 별도 운영 검증 필요 |

## 8. 재발 방지 및 다음 확인

- 배포 경로를 바꾸는 PR은 workflow와 같은 커밋에서 해당 경로를 고정한 계약 테스트도 갱신하고, 제거된 단계는 `doesNotContain`으로 부재를 고정한다.
- 현재 계약 테스트는 workflow 소스와 revision 구성 목록을 검증한다. revision tar의 모든 실제 멤버와 권한을 생성 직후 manifest로 검증하는 P3 수준의 보강은 별도 작업으로 남긴다.
- 운영 전환 전에는 PR #228 기록에 따라 실제 AWS에서 `create-deployment` 직후 취소, S3 pointer 조회, `stop-deployment`, terminal 상태 확인을 리허설한다. 이번 PR에서는 실행하지 않았다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 도입 후 값 | 비교 결과 |
|---|---:|---:|---|
| 배포 계약 테스트 실패 수 | 3건 | 0건 | 로컬 타깃·전체 백엔드 테스트에서 실패 0건 |
| 백엔드 전체 테스트 실패 수 | CI 필수 검사 실패 | 0건 | 전체 Gradle 테스트 통과 |

