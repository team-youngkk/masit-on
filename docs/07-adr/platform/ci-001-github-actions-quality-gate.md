---
id: ADR-CI-001
title: GitHub Actions 빌드·테스트 품질 게이트
status: Accepted
decision_date: 2026-07-27
owners:
  - 이우람
related_requirements:
  - NFR-TEST-003
  - NFR-DEPLOYMENT-001
  - NFR-DEPLOYMENT-002
related_documents:
  - ../../01-requirements/non-functional-requirements.md
  - ../../03-team/ownership.md
  - build-001-gradle-groovy.md
  - runtime-001-docker.md
  - ../quality/test-001-automation-strategy.md
  - ../security/sec-001-secrets-workload-identity.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../00-overview/scope.md
  - ../../06-architecture/technology-policy.md
  - deploy-002-validation-deployment-before-expansion.md
  - ../adr-backlog.md
  - ../../03-team/roles.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-CI-001 GitHub Actions 빌드·테스트 품질 게이트

## 1. 상태

Accepted

## 2. 결정 요약

GitHub Actions에서 고정 런타임으로 빌드·자동 테스트와 프로덕션 의존성 감사를 실행하고 실패한 변경을 단계 완료 후보와 최종 운영 배포 후보에서 차단한다. `workflow_dispatch`로 기존 이미지를 배포할 때도 입력한 이미지 커밋의 프론트엔드 의존성 감사를 별도 실행한다.

## 3. 배경

모든 Workstream 변경이 같은 재현 빌드와 테스트 기준을 통과해야 한다.

4명(이우람·양성훈·박진영·김인안)이 [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)를 독립적으로 개발하며([scope.md](../../00-overview/scope.md) 범위 경계 규칙 6), 각자의 변경이 다른 Workstream을 깨뜨리지 않는지 사람이 매번 수동으로 확인할 인원과 일정 여유는 없다. [ADR-DEPLOY-002](deploy-002-validation-deployment-before-expansion.md)에 따라 빌드·테스트 품질 게이트는 전 단계에 적용하고, 이미지 생성·ECR push·EC2 배포는 M2부터 활성화한다.

## 4. 결정 문제

배포 후보의 빌드·테스트 품질 게이트를 어디서 일관되게 실행할 것인가.

## 5. 고려한 선택지

- GitHub Actions(저장소 내장 CI, OIDC로 AWS 단기 자격 증명 사용)
- 개발자 로컬 확인만 사용(PR 전 각자 로컬에서 빌드·테스트 실행, 별도 CI 없음)
- 다른 CI 서비스(예: Jenkins 자가 호스팅, 외부 SaaS CI)

각 대안이 이 프로젝트에 맞지 않는 이유는 다음과 같다.

- 로컬 확인만 사용: 4명이 각자 독립적으로 작업하는 구조([scope.md](../../00-overview/scope.md) 규칙 6)에서 "내 로컬에서는 통과했다"는 확인이 merge 시점에도 유효한지 보장할 수 없다. 개인별 로컬 환경·Node·JDK 버전 차이나 테스트 실행을 건너뛰는 실수를 사람이 매번 잡아내야 하는데, 팀원별 기술 숙련도가 확인되지 않은 상태([roles.md](../../03-team/roles.md))에서 이를 각자의 자율에 맡기는 것은 통합 리스크가 크다.
- Jenkins 등 자가 호스팅 CI: 서버 운영·패치·계정 관리 비용이 추가로 들고, 이는 초기 월 인프라 예산 15만 원([adr-traceability.md](../adr-traceability.md)) 안에서 우선순위가 낮다.

## 6. 결정

GitHub Actions를 CI 기준으로 사용한다. 모든 단계에서 빌드·자동 테스트를 필수로 하고, 프론트엔드 프로덕션 의존성은 `npm audit --omit=dev --audit-level=high`를 통과해야 한다. `workflow_dispatch` 배포는 선택한 이미지 커밋을 checkout한 dispatch 전용 감사 job이 성공해야 하며, M2부터 AWS 접근이 필요한 단계는 OIDC를 사용한다.

## 7. 선택 근거

