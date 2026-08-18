---
related_documents:
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../infra/production/README.md
---

# CodeDeploy Blue-Green 유휴 환경 정리 절차

CodeDeploy `COPY_AUTO_SCALING_GROUP`은 Green ASG를 Terraform state 밖에서 만든다. 배포 성공 직후에는 Blue를 즉시 복귀할 수 있도록 두 환경을 유지한다. 관찰 기간과 외부 health·오류율 확인이 끝난 뒤에만 유휴 환경을 정리한다.

## 확인

```powershell
aws deploy get-deployment --deployment-id <deployment-id>
aws elbv2 describe-listeners --listener-arns <listener-arn>
aws elbv2 describe-target-health --target-group-arn <blue-or-green-target-group-arn>
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names <candidate-asg-name>
```

현재 listener가 가리키는 target group과 CodeDeploy deployment의 replacement environment를 대조한다. 현재 트래픽 환경이 아닌 ASG만 정리 대상으로 삼고, 운영 오류율·의존성 health·로그를 관찰 기간 동안 확인한다.

## 정리

```powershell
aws autoscaling delete-auto-scaling-group `
  --auto-scaling-group-name <idle-green-asg-name> --force-delete
```

정리 후 target group에 남은 등록 인스턴스와 CloudWatch alarm 상태를 확인한다. 삭제 전에는 반드시 Terraform이 관리하는 `${name_prefix}-blue-asg`를 후보에서 제외한다.

이 절차는 자동화하지 않는다. 삭제 대상 색상 판정과 관찰 종료는 운영자가 listener·배포 ID·target health를 함께 확인해야 한다.
