---
id: ADR-DEPLOY-001
title: 단계별 로컬 검증과 최종 AWS 배포 순서
status: Superseded
decision_date: 2026-07-27
owners:
  - 이우람
related_requirements:
  - NFR-DEPLOYMENT-001
  - NFR-DEPLOYMENT-002
  - NFR-DEPLOYMENT-003
  - NFR-DEPLOYMENT-004
related_documents:
  - ../../00-overview/scope.md
  - ../../00-overview/service-overview.md
  - ../../01-requirements/non-functional-requirements.md
  - ../../02-analysis/mvp-workstreams.md
  - ../../06-architecture/technology-policy.md
  - ci-001-github-actions-quality-gate.md
  - runtime-001-docker.md
  - ../quality/obs-001-logging-observability.md
supersedes: []
superseded_by: ADR-DEPLOY-002
---

# ADR-DEPLOY-001 단계별 로컬 검증과 최종 AWS 배포 순서

## 1. 상태

Superseded by [ADR-DEPLOY-002](deploy-002-validation-deployment-before-expansion.md)

본문의 단계 명칭은 2026-07-28 저장소 전체 표기 통일에 맞춰 `1차 MVP` → `MVP`, `2차~4차 확장` → `1차~3차 확장`으로 갱신했다([README 9절](../README.md#9-변경-및-대체-절차) 용어 통일 예외). 결정 내용, 근거와 적용 시점은 결정 당시 그대로이며 변경하지 않았다. 배포 순서 자체는 ADR-DEPLOY-002가 대체했다.

## 2. 결정 요약

MVP와 1차부터 3차까지의 확장 단계는 로컬 Docker 환경에서 구현·통합 검증한다. AWS 운영 배포는 모든 확장 단계가 끝난 뒤 별도의 최종 배포 단계에서 한 번 수행한다.

## 3. 배경

기존 문서는 단일 EC2·ECR·RDS·CloudWatch 구성을 MVP의 운영 완료 조건으로 두었다. 팀은 MVP를 4명이 2026년 7월 27일부터 28일까지 구현하는 일정으로 확정했고, 이 기간에는 핵심 기능과 로컬 통합 검증에 집중하기로 결정했다. 회원·개인화·지도 등의 1차 확장, 컬렉션·알림 등의 2차 확장, 자연어 검색·AI·코스 추천 등의 3차 확장은 순차 진행하되 각 단계에서 AWS 운영 환경을 만들지 않는다.

## 4. 결정

- MVP 완료 조건은 Next.js, Spring Boot, PostgreSQL과 Redis의 로컬 Docker 통합 실행, 핵심 자동화 테스트와 사용자 흐름 검증이다.
- 1차·2차·3차 확장도 각 단계의 기능과 로컬 통합 검증을 완료 조건으로 한다.
- EC2, ECR, RDS, CloudWatch, 운영 비밀정보, 운영 백업·알림·복구 리허설은 최종 배포 단계에서 활성화한다.
- 단일 EC2, GitHub Actions → ECR → EC2, 운영 RDS와 CloudWatch라는 기존 기술 선택은 폐기하지 않고 적용 시점만 최종 배포로 미룬다.
- GitHub Actions의 빌드·자동화 테스트 품질 게이트는 MVP부터 사용한다.

## 5. 영향

- MVP Workstream에서 AWS 프로비저닝·배포·운영 검증 Task를 제거한다.
- 로컬 Docker 실행과 헬스체크는 MVP의 필수 인수 항목으로 유지한다.
- 운영 환경 전용 NFR과 ADR은 최종 배포 단계까지 비활성 상태로 관리한다.
- 최종 배포 착수 전에는 1차부터 3차까지의 실제 아키텍처 변화와 비용을 반영해 AWS 토폴로지를 다시 검토한다.

## 6. 검증

- 각 단계의 완료 체크리스트에 AWS 운영 리소스가 포함되지 않았는지 확인한다.
- 로컬 Docker 환경에서 해당 단계의 전체 사용자 흐름과 저장소·외부 연동 테스트가 통과하는지 확인한다.
- 최종 배포 계획에는 EC2·ECR·RDS·CloudWatch, 비밀정보, Smoke Test, 백업과 복구 절차가 모두 포함되는지 확인한다.

## 7. 재검토 조건

확장 완료 전 실제 사용자 검증을 위해 공개 환경이 필요해지거나, 외부 연동 제공자의 콜백·네트워크 정책 때문에 로컬 검증이 불가능해지거나, 팀이 별도 스테이징 배포를 승인하면 배포 순서를 재검토한다.
