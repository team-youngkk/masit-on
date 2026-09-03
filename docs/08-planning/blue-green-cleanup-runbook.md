---
related_documents:
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../infra/production/README.md
---

# CodeDeploy Blue-Green 유휴 환경 정리 절차

CodeDeploy `COPY_AUTO_SCALING_GROUP`은 Terraform state 밖에서 replacement ASG를 만든다. EC2/On-Premises Blue-Green은 하나의 target group에 replacement 인스턴스를 등록하고 original 인스턴스를 해제하므로 listener를 전환하지 않는다.

**성공한 배포는 이 절차의 대상이 아니다.** deployment group이 `TERMINATE`이고 `codedeploy_deployment_wait_minutes`(기본 15분)가 지나면 CodeDeploy가 original 인스턴스와 그 ASG를 스스로 정리한다.

이 문서는 **자동 정리가 일어나지 않는 경우**를 운영자가 정리하는 절차다. 다음 두 가지가 해당한다.

- 실패하거나 중단된 배포가 남긴 replacement 환경. 트래픽 전환 전에 실패하면 자동 rollback이 일어나도 그 환경이 남을 수 있다.
- `KEEP_ALIVE`로 운영하던 시기에 누적된 환경.

최초 전환에서는 `codedeploy_termination_enabled=false`로 Terraform apply와 known-good 배포를 먼저 수행한다. 아래 명령으로 deployment group의 `autoscaling_groups`가 Terraform seed ASG가 아닌 replacement ASG를 가리키고, target health가 정상인지 확인한 뒤에만 이 변수를 `true`로 바꿔 별도 plan/apply한다. seed ASG가 원본으로 남아 있는 상태에서 `TERMINATE`를 활성화하면 CodeDeploy가 Terraform state 소유 ASG를 삭제할 수 있다.

```powershell
aws deploy get-deployment-group `
  --application-name <application-name> `
  --deployment-group-name <deployment-group-name> `
  --query 'deploymentGroupInfo.autoScalingGroups'
```

GitHub Actions workflow가 취소된 경우의 CodeDeploy 중단·terminal 상태 확인은 `.github/workflows/ci.yml`의 `codedeploy-cancel-cleanup` job이 실행별 S3 deployment ID pointer를 사용해 자동으로 처리하므로 이 runbook의 수동 정리 대상과 혼동하지 않는다.

## 최초 seeding 후 Terraform seed 축소

첫 배포가 성공하고 deployment group이 replacement ASG를 가리키며 replacement의 target health가 정상인 것을 확인한 뒤, Terraform이 소유한 seed ASG는 삭제하지 않고 0대로 축소한다. `aws_autoscaling_group.blue`에는 `ignore_changes = [desired_capacity]`가 있으므로 `blue_desired_capacity` 값을 tfvars에서 바꾸는 것만으로 이미 실행 중인 seed ASG의 desired capacity가 내려가지 않는다.

```powershell
$seedAsgName = "<name-prefix>-blue-asg"

aws autoscaling update-auto-scaling-group `
  --auto-scaling-group-name $seedAsgName `
  --min-size 0 `
  --desired-capacity 0

aws autoscaling describe-auto-scaling-groups `
  --auto-scaling-group-names $seedAsgName `
  --query 'AutoScalingGroups[0].{MinSize:MinSize,DesiredCapacity:DesiredCapacity,Instances:Instances[*].InstanceId}'
```

위 결과의 `DesiredCapacity`와 `Instances`가 0인지 확인한 뒤 [확인](#확인)의 target health 명령을 다시 실행한다. healthy target instance가 seed ASG에 남아 있지 않고 replacement ASG에만 속하는지 `describe-auto-scaling-instances`로 대조한 다음에만 `codedeploy_termination_enabled=true`를 별도 plan/apply한다. seed ASG 자체는 Terraform state와 target group 연결을 유지하므로 삭제하지 않는다.

## 확인

```powershell
aws deploy get-deployment --deployment-id <deployment-id>
aws deploy list-deployment-instances --deployment-id <deployment-id>
aws elbv2 describe-target-health --target-group-arn <target-group-arn>
aws autoscaling describe-auto-scaling-instances --instance-ids <target-instance-id> <target-instance-id>
```

`describe-target-health`에서 healthy인 target instance ID를 확인하고, 같은 ID를 `describe-auto-scaling-instances`에 넣어 `AutoScalingGroupName`을 확인한다. 그 ASG가 CodeDeploy의 replacement 환경인지 `list-deployment-instances` 결과와 대조한다. 하나의 target group에 등록된 healthy instance와 ASG membership이 일치하는 환경을 활성 환경으로 판정하고, 현재 트래픽 환경이 아닌 ASG만 정리 대상으로 삼는다.

## 정리

```powershell
aws autoscaling delete-auto-scaling-group `
  --auto-scaling-group-name <idle-replacement-asg-name> --force-delete
```

정리 후 target group에 남은 등록 인스턴스와 deployment 상태를 확인한다. 삭제 전에는 반드시 Terraform이 관리하는 `${name_prefix}-blue-asg`와 현재 healthy target instance가 속한 ASG를 후보에서 제외한다. **직전 배포의 original 환경도 대기 시간이 지나기 전에는 지우지 않는다.** 그 시간 동안은 rollback 대상이다.

이 절차는 자동화하지 않는다. 삭제 대상 판정과 관찰 종료는 운영자가 deployment ID·target instance ID·ASG membership·target health를 함께 확인해야 한다.

