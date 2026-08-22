---
related_documents:
  - pr-284-redis-recovery-observability-review.md
  - ../08-planning/redis-recovery-runbook.md
  - ../../deploy/scripts/app-run.sh
  - ../../deploy/scripts/app-deploy.sh
  - ../../deploy/scripts/health-metrics.sh
  - ../../deploy/scripts/tests/redis-endpoint-contract-test.sh
  - ../../deploy/scripts/tests/health-metrics-endpoint-test.sh
  - ../../src/test/java/com/masiton/deployment/RedisEndpointContractTest.java
  - ../../src/test/java/com/masiton/deployment/RedisEndpointParityContractTest.java
  - ../../src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #289 리뷰 트러블슈팅: Redis endpoint fallback·producer 계약 정합성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #289](https://github.com/team-youngkk/masit-on/pull/289) |
| 작성자 | `inan0226` |
| 처리 일자 | 2026-08-22 |
| 범위 | local fallback의 SSM port 오염 2건과 최신 develop 병합 뒤 발생한 Redis endpoint parity 계약 1건 |
| 주 문제 유형 | 인프라·테스트 계약 |
| 기존 기록 | [PR #284 Redis 복구 모드의 ALB 보호 선행 조건](pr-284-redis-recovery-observability-review.md)에서 공유 Redis 복구·fail-closed 의미를 확인했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [tjdgns0618 인라인 리뷰](https://github.com/team-youngkk/masit-on/pull/289#discussion_r3834552695) | `REQUIRE_SHARED_REDIS=false`에서 SSM port를 조회하지 말고 환경변수 또는 6379를 사용 | 인프라 | 수정 필요 | 세 producer의 SSM host/port 조회를 공유 분기 안으로 이동 | Redis endpoint shell contract, parity·runtime 계약 테스트 |
| [w00lam 인라인 리뷰](https://github.com/team-youngkk/masit-on/pull/289#discussion_r3834762284) | SSM port가 6380이어도 local health metrics client가 `127.0.0.1:6379`를 사용 | 인프라 | 수정 필요 | SSM port 6380 fixture와 실제 `redis-cli` 인자를 추가하고 local fallback을 고정 | `health-metrics-endpoint-test.sh` |
| [w00lam 최신 인라인 리뷰](https://github.com/team-youngkk/masit-on/pull/289#discussion_r3835856376) | parity 테스트가 최신 app-run/app-deploy 구현과 맞지 않고 health-metrics가 IPv4 전용이라 세 producer 정책이 불일치 | 테스트 계약·인프라 | 수정 필요 | health-metrics를 최신 IPv4/private·ULA IPv6 endpoint 계약으로 정렬하고 parity 테스트를 세 producer 공통 계약으로 갱신 | 세 producer 공통 계약 비교, ULA·DNS 다중 주소 fixture, 관련 Gradle 테스트 |

## 3. 문제 현상과 발생 조건

- 첫 번째 문제는 `REQUIRE_SHARED_REDIS` 분기 전에 SSM `/masiton/redis/port`를 읽는 구조였다. SSM port가 `6380`이고 `REDIS_PORT`가 비어 있으면 local Redis fallback에 공유 port가 유입될 수 있었다.
- 최신 develop을 병합한 뒤에는 `app-run.sh`와 `app-deploy.sh`가 IPv4 private 및 ULA IPv6를 허용하는 `redis_ipv4_to_words`·`redis_ipv6_to_words`·`getent ahosts --no-addrconfig` 계약으로 바뀌었다.
- 그러나 `health-metrics.sh`와 `RedisEndpointParityContractTest`는 이전 `is_canonical_ipv4`·`getent ahostsv4`·`REDIS_VALIDATED_PORT` 계약을 계속 전제했다. 그 결과 parity 테스트가 최신 producer를 즉시 실패시키고 CI backend build/test가 실패했다.

기대 결과는 세 producer가 같은 shared endpoint 정책을 사용하고, local 모드에서는 공통적으로 SSM을 읽지 않으며, 검증된 endpoint만 Redis client에 전달하는 것이다.

## 4. 근본 원인

SSM 조회 위치 문제와 endpoint 검증 정책 변경이 서로 다른 시점에 반영되면서 producer와 테스트가 분리됐다. app-run/app-deploy는 최신 develop의 IPv4/private·ULA IPv6 정책을 사용했지만 health-metrics와 parity 테스트가 구 계약에 남아 있었다. parity 테스트가 구현의 실제 endpoint 계약이 아니라 과거 helper 이름과 port 대입 문자열을 고정한 것이 CI 실패를 재현했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단 |
|---|---|---|
| PR 최신 head의 세 producer와 미해결 스레드 확인 | app-run/app-deploy와 health-metrics의 helper·DNS·IPv6 허용 범위가 달랐다 | 세 producer를 같은 계약으로 정렬해야 함 |
| 최신 develop의 `redis-endpoint-contract-test.sh`와 app-run/app-deploy 비교 | private IPv4, ULA IPv6, DNS 전체 결과 검사, `--no-addrconfig`가 최신 기준임을 확인 | 최신 develop 계약을 기준으로 채택 |
| SSM port 6380 및 `REDIS_PORT` unset fixture 실행 | local 모드에서 SSM 미호출과 `127.0.0.1:6379` client 인자를 확인 | local fallback 회귀 조건 유지 |
| health-metrics에 ULA·private dual-stack DNS fixture 추가 | `fd00::10` 직접 입력과 DNS의 IPv4/IPv6 결과를 모두 검증하고 public/mapped/link-local 입력을 거부 | 실제 동작 검증으로 parity source assertion 보완 |

## 6. 최종 해결

- `health-metrics.sh`의 endpoint contract를 app-run/app-deploy와 동일하게 정렬했다. private IPv4와 ULA IPv6를 숫자 단위로 검증하고, DNS는 `getent ahosts --no-addrconfig` 결과 전체를 검사한다.
- health-metrics의 Redis client는 검증된 host/port만 사용한다. local 모드에서는 `127.0.0.1`과 환경변수 또는 6379를 사용하며, shared 모드에서만 SSM endpoint를 읽는다.
- `RedisEndpointParityContractTest`는 세 producer의 공통 contract block, shared lookup 범위, local fallback, secret/client 경계를 검증하도록 갱신했다. 줄바꿈 차이는 계약 의미와 무관하므로 비교 전에 정규화했다.
- 최신 endpoint 정책에 맞게 `RuntimeDeploymentContractTest`의 health-metrics 기대 문자열을 갱신했다.

변경 파일:

- `deploy/scripts/health-metrics.sh`
- `deploy/scripts/tests/health-metrics-endpoint-test.sh`
- `src/test/java/com/masiton/deployment/RedisEndpointParityContractTest.java`
- `src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java`
- `docs/troubleshooting/pr-289-redis-recovery-observability-review.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `& 'C:\Program Files\Git\bin\bash.exe' deploy/scripts/tests/redis-endpoint-contract-test.sh` | 통과 | app-run/app-deploy의 IPv4·ULA IPv6·DNS endpoint 계약과 secret 경계 |
| `& 'C:\Program Files\Git\bin\bash.exe' deploy/scripts/tests/health-metrics-endpoint-test.sh` | 통과 | health-metrics의 IPv4·ULA·dual-stack DNS, 위험 주소 거부, local SSM 미호출 |
| `.\gradlew.bat test --tests com.masiton.deployment.RedisEndpointContractTest --tests com.masiton.deployment.RedisEndpointParityContractTest --tests com.masiton.deployment.HealthMetricsShellContractTest --tests com.masiton.deployment.RuntimeDeploymentContractTest --no-daemon --console=plain` | 통과 | producer parity, shell contracts, runtime deployment 계약 |
| `& 'C:\Program Files\Git\bin\bash.exe' -n deploy/scripts/app-run.sh deploy/scripts/app-deploy.sh deploy/scripts/health-metrics.sh deploy/scripts/tests/redis-endpoint-contract-test.sh deploy/scripts/tests/health-metrics-endpoint-test.sh` | 통과 | 셸 문법 오류 없음 |
| `git diff --check` | 통과 | whitespace 오류 없음 |

기본 `bash` 명령은 이 Windows 환경에서 WSL `/bin/bash` 부재로 실행하지 못해 Git Bash 절대 경로로 같은 shell contract를 실행했다.

## 8. 재발 방지 및 다음 확인

- 세 producer의 공통 contract block이 다르면 `RedisEndpointParityContractTest`가 실패한다.
- health-metrics fixture는 SSM port 6380과 local fallback을 계속 확인하고, shared endpoint의 ULA·DNS 다중 주소 정책도 확인한다.
- AWS live plan/apply, 실제 Redis/Docker smoke, CodeDeploy recovery drill은 원격 운영 환경 없이는 확인하지 않았다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| local fallback의 공유 Redis port 오염 | 회귀 테스트로 차단되지 않음 | SSM port 6380, `REDIS_PORT` unset fixture에서 SSM 호출과 client port 확인 | 운영 배포 후 확인 필요 | local contract에서 SSM 미호출·6379 사용 확인 | 운영 배포 담당자, 다음 단일 EC2 smoke |
| Redis endpoint producer 계약 불일치 | parity 테스트와 health-metrics가 구 계약 사용 | 공통 block 비교 및 IPv4·ULA·DNS fixture | 운영 배포 후 확인 필요 | 세 producer 공통 contract와 관련 테스트 통과 | 배포 담당자, 다음 ASG smoke |

## 10. 남은 사항

- 코드·테스트·문서 갱신과 로컬 검증은 완료했다.
- 최신 develop 병합 충돌을 해결한 뒤 발견된 parity P1에 대해 producer 구현과 테스트를 함께 정렬했다.
- push 후 최신 P1 인라인 스레드에 변경 파일, 검증 명령, 이 문서 링크를 답글로 남기고 resolve한다. CI 재실행 결과는 원격 상태가 갱신되면 확인한다.
