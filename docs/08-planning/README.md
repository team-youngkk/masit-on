---
related_documents:
  - mvp-2day-implementation-plan.md
  - m2-deployment-plan.md
  - ../00-overview/scope.md
  - ../02-analysis/mvp-workstreams.md
  - ../03-team/ownership.md
---

# 맛잇온 구현 계획

| 문서 | 목적 |
|---|---|
| [MVP 구현 계획](mvp-2day-implementation-plan.md) | 4명이 순서와 선행 관계에 따라 수행할 Task, 의존성, 검증과 완료 조건 |
| [MVP 로컬 실행·회귀 검증 결과](mvp-local-verification.md) | `T-14`가 실행한 필수 명령과 결과, 완료 정의 판정, 알려진 위험 |
| [M2 초기 운영 배포 계획](m2-deployment-plan.md) | M2 Task 분해, 선행 관계, 검증·복구 절차, 확인 필요 항목 |
| [M2 인스턴스 사양과 월 비용 산정](m2-cost-and-sizing.md) | `M2-01`이 산정한 EC2·RDS·Redis 사양과 월 예상 비용, 예산 목표 대조 |
| [M2 자원 생성 기록](m2-provisioning-record.md) | Task별로 생성한 AWS 자원 식별자와 완료 조건 검증 결과 |

구현 계획은 제품 범위, 요구사항, API·데이터 명세와 ADR을 변경하지 않는다. 상위 문서가 바뀌면 영향받는 Task와 완료 조건을 함께 갱신한다.
