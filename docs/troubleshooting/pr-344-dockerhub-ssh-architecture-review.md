---
related_documents:
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../07-adr/platform/deploy-002-validation-deployment-before-expansion.md
  - ../07-adr/platform/runtime-001-docker.md
  - ../08-planning/m2-deployment-plan.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #344 리뷰 트러블슈팅: Docker Hub 이미지와 운영 EC2 아키텍처 정합성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#344 Docker Hub SSH 배포로 자동 배포 전환](https://github.com/team-youngkk/masit-on/pull/344) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-09-03 |
| 범위 | Docker Hub 게시·SSH 배포 전환에 따른 이미지와 EC2 CPU 아키텍처 정합성 검토 |
| 주 문제 유형 | 인프라·배포 |
| 기존 기록 | 있음. [PR #257 기록](pr-257-runtime-baseline-review.md), [운영 전환 기록](ops-2026-08-19-alb-cutover-review.md), [PR #308 기록](pr-308-external-endpoint-origin-port-review.md)의 계약·운영 검증 형식을 확인했다. 동일한 x86/ARM 이미지 불일치 기록은 없어 새 기록으로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [P1 x86 이미지와 ARM 운영 인프라 불일치](https://github.com/team-youngkk/masit-on/pull/344#issuecomment-5510976678) | Docker Hub에 게시할 이미지 아키텍처와 Terraform의 앱·seed·Redis 인스턴스 아키텍처를 일치시키고, 관련 계약 테스트를 실제 구현과 맞춘다. | 인프라·배포 | 수정 필요 | PR head의 Docker Hub `images`와 SSH `ssh-deploy` 구현은 유지하고, 앱 `t2.micro`, seed `t2.small`, Redis `t2.nano`, 성능 환경·runner의 x86_64 전제를 정합화했다. runtime IAM에서 더 이상 사용하지 않는 GitHub Actions SSM 배포 정책도 제거하고 계약 테스트를 실제 경로에 맞췄다. | 커밋 `21c0a1af`의 `RuntimeDeploymentContractTest`, `PerformanceEnvironmentContractTest`, `com.masiton.deployment.*` 통과. 해당 커밋을 PR branch에 push했고 GitHub Actions는 재실행 대기 중이다. |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `Docker Hub에 push되는 AMD64 이미지가 현재 ARM EC2에서 실행되지 않아 첫 배포가 실패한다.`
- 발생 환경: PR head `chore/dockerhub-ssh-deploy`, 원격 head `392d95165b6df67e4785c3af5e82a33911692001`, base `develop`.
- 재현 조건: 이미지 빌드를 x86_64 runner로 고정하면서 Terraform 기본 인스턴스 타입이 ARM 계열 `t4g.*`인 상태로 배포한다.
- 실제 결과: 기존 PR은 `RuntimeDeploymentContractTest`의 기대값을 x86 계열로 변경했지만 Terraform 기본값은 ARM 계열로 남아 계약 테스트가 실패했다.
- 기대 결과: 이미지 builder, 운영 앱 EC2, seed ASG, Redis 운영 인스턴스와 성능 환경의 실행 아키텍처가 모두 x86_64/amd64로 해석돼야 한다.
- 영향 범위: Docker Hub 이미지 게시, 첫 운영 배포, 수동 재배포·롤백, 성능 환경의 앱·loadgen bootstrap.

## 4. 근본 원인

PR의 배포 workflow 변경과 Terraform·운영 환경 변경이 서로 다른 경계에서 진행됐다. workflow와 테스트는 x86_64 전환을 전제로 했지만 Terraform 기본값과 일부 성능 환경은 ARM `t4g` 기준을 유지해, 이미지 플랫폼과 EC2 실행 플랫폼이 분리됐다. runtime IAM에도 SSH 배포 전환 후 사용하지 않는 GitHub Actions SSM 정책이 남아 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 원격 PR 메타데이터·변경 파일·check run 확인 | PR 작성자는 `w00lam`, head는 `392d951...`; backend build/test는 실패했고, inline review comment는 없으며 issue comment P1 한 건을 확인했다. | 해당 P1을 유일한 리뷰 요청으로 분류하고 로컬 수정 범위를 정했다. |
| Terraform 기본값과 현재 계약 테스트 대조 | 원격 PR 기준 테스트는 `t2`를 기대하지만 앱·seed·Redis Terraform 기본값은 `t4g`였다. | x86_64 전환으로 통일하고 인스턴스 타입 계약을 유지한다. |
| CI workflow와 배포 산출물 전달 경계 확인 | PR head의 `images`와 `ssh-deploy` job은 Docker Hub digest를 출력·전달하고, `app-deploy.sh`는 `--image-refs` 명시 인자를 받는 구조였다. | 최신 PR 구현을 유지하고, 계약 테스트가 Docker Hub·SSH·digest·단일 EC2 경계를 고정하도록 확인한다. |
| 계약 테스트 실행 | 관련 계약 테스트 전체가 통과했다. | 정적 계약·Java 테스트 수준에서 P1 수정 확인. 실제 Docker Hub·SSH·EC2는 이 환경에서 실행하지 않는다. |

## 6. 최종 해결

- CI의 기존 Docker Hub `images`·SSH `ssh-deploy` job을 유지하고, x86_64 runner에서 게시 후 확인한 backend/frontend digest ref를 SSH 원격 래퍼에 전달하게 했다.
- `dockerhub-app-deploy.sh`가 Docker Hub token을 표준 입력으로 한 번만 인증하고 임시 `DOCKER_CONFIG`를 정리한 뒤 `app-deploy.sh --image-refs`를 호출하도록 추가했다.
- `app-deploy.sh`는 명시적 Docker Hub digest 경로와 보존된 legacy ECR tag 경로를 구분하고, Docker Hub 참조가 허용된 저장소·digest 형식인지 검증한다.
- 후속 장애에서 확인된 `workflow_dispatch`의 Docker `RepoDigests[0]` 레지스트리 접두사 차이는 `@` 뒤 digest만 검증한 뒤 `docker.io/<namespace>/masiton-<component>@sha256:...` canonical ref로 재조합하도록 보완했다.
- 앱 runtime IAM에서는 SSM parameter 조회·KMS 복호화와 보존된 legacy ECR pull 권한만 유지하고, GitHub Actions의 직접 SSM 배포 정책은 제거했다.
- x86_64 운영·성능 Terraform 기본값과 관련 계약 테스트를 유지하고, 운영 README에 필요한 SSH 사전 조건을 반영했다.
- 선택 이유: 이미지 digest를 workflow 산출물 파일과 원격 셸의 표준 입력에 섞지 않고 명시적 인자로 전달하면 참조 누락·부분 전달을 조기에 실패시킬 수 있다. legacy ECR 경로는 seed/보존 자원의 호환성을 위해 별도 경로로 남긴다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `ruby -e 'require "yaml"; ...' .github/workflows/ci.yml .github/workflows/performance.yml` | 통과 | 현재 사용 중인 두 GitHub Actions YAML 문법을 파싱했다. |
| `./gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest --tests com.masiton.deployment.PerformanceEnvironmentContractTest` | 통과 | 10개 운영·성능 계약 테스트가 통과했다. |
| `./gradlew.bat test --tests 'com.masiton.deployment.*'` | 통과 | deployment 패키지 전체 계약 테스트가 통과했다. |
| `git diff --check` | 통과 | 커밋 `21c0a1af`의 변경에 whitespace 오류가 없다. |
| Docker/WSL 실제 셸·컨테이너 실행 | 미실행 | 이 환경에서 Docker API와 WSL 접근 권한이 거부됐다. 실제 Docker Hub 게시·SSH·EC2 smoke는 원격 push 후 CI/운영 환경에서 확인해야 한다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: CI의 운영 게시·배포 job과 `app-deploy.sh --image-refs` 호출 계약을 계약 테스트로 고정한다.
- 재발 방지: raw `RepoDigests[0]`의 repository prefix를 신뢰하지 않고 `@` 뒤 `sha256` digest만 검증한 뒤 canonical Docker Hub ref를 구성하는 workflow dispatch 계약을 함께 테스트한다.
- 재발 방지: 운영·성능 Terraform 인스턴스 타입과 runner/bootstrap 아키텍처를 같은 계약 테스트에서 확인한다.
- 다음 확인: 로컬 변경을 원격 PR head에 push한 뒤 GitHub Actions backend build/test와 Docker Hub SSH workflow를 확인하고, 운영 EC2에서 `uname -m`, `docker image inspect`, 내부 health 및 rollback 결과를 기록한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 이미지·EC2 CPU 아키텍처 불일치 | 1건: amd64 이미지와 ARM `t4g` 기본값 혼재 | Terraform·workflow·계약 테스트 정적 대조, 2026-09-03 | 0건(로컬 형상 기준) | x86_64 builder·Terraform 기본값·계약 테스트가 일치 | 원격 push 후 CI와 운영 smoke에서 재확인 |
| 폐기 배포 job의 형상 잔존 | CI에 ECR·SSM 배포 job이 잔존 | CI YAML 정적 대조 | 0개 | 현재 CI에 폐기된 직접 SSM 배포 경로가 남지 않음 | 원격 push 후 required checks 재확인 |

## 10. 남은 사항

- 커밋 `21c0a1af`를 원격 PR branch에 push했다. push 직후 GitHub Actions의 필수 check는 queued 상태이며, 통과 여부는 아직 확인하지 못했다.
- 팀의 Accepted ADR과 M2 계획 일부는 역사적 ECR·ARM 초기 운영을 기록한다. 이를 현재 Docker Hub·x86_64 경로의 결정 문서로 바꾸는 것은 별도 owner/team 결정이 필요하므로 이번 로컬 수정에서 역사 기록을 덮어쓰지 않았다.
- 실제 Docker Hub 게시, GitHub-hosted runner에서의 SSH 접속, SSH known_hosts, sudo 정책, EC2 아키텍처와 운영 rollback은 이 환경에서 검증하지 못했다.
