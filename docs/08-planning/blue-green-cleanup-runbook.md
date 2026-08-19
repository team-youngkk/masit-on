---
related_documents:
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../infra/production/README.md
---

# CodeDeploy Blue-Green 유휴 환경 정리 절차

CodeDeploy `COPY_AUTO_SCALING_GROUP`은 Terraform state 밖에서 replacement ASG를 만든다. EC2/On-Premises Blue-Green은 하나의 target group에 replacement 인스턴스를 등록하고 original 인스턴스를 해제하므로 listener를 전환하지 않는다.

**성공한 배포는 이 절차의 대상이 아니다.** deployment group이 `TERMINATE`이므로 CodeDeploy가 관찰 대기 시간(`codedeploy_deployment_wait_minutes`, 기본 15분)이 지나면 original 인스턴스와 그 ASG를 스스로 정리한다.

이 문서는 **자동 정리가 일어나지 않는 경우**를 운영자가 정리하는 절차다. 다음 두 가지가 해당한다.

- 실패하거나 중단된 배포가 남긴 replacement 환경. 트래픽 전환 전에 실패하면 자동 rollback이 일어나도 그 환경이 남을 수 있다.
- `KEEP_ALIVE`로 운영하던 시기에 누적된 환경.

GitHub Actions workflow가 취소된 경우의 CodeDeploy 중단·terminal 상태 확인은 `.github/workflows/ci.yml`의 `codedeploy-cancel-cleanup` job이 실행별 S3 deployment ID pointer를 사용해 자동으로 처리하므로 이 runbook의 수동 정리 대상과 혼동하지 않는다.

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

정리 후 target group에 남은 등록 인스턴스와 CloudWatch alarm 상태를 확인한다. 삭제 전에는 반드시 Terraform이 관리하는 `${name_prefix}-blue-asg`와 현재 healthy target instance가 속한 ASG를 후보에서 제외한다. **직전 배포의 original 환경도 대기 시간이 지나기 전에는 지우지 않는다.** 그 시간 동안은 rollback 대상이다.

이 절차는 자동화하지 않는다. 삭제 대상 판정과 관찰 종료는 운영자가 deployment ID·target instance ID·ASG membership·target health를 함께 확인해야 한다.
