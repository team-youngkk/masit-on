---
status: In progress
started_date: 2026-08-19
owners:
  - 이우람
related_documents:
  - deployment-hardening-impact-review.md
  - blue-green-cleanup-runbook.md
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../../infra/production/README.md
---

# 배포 고도화 전환 기록

## 1. 문서 목적

[배포 고도화 비용·일정 영향 검토](deployment-hardening-impact-review.md) 8.2절이 나눈 세 단계를 실제 AWS에 적용한 결과를 남긴다. 검토 문서가 "무엇을 왜 하는가"라면 이 문서는 "무엇이 실제로 일어났는가"이며, 특히 **리허설에서 드러나지 않고 CI 경로에서만 드러난 결함 2건**을 기록한다.

이 저장소는 공개되어 있다. 보안 그룹 ID, EC2 인스턴스 ID, 사설 IP, hosted zone ID는 `<...>` 자리표시자로 마스킹했다. 실제 값은 AWS 콘솔에서 확인한다.

## 2. 전환 시점의 구성

| 자원 | 값 |
|---|---|
| 배포 리비전 | `7deaf23` (`v0.7.0`, `main`) |
| 성공한 CodeDeploy 배포 | `d-HBU3LWSBK`, 2026-08-19 18:10:59 KST |
| 활성 인스턴스 | `<blue-instance-id>` (t4g.small), ALB target group `masiton-prod-blue`에 healthy |
| 전용 Redis | `<redis-instance-id>` (t4g.nano), 사설 서브넷, 데이터 EBS volume 연결 |
| 기존 단일 인스턴스 | `<legacy-app-instance-id>` (t4g.medium), 트래픽 없음. rollback 경로로 유지 |

## 3. 실행 순서와 결과

### 3.1. 1단계 — Redis 분리

전용 Redis 인스턴스와 데이터 volume은 2026-08-18에 생성돼 있었다. 다만 **애플리케이션이 읽는 endpoint가 함께 갱신되지 않았다.** 4.2절에 원인을 남긴다.

### 3.2. 2단계 — ALB 도입

ALB는 HTTPS 443(ACM 인증서)과 HTTP 80(301 승격) listener를 갖췄다. DNS 전환 전에 `--resolve`로 도메인만 ALB IP로 강제해 두 AZ 모두에서 검증했다.

| 검증 | 결과 |
|---|---|
| `GET /` | 302 → `/verification/login#returnTo=/`, TLS 검증 통과 |
| `GET /api/restaurants?page=1&size=10` | 200 |
| `GET /api/creators` | 200 |
| `http://` 접근 | 301 → HTTPS |

같은 요청을 기존 medium 인스턴스에도 보내 응답이 동일한 것을 확인한 뒤 전환했다.

### 3.3. 3단계 — Blue-Green과 DNS 전환

CI `workflow_dispatch`의 `deployment_target=codedeploy` 경로로 배포를 실행했다. 이 경로는 이 배포가 **최초 실행**이다. 그 이전 성공 사례(`d-G97LKY0BK`, 2026-08-18)는 로컬 admin 자격증명과 미커밋 트리로 수행한 리허설이라 CI의 OIDC role 권한과 커밋된 리비전을 검증하지 못했다.

DNS는 [route53.tf](../../infra/production/terraform/route53.tf)의 `initial_blue_verified`를 `true`로 올려 alias record로 전환했다. 기존 apex A record는 Terraform 관리 밖에 있었으므로 `terraform import`로 state에 넣고 in-place 변경으로 처리했다. `allow_overwrite`를 도입하지 않은 이유는 그 속성이 이후 모든 apply에서 무단 덮어쓰기를 허용하기 때문이다.

전환 후 실제 도메인 검증 결과는 3.2절 표와 같고, 접속 IP가 ALB로 바뀐 것을 확인했다.

## 4. 리허설에서 드러나지 않은 결함

두 결함 모두 **로컬 admin 자격증명과 동거 Redis 구성에서는 재현되지 않는다.** 리허설의 검증 범위가 실제 경로보다 좁았다는 것이 공통 원인이다.

### 4.1. 배포 역할에 revision 등록 권한이 없었다

CI 첫 실행이 `CreateDeployment`에서 실패했다.

```
AccessDeniedException: not authorized to perform:
codedeploy:RegisterApplicationRevision on resource: ...application:masiton-prod-codedeploy
```

`CreateDeployment`에 revision을 실어 보내면 CodeDeploy가 그 revision을 application에 먼저 등록한다. 등록·조회 권한의 리소스 타입은 deployment group이 아니라 **application**이라, deployment group ARN으로 좁힌 기존 statement가 덮지 못했다. 리허설은 admin 자격증명이라 이 경계를 통과했다.

조치는 [iam.tf](../../infra/production/terraform/iam.tf)에 application ARN 범위의 `codedeploy:RegisterApplicationRevision`·`codedeploy:GetApplicationRevision` statement를 추가한 것이다.

### 4.2. `/masiton/redis/host`가 `127.0.0.1`로 남아 있었다

