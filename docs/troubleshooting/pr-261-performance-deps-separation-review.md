---
related_documents:
  - ../../infra/performance/README.md
  - ../../infra/performance/terraform/templates/app-user-data.sh.tftpl
  - ../../infra/performance/terraform/templates/deps-user-data.sh.tftpl
  - ../../deploy/scripts/redis-render-conf.sh
  - ../07-adr/quality/perf-003-isolated-performance-terraform.md
  - ../08-planning/post-cutover-runtime-baseline.md
  - pr-218-isolated-performance-review.md
---

# PR #261 리뷰 트러블슈팅: 의존 인스턴스 분리가 만든 Redis 연결 거부와 무인증 노출

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#261 격리 성능 환경을 운영 토폴로지에 맞춘다](https://github.com/team-youngkk/masit-on/pull/261) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-20 |
| 범위 | 미해결 인라인 리뷰 5건(`inan0226` 1건, `jinyp01` 4건) |
| 주 문제 유형 | 인프라 — Redis 인증·연결 조건, 운영 절차 순서, 증적 누락 |
| 기존 기록 | [PR #218 격리 성능 환경](pr-218-isolated-performance-review.md)이 이 Terraform 구성의 추적성과 운영 리소스 격리를 다뤘고, [PR #257 전환 후 런타임 기준선](pr-257-runtime-baseline-review.md)이 Redis 메모리 실측을 남겼다. 두 기록 모두 **WireMock·Redis가 앱과 같은 호스트에서 loopback에 바인딩된 시점**을 전제했다. 이번 PR이 의존을 별도 인스턴스로 분리하면서 그 전제가 깨진 것이 리뷰 지적 5건의 공통 원인이므로 새 기록으로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [원격 Redis protected mode 연결 실패](https://github.com/team-youngkk/masit-on/pull/261#discussion_r3817964872) | 비밀번호 없는 원격 Redis가 protected mode에서 앱 연결을 거부하므로 인증 또는 무인증 측정에 맞는 설정·연결 검증이 필요함 | 인프라 | **최초 지적이 맞다.** 같은 스레드의 [철회 코멘트](https://github.com/team-youngkk/masit-on/pull/261#discussion_r3817997921)는 실제 동작과 다르다 | 성능 전용 `requirepass`를 SecureString으로 추가하고 `protected-mode yes`를 유지했다. 앱 인스턴스에서 RESP inline `AUTH`·`PING`으로 연결을 사전 검증한다 | 고정 이미지 `redis:8.8-alpine` 실측 3종(5절) |
| [무인증 Redis에서 SG가 유일한 방어선](https://github.com/team-youngkk/masit-on/pull/261#discussion_r3817981500) | `requirepass`로 이중 방어하거나 리스크를 팀 결정으로 명시 | 인프라 | 수정 필요 | 권고한 `requirepass` + `REDIS_PASSWORD_PARAMETER` 방식을 `DB_PASSWORD_PARAMETER`와 같은 패턴으로 구현했다 | 무인증 원격 연결이 `-NOAUTH Authentication required.`로 거부되는 것을 실측 확인 |
| [WireMock `/__admin` 네트워크 노출](https://github.com/team-youngkk/masit-on/pull/261#discussion_r3817981502) | 스텁 런타임 변조 위험을 README에 명시하거나 인증 프록시 | 인프라 | 문서화로 처리 | README에 노출 범위와 **측정 중 `/__admin` 호출 금지**를 명시했다. 매핑 확인은 기동 전 사전 검증 1회로 고정했다 | 매핑 변경이 이후 측정값의 fixture 대표성을 깨뜨림 |
| [Redis 컨테이너 OOM 증적 누락](https://github.com/team-youngkk/masit-on/pull/261#discussion_r3817981503) | 증적 항목에 컨테이너 재시작·OOM 확인 추가 | 인프라 | 수정 필요 | README 5단계에 `docker inspect`로 `OOMKilled`·`RestartCount`를 수집하고 해당 구간 결과를 무효 판정하는 규칙을 추가했다 | 메모리 한도 조합 자체는 운영 설정 재현이므로 바꾸지 않음 |
| [절차 1·2단계 순서 자기모순](https://github.com/team-youngkk/masit-on/pull/261#discussion_r3817981508) | 의존 확인을 백엔드 기동보다 앞으로 | 인프라 | 수정 필요 | 의존 검증을 1단계로 올리고 백엔드 기동을 2단계로 내렸다 | README 절차 재검토 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `-DENIED Redis is running in protected mode because protected mode is enabled and no password is set for the default user. In this mode connections are only accepted from the loopback interface.`
- 발생 환경: PR #261이 앱과 의존(WireMock·Redis)을 서로 다른 EC2로 분리하고 두 의존 포트를 loopback에서 `0.0.0.0`으로 바꾼 구성. Redis는 `--bind 0.0.0.0 --protected-mode yes`이고 `requirepass`·ACL이 없었다.
- 재현 조건: 앱의 `REDIS_HOST`가 deps private IP를 가리켜 loopback이 아닌 주소에서 Redis에 명령을 보내는 경우
- 실제 결과
  - 원격 연결이 `DENIED`로 거부된다. AWS apply는 하지 않았으나 고정 이미지로 같은 옵션 조합을 실행해 재현했다.
  - 절차 1단계가 백엔드 기동, 2단계가 의존 경로 확인이어서 "확인에 실패하면 백엔드를 시작하지 않는다"를 지킬 수 없었다.
  - 증적 항목에 의존 컨테이너 OOM·재시작이 없어 측정 중 조용한 실패를 놓칠 수 있었다.
- 기대 결과: 앱 인스턴스에서 Redis 인증과 `PING`이 성공하고, 백엔드 기동 전에 의존 경로를 중단 가능하게 확인하며, 컨테이너 OOM 여부가 증적에 남는다.
- 영향 범위: 성능 환경 백엔드의 Redis 기반 기능 전체가 기동 직후 실패해 부하 측정을 시작할 수 없다. AWS apply 전이므로 운영 영향은 없다.

## 4. 근본 원인

앱과 의존을 다른 호스트로 분리하면 세 가지가 함께 바뀐다. **Redis의 protected mode 판정**(loopback 연결 → 원격 연결), **기동 순서 의존성**(같은 호스트 안 프로세스 → 원격 인스턴스 준비 여부), **인증 경계**(도달 불가 → SG 규칙 하나)다. 이번 변경은 바인딩 주소와 보안 그룹 규칙만 그 분리에 맞췄고, 그로 인해 달라진 세 지점은 이전 동거 구성의 전제를 그대로 유지했다. 리뷰 지적 5건은 모두 이 미반영 지점이다.

Redis의 protected mode를 "`bind`를 명시하면 해제된다"고 읽은 것이 이번 판단 착오의 핵심이다. Redis 8.8은 기본 사용자에 비밀번호가 없으면 `bind` 지정 여부와 무관하게 loopback 외 연결을 거부한다. 보안 그룹은 네트워크 경계일 뿐 이 판정에 관여하지 않는다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `redis:8.8-alpine`을 `--bind 0.0.0.0 --protected-mode yes`, 비밀번호 없이 기동한 뒤 비-loopback 주소에서 RESP `PING` | `-DENIED Redis is running in protected mode ...` | 최초 P1 지적이 맞다. `bind` 명시가 protected mode를 해제한다는 철회 근거는 성립하지 않는다. |
| 같은 이미지를 `--protected-mode no`로 기동해 같은 경로로 `PING` | `+PONG` | 연결은 되지만 인증이 없어 스레드 B의 지적이 그대로 남는다. 채택하지 않는다. |
| 같은 이미지를 `--protected-mode yes --requirepass ...`로 기동해 inline `AUTH` 뒤 `PING` | `+OK`, `+PONG`. 인증 없이 보낸 `PING`은 `-NOAUTH Authentication required.` | 연결 문제와 인증 부재를 한 번에 해소한다. 이 방식을 채택한다. |
| 운영 Redis 비밀값 전달 절차 대조([deploy/scripts/redis-render-conf.sh](../../deploy/scripts/redis-render-conf.sh)) | 운영은 명령행에 비밀값을 두지 않고 tmpfs(`/run`)에 설정을 렌더링해 읽기 전용으로 마운트한다. CR 제거와 공백 거부까지 이미 다룬다. | 성능 환경도 같은 절차를 따른다. `--requirepass`를 `docker run` 인자로 붙이지 않는다. |
| 렌더링한 설정으로 실제 컨테이너 기동 후 적용값 확인 | `maxmemory=268435456`, `maxmemory-policy=noeviction`, `appendonly=yes`, `protected-mode=yes`, `requirepass` 설정됨. 원격 `AUTH`+`PING` 성공 | 운영 제약 재현과 인증이 동시에 성립함을 확인했다. |
| 상속받은 `check-dependencies.sh` 실행 | RESP 단순 문자열이 CR로 끝나 `[ "$response" = "+PONG" ]` 비교가 항상 실패했다. `curl` 출력을 `grep -q`로 넘기는 파이프는 `pipefail`과 겹쳐 오탐 가능했다. | 두 결함을 고쳤다(6절). |
| 이전 리비전의 바인딩과 대조 | 이전은 Redis·WireMock 모두 `127.0.0.1`로 네트워크 도달 자체가 불가능했다. | WireMock `/__admin` 노출의 리스크 수준이 실제로 달라졌음을 README에 명시한다. |
| README 절차 1~6단계 재독 | 1단계 기동이 2단계 확인보다 앞서 중단 조건을 지킬 수 없었다. | 순서를 바꾼다. |
| AWS apply 및 실제 EC2 연결 | 실행하지 않았다. | 계정·비용·외부 상태를 바꾸지 않고, 고정 이미지 실측과 템플릿 정적 검증으로 범위를 제한한다. |

## 6. 최종 해결

- 변경 내용
  - `variables.tf`·`ssm.tf`: `TF_VAR_redis_password`로 주입하는 성능 전용 `requirepass`를 SecureString parameter로 만든다. 공백 금지와 16~128자를 등록 시점에 검증한다.
  - `iam.tf`: 앱 역할은 DB·Redis 두 parameter를, deps 역할은 Redis parameter만 읽는다.
  - `deps-user-data.sh.tftpl`: `/opt/masiton-perf/render-redis-conf.sh`가 Parameter Store에서 값을 읽어 tmpfs `/run/masiton-perf/redis.conf`(`0400`, uid 999)에 렌더링하고, Redis를 그 설정 파일로 기동한다. 비밀값을 명령행과 디스크에 남기지 않는다.
  - `app-user-data.sh.tftpl`: `runtime.env`에 `REDIS_PASSWORD_PARAMETER`를 추가하고, `/opt/masiton-perf/check-dependencies.sh`가 RESP inline `AUTH`·`PING`과 WireMock 매핑을 재시도 확인한다. CR로 끝나는 응답 비교와 `pipefail` 오탐을 함께 고쳤고, 인증 실패는 `-WRONGPASS`를 남겨 도달 불가와 구분한다.
  - `infra/performance/README.md`: `requirepass`가 연결 조건이라는 사실과 tmpfs 재기동 주의, WireMock 관리 API 노출 수용 범위, 절차 순서, OOM 증적을 반영했다.
- 선택 이유: `protected-mode no`는 연결만 열고 인증 부재를 그대로 남긴다. `requirepass`는 연결 조건(스레드 A)과 인증 경계(스레드 B)를 함께 해소하고, 운영이 이미 쓰는 비밀값 전달 절차를 재사용하므로 새 관례를 만들지 않는다.
- 변경 파일: `infra/performance/terraform/variables.tf`, `ssm.tf`, `iam.tf`, `ec2.tf`, `outputs.tf`, `templates/deps-user-data.sh.tftpl`, `templates/app-user-data.sh.tftpl`, `infra/performance/README.md`
- 고려한 대안: `--protected-mode no` + 리스크 문서화는 변경 범위가 작지만 인증이 없는 상태가 유지되고, 리뷰가 지적한 "SG 하나가 유일한 방어선"이 그대로 남는다. WireMock 관리 API는 같은 이유로 문서화만 했는데, 스텁 서버에 인증 프록시를 새로 세우는 비용이 측정 목적에 비해 크다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `terraform fmt -check -recursive` | 통과 | Terraform 변경 형식에 문제가 없다. |
| `terraform validate` | 통과 | 새 변수·parameter·IAM 문서와 `templatefile()` 변수 계약이 유효하다. |
| user-data 3종과 내부 스크립트 2종 `bash -n` | 통과 | Terraform 변수 표기를 치환한 뒤 heredoc 안의 `check-dependencies.sh`·`render-redis-conf.sh`까지 개별 검사했다. |
| `redis:8.8-alpine` protected mode 실측 3종 | 통과 | 비밀번호 없음은 `DENIED`, `protected-mode no`는 `+PONG`, `requirepass`는 `AUTH` 후 `+PONG`. 무인증 요청은 `-NOAUTH`. |
| `render-redis-conf.sh` 렌더링 결과로 컨테이너 기동 | 통과 | `maxmemory`·`maxmemory-policy`·`appendonly`·`protected-mode`·`requirepass`가 모두 적용됐다. |
| `check-dependencies.sh` 경로별 실행 | 통과 | 정상은 `Redis AUTH+PING: OK`·`WireMock mappings: OK`로 종료 코드 0, 잘못된 비밀번호는 `-WRONGPASS`를 남기고 1, Redis 도달 불가와 WireMock 도달 불가도 각각 1. |
| AWS apply·실제 EC2 연결 | 미실행 | 임시 성능 자원과 비용을 만들지 않았다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지
  - 앱 user-data에 Redis `AUTH`+`PING`과 WireMock 매핑 확인을 함께 두고, README 절차가 이 스크립트를 백엔드 기동 전에 실행하도록 고정했다.
  - protected mode 판정을 문서 통념이 아니라 고정 이미지 실측으로 확인하는 절차를 5절에 남겼다. Redis 기동 옵션을 바꿀 때 같은 방식으로 확인한다.
  - 호스트 배치를 바꿀 때 protected mode 판정·절차 순서·인증 경계·증적 항목을 함께 검토해야 한다는 점을 4절에 근본 원인으로 남겼다.
- 다음 확인: apply 후 앱 인스턴스에서 `/opt/masiton-perf/check-dependencies.sh`를 실행해 `Redis AUTH+PING: OK`와 `WireMock mappings: OK`를 확인하고, 측정 종료 시 `docker inspect`로 두 컨테이너의 `OOMKilled`·`RestartCount`를 증적에 남긴다. 성능 측정 담당자가 수행한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 앱→Redis 원격 연결 | `DENIED`(고정 이미지 실측) | apply 후 `check-dependencies.sh` 1회 | 확인 예정 | `Redis AUTH+PING: OK` 출력 여부로 판정 | 성능 측정 담당자, apply 직후 |
| 무인증 Redis 접근 | 연결이 성립하면 인증 없이 접근 가능 | 같은 스크립트에서 AUTH를 생략한 응답 확인 | 확인 예정 | `-NOAUTH`로 거부되어야 함 | 성능 측정 담당자, apply 직후 |
| 의존 컨테이너 OOM·재시작 | 증적 미수집 | 시나리오 종료 시 `docker inspect` 1회 | 확인 예정 | `OOMKilled=false`, `RestartCount=0`이어야 결과 유효 | 성능 측정 담당자, 시나리오 종료 시 |

## 10. 남은 사항

- AWS apply와 실제 EC2 의존 연결은 비용·외부 상태 변경을 수반하므로 이번 작업에서 실행하지 않았다. Redis 동작 확인은 같은 고정 이미지를 로컬에서 실행해 대체했다.
- WireMock 관리 API 보호는 이번 PR에서 추가하지 않고 문서화된 수용 범위로 남겼다. 측정 기간이 길어지거나 app SG 도달 호스트가 늘어나면 다시 판단한다.
- `/run`이 tmpfs이므로 의존 인스턴스를 재기동하면 Redis 설정이 사라진다. 운영은 systemd `ExecStartPre`로 매 기동 렌더링을 보장하지만, 임시 성능 환경은 재기동을 전제하지 않고 README 주의 사항으로 남겼다.
