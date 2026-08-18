---
related_documents:
  - mvp-2day-implementation-plan.md
  - m2-deployment-plan.md
  - second-expansion-baseline-review.md
  - second-expansion-scope-and-terminology.md
  - second-expansion-test-matrix.md
  - second-expansion-browser-verification.md
  - third-expansion-baseline-review.md
  - third-expansion-scope-and-terminology.md
  - third-expansion-evaluation-strategy.md
  - third-expansion-test-matrix.md
  - third-expansion-implementation-plan.md
  - third-expansion-task-breakdown.md
  - third-expansion-browser-verification.md
  - third-expansion-ai-candidate-loss-analysis.md
  - third-expansion-ai-candidate-registration-assist.md
  - expansion-2-implementation-plan.md
  - expansion-2-task-breakdown.md
  - ../00-overview/scope.md
  - ../02-analysis/mvp-workstreams.md
  - ../03-team/ownership.md
  - issue-200-application-port-binding.md
  - issue-207-natural-language-load-model.md
  - deployment-hardening-impact-review.md
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
| [2차 확장 브라우저 검증 기록](second-expansion-browser-verification.md) | `TST-E2-E2E-001`의 확인 환경·화면 폭·접근성 결과와 미검증으로 남는 항목 |
| [3차 확장 선행 상태 검토](third-expansion-baseline-review.md) | 1·2차 확장 운영·데이터·외부 비용·비동기 기반과 2차 성능 미측정의 3차 완료 게이트 승계 검토 |
| [3차 확장 범위와 용어 결정](third-expansion-scope-and-terminology.md) | 자연어 검색·AI 영상 정보 추출·동선 및 코스 추천의 초기 포함·제외 범위와 용어 확정 |
| [3차 확장 평가 주도 개발 전략](third-expansion-evaluation-strategy.md) | 기능별 평가, 골든 데이터, 정답 판정, 품질 목표, 활성화·롤백과 개인정보 보호 환류 기준 |
| [3차 확장 테스트 추적표](third-expansion-test-matrix.md) | `FR`·`BR`·`NFR`·`EVAL`을 자동화·브라우저·운영 증거와 연결 |
| [3차 확장 구현 계획](third-expansion-implementation-plan.md) | AI 자동 등록·태그 생성·예외 보정·Worker·평가·운영 게이트의 구현 순서와 Task |
| [3차 확장 E3 Task 분해](third-expansion-task-breakdown.md) | WS-14~WS-16·QUALITY-EVAL·OPS의 담당·선행·테스트·완료 증거 |
| [3차 확장 브라우저 검증 기록](third-expansion-browser-verification.md) | `TST-E3-E2E-001`의 확인 환경·화면 폭·키보드·사용자 여정 결과와 미검증으로 남는 항목 |
| [운영 애플리케이션 포트 바인딩 계획](issue-200-application-port-binding.md) | 운영 Spring Boot·Next.js 포트의 loopback 고정, Nginx 우회 차단, 배포 후 검증 명령 |
| [3차 확장 AI 후보 손실 분석](third-expansion-ai-candidate-loss-analysis.md) | 등록 0건의 원인인 태그 Schema 불일치·복수 후보 폐기 결함과 장소 확정 전제의 남은 결정 |
| [3차 확장 AI 후보 등록 보조 설계](third-expansion-ai-candidate-registration-assist.md) | 후보 선택 화면과 카카오 장소 검색 자동 입력의 API·화면 설계, 소유자 합의 필요 항목. `PROPOSED` |
| [자연어 검색 부하 검증 모델](issue-207-natural-language-load-model.md) | 요청 제한과 충돌한 부하 모델을 계약 검증·포화 관찰로 분리한 기준, 실행 명령, 재측정 시 보존할 증적 |
| [배포 고도화 비용·일정 영향 검토](deployment-hardening-impact-review.md) | ADR-DEPLOY-002 3.1절이 착수 조건으로 남긴 비용·일정 영향 산정과 구성별 예산 대조, 착수 권고. `PROPOSED` |

구현 계획은 제품 범위, 요구사항, API·데이터 명세와 ADR을 변경하지 않는다. 상위 문서가 바뀌면 영향받는 Task와 완료 조건을 함께 갱신한다.