권한 수정 후 배포는 `AfterInstall` hook에서 실패했다. 인스턴스의 dependency health가 `redis: DOWN`이었고 백엔드 로그는 `Unable to connect to 127.0.0.1/<unresolved>:6379`였다.

전용 Redis 인스턴스는 만들어졌지만 그 사설 IP를 애플리케이션에 알리는 `/masiton/redis/host` 파라미터가 동거 Redis 시절 값 그대로였다. 새 ASG 인스턴스는 로컬 Redis를 설치하지 않으므로 붙을 상대가 없다.

**`REQUIRE_SHARED_REDIS=true` 가드는 이 상황을 막지 못한다.** 이 가드는 값이 *비어 있을 때만* 기동을 실패시키는데, 값이 있긴 했기 때문이다. 값이 로컬 루프백을 가리키는 경우는 검사 대상이 아니었다.

조치는 두 가지다.

- `terraform-redis` 레이어를 apply해 `aws_ssm_parameter.redis_host`가 실제 사설 IP를 갖도록 했다. 이 파라미터는 원래 Terraform이 소유(`manage_host_parameter` 기본값 `true`)하고 있었으므로 코드 변경 없이 apply만으로 교정됐다.
- Redis 보안 그룹의 6379 ingress에 **기존 medium 인스턴스의 보안 그룹을 함께 추가**했다. 파라미터가 공유 자원이라 medium도 다음 재기동 때 전용 Redis로 붙게 되는데, ingress에 신규 ASG SG만 있으면 그 시점에 rollback 경로가 깨진다.

### 4.3. 배포 알람 게이트의 순환 의존

`fleet_dependency_redis` 알람은 `treat_missing_data = "breaching"`이고, 이 알람이 감시하는 `FleetDependencyRedis`(`Environment=asg`) 지표는 배포 리비전에 포함된 `health-metrics.sh`가 올린다. 즉 **지표를 올려줄 리비전을 배포하려는데 그 지표가 없다고 배포가 막히는** 순환이 성립한다.

[codedeploy.tf](../../infra/production/terraform/codedeploy.tf)가 이 상황을 위해 `deployment_alarms_enabled`를 두고 있다. 한 사이클만 `-var deployment_alarms_enabled=false`로 완화해 배포하고, 지표 발생과 알람 `OK` 전환을 확인한 뒤 곧바로 기본값 `true`로 되돌렸다. `terraform.tfvars`는 수정하지 않았다.

## 5. 유휴 환경 정리

[Blue-Green 유휴 환경 정리 절차](blue-green-cleanup-runbook.md)에 따라 배포 성공 후 유휴 ASG 2개를 정리했다. 정리 대상은 어제 리허설이 만든 환경과 4.2절에서 실패한 배포가 만든 환경이다.

판정 근거는 runbook이 요구하는 세 가지를 모두 대조했다.

- target group에 healthy로 등록된 인스턴스가 `<blue-instance-id>` 하나뿐이다
- 그 인스턴스의 ASG membership이 `CodeDeploy_masiton-prod-deployment-group_d-HBU3LWSBK`다
- deployment group이 다음 배포의 복사 원본으로 참조하는 ASG도 같다

Terraform이 소유한 `masiton-prod-blue-asg`(desired 0)는 후보에서 제외했다. 정리 후 target group 등록 상태와 알람 4종이 모두 그대로인 것을 확인했다.

## 6. 남은 작업

| 항목 | 내용 |
|---|---|
| 기존 medium 정리 | 동거 Redis 종료, `masiton-tls-renew.timer` 해제, 인스턴스 종료. 절감은 여기서 실현된다. **수행 즉시 rollback 경로가 사라지므로 관찰 기간 뒤에 판단한다** |
| 유휴 환경 자동 정리 | `terminate_blue_instances_on_deployment_success.action`을 `TERMINATE`로 바꾸면 배포 성공 후 자동 종료된다. 다만 대기 시간이 rollback 가능 시간의 상한이 되고, [ADR-DEPLOY-005](../07-adr/platform/deploy-005-asg-blue-green-rollout.md)가 "관찰 기간 유지 + runbook 정리"를 명시하므로 ADR·runbook 개정과 같은 PR에서 판단한다 |
| `REQUIRE_SHARED_REDIS` 가드 보완 | 4.2절 참조. 값이 로컬 루프백일 때도 실패시킬지 결정한다 |
| 문서 계약 | 마이그레이션 규칙 문서화, ADR-DATA-005 6절 합의, ADR-DEPLOY-005 Accepted 전환 |

## 7. 이 기록이 확인하지 않은 것

- **비용 실측.** 인스턴스 하향과 Redis 분리의 실제 청구액은 다음 청구 주기에 확인한다. [비용·일정 영향 검토](deployment-hardening-impact-review.md) 5절의 산정은 공개 단가 기준이다.
- **t4g.small의 실사용 메모리와 CPU 크레딧 거동.** 같은 검토 4.1절이 추정으로 남긴 항목이며 이번 전환에서 실측하지 않았다.
- **전환 후 장시간 관찰.** 이 문서는 전환 직후 시점의 기록이다.
