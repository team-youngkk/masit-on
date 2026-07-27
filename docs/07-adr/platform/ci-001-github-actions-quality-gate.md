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

GitHub Actions에서 고정 런타임으로 빌드·자동 테스트를 실행하고 실패한 변경을 운영 배포 후보에서 차단한다.

## 3. 배경

모든 Workstream 변경이 같은 재현 빌드와 테스트 기준을 통과해야 한다.

4명(이우람·양성훈·박진영·김인안)이 [WS-01](../../02-analysis/mvp-workstreams.md#5-ws-01-맛집-탐색)~[WS-04](../../02-analysis/mvp-workstreams.md#8-ws-04-관리자-데이터-등록)를 독립적으로 개발하며([scope.md](../../00-overview/scope.md) 범위 경계 규칙 6), 각자의 변경이 다른 Workstream을 깨뜨리지 않는지 사람이 매번 수동으로 확인할 인원과 일정 여유는 없다. 배포 토폴로지는 단일 EC2 인스턴스(Nginx 리버스 프록시 + Next.js + Spring Boot, 장애 시 수동 복구)로 결정되었고, ALB·ASG·Blue-Green은 Post-MVP로 보류되었다([technology-policy.md](../../06-architecture/technology-policy.md) 13장, [adr-backlog.md](../adr-backlog.md) 5장). GitHub Actions는 빌드·테스트·컨테이너 이미지 생성·ECR push까지 자동화하고, 운영 EC2 배포는 지정 담당자의 수동 승인을 받은 뒤 실행한다.

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

GitHub Actions를 CI 기준으로 사용한다. AWS 접근이 필요한 단계는 OIDC를 사용한다. ECR·Green·ALB 전환 자동화는 배포 토폴로지 결정 전 확정하지 않는다.

## 7. 선택 근거

GitHub Actions는 이미 사용 중인 저장소에 내장되어 있어 별도 서버 운영 비용이 없고, OIDC로 AWS 자격 증명을 단기 발급받을 수 있어 배포 관련 장기 키를 저장소에 두지 않아도 된다([ADR-SEC-001](../security/sec-001-secrets-workload-identity.md)과 일치). 반복 가능한 빌드·테스트·이미지 생성·ECR push는 자동화하고 실제 운영 반영은 수동 승인으로 보호하는 것이 단일 EC2·수동 복구 토폴로지와 일치한다. ALB·Blue-Green 전환 자동화는 토폴로지가 확장될 때 별도로 설계한다.

## 8. 트레이드오프

GitHub Actions를 CI 기준으로 삼으면 자체 서버를 운영하지 않아도 되는 대신, 워크플로 실행 시간과 캐시 효율, GitHub Actions 자체의 가용성에 의존하게 된다. 4명이 동시에 여러 PR을 올리는 시점이 몰리면(예: 통합 직전) 동시 실행 한도나 실행 시간 과금이 15만 원 예산에 영향을 줄 수 있다. 배포 후 Smoke Test는 자동 실행하고 실패 시 파이프라인을 실패로 끝내며, 운영자가 직전 검증 이미지로 수동 복구한다.

## 9. 적용 범위

백엔드(Spring Boot, JDK 21.0.12)·프론트엔드(Node.js 24.18.0, Next.js) 빌드, 단위·통합·계약 테스트([ADR-TEST-001](../quality/test-001-automation-strategy.md))와 컨테이너 이미지 검증에 적용한다. 단일 EC2 인스턴스로의 배포 실행 자체(ECR 푸시 이후 단계)는 이 ADR의 품질 게이트 범위에 포함하되, ALB·Blue-Green 전환 자동화는 포함하지 않는다.

## 10. 강제 규칙

고정 런타임(JDK 21.0.12, Node 24.18.0)·Wrapper(Gradle 8.14.3)·잠금 파일을 사용하고, 단위·통합·계약 테스트 중 하나라도 실패하면 해당 커밋을 배포 후보에서 제외한다. AWS 접근이 필요한 단계(ECR 푸시 등)는 GitHub Actions OIDC로 발급된 단기 자격 증명만 사용한다.

## 11. 금지 사항

실패 무시(스킵·재시도 남용 포함), 운영 비밀·장기 AWS 키의 저장소·워크플로 파일 저장, 품질 게이트를 통과하지 않은 산출물의 배포, 그리고 배포 토폴로지가 확정되지 않은 ALB·Blue-Green 자동화의 선제 구현을 금지한다.

## 12. 구현 및 운영 영향

빌드 캐시, Testcontainers 실행 환경, 테스트 결과·산출물 보관 기간, OIDC trust policy(어떤 저장소·브랜치가 어떤 IAM Role을 assume할 수 있는지)가 필요하다. 단일 EC2 배포이므로 배포 단계 자체는 상대적으로 단순하지만(ECR에 이미지를 올리고 EC2에서 받아 실행), 장애 시 수동 복구 절차([technology-policy.md](../../06-architecture/technology-policy.md) 13장)와 CI 산출물(배포 가능한 이미지 태그)이 어떻게 연결되는지는 별도 운영 문서에서 다룬다.

## 13. 검증 방법

의도적으로 실패하는 커밋(테스트 실패, 빌드 오류)을 올려 배포 후보에서 차단되는지 확인한다. 캐시를 지운 깨끗한 상태에서 재빌드가 동일한 결과를 내는지, 테스트 결과·커버리지가 기록되는지, OIDC로 발급된 자격 증명이 필요한 권한 범위를 넘지 않는지 확인한다. 이미지 생성·ECR push, 운영 배포 수동 승인, 배포 후 Smoke Test와 직전 이미지 수동 복구도 검증한다.

## 14. 재검토 조건

배포 토폴로지가 단일 EC2에서 다중 인스턴스·ALB·Blue-Green으로 확장되거나, 운영 배포까지 무승인 자동화할 필요가 승인되거나, CI 실행 비용이 15만 원 예산 제약을 초과하거나, GitHub Actions 가용성 문제가 반복될 때 재검토한다.

## 15. 관련 문서

- [테스트 ADR](../quality/test-001-automation-strategy.md)
- [비밀정보 ADR](../security/sec-001-secrets-workload-identity.md)
- [ADR Backlog](../adr-backlog.md#5-범위-충돌-검토)
