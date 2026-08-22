---
related_documents:
  - ../../deploy/scripts/app-deploy.sh
  - ../08-planning/m2-deployment-plan.md
  - ../07-adr/platform/runtime-001-docker.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# 이슈 #282 리뷰 반영: dependency health 진단과 롤백 안전성

## 1. 개요

| 항목 | 내용 |
|---|---|
| 이슈 | [#282 배포 dependency health 실패 시 실제 장애 컴포넌트를 출력](https://github.com/team-youngkk/masit-on/issues/282) |
| 처리 일자 | 2026-08-21 |
| 범위 | dependency health의 HTTP·JSON·구성요소 진단과 서비스 활성화 이후 롤백 보장 |
| 주 문제 유형 | 배포 |
| 기존 기록 | [운영 전환: ALB·Blue-Green 트래픽 전환에서 드러난 결함](ops-2026-08-19-alb-cutover-review.md)을 확인했다. 해당 기록은 Redis dependency 장애가 배포 health 단계에서 관찰된 배경을 제공하고, 이번 기록은 진단 코드의 종료·롤백 경계를 다룬다. |

## 2. 리뷰 요청 처리 결과

| 요청 | 문제 유형 | 판단 | 처리 결과 | 근거·검증 |
|---|---|---|---|---|
| dependency health 진단의 실패 경로가 `ERR` trap·rollback을 우회할 수 있음 | 배포 | 수정 필요 | 진단을 `check_dependency_health` 함수로 묶고 호출부를 조건문 밖에 둬 실패를 기존 `ERR` trap으로 전달했다. transport 실패는 원래 curl 상태를 보존하도록 `rollback`의 선택적 원래 상태 인자를 추가했다. | mock curl harness에서 transport 종료 7과 rollback 상태 7, HTTP·JSON·구성요소 종료 1과 rollback 상태 1 확인 |
| transport·HTTP·malformed JSON·복수 DOWN/누락 구성요소와 본문 누출을 검증해야 함 | 배포 | 수정 필요 | `DependencyHealthContractTest`에 bounded shell harness를 추가해 다섯 실패 모드를 검증하고, 민감 본문 sentinel이 출력되지 않음을 확인했다. | 대상 계약 테스트 5건 통과 |

## 3. 문제 현상과 발생 조건

- 발생 환경: 서비스 재기동 후 `deploy/scripts/app-deploy.sh`가 `/internal/health/dependencies`를 검사하는 단계.
- 기존 결과: `if ! curl ...; then exit 1`, HTTP 상태 조건의 `exit 1`, Python 판정 조건의 `exit 1`이 모두 `ERR` trap이 적용되지 않는 문맥 또는 직접 종료 경로에 있었다.
- 영향: 새 이미지·실행 산출물이 활성화된 뒤 dependency health가 실패해도 이전 산출물 복구가 실행되지 않을 수 있었다. 구성요소 진단은 실제 `DOWN`·누락 이름을 표시해야 하며 전체 응답 본문은 로그에 남기면 안 된다.
- 기대 결과: transport·HTTP·JSON·구성요소 실패를 구분하고, 모든 실패가 원래 nonzero 상태로 rollback을 거친 뒤 종료한다.

## 4. 근본 원인

진단 메시지를 출력하기 위해 실패한 명령을 `if`/`!` 조건으로 감싼 뒤 블록 안에서 `exit`했다. Bash의 해당 조건 문맥에서는 `ERR` trap이 실행되지 않으며, `exit` 자체도 trap을 호출하지 않는다. 따라서 메시지 구분과 롤백 안전성을 같은 조건문 안에서 처리하려던 구조가 원인이었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단 |
|---|---|---|
| 기존 `app-deploy.sh`의 `ERR` trap과 dependency health 블록 대조 | 서비스 활성화 뒤 진단 블록에 직접 `exit` 경로가 존재 | 수정 필요 |
| HTTP·JSON·구성요소 판정 로직을 함수로 분리 | 함수 내부에서 진단하고 nonzero를 반환할 수 있음 | 호출부를 조건문 밖에 두어 기존 trap을 재사용 |
| mock curl transport·HTTP·JSON·구성요소·누락 입력 실행 | 다섯 실패 모드에서 진단·종료·rollback 상태를 재현 | bounded harness로 회귀 고정 |

## 6. 최종 해결

- `check_dependency_health`가 curl transport 실패, HTTP 비정상, JSON 파싱 실패, DOWN·누락 구성요소를 각각 진단하고 nonzero를 반환하도록 변경했다.
- 구성요소는 `db`, `mail`, `redis`를 필수로 확인하고 응답의 추가 구성요소 중 UP이 아닌 항목도 이름으로 수집한다.
- 응답 파일은 파싱에만 사용하며 전체 본문은 로그에 출력하지 않는다.
- `rollback`은 trap 호출 시 기존 `$?`를 사용하고, 명시적 호출 시 전달된 원래 상태를 사용할 수 있게 했다.
- 변경 파일:
  - `deploy/scripts/app-deploy.sh`
  - `src/test/java/com/masiton/deployment/DependencyHealthContractTest.java`
  - `src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java`
  - `docs/troubleshooting/README.md`
  - `docs/troubleshooting/pr-282-dependency-health-diagnostics-review.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `bash -n deploy/scripts/app-deploy.sh` | 통과 | 셸 문법 |
| `git diff --check` | 통과 | 패치 공백 |
| `./gradlew test --tests com.masiton.deployment.DependencyHealthContractTest --no-daemon --console=plain` | 통과 | mock curl 다섯 실패 모드, 구성요소 이름, rollback 상태, 본문 미노출 |
| `./gradlew test --tests com.masiton.deployment.RuntimeDeploymentContractTest --no-daemon --console=plain` | 통과 | 기존 배포 rollback 계약과 변경된 rollback 상태 인자 |
| 전체 Gradle 테스트 | 환경 제약으로 실패 | 1,378건 중 885 통과, 2 skipped, 491 실패. 대부분은 Docker daemon 부재로 Testcontainers가 초기화되지 않은 통합 테스트였고, 별도의 기존 rollback 문자열 assertion 1건은 수정 후 대상 계약 테스트가 통과했다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 배포 후 health 진단은 실패를 조건문 안에서 직접 종료하지 않고, 진단 함수가 nonzero를 반환해 상위 `ERR` trap으로 전달하는 구조를 유지한다.
- 다음 확인: 실제 운영 배포에서는 dependency health 실패 시 rollback 로그와 이전 이미지·실행 산출물 복구 여부를 배포 기록에서 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 값 | 비교 결과 |
|---|---|---|---|---|
| dependency health 실패 rollback 상태 검증 시나리오 | 0건 | 기존 계약 테스트 기준 | 5건 | transport·HTTP·JSON·복수 DOWN·누락 경로의 nonzero와 rollback 상태를 자동 검증 |

## 10. 남은 사항

- #282는 PR이 아닌 Issue로 확인됐고, 연결된 GitHub 인라인 리뷰 스레드나 해결 처리 대상은 없다. 사용자의 요청대로 PR은 생성하지 않는다.
