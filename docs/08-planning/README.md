---
related_documents:
  - mvp-2day-implementation-plan.md
  - m2-deployment-plan.md
  - second-expansion-baseline-review.md
  - second-expansion-scope-and-terminology.md
  - second-expansion-test-matrix.md
  - expansion-2-implementation-plan.md
  - expansion-2-task-breakdown.md
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
| [1차 확장 구현 계획](expansion-1-implementation-plan.md) | WS-05~WS-08과 `OPS-VALIDATION`의 FE 실행 단위·선행 관계·통합 순서 |
| [1차 확장 최종 Task 분해](expansion-1-task-breakdown.md) | `E1-T01`~`E1-T13`의 담당·리뷰·계약·완료 조건. 제한 공개 전환은 `E1-T13` |
| [2차 확장 선행 상태 검토](second-expansion-baseline-review.md) | 1차 확장 계약·구현과 행동 데이터·비동기 알림 운영 기반을 확인하고 2차 확장 구현 착수 게이트 정의 |
| [2차 확장 범위와 용어 결정](second-expansion-scope-and-terminology.md) | 컬렉션·인기·큐레이션·제보·신고·사용자 알림의 승인된 초기 범위와 제외 범위 정의 |
| [2차 확장 테스트 추적표](second-expansion-test-matrix.md) | 21개 기능 요구사항과 BR·NFR을 자동화·인수 테스트 묶음으로 연결 |
| [2차 확장 성능 검증 결과](second-expansion-performance-verification.md) | `NFR-PERFORMANCE-006` 정상 부하 측정의 환경·기준 데이터·재현 절차와 판정 결과 |
| [2차 확장 구현 계획](expansion-2-implementation-plan.md) | 상위 계약 이후 기준선·병렬 기능 경로·알림·통합의 구현 순서 정의 |
| [2차 확장 최종 Task 분해](expansion-2-task-breakdown.md) | `E2-T01`~`E2-T15` 실행 Task와 선행·병렬·완료 조건 정의. 범위 밖 푸시 `E2-T12`는 미생성 |

구현 계획은 제품 범위, 요구사항, API·데이터 명세와 ADR을 변경하지 않는다. 상위 문서가 바뀌면 영향받는 Task와 완료 조건을 함께 갱신한다.
