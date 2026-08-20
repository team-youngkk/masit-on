---
related_documents:
  - ../../infra/performance/README.md
  - ../../infra/performance/terraform/templates/deps-user-data.sh.tftpl
  - ../../infra/production/terraform-redis/security.tf
  - ../07-adr/quality/perf-003-isolated-performance-terraform.md
  - pr-261-performance-deps-separation-review.md
---

# 운영 작업: 격리 성능 환경 첫 apply에서 부트스트랩이 전혀 실행되지 않았다

## 1. 개요

| 항목 | 내용 |
|---|---|
| 발생 일자 | 2026-08-20 |
| 작업 | Issue #207 격리 성능 환경 첫 `terraform apply` (`run_id = 20260820-01`) |
| 실행자 | `w00lam` |
| 주 문제 유형 | 인프라 — user-data 패키지 충돌, VPC endpoint 접근 경계 |
| 결과 | 인스턴스 3대가 아무것도 설치되지 않은 상태로 기동. SSM 접근 불가. 측정 착수 실패 후 `terraform destroy` |
| 기존 기록 | [PR #261 리뷰 기록](pr-261-performance-deps-separation-review.md)이 같은 환경의 Redis 인증·절차 순서를 다뤘다. 그 기록의 "AWS apply 미실행" 항목이 이번 apply로 해소됐고, **정적 검증만으로는 드러나지 않는 결함 2건**이 여기서 드러났다. |

## 2. 문제 현상과 발생 조건

apply는 `40 added, 0 changed, 0 destroyed`로 정상 종료했고 출력값도 모두 정상이었다. 그러나 SSM `send-command`가 거부됐다.

```
An error occurred (InvalidInstanceId) when calling the SendCommand operation:
Instances not in a valid state for account
```

인스턴스 상태 검사는 3대 모두 통과(`running`, `ok`)한 상태였다. deps 인스턴스 콘솔 출력에서 두 가지가 함께 확인됐다.

```
masiton-perf-bootstrap: - package curl-minimal-8.17.0-1.amzn2023.0.3.aarch64 from @System
  conflicts with curl provided by curl-8.17.0-1.amzn2023.0.3.aarch64 from amazonlinux
masiton-perf-bootstrap: (try to add '--allowerasing' to command line ...)

SSM Agent unable to acquire credentials: ... Post "https://ssm.ap-northeast-2.amazonaws.com/":
dial tcp 10.0.10.85:443: i/o timeout
```

- 발생 환경: Amazon Linux 2023 arm64(`ami-0a1231e819ae021a0`), public subnet, 공인 IP 부여, egress HTTPS `0.0.0.0/0` 허용
- 실제 결과: Docker 미설치, WireMock·Redis 컨테이너 없음, `check-dependencies.sh`·`render-redis-conf.sh` 미생성, SSM 미등록
- 기대 결과: user-data가 끝까지 실행되고 SSM으로 의존 검증 스크립트를 호출할 수 있다
- 영향 범위: 성능 측정 착수 불가. 운영 영향은 없다. 과금은 apply부터 destroy까지 발생했다

## 3. 근본 원인

독립된 두 결함이 같은 시점에 드러났다.

**결함 1 — `dnf install`이 패키지 충돌로 실패한다.** user-data 6번째 줄이 `dnf install -y docker curl tar`인데, AL2023에는 `curl-minimal`이 기본 설치돼 있고 전체 `curl` 패키지와 같은 파일을 제공해 트랜잭션이 거부된다. 스크립트가 `set -euo pipefail`이므로 이 한 줄이 부트스트랩 전체를 중단시켰다. `git log -L`로 확인하니 이 줄은 [PR #218](pr-218-isolated-performance-review.md)에서 처음 들어왔고, 그 뒤 이 환경을 apply한 적이 없어 이번에 처음 드러났다. 세 template 모두 같은 형태였다.

**결함 2 — private DNS를 켠 SSM endpoint를 성능 SG가 통과하지 못한다.** 운영 VPC에는 `private_dns_enabled = true`인 SSM 인터페이스 endpoint가 있어 `ssm.<region>.amazonaws.com`이 VPC 전역에서 endpoint 사설 IP(`10.0.10.85`)로 해석된다. 성능 인스턴스가 public subnet에 공인 IP를 갖고 egress를 전부 열어도 인터넷 게이트웨이 경로를 쓸 수 없다. endpoint security group(`masiton-prod-vpce-sg`)의 443 인바운드는 운영 SG 2개만 허용했고 성능 SG는 없었다.

같은 규칙의 누락이 2026-08-18에 운영 인스턴스를 약 5분간 SSM에서 이탈시킨 적이 있고, 그 경고가 `security.tf` 주석으로 남아 있었다. **성능 환경이 새로 만든 SG로 같은 함정을 다시 밟았다.** 보안 그룹은 네트워크 경계일 뿐이라는 점은 PR #261의 Redis protected mode 판단 착오와 구조가 같다 — 경계를 한 층만 보고 다른 층의 판정을 놓쳤다.

## 4. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `terraform plan` JSON 정적 검증 | 40건 전부 create, 운영 자원 미포함, SG·RDS·SecureString 항목 모두 통과 | 계획 단계에서는 두 결함이 드러나지 않는다. 실제 부팅이 필요한 문제였다. |
| `describe-instance-status` | 3대 모두 `running`, 시스템·인스턴스 검사 `ok` | 인스턴스 자체는 정상이므로 user-data 또는 네트워크 문제로 좁힌다. |
| `get-console-output` (deps) | `dnf` 충돌과 SSM 자격 증명 실패가 함께 기록 | 두 결함을 동시에 식별했다. |
| `describe-vpc-endpoints` | `ssm` 인터페이스 endpoint가 private subnet 1개에 있고 `PrivateDnsEnabled=true` | DNS 가로채기가 원인임을 확인했다. |
| endpoint SG 인바운드 확인 | 443이 운영 SG 2개(`sg-0ec998a9b4d77f777`, `sg-08eb4b9f95caed750`)만 허용 | 성능 SG 누락을 확정했다. |
| 운영 앱 인스턴스 배치 확인 | 성능 인스턴스와 같은 public subnet에 있고 공인 IP를 가진다 | 공인 IP가 있어도 SG 허용이 있어야 SSM이 되는 것을 확인했다. `ssmmessages`는 IGW로 나가므로 endpoint를 추가하지 않는다. |
| `git log -L`로 `dnf` 줄 이력 추적 | PR #218에서 도입, 이후 apply 이력 없음 | PR #261이 만든 결함이 아니며 이 환경이 한 번도 부트스트랩에 성공한 적이 없음을 확인했다. |

## 5. 최종 해결

- **결함 1**: 세 template의 설치 목록에서 `curl`을 제거했다. `curl-minimal`이 이미 `/usr/bin/curl`을 제공하므로 기능 손실이 없다. `--allowerasing`으로 기본 패키지를 교체하는 대신 목록에서 빼는 쪽을 골랐다. 필요한 용도는 HTTPS·HTTP 호출뿐이라 `curl-minimal`로 충분하다.
- **결함 2**: 운영 `terraform-redis` 모듈의 `vpce` security group에 VPC CIDR 443을 허용하는 `vpce_from_vpc` 규칙을 추가하고, SG 설명을 실제 범위(`443 from inside the VPC only`)에 맞췄다.
- 선택 이유: 기존 `ssm_endpoint_client_security_group_ids` 열거 방식을 쓰면 성능 SG가 실행마다 새로 만들어지므로 측정 1회마다 운영 모듈을 두 번 apply해야 한다. 2026-08-18 사고가 이 규칙 변경에서 났으므로, 측정마다 운영 SG를 건드리는 구조를 만들지 않는다. SSM endpoint의 실질 경계는 호출자의 IAM 권한이고 이 규칙은 VPC 내부 출처만 매칭한다.
- 변경 파일: `infra/performance/terraform/templates/{app,deps,loadgen}-user-data.sh.tftpl`, `infra/production/terraform-redis/security.tf`, `infra/performance/README.md`
- 고려한 대안: 성능 환경을 별도 VPC에 두면 운영 endpoint에 의존하지 않지만, 운영 토폴로지를 대변한다는 이 환경의 목적과 어긋나고 비용·구성 범위가 크게 늘어난다.

## 6. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| 성능 template 3종 `bash -n` | 통과 | Terraform 변수 표기를 치환한 뒤 구문 검사했다. |
| 성능 모듈 `terraform fmt -check -recursive`·`validate` | 통과 | |
| 운영 `terraform-redis` `fmt -check -recursive`·`validate` | 통과 | 새 ingress 규칙과 `data.aws_vpc.existing.cidr_block` 참조가 유효하다. |
| destroy 후 잔여 자원 확인 | 통과 | EC2 3대 `terminated`, 성능 RDS 없음, `perf-207` SecureString 0건, `Purpose=issue-207` SG 0건, state 비었음. |
| 운영 모듈 apply | **미실행** | 운영 자원을 바꾸는 작업이라 별도 승인·검토 후 진행한다. |
| 성능 환경 재apply와 SSM 검증 | **미실행** | 운영 모듈 apply가 선행 조건이다. |

## 7. 재발 방지 및 다음 확인

- 재발 방지
  - user-data는 `plan`으로 검증되지 않는다. **template을 바꾸면 실제 apply로 부팅까지 확인**해야 하며, 실패 시 `get-console-output`이 1차 진단 경로다. 이 절차를 성능 README 절차에 반영했다.
  - private DNS를 켠 인터페이스 endpoint가 있는 VPC에 **새 SG를 만드는 모든 작업**은 endpoint SG 허용 여부를 함께 검토한다. VPC CIDR 규칙으로 SG 단위 열거 의존을 없앴다.
- 다음 확인
  1. 운영 `terraform-redis` plan을 검토해 `vpce_from_vpc` 1건 추가와 SG 설명 변경만 있는지 확인한 뒤 apply한다. 기존 규칙이 사라지지 않는지 특히 확인한다.
  2. 성능 환경을 다시 apply하고 SSM 등록을 기다린 뒤 `/opt/masiton-perf/check-dependencies.sh`로 `Redis AUTH+PING: OK`·`WireMock mappings: OK`를 확인한다.
  3. 담당: 성능 측정 담당자. 시점: 운영 apply 승인 직후.

## 8. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| user-data 완주 여부 | 6번째 줄에서 중단(Docker 미설치) | 재apply 후 `get-console-output`과 `docker ps` | 확인 예정 | 컨테이너 2종 기동이어야 통과 | 성능 측정 담당자, 재apply 직후 |
| SSM 등록 인스턴스 수 | 0/3 | `describe-instance-information` | 확인 예정 | 3/3 `Online` | 성능 측정 담당자, 재apply 직후 |
| 운영 인스턴스 SSM 연속성 | `Online` | 운영 apply 전후 `describe-instance-information` | 확인 예정 | 규칙 추가는 허용 확대뿐이라 이탈이 없어야 한다 | 운영 담당자, 운영 apply 직후 |

## 9. 남은 사항

- 이번 apply는 부트스트랩 실패로 측정에 착수하지 못했고, 자원은 destroy했다. 발생한 비용은 apply부터 destroy까지의 EC2 3대와 RDS 1대분이다.
- 성능 환경이 운영 VPC endpoint에 의존한다는 점은 구조적 결합이다. 운영 endpoint 구성이 바뀌면 성능 환경 SSM 경로가 함께 끊긴다. 성능 README 사전 조건에 명시했다.
