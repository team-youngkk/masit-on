---
related_documents:
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../07-adr/platform/runtime-001-docker.md
  - pr-344-dockerhub-ssh-architecture-review.md
---

# PR #356 리뷰 트러블슈팅: Docker Hub 두 이미지 게시 사전 검증

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#356 Docker Hub SSH 배포 파이프라인 하드닝](https://github.com/team-youngkk/masit-on/pull/356) |
| 작성자 | w00lam |
| 처리 일자 | 2026-09-05 |
| 범위 | P1 변경 요청 1건: backend·frontend Docker Hub tag 부분 게시 방지 |
| 주 문제 유형 | 배포 |
| 기존 기록 | [PR #344 Docker Hub 이미지와 운영 EC2 아키텍처 정합성](pr-344-dockerhub-ssh-architecture-review.md)을 확인했다. 해당 기록은 digest·SSH·CPU 아키텍처 경계를 다루지만 두 이미지 tag의 사전 존재 확인 순서는 다루지 않아, 이번 P1을 별도 결함으로 기록한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [P1 두 이미지의 부분 게시](https://github.com/team-youngkk/masit-on/pull/356#pullrequestreview-5115378602) | backend push 뒤 frontend push 또는 digest 확인이 실패하면 backend immutable tag만 남아 같은 tag 재시도가 막힌다. 두 tag의 부재를 모두 확인한 뒤 push를 시작한다. | 배포 | 수정 필요 | publish step을 tag preflight loop와 push loop로 분리하고, tag 존재 확인 오류는 fail-closed로 중단한다. | DeploymentPipelineContractTest의 사전 검증 순서 회귀 테스트 통과, 배포 계약 테스트 전체 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 리뷰에서 지적한 부분 게시 시나리오다.
- 발생 환경: GitHub Actions ubuntu-24.04의 main push 이미지 게시 job, Docker Hub immutable commit-SHA tag
- 재현 조건: backend tag가 아직 없음을 확인한 직후 backend push가 성공하고, frontend의 push 또는 digest 확인이 실패하는 경우
- 실제 결과: Docker Hub에 backend tag만 남고 frontend digest ref가 생성되지 않는다. 같은 commit-SHA로 재시도하면 backend immutable tag 존재 검사에서 중단된다.
- 기대 결과: backend·frontend 두 tag가 모두 없음을 확인하기 전에는 어느 이미지도 push하지 않는다. 하나라도 이미 존재하거나 존재 여부 확인이 실패하면 push를 시작하지 않는다.
- 영향 범위: 이미지 게시 결과의 불완전성, 같은 커밋 재시도, 이후 SSH 배포 입력의 digest ref 생성과 운영 배포

## 4. 근본 원인

기존 publish step은 backend와 frontend를 한 loop에서 순차 처리했다. 각 반복에서 해당 tag만 확인한 뒤 즉시 tag·push·digest 조회를 수행했기 때문에, frontend의 사전 확인이 backend push 이후로 밀렸다. Docker Hub tag를 immutable 정책으로 운영하므로 backend tag를 자동 삭제해 복구하는 대안도 허용되지 않는다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 기존 PR #344 트러블슈팅 기록 확인 | digest canonical ref·SSH 전달·아키텍처 정합성은 기록돼 있었지만 두 이미지 게시 순서 기록은 없었다. | 이번 P1은 별도 배포 결함으로 기록한다. |
| 현재 CI publish step의 loop 순서 확인 | 각 이미지의 tag 확인과 push가 같은 loop에 있어 backend push 전에 frontend tag를 확인하지 않았다. | 두 단계 loop로 분리한다. |
| non-existent tag 조회 결과를 manifest unknown과 그 밖의 오류로 구분 | manifest unknown만 tag 부재로 인정하고 인증·네트워크 등 다른 오류는 게시 중단 대상으로 정했다. | 존재 여부를 확인하지 못한 상태에서 push하지 않도록 fail-closed를 적용한다. |
| 배포 계약 테스트 추가 | preflight 조회가 첫 push보다 앞서는 계약을 고정했다. | 코드 변경과 함께 회귀 테스트를 커밋한다. |

## 6. 최종 해결

- 변경 내용: 두 이미지 tag를 먼저 조회하는 preflight loop를 추가했다. 조회 성공은 기존 immutable tag로 판단해 중단하고, 실패 응답은 manifest unknown일 때만 부재로 인정한다. 두 tag 검증이 모두 끝난 뒤 별도의 loop에서 tag·push·digest 조회를 수행한다.
- 선택 이유: 두 이미지가 모두 게시 가능한 상태인지 먼저 확인해 검증 순서 때문에 발생하는 부분 게시 가능성을 제거한다. immutable tag를 삭제하는 복구 방식은 이력 보존과 권한 경계를 훼손하므로 선택하지 않았다.
- 변경 파일: .github/workflows/ci.yml, src/test/java/com/masiton/deployment/DeploymentPipelineContractTest.java
- 고려한 대안: backend tag를 먼저 push한 뒤 frontend 실패 시 Docker Hub tag를 삭제하는 방법은 immutable 정책과 최소 권한 원칙에 맞지 않아 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| .\gradlew.bat test --tests 'com.masiton.deployment.*' --no-daemon --rerun-tasks --console=plain | 통과 | 배포 계약 테스트를 포함한 deployment 패키지 테스트가 BUILD SUCCESSFUL로 완료됐다. 신규 두 이미지 preflight 순서 테스트도 통과했다. |
| ruby YAML parse for ci.yml and performance.yml | 통과 | GitHub Actions YAML 두 파일을 파싱했다. |
| Git Bash bash -n app-deploy.sh dockerhub-app-deploy.sh nginx-install.sh cloudwatch-install.sh | 통과 | 변경과 연관된 운영 셸 문법을 확인했다. |
| git diff --check | 통과 | 변경 diff에 whitespace 오류가 없다. |
| Gradle Wrapper sandbox 실행 | 실패 후 재시도 통과 | 초기 실행은 샌드박스 네트워크 차단으로 Gradle 8.14.3 다운로드에 실패했다. 외부 네트워크 권한으로 같은 명령을 재실행해 통과했다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: DeploymentPipelineContractTest가 모든 tag preflight 조회가 첫 docker push보다 앞서는 순서를 고정한다. manifest unknown 외 조회 오류는 게시 중단으로 처리한다.
- 다음 확인: 리뷰 승인 후 첫 main push에서 GitHub Actions 이미지 게시 결과와 backend·frontend tag 동시 존재를 확인한다. 담당자는 이우람이며, 실제 Docker Hub·EC2 smoke와 rollback은 운영 배포 승인 후 수행한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 이미지 부분 게시 후 동일 tag 재시도 차단 건수 | 미측정 | 각 main push run의 두 tag 존재 상태와 publish job 결과를 기록 | 미측정 | 실제 운영 게시 후 같은 기준으로 비교 | 이우람, 첫 production push 이후 |

## 10. 남은 사항

- 최초 리뷰 요청은 수정했으며, 최신 커밋 push 후 같은 리뷰의 재검토가 필요하다.
- 실제 Docker Hub 게시·EC2 SSH·운영 rollback은 이 환경에서 실행하지 않았다.
