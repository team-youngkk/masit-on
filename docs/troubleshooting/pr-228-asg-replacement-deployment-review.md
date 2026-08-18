---
related_documents:
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../08-planning/blue-green-cleanup-runbook.md
  - ../../infra/production/README.md
  - ../../infra/production/terraform-redis/README.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #228 리뷰 트러블슈팅: ASG replacement 배포의 상태 보존·중단 제어·계약 정합성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#228 ASG 기반 Blue-Green 운영 배포 인프라 반영](https://github.com/team-youngkk/masit-on/pull/228) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-18 |
| 범위 | 최초 리뷰 7건, 후속 P1 리뷰 3건, 최신 후속 리뷰 8건, 최신 HEAD에서 이미 반영된 RDS ingress 예시 1건 |
| 주 문제 유형 | 배포 / 인프라 / 애플리케이션 |
| 기존 기록 | [트러블슈팅 인덱스](README.md), [PR #221 배포 hardening 기록](pr-221-deployment-hardening-cost-review.md), ADR-DEPLOY-005와 비용·일정 영향 검토를 확인했다. 동일한 Redis volume·CodeDeploy 중단·단일 target group 문제를 직접 다룬 기존 기록은 없어 새 기록으로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [Redis volume](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801367860) | root volume 교체 시 AOF·Refresh Token·rate-limit 상태가 삭제되지 않도록 분리 | 데이터베이스 / 인프라 | 수정 필요 | 별도 암호화 gp3 EBS와 attachment를 추가하고 mount·`prevent_destroy`를 적용 | `RuntimeDeploymentContractTest`, Git Bash `bash -n`, `terraform validate`·Redis `plan` 통과 |
| [CodeDeploy timeout/cancel](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801367865) | timeout·workflow 취소 시 `stop-deployment --auto-rollback-enabled`와 terminal 상태 확인 | 배포 | 수정 필요 | 45분 polling, EXIT·signal trap, 중지 후 terminal polling, `StopDeployment` 권한 추가 | `RuntimeDeploymentContractTest` 통과. 실제 CodeDeploy 취소 리허설은 미실행 |
| [ADR·runbook target group](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801367867) | listener 전환 대신 단일 target group의 instance 등록·해제와 ASG membership 판정으로 정정하고 green 자원 제거 | 배포 / 인프라 | 수정 필요 | ADR·runbook·운영 README 정정, green target group·alarm·output 제거 | `RuntimeDeploymentContractTest`, 관련 문자열 검색 통과. AWS 리허설은 미실행 |
| [RDS ingress 예시](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801373600) | 예시에 `manage_rds_ingress_rule = true` 명시 | 인프라 | 이미 해결 | 최신 HEAD의 `infra/production/terraform/terraform.tfvars.example`에 이미 설정되어 있어 추가 변경 없음 | 현재 파일 36행의 `true` 확인 |
| [subnet route 조건](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801373603) | ALB에는 IGW default route, app에는 IGW default route가 없음을 plan에서 검증 | 인프라 | 수정 필요 | `app_subnet_is_private` 변수로 배치 의도를 명시하고 선언값과 실제 route를 대조하도록 정정 | `RuntimeDeploymentContractTest` 통과. 운영 public subnet 전제는 `false`로 선언해 plan 충돌을 해소 |
| [rollback 실행 산출물](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801373606) | 이미지뿐 아니라 script·systemd unit도 실패 시 이전 버전 복원 | 배포 / 애플리케이션 | 수정 필요 | 배포 전 script·unit backup과 missing marker를 만들고 rollback에서 복원·제거 | Git Bash `bash -n`, `RuntimeDeploymentContractTest` 통과 |
| [CodeDeploy IAM wildcard](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801373612) | CreateDeployment를 deployment group으로 제한하고 wildcard 조회를 별도 분리 | 인프라 | 수정 필요 | 생성·고정 리소스 조회·wildcard 조회·중지 권한을 별도 statement로 분리 | `RuntimeDeploymentContractTest` 통과. IAM policy simulator는 미실행 |
| [Redis 최초 데이터 이전](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801505483) | 기존 root volume의 AOF·RDB를 새 data EBS로 이전하는 절차가 없음 | 인프라 / 데이터베이스 | 수정 필요 | 기존 상태를 offline copy하고 Terraform import 후 replacement를 승인하는 운영 절차를 Redis README에 추가 | `RuntimeDeploymentContractTest` 통과. 실제 운영 데이터 이전은 미실행 |
| [rollback 오류 전파](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801505488) | rollback 성공처럼 반환되어 실패한 배포가 성공으로 끝날 수 있음 | 배포 / 애플리케이션 | 수정 필요 | 원래 exit code 보존, 복구 실패 non-zero 반환, 수동 복구 error를 추가 | Git Bash `bash -n`, `RuntimeDeploymentContractTest` 통과 |
| [CodeDeploy stop 실패](https://github.com/team-youngkk/masit-on/pull/228#discussion_r3801505494) | stop API 실패·terminal 미확인이 성공 경로로 처리됨 | 배포 / 인프라 | 수정 필요 | stop 실패와 terminal 미확인을 non-zero·GitHub error annotation으로 전파 | `RuntimeDeploymentContractTest` 통과. 실제 cancel 리허설은 미실행 |
| [Redis NVMe serial](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7imXGN`) | Nitro NVMe `/dev/disk/by-id` serial에서 EBS volume ID 하이픈이 제거됨 | 인프라 / 배포 | 수정 필요 | user-data에서 volume ID를 하이픈 제거 후 stable by-id 경로로 변환 | Redis user-data `bash -n`, `RuntimeDeploymentContractTest` |
| [취소 cleanup](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7imXGY`) | GitHub 취소가 EXIT trap보다 빨라 원격 CodeDeploy가 계속 진행할 수 있음 | 배포 / Git | 수정 필요 | production 자동 취소를 끄고 deployment ID artifact와 별도 cleanup job에서 stop·terminal 확인 | `RuntimeDeploymentContractTest` 통과. 실제 취소 리허설은 미실행 |
| [Redis multipart AOF](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7imXGd`) | Redis 8의 `appendonly.aof` 단일 파일 검사가 정상 복사본에서도 실패함 | 데이터베이스 / 인프라 | 수정 필요 | manifest·multipart AOF 파일·`redis-check-aof`·known fixture key 검증으로 교체 | README 계약 테스트 통과. 실제 운영 데이터 복구 리허설은 미실행 |
| [public IPv4·private egress](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7imXGk`) | public route만 있고 public IPv4 할당·private egress가 보장되지 않음 | 인프라 / 네트워크 | 수정 필요 | launch template에서 public IPv4를 명시하고 private 모드는 NAT default route를 plan에서 검증 | `RuntimeDeploymentContractTest`, Terraform fmt. 실제 AWS plan/apply는 미실행 |
| [rollback trap 시점](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7imuyR`) | 활성 파일 교체 전에 rollback trap이 없어 install·daemon-reload·enable 실패를 복구하지 못함 | 배포 / 애플리케이션 | 수정 필요 | 이전 산출물 backup 직후 trap을 등록해 첫 활성 install부터 rollback 보호 | Git Bash `bash -n`, `RuntimeDeploymentContractTest` |
| [ACM 인증서 기본값](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7imuyT`) | `null` 기본값은 HTTP listener만 만들지만 Nginx·배포는 항상 TLS 인증서를 요구함 | 인프라 / 배포 | 수정 필요 | ACM certificate ARN을 필수 변수로 만들고 ARN 형식 validation 추가 | Terraform validate·`RuntimeDeploymentContractTest`. 실제 AWS apply는 미실행 |
| [Redis templatefile 보간](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7inMHo`) | Terraform `templatefile()`이 셸의 `${...}`를 Terraform 보간식으로 해석함 | 인프라 / 배포 | 수정 필요 | `$${...}`로 escape해 렌더링 결과가 셸 변수 확장으로 남도록 정정하고 계약 테스트 보강 | Redis Terraform 렌더링 구문·`RuntimeDeploymentContractTest` 확인 |
| [취소 cleanup ID 보존](https://github.com/team-youngkk/masit-on/pull/228) (`PRRC_kwDOTf2xKc7inMHt`) | 취소 시 deploy job 후속 artifact step이 실행되지 않아 cleanup job이 deployment ID를 얻지 못함 | 배포 / Git | 수정 필요 | deployment 생성 직후 S3에 ID를 기록하고 cleanup job이 동일한 키에서 직접 조회 | `RuntimeDeploymentContractTest`, workflow YAML parse. 실제 취소 리허설은 미실행 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: Redis `root_block_device.delete_on_termination = true`와 AOF 경로가 같은 root volume에 있음. CodeDeploy polling이 20분에서 종료. `InvalidLoadBalancerInfoException`은 이전 배포에서 확인된 별도 증상이며 이번 정정의 근거가 됐다.
- 발생 환경: PR #228 head `build/asg-blue-green-provisioning`, Terraform AWS 운영 모듈, GitHub Actions `deployment_target=codedeploy`, EC2/On-Premises CodeDeploy.
- 재현 조건: user-data·AMI 변경으로 Redis EC2 교체, CodeDeploy가 polling 제한보다 오래 진행되거나 workflow 취소, 잘못된 subnet·기존 실행 산출물·wildcard CI role을 기본값으로 적용.
- 실제 결과: 상태 volume·배포 제어·network placement·rollback 산출물·IAM 범위가 코드와 운영 계약에 충분히 고정되지 않았다.
- 기대 결과: 상태는 인스턴스 교체와 독립적으로 보존되고, CI가 중단되면 CodeDeploy도 중지되며, plan·rollback·배포 role이 안전한 기본 경계를 보장해야 한다.
- 영향 범위: 관리자·회원 인증 상태와 rate-limit, 배포 중 운영 트래픽, public/private 네트워크 배치, 실패 후 재부팅, CI role의 임의 CodeDeploy 배포 가능성.

## 4. 근본 원인

1. Redis 데이터 경로를 인스턴스 root volume에 두면서 인스턴스 수명주기와 데이터 수명주기를 함께 모델링했다.
2. CI가 CodeDeploy의 비동기 deployment를 조회만 하고, timeout·cancel 시 원격 deployment를 중지하는 보상 동작이 없었다.
3. EC2/On-Premises CodeDeploy의 단일 target group instance 교체 방식과 일반적인 listener 기반 Blue-Green 서술을 같은 계약에 섞었다.
4. 입력 subnet의 VPC 소속만 확인하고 route table의 public/private 의미를 plan에서 검증하지 않았다.
5. rollback 백업 대상이 이미지 참조에 한정되어 실행 script·unit의 버전 불일치가 남을 수 있었다.
6. CI role의 CreateDeployment와 조회 권한을 한 statement에 묶고 wildcard를 포함했다.
7. 최초 Redis 교체에서 기존 root volume 데이터를 새 EBS로 옮기는 운영 전제와 절차를 기록하지 않았다.
8. rollback 함수가 원래 오류 코드와 복구 실패를 보존하지 않았다.
9. CodeDeploy 중지 보상 함수가 stop API 실패와 terminal 미확인을 성공으로 반환했다.
10. Nitro NVMe serial 형식과 Terraform volume ID 형식을 그대로 연결해 data EBS by-id 경로가 불일치했다.
11. GitHub Actions 취소 수명주기보다 긴 polling을 deploy job의 EXIT trap에만 의존했다.
12. Redis 8 multipart AOF를 Redis 6 이전의 단일 `appendonly.aof` 파일로 가정했다.
13. app subnet의 IGW route 검증만으로 public IPv4 할당과 private egress 경로까지 보장된다고 가정했다.
14. 활성 경로를 처음 변경하는 install 단계보다 rollback trap 등록이 늦었다.
15. Terraform은 ACM ARN이 없을 때 HTTP listener을 허용했지만, Nginx와 배포 스크립트는 TLS 인증서를 항상 요구했다.
16. Terraform `templatefile()`의 보간 문법과 셸 parameter expansion 문법을 구분하지 않았다.
17. 취소 시 종료되는 deploy job의 후속 artifact 업로드를 deployment ID의 유일한 보존 경로로 사용했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub GraphQL `reviewThreads`와 `isResolved`·`isOutdated` 조회 | 7건 모두 미해결·outdated 아님 | 모든 스레드를 표에 기록하고 반영 대상과 이미 해결된 대상을 분리 |
| 제공된 `fetch_comments.py` 실행 | Windows CP949로 한글 출력에서 실패 | UTF-8 모드로 재실행해 전체 thread·review 문맥 확보 |
| 관련 troubleshooting 인덱스와 ADR·README 검색 | 동일한 증상을 직접 기록한 문서 없음 | PR #228 전용 기록 생성 |
| 공식 AWS provider `aws_route_table` data source 확인 | route 목록 속성은 `routes`, 대상 속성은 `gateway_id` | postcondition을 `self.routes` 기준으로 작성 |
| `RuntimeDeploymentContractTest` 실행 | 통과 | 변경된 배포 계약의 정적 회귀 확인 |
| Git Bash `bash -n`으로 배포 script·Redis template 검사 | 통과 | 셸 구문 오류 없음 확인 |
| Docker Terraform 1.6.6 `fmt -check -recursive` 두 레이어 | 통과 | HCL 포맷 정합성 확인 |
| Terraform 1.6.6 `validate` 두 레이어 | 통과 | 선행 검증 기록(커밋 `5ac0298`)에서 실제 AWS 자격 증명으로 실행했다. terraform-redis의 중복 data source 선언을 이 단계에서 발견해 `data.tf`로 통합 |
| Terraform 1.6.6 `plan` | 초기 운영 레이어 실패 후 해결 | 초기 postcondition이 현재 public app subnet과 충돌했지만, `app_subnet_is_private` 변수로 의도를 선언하도록 정정한 뒤 현재 구성 plan은 통과했다. Redis는 데이터 volume 도입으로 인스턴스가 교체되며 교체 후 앱 재배포가 필요하다. 11절 참조 |
| CodeDeploy cancel/rollback 리허설·IAM policy simulator | 미실행: 승인된 운영 리허설 범위 밖 | 운영 전환 전에 담당자가 확인 |
| 후속 리뷰 3건 대조 | Redis migration 절차, rollback 오류 전파, stop 실패 전파를 코드·문서에 반영 | 관련 테스트와 셸 문법을 재실행하고 실제 운영 리허설은 별도 확인 |
| 최신 후속 리뷰 8건 대조 | NVMe serial 정규화, 취소 cleanup job, multipart AOF 검증, public IPv4·private NAT 검증, rollback trap 시점, ACM ARN 필수화, Terraform templatefile escape, S3 deployment ID 보존을 코드·문서에 반영 | 관련 테스트·셸 문법·Terraform fmt를 재실행하고 실제 AWS 리허설은 별도 확인 |

## 6. 최종 해결

- 변경 내용: Redis data EBS 분리·NVMe serial 정규화·multipart AOF 이전 검증, CodeDeploy 중단 보상·취소 cleanup job·실패 전파, 단일 target group 계약 정정과 green 자원 제거, public IPv4·private NAT route 검증, rollback 실행 산출물 backup/restore·trap 시점 보장과 오류 전파, ACM ARN 필수화, IAM statement 분리, 회귀 테스트와 문서 기록을 추가했다.
- 선택 이유: 운영 상태·배포 제어·계약 문서를 각각 소유 경계에 맞춰 최소 변경하고, 기존 단일 EC2 경로는 유지하기 위해서다.
- 변경 파일: `.github/workflows/ci.yml`, `deploy/scripts/app-deploy.sh`, `infra/production/terraform-redis/*`, `infra/production/terraform/*`, `infra/production/README.md`, `docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md`, `docs/08-planning/blue-green-cleanup-runbook.md`, `src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java`
- 고려한 대안: Redis snapshot 복구 절차만 추가하는 방법보다 별도 EBS 수명주기 분리가 교체 시 즉시 보존을 보장한다. listener rollback 계약을 유지하는 방법은 실제 Server 플랫폼 동작과 충돌하므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.deployment.RuntimeDeploymentContractTest` | 통과 | 배포·Terraform·Redis 정적 계약 회귀 테스트 |
| `C:\Program Files\Git\bin\bash.exe -n deploy/scripts/app-deploy.sh` | 통과 | rollback backup/restore 셸 문법 |
| `C:\Program Files\Git\bin\bash.exe -n infra/production/terraform-redis/templates/redis-user-data.sh.tftpl` | 통과 | EBS mount bootstrap 셸 문법 |
| `git diff --check` | 통과 | whitespace 오류 없음 |
| Ruby YAML parser로 `.github/workflows/ci.yml` 구문 확인 | 통과 | workflow YAML 구문 오류 없음 |
| `docker run hashicorp/terraform:1.6.6 ... fmt -check -recursive` | 통과 | 운영·Redis 두 Terraform 레이어의 포맷 |
| `terraform validate` | 통과 | 두 레이어. 선행 검증 기록(커밋 `5ac0298`)에서 terraform-redis의 중복 data source 선언을 잡았다 |
| 이번 변경 후 production `terraform validate` | 통과 | Docker Terraform 1.6.6에서 provider schema와 `network_interfaces`·NAT postcondition 구문을 확인했다. 실제 AWS API 조회는 수행하지 않았다 |
| 이번 변경 후 Redis `terraform validate` | 미실행 | 현재 환경에서 Redis provider 초기화가 실제 AWS 자격 증명 검증에서 중단됐다. 이번 Redis Terraform HCL 변경은 없고 user-data·README만 변경했다 |
| `terraform plan` | 통과(현재 구성) | Redis 레이어는 통과(3 add·1 change·1 destroy, 인스턴스 교체). 운영 레이어는 `app_subnet_is_private = false`와 public app subnet이 일치하며, 미사용 green target group·alarm 2건만 destroy 대상으로 확인됐다 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: `RuntimeDeploymentContractTest`에 CodeDeploy 중단, IAM 권한, route 조건, 단일 target group, Redis data volume, rollback 산출물 계약을 추가했다.
- 다음 확인: 실제 AWS에서 CodeDeploy timeout/cancel·replacement failure·Redis 기존 데이터 이전·Redis instance replacement·IAM policy simulator를 운영 전환 전에 담당자 승인으로 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| Redis 상태 보존 | 정량 기준 미측정; root volume 교체 시 삭제 가능 | 인스턴스 교체 전후 AOF·Refresh Token·rate-limit key 존재를 같은 fixture로 확인 | 미측정 | 로컬에서 AWS 상태 확인 불가 | 배포 담당자, 운영 전환 전 리허설 |
| CodeDeploy 중단 회수 시간 | 중단 보상 없음 | timeout/cancel부터 terminal 상태까지 측정 | 미측정 | 로컬에서 AWS 상태 확인 불가 | 배포 담당자, 첫 replacement 배포 |
| IAM 허용 범위 | CreateDeployment statement에 wildcard 포함 | IAM policy simulator에서 타 application/group 허용 여부 확인 | 미측정 | 정적 policy 분리는 완료, simulator 비교는 미실행 | 보안 담당자, 머지 전 |

## 10. 남은 사항

- 저장소의 선행 검증 기록(커밋 `5ac0298`)에 실제 AWS 자격 증명으로 두 레이어 `validate` 통과가 남아 있다. 현재 작업 환경에서는 실제 자격 증명 없이 provider 초기화를 재현할 수 없었다.
- AWS 계정 리허설과 IAM policy simulator는 이 작업에서 실행하지 않았으므로 운영 전환 전 확인이 필요하다.
- RDS ingress 예시 스레드는 최신 HEAD에 이미 `manage_rds_ingress_rule = true`가 있어 코드 변경 없이 “이미 해결”로 답변한다.
- 후속 P1 스레드 3건은 코드·운영 문서에 반영했지만 실제 데이터 이전·cancel·rollback 리허설은 미실행이다.
- 최신 후속 8건은 코드·운영 문서에 반영했지만 실제 AWS 취소 cleanup·Redis 복구·Terraform apply 리허설은 미실행이다.

## 11. 해결된 계약 충돌: app subnet의 IGW 경로

초기 구현의 `data "aws_route_table" "app"` postcondition은 app subnet에 IGW 기본 경로가 없을 것을 고정적으로 요구했다. 당시 `app_subnet_ids`가 public subnet 2개라서 운영 레이어 `terraform plan`이 다음 조건에서 실패했다.

```text
Error: Resource postcondition failed
  on data.tf line 50, in data "aws_route_table" "app"
app_subnet_ids의 route table에는 IGW를 향한 0.0.0.0/0 경로가 없어야 한다.
```

두 근거가 서로 반대 방향을 요구한다.

| 근거 | 요구 |
|---|---|
| 리뷰 지적 | app subnet에 IGW 기본 경로가 없어야 한다. 있으면 app이 의도와 다르게 public에 놓인다 |
| [영향 검토 6.6절](../08-planning/deployment-hardening-impact-review.md) | 앱 인스턴스는 public subnet에 남기고 보안 그룹 인바운드만 ALB 출처로 좁힌다. 사설 배치는 NAT 또는 인터페이스 엔드포인트를 요구해 [8.1절](../08-planning/deployment-hardening-impact-review.md) 판정에서 예산을 초과한다 |

즉 초기 postcondition은 "app은 사설"을 불변식으로 굳혔고, 실제 채택된 토폴로지는 "app은 공용 + 보안 그룹 경계"였다. 한쪽을 임의로 고칠 수 없었다.

선택지는 셋이다.

1. app을 사설 subnet으로 옮기고 NAT를 추가한다. 리뷰 의도에 정확히 맞지만 8.1절이 예산 초과로 판정한 구성이다
2. postcondition을 6.6절 전제에 맞게 바꾼다. 예산 산정은 유지되지만 리뷰가 막으려던 오배치 검증이 약해진다
3. 배치 의도를 변수로 명시하고 그 값에 따라 postcondition을 반대로 적용한다. 오배치 검증을 유지하면서 6.6절 구성을 허용하지만 변수 하나가 늘어난다

**3번으로 결정해 반영했다.** `app_subnet_is_private` 변수를 도입해 배치 의도를 tfvars에 선언하고, postcondition이 선언과 실제 route를 대조한다. 현재 운영은 6.6절 근거로 `false`이며 앱은 public subnet에 있고 인터넷 경계는 security group이 ALB 출처 `443`만 허용하는 것으로 지킨다.

방향을 코드에 굳히지 않았으므로 오배치 검증은 양쪽으로 살아 있다.

| 시나리오 | plan 결과 |
|---|---|
| `false` + public subnet (현재 구성) | 통과 |
| `true` + public subnet | 실패 — postcondition |
| ALB에 private subnet 지정 | 실패 — 해당 subnet에 명시적 route table 연결이 없어 data source 조회가 비어 있다 |

마지막 항목은 실패하기는 하지만 `query returned no results`로 나와 의도가 드러나지 않는다. 메시지 개선은 후속 과제로 남긴다.

정정 후 운영 레이어 `plan`은 통과하며 destroy 대상은 미사용 green target group과 alarm 2건뿐이다. 현재 작업 환경에서는 실제 AWS 자격 증명이 없어 이 plan을 재실행하지 못했으며, 해당 결과는 최신 저장소 검증 기록과 PR 답글에 남겼다.

## 12. 최신 후속 리뷰 해결: NVMe·취소 cleanup·multipart AOF·egress·templatefile·ID 보존

1. **Redis NVMe 경로**: AWS Nitro의 EBS serial은 `vol-` 뒤 하이픈이 제거된다. user-data template에서 `$${DATA_VOLUME_ID//-/}`와 `$${DATA_VOLUME_SERIAL}`로 escape해 Terraform 렌더링 후 셸 parameter expansion이 남도록 수정했다. 장치명이 바뀌어도 volume ID 기반 식별을 유지한다.
2. **GitHub 취소 cleanup**: production `workflow_dispatch`와 push 배포는 자동 취소하지 않도록 concurrency를 PR에만 적용했다. CodeDeploy deployment ID를 생성 직후 같은 배포 step에서 S3의 실행별 고정 키에 보관하고, deploy job이 취소되면 별도 cleanup job이 해당 키를 직접 읽어 stop 요청과 24회 terminal polling을 수행한다. 5분 취소 강제 종료 한도 안에 끝나도록 cleanup polling은 4분으로 제한했다.
3. **Redis 8 multipart AOF**: 최초 이전 절차에서 단일 `appendonly.aof` 검사를 제거하고 `appendonly.aof.manifest`, multipart AOF 파일, 동일 Redis digest의 `redis-check-aof` 결과를 확인하도록 바꿨다. Redis 재기동 뒤에는 운영 비밀값을 노출하지 않고 known fixture key의 `EXISTS` 결과를 확인한다.
4. **public/private egress**: public app 모드에서는 launch template의 `network_interfaces.associate_public_ip_address`를 `!var.app_subnet_is_private`로 명시한다. private 모드는 현재 모듈이 NAT egress를 지원 경로로 삼고 `0.0.0.0/0 -> nat-*` route를 postcondition으로 요구한다. endpoint-only private 토폴로지는 별도 서비스·subnet 연결 계약 없이는 허용하지 않는다.
5. **rollback 보호 시점**: 이전 이미지·스크립트·unit backup이 끝난 직후 `rollback_enabled=yes`와 `trap rollback ERR`를 등록한다. 따라서 첫 활성 `install`·`daemon-reload`·`enable` 실패도 이전 산출물 복구 경로로 들어간다.
6. **ACM 계약**: HTTP-only fallback을 제거하고 `acm_certificate_arn`을 nullable false의 필수 변수로 바꿨다. 유효한 ACM ARN 형식이 아니면 Terraform variable validation에서 멈추므로, ALB HTTPS listener·Nginx 인증서 export·배포 흐름이 같은 필수 전제를 공유한다.
7. **templatefile 회귀 방지**: `RuntimeDeploymentContractTest`가 Terraform template에 `$${...}` escape가 있고 과거의 unescaped 셸 보간식이 없는지 확인해, Terraform plan 단계에서 template parsing이 깨지는 회귀를 차단한다.
8. **취소 ID 회귀 방지**: artifact 후속 step에 의존하지 않고 S3 pointer를 같은 실행 step에서 먼저 기록하도록 해, workflow 취소로 후속 step이 생략되어도 cleanup job이 deployment ID를 조회할 수 있게 했다.
