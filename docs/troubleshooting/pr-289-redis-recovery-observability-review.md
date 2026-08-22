---
related_documents:
  - pr-284-redis-recovery-observability-review.md
  - ../08-planning/redis-recovery-runbook.md
  - ../../deploy/scripts/app-run.sh
  - ../../deploy/scripts/app-deploy.sh
  - ../../deploy/scripts/health-metrics.sh
  - ../../deploy/scripts/tests/health-metrics-endpoint-test.sh
  - ../../src/test/java/com/masiton/deployment/RedisEndpointParityContractTest.java
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #289 리뷰 트러블슈팅: 로컬 Redis fallback의 SSM port 오염

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #289](https://github.com/team-youngkk/masit-on/pull/289) |
| 작성자 | `inan0226` |
| 처리 일자 | 2026-08-22 |
| 범위 | 미해결 인라인 리뷰 2건: 공유 모드가 아닐 때 세 Redis endpoint producer가 SSM port를 사용하지 않는지와 회귀 테스트 보강 |
| 주 문제 유형 | 인프라 |
| 기존 기록 | [PR #284 Redis 복구 모드의 ALB 보호 선행 조건](pr-284-redis-recovery-observability-review.md)을 확인했다. Redis 복구·관측 계약은 재사용하고, 이번에는 local fallback 입력 경계를 보완했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [tjdgns0618 인라인 리뷰](https://github.com/team-youngkk/masit-on/pull/289#discussion_r3834552695) | `REQUIRE_SHARED_REDIS=false`일 때 SSM port를 조회하지 말고 환경변수 또는 6379를 사용하며 세 producer에 회귀 테스트를 추가 | 인프라 | 수정 필요 | 세 스크립트의 SSM host/port 조회를 공유 분기 안으로 이동하고 local fallback endpoint를 유지 | Git Bash endpoint contract 통과, `RedisEndpointParityContractTest` 통과 |
| [w00lam 인라인 리뷰](https://github.com/team-youngkk/masit-on/pull/289#discussion_r3834762284) | SSM port가 6380이어도 local health metrics client가 `127.0.0.1:6379`를 사용하는 조건을 고정 | 인프라 | 수정 필요 | SSM port 6380 fixture, `REDIS_PORT` unset, 실제 `redis-cli` 인자 `127.0.0.1:6379` 검증 추가 | `health-metrics-endpoint-test.sh` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 잘못된 endpoint가 선택되어 Redis client가 오동작할 수 있었다.
- 발생 환경: PR #289의 운영 배포 스크립트, `REQUIRE_SHARED_REDIS=false`인 단일 EC2 또는 로컬 fallback.
- 재현 조건: SSM `/masiton/redis/port`가 `6380`으로 존재하고 `REDIS_PORT`가 비어 있는 상태에서 앱 실행·health metrics·배포 smoke를 수행한다.
- 실제 결과: 기존 구현은 local fallback 분기보다 먼저 SSM port를 읽어 6380을 보존했다. health metrics는 로컬 endpoint 검증에 그 값을 사용했고, app-run/app-deploy도 이후 기본값 대입 전에 이미 값이 채워졌다.
- 기대 결과: 공유 모드에서만 SSM endpoint를 읽고, 로컬 모드에서는 명시된 `REDIS_PORT` 또는 기본값 `6379`를 사용해야 한다.
- 영향 범위: 단일 EC2의 앱 기동, Redis health metrics, 배포 smoke가 로컬 Redis 대신 잘못된 port에 연결해 인증·지표·배포 게이트가 함께 실패할 수 있다.

## 4. 근본 원인

세 producer가 `REQUIRE_SHARED_REDIS` 분기를 평가하기 전에 SSM port를 조회하거나, health metrics에서 분기 밖의 port 조회 결과를 유지했다. 따라서 환경변수 우선순위와 무관하게 SSM의 공유 Redis port가 local fallback에 유입되는 구조였다. 기존 테스트는 `REDIS_PORT=6379`를 명시해 이 경계를 가리고 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #289 미해결 스레드와 현재 세 스크립트 확인 | 두 스레드가 같은 원인을 지적했고, 세 스크립트 모두 관련 조회 순서가 계약과 어긋났다 | 두 요청을 하나의 원인으로 묶어 최소 수정 |
| 기존 [PR #284 기록](pr-284-redis-recovery-observability-review.md)과 Redis 복구 runbook 확인 | 공유 Redis 복구·fail-closed 게이트 의미는 유지해야 하며 local fallback은 별도 경계임을 확인 | 복구 모드·알람 계약은 변경하지 않음 |
| SSM port 6380 fixture와 `REDIS_PORT` unset 조건으로 endpoint shell contract 실행 | 수정 후 `redis-cli` 인자에 `127.0.0.1`, `-p`, `6379`가 기록되고 SSM shim 호출 파일은 생성되지 않음 | 실제 local client 입력 회귀 조건으로 채택 |
| `RedisEndpointParityContractTest`에서 세 producer의 조회·fallback 순서 검증 | 세 스크립트 모두 shared lookup이 local fallback 뒤로 새지 않는 조건 통과 | 동일 계약의 재발 방지 테스트로 채택 |

## 6. 최종 해결

- 변경 내용: `app-run.sh`, `health-metrics.sh`, `app-deploy.sh`에서 SSM Redis host/port 조회를 `REQUIRE_SHARED_REDIS=true` 분기 내부로 이동했다. local 분기에서는 host를 `127.0.0.1`로 고정하고 `REDIS_PORT`가 없으면 `6379`를 사용한다.
- 선택 이유: 기존 공유 Redis endpoint 검증과 운영 복구 계약은 유지하면서, 단일 EC2 fallback에 공유 Redis 설정이 오염되는 경계만 제거한다.
- 변경 파일:
  - `deploy/scripts/app-run.sh`
  - `deploy/scripts/health-metrics.sh`
  - `deploy/scripts/app-deploy.sh`
  - `deploy/scripts/tests/health-metrics-endpoint-test.sh`
  - `src/test/java/com/masiton/deployment/RedisEndpointParityContractTest.java`
  - `docs/troubleshooting/pr-289-redis-recovery-observability-review.md`
  - `docs/troubleshooting/README.md`
  - `docs/troubleshooting/pr-284-redis-recovery-observability-review.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `& 'C:\Program Files\Git\bin\bash.exe' deploy/scripts/tests/health-metrics-endpoint-test.sh` | 통과 | SSM port 6380 fixture에서도 local health metrics가 SSM을 호출하지 않고 `127.0.0.1:6379`로 Redis client를 호출 |
| `.\gradlew.bat test --tests com.masiton.deployment.RedisEndpointParityContractTest --tests com.masiton.deployment.HealthMetricsShellContractTest --no-daemon --console=plain` | 통과 | 세 producer 분기 순서와 health metrics shell contracts 확인 |
| `.\gradlew.bat test --tests com.masiton.deployment.AppRunScriptContractTest --tests com.masiton.deployment.RuntimeDeploymentContractTest --no-daemon --console=plain` | 통과 | 기존 앱 실행·배포·Redis 복구 운영 계약 회귀 확인 |
| `& 'C:\Program Files\Git\bin\bash.exe' -n deploy/scripts/app-run.sh deploy/scripts/health-metrics.sh deploy/scripts/app-deploy.sh deploy/scripts/tests/health-metrics-endpoint-test.sh` | 통과 | 셸 문법 오류 없음 |
| `git diff --check` | 통과 | whitespace 오류 없음 |

기본 `bash` 명령은 이 Windows 환경에서 WSL `/bin/bash` 부재로 실행되지 않아 Git Bash 절대 경로로 같은 테스트를 재실행했다.

## 8. 재발 방지 및 다음 확인

- endpoint shell contract가 SSM port `6380`, `REDIS_PORT` unset 조건과 실제 `redis-cli -p 6379` 인자를 계속 검증한다.
- `RedisEndpointParityContractTest`가 세 producer 모두에서 shared lookup이 local fallback보다 먼저 실행되지 않는 구조를 고정한다.
- AWS live plan/apply, 실제 Redis/Docker smoke, CodeDeploy recovery drill은 원격 운영 환경 없이는 확인하지 않았다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| local fallback의 공유 Redis port 오염 | 회귀 테스트로 차단되지 않음 | SSM port 6380, `REDIS_PORT` unset fixture에서 SSM 호출과 client port를 확인 | 배포 후 확인 필요 | 로컬 계약 테스트에서 SSM 미호출·6379 사용으로 수정 동작 확인 | 운영 배포 담당자, 다음 단일 EC2 smoke 시 확인 |

## 10. 남은 사항

- 로컬 코드·문서·검증은 완료했다.
- 커밋 `e05c09cc`를 원격 PR 브랜치에 push했고, 두 인라인 스레드에 검증 결과와 이 문서 링크로 답글을 단 뒤 모두 resolve했다.