GitHub Actions는 저장소에 내장되어 있어 별도 서버 운영 비용 없이 단계별 동일 품질 기준을 제공한다. M2부터는 OIDC로 AWS 자격 증명을 단기 발급받아 장기 키를 저장소에 두지 않고, 이미지 생성·ECR push를 자동화하며 실제 운영 반영은 수동 승인으로 보호한다.

## 8. 트레이드오프

GitHub Actions를 CI 기준으로 삼으면 자체 서버를 운영하지 않아도 되는 대신, 워크플로 실행 시간과 캐시 효율, GitHub Actions 자체의 가용성에 의존하게 된다. 운영 배포 파이프라인은 M2 공개 전에 별도 리허설이 필요하다.

## 9. 적용 범위

백엔드(Spring Boot, JDK 21.0.12)·프론트엔드(Node.js 24.18.0, Next.js) 빌드와 단위·통합·계약 테스트에 MVP부터 적용한다. 컨테이너 이미지 생성·ECR push·EC2 배포 검증은 M2부터 적용한다.

## 10. 강제 규칙

고정 런타임(JDK 21.0.12, Node 24.18.0)·Wrapper(Gradle 8.14.3)·잠금 파일을 사용하고, 단위·통합·계약 테스트 중 하나라도 실패하면 해당 커밋을 단계 완료 후보에서 제외한다. 프론트엔드 프로덕션 감사가 실패하거나 실행 결과가 확정되지 않은 커밋은 운영 배포 후보에서 제외한다. `workflow_dispatch`는 입력한 `image_tag` 또는 해당 경로의 `github.sha`를 checkout해 감사하며, 이 job이 성공하지 않으면 배포 job이 실행되지 않는다. M2부터 AWS 접근 단계는 GitHub Actions OIDC로 발급된 단기 자격 증명만 사용한다.

## 11. 금지 사항

실패 무시(스킵·재시도 남용 포함), 운영 비밀·장기 AWS 키의 저장소·워크플로 파일 저장, 품질 게이트를 통과하지 않은 산출물의 배포, 그리고 Accepted ADR로 확정되지 않았거나 승인된 운영 범위를 벗어난 ALB·Blue-Green 자동화의 선제 구현을 금지한다.

## 12. 구현 및 운영 영향

빌드 캐시, Testcontainers 실행 환경, 테스트 결과·산출물 보관 기간, OIDC trust policy(어떤 저장소·브랜치가 어떤 IAM Role을 assume할 수 있는지)가 필요하다. Terraform user-data `templatefile()` 렌더링처럼 이미지 생성 전에 확인해야 하는 인프라 계약은 고정 Terraform 1.6.6 `terraform-contract` job으로 별도 게이트를 둔다. CodeDeploy 배포는 생성 직후 실행별 deployment ID를 S3 pointer에 기록하고, 취소 cleanup job이 이를 읽어 중단·terminal 상태 확인을 수행하므로 OIDC role에 해당 S3 Put/Get과 `StopDeployment` 권한도 필요하다.

## 13. 검증 방법

의도적으로 실패하는 커밋을 올려 단계 완료 후보에서 차단되는지 확인한다. 캐시를 지운 깨끗한 상태에서 재빌드가 동일한 결과를 내는지와 테스트 결과가 기록되는지 확인하고, Terraform contract fixture 실패가 이미지 생성·배포 후보를 차단하는지 검증한다. OIDC, 이미지 생성·ECR push, 운영 배포 수동 승인, 배포 후 Smoke Test, CodeDeploy 취소 cleanup, 직전 이미지 복구는 M2에서 검증한다.

## 14. 재검토 조건

배포 토폴로지가 단일 EC2에서 다중 인스턴스·ALB·Blue-Green으로 확장되거나, 운영 배포까지 무승인 자동화할 필요가 승인되거나, CI 실행 비용이 15만 원 예산 제약을 초과하거나, GitHub Actions 가용성 문제가 반복될 때 재검토한다.

## 15. 관련 문서

- [테스트 ADR](../quality/test-001-automation-strategy.md)
- [비밀정보 ADR](../security/sec-001-secrets-workload-identity.md)
- [ADR Backlog](../adr-backlog.md#5-범위-충돌-검토)
