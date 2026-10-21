---
related_documents:
  - ../06-architecture/implementation-conventions.md
  - ../06-architecture/technology-policy.md
  - ../07-adr/platform/lang-001-java-21-runtime.md
  - ../../.github/workflows/ci.yml
---

# PR #443 리뷰 트러블슈팅: PR 이미지 검증 경계 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#443 JDK 21.0.12 이미지 기준선 정합화](https://github.com/team-youngkk/masit-on/pull/443) |
| 작성자 | 이우람 (`@w00lam`) |
| 처리 일자 | 2026-10-21 |
| 범위 | 새 Temurin 백엔드 이미지의 병합 전 실제 빌드·JDK·취약점 검증 요청 1건 |
| 주 문제 유형 | 인프라 |
| 기존 기록 | [PR #356 Docker Hub 두 이미지 게시 사전 검증](pr-356-dockerhub-push-atomicity-review.md)의 게시 전 이미지 검증 원칙과 [PR #257 전환 후 런타임 기준선 리뷰](pr-257-runtime-baseline-review.md)의 런타임 기준선 검증 경계를 확인했다. 기존 운영 `images` job은 `main` push에서만 실행되므로 PR 검증에 그대로 재사용하지 않았다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [새 베이스 이미지의 실제 빌드·JDK 버전 검증](https://github.com/team-youngkk/masit-on/pull/443#discussion_r4332630277) | 운영 `images` job이 PR에서 skipped되어 새 Dockerfile의 실제 build/runtime, `java -version`, Trivy 결과를 병합 전에 확인할 수 없음 | 인프라 | 수정 필요 | PR 전용 `backend-image-contract` job을 추가하고, 첫 실행에서 build·JDK는 통과했지만 Trivy가 `libexpat` HIGH 2건을 발견했다. `expat` parent pin만으로는 runtime `libexpat`가 갱신되지 않아 `libexpat=2.9.0-r0`도 양 stage에 직접 고정한다. | [PR CI run 39539448476](https://github.com/team-youngkk/masit-on/actions/runs/39539448476)과 [후속 run 39540210128](https://github.com/team-youngkk/masit-on/actions/runs/39540210128)에서 build·JDK는 통과했지만 Trivy가 `libexpat 2.8.4-r0`를 계속 발견했다. Alpine [libexpat 패키지](https://pkgs.alpinelinux.org/package/v3.22/main/x86_64/libexpat)는 `2.9.0-r0`를 제공하므로 직접 pin 후 다시 CI를 확인한다. |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. PR CI에서 운영 `이미지 빌드·검증·push` job이 `skipped` 상태였다.
- 발생 환경: GitHub Actions PR 이벤트, PR #443의 Temurin Alpine 3.22 digest 변경.
- 재현 조건: `.github/workflows/ci.yml`의 운영 `images` job은 `push` 이벤트이며 `refs/heads/main`일 때만 실행된다. 로컬 Docker 데몬도 실행되지 않아 PR head에서 이미지 build를 수행할 수 없었다.
- 실제 결과: PR 전용 job에서 새 베이스 이미지 pull, Alpine OpenSSL pin 설치, build/runtime stage, 컨테이너 JDK 21.0.12는 통과했다. 그러나 `expat=2.9.0-r0`를 추가한 후속 실행에서도 Trivy가 실제 스캔 대상인 `libexpat 2.8.4-r0`에서 수정 가능한 HIGH 취약점 2건을 발견해 job이 실패했다.
- 기대 결과: PR 단계에서 운영 자격 증명이나 Docker Hub push 없이 변경된 백엔드 Dockerfile을 실제로 build하고, 이미지 내부 `java -version`이 정확히 `21.0.12`이며 수정 가능한 CRITICAL/HIGH 취약점이 없음을 확인해야 한다.
- 영향 범위: 잘못된 digest·패키지 pin·JDK 패치가 develop에 병합된 뒤 운영 이미지 생성과 배포에서 실패할 수 있다.

## 4. 근본 원인

기존 CI는 운영 이미지 게시와 배포 권한을 보호하기 위해 `images` job을 `main` push로 제한하고 있었다. 이 경계 자체는 유지되어야 하지만, 같은 workflow의 PR 경로에는 Dockerfile을 실제로 검증하는 별도 job이 없었다. 따라서 운영 job의 조건을 완화하지 않는 한 PR에서 새 이미지의 실행 가능성을 확인할 수 없는 검증 공백이 있었다. PR 전용 검증을 추가한 뒤에는 새 Temurin Alpine 3.22.6 레이어에 포함된 `libexpat 2.8.4-r0`가 수정 기준선 `2.9.0-r0`보다 낮아 Trivy가 차단했다. 첫 보완에서 parent package인 `expat=2.9.0-r0`만 고정했지만 runtime subpackage인 `libexpat`에는 적용되지 않아, 실제 스캔 대상 패키지를 직접 고정해야 했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR 리뷰 스레드와 PR head CI run 확인 | backend·frontend·Terraform·secret 검사는 통과했지만 운영 이미지 job은 skipped | 리뷰 요청은 실제 검증 공백으로 분류하고 수정 필요로 판단 |
| 기존 `images` job 조건 확인 | `push`와 `refs/heads/main`을 동시에 요구하고 Docker Hub 자격 증명·production environment를 사용 | 운영 job 조건을 PR까지 넓히지 않고 별도 push 없는 job을 추가 |
| 로컬 Docker 데몬 확인 | Docker daemon이 실행되지 않아 clean build와 컨테이너 `java -version` 실행 불가 | GitHub Actions PR 러너에서 실제 검증하도록 CI 계약을 보완 |
| 기존 `images` job의 JDK·Trivy 명령 확인 | 동일한 JDK 문자열 검사와 Trivy 이미지·severity 기준을 재사용 가능 | PR job은 백엔드 이미지에 같은 기준을 적용하고 secret·push는 제외 |
| PR 전용 job의 첫 GitHub Actions 실행 확인 | Docker build와 JDK 검사는 통과했지만 `libexpat 2.8.4-r0`의 HIGH 2건으로 Trivy 실패 | Alpine 패키지 기준선을 `expat=2.9.0-r0`로 고정하고 다시 CI 실행 |
| `expat` parent pin 후속 실행 확인 | `expat=2.9.0-r0` 설치는 성공했지만 Trivy가 `libexpat 2.8.4-r0`를 계속 보고함 | runtime subpackage인 `libexpat=2.9.0-r0`를 양 stage에 직접 추가하고 다시 CI 실행 |

## 6. 최종 해결

- 변경 내용: `.github/workflows/ci.yml`에 PR 전용 `backend-image-contract` job을 추가했다. `backend` 테스트 성공 후 Dockerfile을 push 없이 build하고, 이미지 내부 `java -version`의 정확한 `21.0.12`와 Trivy의 수정 가능한 `CRITICAL,HIGH` 취약점을 검사한다. Trivy 결과에 따라 `Dockerfile` build/runtime 양쪽에 `expat=2.9.0-r0`와 실제 런타임 스캔 대상인 `libexpat=2.9.0-r0`를 추가하고 기술 정책과 배포 계약 테스트에 반영했다.
- 선택 이유: 운영 `images` job의 main-only·production 보호 경계를 유지하면서, 리뷰가 요구한 병합 전 실제 이미지 검증을 PR에서 수행할 수 있다.
- 변경 파일: `Dockerfile`, `.github/workflows/ci.yml`, `docs/06-architecture/technology-policy.md`, `src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java`, `docs/troubleshooting/README.md`, 이 기록.
- 고려한 대안: 운영 `images` job을 PR에서 직접 실행하는 방법은 Docker Hub push 경로와 production 자격 증명 경계를 넓히므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\\gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest --tests com.masiton.deployment.DeploymentPipelineContractTest --rerun-tasks --no-daemon --console=plain` | 통과 | PR job의 workflow 문자열 계약을 포함한 배포 계약 테스트 20건이 통과했다. 기존 `FlywayMigrationIntegrationTest`의 비-varargs 컴파일 경고 1건은 이번 변경과 무관하다. |
| `git diff --check` | 통과 | CI workflow·테스트·문서 패치의 공백 오류가 없었다. |
| [GitHub Actions PR `backend-image-contract` 첫 실행](https://github.com/team-youngkk/masit-on/actions/runs/39539448476) | 실패 | Docker build와 이미지 내부 JDK 21.0.12는 통과했지만 Trivy에서 `libexpat` HIGH 2건을 발견했다. |
| [GitHub Actions PR `backend-image-contract` `expat` 보완 실행](https://github.com/team-youngkk/masit-on/actions/runs/39540210128) | 실패 | `expat=2.9.0-r0` 설치는 통과했지만 runtime `libexpat`는 `2.8.4-r0`로 남아 Trivy HIGH 2건이 재현됐다. `libexpat` 직접 pin 후 새 head에서 재검증한다. |

로컬 Docker 데몬이 실행되지 않아 이 기록 작성 시점에는 실제 이미지 build를 완료했다고 보고하지 않는다.

## 8. 재발 방지 및 다음 확인

- 재발 방지: PR 이벤트에서만 실행되는 `backend-image-contract` job과 그 조건·push 금지·JDK·Trivy 기준, Alpine `expat`·`libexpat` 보안 pin을 `RuntimeDeploymentContractTest`로 고정했다.
- 다음 확인: PR #443의 새 head에서 `backend-image-contract`가 실제로 성공하는지 확인한다. 담당자는 `@w00lam`, 확인 시점은 새 CI 실행 완료 후다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| PR 베이스 이미지 실제 검증 | PR #443 최초 head에서 이미지 job skipped | PR 이벤트별 `backend-image-contract` 실행 여부와 성공 결과 | PR 전용 job은 실행되며 build·JDK는 통과하지만 `libexpat` pin 보완 후 최종 성공 여부 확인 중 | 검증 공백은 해소됐고 실제 Trivy 대상 패키지 pin을 추가 보완 | @w00lam, PR #443 새 head CI 완료 시점 |

## 10. 남은 사항

- `libexpat` pin 커밋을 push한 뒤 PR CI에서 Docker build·JDK·Trivy 결과를 확인하고 리뷰 스레드에 답글을 단다.
- 해당 CI가 성공하기 전에는 리뷰 스레드를 해결 처리하지 않는다.
