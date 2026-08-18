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
| 범위 | 최초 리뷰 7건과 후속 P1 리뷰 3건, 최신 HEAD에서 이미 반영된 RDS ingress 예시 1건 |
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
| Terraform 1.6.6 `validate` 두 레이어 | 통과 | 실제 AWS 자격 증명으로 실행. terraform-redis의 중복 data source 선언을 이 단계에서 발견해 `data.tf`로 통합 |
| Terraform 1.6.6 `plan` | Redis 레이어 통과, **운영 레이어 실패** | Redis는 데이터 volume 도입으로 인스턴스가 교체되며 교체 후 앱 재배포가 필요하다. 운영 레이어는 app subnet postcondition이 현재 구성과 충돌한다. 11절 참조 |
| CodeDeploy cancel/rollback 리허설·IAM policy simulator | 미실행: 승인된 운영 리허설 범위 밖 | 운영 전환 전에 담당자가 확인 |
| 후속 리뷰 3건 대조 | Redis migration 절차, rollback 오류 전파, stop 실패 전파를 코드·문서에 반영 | 관련 테스트와 셸 문법을 재실행하고 실제 운영 리허설은 별도 확인 |

## 6. 최종 해결

- 변경 내용: Redis data EBS 분리와 최초 데이터 이전 절차, CodeDeploy 중단 보상과 실패 전파, 단일 target group 계약 정정과 green 자원 제거, route postcondition, rollback 실행 산출물 backup/restore와 오류 전파, IAM statement 분리, 회귀 테스트와 문서 기록을 추가했다.
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
| `docker run hashicorp/terraform:1.6.6 ... fmt -check -recursive` | 통과 | 운영·Redis 두 Terraform 레이어의 포맷 |
| `terraform validate` | 통과 | 두 레이어. terraform-redis의 중복 data source 선언을 여기서 잡았다 |
| `terraform plan` | **운영 레이어 실패** | Redis 레이어는 통과(3 add·1 change·1 destroy, 인스턴스 교체). 운영 레이어는 app subnet postcondition이 실패한다. 아래 11절 참조 |

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

## 11. 해결된 계약 충돌: app subnet의 IGW 경로

`data "aws_route_table" "app"`의 postcondition이 app subnet에 IGW 기본 경로가 없을 것을 요구하는데, 현재 `app_subnet_ids`는 public subnet 2개다. `terraform plan`이 이 조건에서 실패한다.

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

즉 postcondition은 "app은 사설"을 불변식으로 굳혔고, 실제 채택된 토폴로지는 "app은 공용 + 보안 그룹 경계"다. 한쪽을 임의로 고칠 수 없다.

선택지는 셋이다.

1. app을 사설 subnet으로 옮기고 NAT를 추가한다. 리뷰 의도에 정확히 맞지만 8.1절이 예산 초과로 판정한 구성이다
2. postcondition을 6.6절 전제에 맞게 바꾼다. 예산 산정은 유지되지만 리뷰가 막으려던 오배치 검증이 약해진다
3. 배치 의도를 변수로 명시하고 그 값에 따라 postcondition을 반대로 적용한다. 오배치 검증을 유지하면서 6.6절 구성을 허용하지만 변수 하나가 늘어난다

**3번으로 결정했다.** `app_subnet_is_private` 변수를 도입해 배치 의도를 tfvars에 선언하고, postcondition이 선언과 실제 route를 대조한다. 현재 운영은 6.6절 근거로 `false`이며 앱은 public subnet에 있고 인터넷 경계는 security group이 ALB 출처 `443`만 허용하는 것으로 지킨다.

방향을 코드에 굳히지 않았으므로 오배치 검증은 양쪽으로 살아 있다.

| 시나리오 | plan 결과 |
|---|---|
| `false` + public subnet (현재 구성) | 통과 |
| `true` + public subnet | 실패 — postcondition |
| ALB에 private subnet 지정 | 실패 — 해당 subnet에 명시적 route table 연결이 없어 data source 조회가 비어 있다 |

마지막 항목은 실패하기는 하지만 `query returned no results`로 나와 의도가 드러나지 않는다. 메시지 개선은 후속 과제로 남긴다.

운영 레이어 `plan`은 통과하며 destroy 대상은 미사용 green target group과 alarm 2건뿐이다.
